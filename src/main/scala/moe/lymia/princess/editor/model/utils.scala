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

package moe.lymia.princess.editor.model

import java.nio.file.{Files, Path}
import java.util.UUID

import moe.lymia.princess.util.IOUtils
import play.api.libs.json._
import rx._

import scala.collection.mutable

trait PathSerializable {
  val extension = ""
  def writeTo(path: Path): Unit = { }
  def readFrom(path: Path): Unit = { }
}

trait JsonSerializable {
  def serialize: JsObject = Json.obj()
  def deserialize(js: JsObject): Unit = { }
}

trait DirSerializable extends PathSerializable {
  override def writeTo(path: Path): Unit = {
    super.writeTo(path)
    Files.createDirectories(path)
  }
  override def readFrom(path: Path): Unit = super.readFrom(path)
}

trait JsonPathSerializable extends JsonSerializable with PathSerializable {
  override val extension = ".json"
  override def writeTo(path: Path): Unit = {
    super.writeTo(path)
    SerializeUtils.writeJson(path, serialize)
  }
  override def readFrom(path: Path): Unit = {
    super.readFrom(path)
    deserialize(SerializeUtils.readJson(path).as[JsObject])
  }
}

trait ModifyListener extends {
  def onModified(): Unit
}
trait TrackModifyTime extends JsonSerializable with ModifyListener {
  var createTime = System.currentTimeMillis()
  var modifyTime = System.currentTimeMillis()

  private var listeners = new mutable.ArrayBuffer[ModifyListener]()
  def addModifyListener(listener: ModifyListener) = listeners += listener
  def removeModifyListener(listener: ModifyListener) = listeners = listeners.filter(_ ne listener)
  def modified() = {
    listeners.foreach(_.onModified())
    modifyTime = System.currentTimeMillis()
  }

  override def onModified(): Unit = modified()

  override def serialize = super.serialize ++ Json.obj("create" -> createTime, "modify" -> modifyTime)
  override def deserialize(js: JsObject) = {
    super.deserialize(js)
    createTime = (js \ "create").as[Long]
    modifyTime = (js \ "modify").as[Long]
  }
}

trait HasDataStore extends JsonSerializable {
  protected val project: Project

  val fields = new DataStore

  override def serialize = super.serialize ++ Json.obj("fields" -> fields.serialize)
  override def deserialize(js: JsObject) = {
    super.deserialize(js)
    fields.deserialize((js \ "fields").as[JsValue])
  }
}

final class UUIDMapVar[T <: PathSerializable](newFn: UUID => T)
  extends Var.Base[Map[UUID, T]](Map.empty) with PathSerializable {

  def create(): (UUID, T) = {
    val uuid = UUID.randomUUID()
    val tuple = uuid -> newFn(uuid)
    update(now + tuple)
    tuple
  }
  def get(uuid: UUID)(implicit ctx: Ctx.Data) = apply().get(uuid)

  override def writeTo(path: Path): Unit = {
    super.writeTo(path)
    SerializeUtils.writeMap(path, now)(_.toString)
  }
  override def readFrom(path: Path): Unit = {
    super.readFrom(path)
    update(SerializeUtils.readMap(path)(newFn, (_ : UUID).toString))
  }
}

private[model] trait RefCount {
  private var refCount0 = 0
  private[model] def refCount = refCount0
  private[model] def ref()    = refCount0 = refCount + 1
  private[model] def unref()  = refCount0 = refCount - 1
}

trait DataStoreModifyListener { this: HasDataStore with TrackModifyTime =>
  fields.addChangeListener((_, _) => modified())
}
trait HasModifyTimeDataStore extends HasDataStore with TrackModifyTime with DataStoreModifyListener

object SerializeUtils {
  def writeJson(path: Path, json: JsValue) =
    IOUtils.writeFile(path, Json.prettyPrint(json))
  def writeMap[K : Writes, V <: PathSerializable](path: Path, map: Map[K, V])(fileName: K => String) = {
    if(Files.exists(path)) IOUtils.deleteDirectory(path)
    Files.createDirectories(path)
    writeJson(path.resolve("_index.json"), Json.toJson(map.keys.map(fileName)))
    for((id, entry) <- map) entry.writeTo(path.resolve(s"${fileName(id)}${entry.extension}"))
  }

  def readJson(path: Path) = Json.parse(IOUtils.readFileAsString(path))
  def readMap[K: Reads, V <: PathSerializable](path: Path)(newValue: K => V, fileName: K => String) = {
    val list = readJson(path.resolve("_index.json")).as[Seq[K]]
    (for(k <- list) yield k -> {
      val v = newValue(k)
      v.readFrom(path.resolve(s"${fileName(k)}${v.extension}"))
      v
    }).toMap
  }
}