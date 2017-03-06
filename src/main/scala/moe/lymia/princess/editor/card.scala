/*
 * Copyright (c) 2017 Lymia Alusyia <lymia@lymiahugs.com>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package moe.lymia.princess.editor

import java.util.UUID

import moe.lymia.princess.lua._
import play.api.libs.json._

import scala.collection.mutable
import scala.reflect.ClassTag
import rx._

sealed abstract class CardFieldType[T : ClassTag : Reads : Writes : ToLua]() {
  val typeName = implicitly[ClassTag[T]].toString()

  def serialize(t: T): JsValue = Json.toJson(t)
  def deserialize(v: JsValue): T = v.as[T]

  def toLua  (L: LuaState, t: T) = t.toLua(L)
}
object CardFieldType {
  private implicit object LuaUnit extends LuaParameter[Unit] {
    override def toLua(t: Unit): LuaObject = LuaNil
    override def fromLua(L: Lua, v: Any, source: => Option[String]): Unit = Unit
  }
  private implicit object UnitFormat extends Format[Unit] {
    override def writes(o: Unit): JsValue = JsNull
    override def reads(json: JsValue): JsResult[Unit] = JsSuccess(())
  }

  case object Nil extends CardFieldType[Unit]
  case object Int extends CardFieldType[Int]
  case object Double extends CardFieldType[Double]
  case object String extends CardFieldType[String]
  case object Boolean extends CardFieldType[Boolean]

  val allTypes: Seq[CardFieldType[_]] = Seq(Nil, Int, Double, String, Boolean)
  val typeMap = allTypes.map(x => (x.typeName -> x).asInstanceOf[(String, CardFieldType[_])]).toMap
}
trait CardFieldImplicits {
  implicit val CardFieldTypeNil     = CardFieldType.Nil
  implicit val CardFieldTypeInt     = CardFieldType.Int
  implicit val CardFieldTypeDouble  = CardFieldType.Double
  implicit val CardFieldTypeString  = CardFieldType.String
  implicit val CardFieldTypeBoolean = CardFieldType.Boolean
}

final case class CardField[T](t: CardFieldType[T], value: T) {
  def serialize: JsValue = Json.obj(
    "type"  -> t.typeName,
    "value" -> t.serialize(value)
  )

  def toLua(L: LuaState) = t.toLua(L, value)
}
object CardField {
  val Nil = CardField(CardFieldType.Nil, ())
  def deserialize(v: JsValue) = {
    val typeName = (v \ "type").as[String]
    CardFieldType.typeMap.get(typeName) match {
      case Some(t) =>
        val t2 = t.asInstanceOf[CardFieldType[Any]]
        CardField(t2.asInstanceOf, t2.deserialize((v \ "value").as[JsValue]))
      case None    => sys.error(s"unknown field type $typeName")
    }
  }
}

final case class EditorField[T : CardFieldType](id: UUID) {
  val t: CardFieldType[T] = implicitly[CardFieldType[T]]
}

sealed trait CardDataSection {
  val notifier: Rx[Unit]
  def getField(name: String, default: => CardField[_] = sys.error("field does not already exist")): Rx[CardField[_]]
}
final class CardData {
  val notifier = Rx.unsafe { () }

  private class CardDataSectionImpl[K : Reads : Writes] {
    val notifier = Rx.unsafe { () }
    private val superNotifier = {
      import Ctx.Owner.Unsafe._
      CardData.this.notifier.flatMap(_ => notifier)
    }

    private val fields    = new mutable.HashMap[K, Var[CardField[_]]]
    private val derivedRx = new mutable.ArrayBuffer[Rx[_]]

    private def newVar(default: CardField[_]) = {
      import Ctx.Owner.Unsafe._
      val rx = Var[CardField[_]](default)
      derivedRx.append(rx.flatMap(_ => notifier))
      rx
    }

    def init(js: JsValue) = for(t <- js.as[Seq[JsValue]]) {
      val k = (t \ "key").as[K]
      val v = CardField.deserialize((t \ "value").as[JsValue])
      this.fields.put(k, newVar(v))
    }
    def getField(name: K, default: => CardField[_] = CardField.Nil) =
      fields.getOrElseUpdate(name, newVar(default))
    def toMap = fields.mapValues(_.now).toMap

    def serialize = fields.map(x => Json.obj("key" -> x._1, "value" -> x._2.now.serialize))

    def kill(): Unit = {
      for((_, rx) <- fields) rx.kill()
      for(rx <- derivedRx) rx.kill()
      notifier.kill()
      superNotifier.kill()
    }
  }

  private val styleFields  = new CardDataSectionImpl[String] with CardDataSection
  private val cardFields   = new CardDataSectionImpl[String] with CardDataSection
  private val editorFields = new CardDataSectionImpl[UUID]

  def getEditorField[T](field: EditorField[T])(implicit ctx: Ctx.Owner) =
    editorFields.getField(field.id, CardField.Nil).filter(_.t == field.t).map(_.value.asInstanceOf[T])
  def setEditorField[T](field: EditorField[T], v: T) =
    editorFields.getField(field.id, CardField.Nil).update(CardField(field.t, v))

  private def init(json: JsValue) = {
    this.styleFields .init((json \ "styleFields" ).as[JsValue])
    this.cardFields  .init((json \ "cardFields"  ).as[JsValue])
    this.editorFields.init((json \ "editorFields").as[JsValue])
  }

  def serialize = Json.obj(
    "styleFields"  -> styleFields .serialize,
    "cardFields"   -> cardFields  .serialize,
    "editorFields" -> editorFields.serialize
  )

  def style    = styleFields : CardDataSection
  def cardData = cardFields  : CardDataSection

  def kill(): Unit = {
    notifier.kill()
    styleFields.kill()
    cardFields.kill()
  }
}
object CardData {
  def deserialize(js: JsValue) = {
    val data = new CardData
    data.init(js)
    data
  }
}