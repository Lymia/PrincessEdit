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

package moe.lymia.princess.editor.project

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

import moe.lymia.princess.util.IOUtils
import play.api.libs.json._

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
    Files.write(path, Json.prettyPrint(json).getBytes(StandardCharsets.UTF_8))
  def writeJsonMap[K : Writes, V <: JsonSerializable](path: Path, map: Map[K, V])(fileName: K => String) = {
    Files.createDirectories(path)
    writeJson(path.resolve("index.json"), Json.toJson(map.keys.map(fileName)))
    for((id, entry) <- map) writeJson(path.resolve(s"${fileName(id)}.json"), entry.serialize)
  }
  def writeDirMap[K : Writes, V <: DirSerializable](path: Path, map: Map[K, V])(fileName: K => String) = {
    Files.createDirectories(path)
    writeJson(path.resolve("index.json"), Json.toJson(map.keys.map(fileName)))
    for((id, entry) <- map) entry.writeTo(path.resolve(fileName(id)))
  }

  def readJson(path: Path) = Json.parse(IOUtils.readFileAsString(path))
  def readJsonMap[K: Reads, V <: JsonSerializable](path: Path, newValue: () => V)(fileName: K => String) = {
    val list = readJson(path.resolve("index.json")).as[Seq[K]]
    (for(k <- list) yield k -> {
      val v = newValue()
      v.deserialize(readJson(path.resolve(s"${fileName(k)}.json")))
      v
    }).toMap
  }
  def readDirMap[K : Reads, V <: DirSerializable](path: Path, newValue: () => V)(fileName: K => String) = {
    val list = readJson(path.resolve("index.json")).as[Seq[K]]
    (for(k <- list) yield k -> {
      val v = newValue()
      v.readFrom(path.resolve(fileName(k)))
      v
    }).toMap
  }
}