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

final case class CardField private (t: CardFieldType[_], value: Any, private val disambiguateConstructors: Boolean) {
  def serialize: JsValue = Json.obj(
    "type"  -> t.typeName,
    "value" -> t.asInstanceOf[CardFieldType[Any]].serialize(value)
  )

  def toLua(L: LuaState) = t.asInstanceOf[CardFieldType[Any]].toLua(L, value)
}
object CardField {
  def apply[T](t: CardFieldType[T], value: T): CardField = new CardField(t, value, false)

  val Nil = CardField(CardFieldType.Nil, ())
  def deserialize(v: JsValue): CardField = {
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
  def fields: Rx[Map[String, CardField]]
  def getField(name: String, default: => CardField = sys.error("field does not already exist")): Rx[CardField]
}
final class CardData {
  private class CardDataSectionImpl[K : Reads : Writes] {
    private val fieldsVar = new mutable.HashMap[K, Var[CardField]]

    def toMap = fieldsVar.mapValues(_.now).toMap
    val fields = Rx.unsafe { fieldsVar.mapValues(_()).toMap }

    def init(js: JsValue) = for(t <- js.as[Seq[JsValue]]) {
      val k = (t \ "key").as[K]
      val v = CardField.deserialize((t \ "value").as[JsValue])
      this.fieldsVar.put(k, Var(v))
      fields.recalc()
    }
    def getField(name: K, default: => CardField = CardField.Nil): Var[CardField] = {
      if(!fieldsVar.contains(name)) {
        fieldsVar.put(name, Var(default))
        fields.recalc()
      }
      fieldsVar(name)
    }

    def serialize = fieldsVar.map(x => Json.obj("key" -> x._1, "value" -> x._2.now.serialize))

    def kill(): Unit = {
      for((_, rx) <- fieldsVar) rx.kill()
      fields.kill()
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