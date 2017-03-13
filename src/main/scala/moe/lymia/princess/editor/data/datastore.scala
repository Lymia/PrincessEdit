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

package moe.lymia.princess.editor.data

import java.util.UUID

import moe.lymia.princess.lua._
import play.api.libs.json._
import rx._

import scala.collection.mutable

sealed abstract class DataFieldType[T : Reads : Writes : ToLua : FromLua](val typeName: String) {
  def serialize(t: T): JsValue = Json.toJson(t)
  def deserialize(v: JsValue): T = v.as[T]

  def toLua(L: LuaState, t: T): Any = t.toLua(L)
  def fromLua(L: LuaState, a: Any): T = a.fromLua[T](L)
}
object DataFieldType {
  private implicit object LuaUnit extends LuaParameter[Unit] {
    override def toLua(t: Unit): LuaObject = LuaNil
    override def fromLua(L: Lua, v: Any, source: => Option[String]): Unit = Unit
  }
  private implicit object UnitFormat extends Format[Unit] {
    override def writes(o: Unit): JsValue = JsNull
    override def reads(json: JsValue): JsResult[Unit] = JsSuccess(())
  }

  case object Nil extends DataFieldType[Unit]("nil")
  case object Int extends DataFieldType[Int]("int")
  case object Double extends DataFieldType[Double]("double")
  case object String extends DataFieldType[String]("string")
  case object Boolean extends DataFieldType[Boolean]("boolean")

  val allTypes: Seq[DataFieldType[_]] = Seq(Nil, Int, Double, String, Boolean)
  val typeMap = allTypes.map(x => (x.typeName -> x).asInstanceOf[(String, DataFieldType[_])]).toMap
}
trait DataFieldImplicits {
  implicit val DataFieldTypeNil     = DataFieldType.Nil
  implicit val DataFieldTypeInt     = DataFieldType.Int
  implicit val DataFieldTypeDouble  = DataFieldType.Double
  implicit val DataFieldTypeString  = DataFieldType.String
  implicit val DataFieldTypeBoolean = DataFieldType.Boolean
}

final case class DataField private(t: DataFieldType[_], value: Any, private val disambiguateConstructors: Boolean) {
  def serialize: JsValue = Json.arr(t.typeName, t.asInstanceOf[DataFieldType[Any]].serialize(value))
  def toLua(L: LuaState) = t.asInstanceOf[DataFieldType[Any]].toLua(L, value)
  override def toString: String = s"DataField($t, $value)"
}
object DataField {
  def apply[T](t: DataFieldType[T], value: T): DataField = new DataField(t, value, false)

  val Nil = DataField(DataFieldType.Nil, ())
  def deserialize(v: JsValue): DataField = {
    val Seq(typeName, data) = v.as[Seq[JsValue]]
    DataFieldType.typeMap.get(typeName.as[String]) match {
      case Some(t) =>
        val t2 = t.asInstanceOf[DataFieldType[Any]]
        DataField(t2, t2.deserialize(data))
      case None    => sys.error(s"unknown field type $typeName")
    }
  }
}

final case class EditorField[T : DataFieldType](id: UUID) {
  val t: DataFieldType[T] = implicitly[DataFieldType[T]]
}

private class DataSection {
  protected val fieldsVar = new mutable.HashMap[String, Var[DataField]]
  protected def setFieldVar(name: String, field: Var[DataField]) = fieldsVar.put(name, field)

  def init(js: JsValue) = for((k, v) <- js.as[Map[String, JsValue]])
    setFieldVar(k, Var(DataField.deserialize(v)))
  def getField(name: String, default: => DataField = DataField.Nil): Var[DataField] = {
    if(!fieldsVar.contains(name)) setFieldVar(name, Var(default))
    fieldsVar(name)
  }

  def serialize = Json.toJson(fieldsVar.mapValues(_.now.serialize))
  def kill(): Unit = for((_, rx) <- fieldsVar) rx.kill()
}
private class DataSectionWithFields extends DataSection {
  val fields = Rx.unsafe { fieldsVar.mapValues(_()).toMap }
  override protected def setFieldVar(name: String, field: Var[DataField]) = {
    val ret = super.setFieldVar(name, field)
    fields.recalc()
    ret
  }
  override def kill(): Unit = {
    super.kill()
    fields.kill()
  }
}

final class DataStore {
  private val dataFields   = new DataSectionWithFields
  private val editorFields = new DataSection

  val data = dataFields.fields
  def getDataField(name: String, default: => DataField = DataField.Nil) = dataFields.getField(name, default)

  def getEditorField[T](field: EditorField[T])(implicit ctx: Ctx.Owner) =
    editorFields.getField(field.id.toString, DataField.Nil).filter(_.t == field.t).map(_.value.asInstanceOf[T])
  def setEditorField[T](field: EditorField[T], v: T) =
    editorFields.getField(field.id.toString, DataField.Nil).update(DataField(field.t, v))

  private def init(json: JsValue) = {
    this.dataFields  .init((json \ "dataFields"  ).as[JsValue])
    this.editorFields.init((json \ "editorFields").as[JsValue])
  }

  def serialize = Json.obj(
    "dataFields"   -> dataFields  .serialize,
    "editorFields" -> editorFields.serialize
  )

  def kill() = {
    dataFields.kill()
    editorFields.kill()
  }
}
object DataStore {
  def deserialize(js: JsValue) = {
    val data = new DataStore
    data.init(js)
    data
  }
}