/*
 * Copyright (c) 2017-2022 Lymia Alusyia <lymia@lymiahugs.com>
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

import moe.lymia.princess.Platform
import moe.lymia.princess.util.IOUtils
import org.apache.commons.codec.digest.DigestUtils
import play.api.libs.json._

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.util.UUID
import scala.collection.concurrent

final case class SettingsKey[T : Reads : Writes](id: String) {
  def serialize(t: T) = Json.toJson(t)
  def deserialize(t: JsValue) = t.as[T]
}

private sealed trait SettingsStoreEntry
private final case class SettingsStoreObjEntry[T](key: SettingsKey[T], obj: T) extends SettingsStoreEntry
private final case class SettingsStoreJsonEntry(value: JsValue) extends SettingsStoreEntry

abstract class SettingsStore {
  private val underlying = new concurrent.TrieMap[String, SettingsStoreEntry]

  def clear() = underlying.clear()

  def getSetting[T](key: SettingsKey[T], default: => T) = underlying.get(key.id) match {
    case Some(SettingsStoreObjEntry(_, obj)) =>
      obj.asInstanceOf[T]
    case Some(SettingsStoreJsonEntry(js)) =>
      val v = key.deserialize(js)
      underlying.put(key.id, SettingsStoreObjEntry(key, v))
      v
    case None =>
      val v = default
      underlying.put(key.id, SettingsStoreObjEntry(key, v))
      v
  }
  def setSetting[T](key: SettingsKey[T], obj: T): Unit = {
    underlying.put(key.id, SettingsStoreObjEntry(key, obj))
    save()
  }

  def transferFrom(store: SettingsStore) = {
    underlying.clear()
    underlying ++= store.underlying
    save()
  }

  def serialize = Json.toJson(underlying.mapValues {
    case SettingsStoreObjEntry(key, obj) => key.serialize(obj)
    case SettingsStoreJsonEntry(js) => js
  })
  def deserialize(js: JsValue) = {
    underlying.clear()
    for((k, v) <- js.as[Map[String, JsValue]]) underlying.put(k, SettingsStoreJsonEntry(v))
  }

  def load()
  def save()
}

class UnbackedSettingsStore extends SettingsStore {
  def load() = { }
  def save() = { }
}
class FilesystemSettingsStore(path: Path) extends SettingsStore {
  def load() = if(!Files.exists(path)) clear() else deserialize(Json.parse(IOUtils.readFileAsString(path)))
  def save() = IOUtils.writeFile(path, Json.prettyPrint(serialize))
}
object FilesystemSettingsStore {
  def load(path: Path) = {
    val store = new FilesystemSettingsStore(path)
    store.load()
    store
  }
}

object Settings {
  val rootDirectory = Platform.platform.getConfigDirectory("PrincessEdit")
  Files.createDirectories(rootDirectory)

  private val globalSettingsPath = rootDirectory.resolve("settings.json")

  private def hashPath(path: Path) =
    DigestUtils.sha256Hex(path.toAbsolutePath.toUri.toString.getBytes(StandardCharsets.UTF_8))
  private def hashPath(path: Path, id: UUID) =
    DigestUtils.sha256Hex(id.toString.getBytes(StandardCharsets.UTF_8) ++
      path.toAbsolutePath.toUri.toString.getBytes(StandardCharsets.UTF_8))

  private val projectSettingsDirectory = rootDirectory.resolve("project-settings")
  Files.createDirectories(projectSettingsDirectory)

  private val projectLockDirectory = rootDirectory.resolve("project-locks")
  Files.createDirectories(projectLockDirectory)

  lazy val global = FilesystemSettingsStore.load(globalSettingsPath)
  def getProjectSettings(path: Path, id: UUID) =
    FilesystemSettingsStore.load(projectSettingsDirectory.resolve(s"${hashPath(path, id)}.json"))
  def getProjectLock(path: Path) = projectLockDirectory.resolve(s"${hashPath(path)}.lock")
}