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

import moe.lymia.princess.util.IOUtils
import play.api.libs.json._

trait TrackModifyTime {
  var createTime = System.currentTimeMillis()
  var modifyTime = System.currentTimeMillis()

  def modified() = modifyTime = System.currentTimeMillis()

  protected def serializeModifyTime = Json.obj("create" -> createTime, "modify" -> modifyTime)
  protected def deserializeModifyTime(js: JsValue) = {
    createTime = (js \ "create").as[Long]
    modifyTime = (js \ "modify").as[Long]
  }
}

trait HasDataStore extends TrackModifyTime {
  protected val project: Project

  val fields = new DataStore
  fields.addChangeListener((_, _) => {
    project.modified()
    modified()
  })

  protected def serializeDataStore = Json.obj("fields" -> fields.serialize, "time" -> serializeModifyTime)
  protected def deserializeDataStore(js: JsValue) = {
    fields.deserialize((js \ "fields").as[JsValue])
    deserializeModifyTime((js \ "time").as[JsValue])
  }
}

trait JsonSerializable {
  def serialize: JsValue
  def deserialize(js: JsValue): Unit
}

trait DirSerializable {
  def writeTo(path: Path): Unit
  def readFrom(path: Path): Unit
}

object SerializeUtils {
  def writeJson(path: Path, json: JsValue) =
    IOUtils.writeFile(path, Json.prettyPrint(json))
  def writeJsonMap[K : Writes, V <: JsonSerializable](path: Path, map: Map[K, V])(fileName: K => String) = {
    if(Files.exists(path)) IOUtils.deleteDirectory(path)
    Files.createDirectories(path)
    writeJson(path.resolve("index.json"), Json.toJson(map.keys.map(fileName)))
    for((id, entry) <- map) writeJson(path.resolve(s"${fileName(id)}.json"), entry.serialize)
  }
  def writeDirMap[K : Writes, V <: DirSerializable](path: Path, map: Map[K, V])(fileName: K => String) = {
    if(Files.exists(path)) IOUtils.deleteDirectory(path)
    Files.createDirectories(path)
    writeJson(path.resolve("index.json"), Json.toJson(map.keys.map(fileName)))
    for((id, entry) <- map) entry.writeTo(path.resolve(fileName(id)))
  }

  def readJson(path: Path) = Json.parse(IOUtils.readFileAsString(path))
  def readJsonMap[K: Reads, V <: JsonSerializable](path: Path)(newValue: K => V, fileName: K => String) = {
    val list = readJson(path.resolve("index.json")).as[Seq[K]]
    (for(k <- list) yield k -> {
      val v = newValue(k)
      v.deserialize(readJson(path.resolve(s"${fileName(k)}.json")))
      v
    }).toMap
  }
  def readDirMap[K : Reads, V <: DirSerializable](path: Path)(newValue: K => V, fileName: K => String) = {
    val list = readJson(path.resolve("index.json")).as[Seq[K]]
    (for(k <- list) yield k -> {
      val v = newValue(k)
      v.readFrom(path.resolve(fileName(k)))
      v
    }).toMap
  }
}