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

import java.nio.file.{Files, Path}
import java.util.UUID

import moe.lymia.princess.editor.core._
import moe.lymia.princess.util.{IOUtils, VersionInfo}
import play.api.libs.json._
import rx._

import SerializeUtils._

final class CardData(project: Project) extends JsonSerializable {
  val fields = new DataStore
  var createTime = System.currentTimeMillis()

  val root = project.ctx.syncLuaExec { project.idData.card.createRoot(fields, Seq()) }

  val columnData = project.ctx.syncLuaExec {
    Rx.unsafe {
      val fields = root.luaData()
      project.idData.columns.columns.map { f =>
        f -> f.L.newThread().call(f.fn, 1, fields).head.as[String]
      }.toMap
    }
  }

  def copied() = createTime = System.currentTimeMillis()

  def serialize = Json.obj("fields" -> fields.serialize, "createTime" -> createTime)
  def deserialize(js: JsValue) = {
    fields.deserialize((js \ "fields").as[JsValue])
    createTime = (js \ "createTime").as[Long]
  }
}

final class SlotData(project: Project) extends JsonSerializable {
  val cardRef = Var[Option[UUID]](None)
  val fields = new DataStore

  def serialize = Json.obj("cardRef" -> cardRef.now, "fields" -> fields.serialize)
  def deserialize(js: JsValue): Unit = {
    cardRef.update((js \ "fields").asOpt[UUID])
    fields.deserialize((js \ "fields").as[JsValue])
  }
}

final class CardSourceInfo(project: Project) extends JsonSerializable {
  val fields = new DataStore
  val root = project.ctx.syncLuaExec { project.idData.set.createRoot(fields, Seq()) }

  def serialize = Json.obj("fields" -> fields.serialize)
  def deserialize(js: JsValue) = fields.deserialize((js \ "fields").as[JsValue])
}

trait CardSource {
  val info: CardSourceInfo
  val allCards: Rx[Seq[UUID]]
  val allSlots: Option[Rx[Seq[SlotData]]]

  def newCard(): UUID
}

final class CardPool(project: Project) extends CardSource with DirSerializable {
  val displayName = Var[String]("")
  val slots = Var(Seq.empty[SlotData])
  var createTime = System.currentTimeMillis()

  override val info: CardSourceInfo = new CardSourceInfo(project)

  override val allCards = Rx.unsafe { slots().flatMap(_.cardRef()) }
  override val allSlots = Some(slots)

  def addSlot(slot: SlotData): Unit = slots.update(slots.now :+ slot)
  def addSlot(card: UUID): Unit = addSlot({
    val slot = new SlotData(project)
    slot.cardRef.update(Some(card))
    slot
  })

  override def newCard(): UUID = {
    val uuid = project.newCard()
    addSlot(uuid)
    uuid
  }

  def removeSlot(slot: SlotData): Unit = slots.update(slots.now.filter(_ != slot))

  def copied() = createTime = System.currentTimeMillis()

  override def writeTo(path: Path): Unit = {
    writeJsonMap(path.resolve("slots"), slots.now.zipWithIndex.map(x => (x._2 + 1) -> x._1).toMap)(_.toString)
    writeJson(path.resolve("info.json"), Json.obj(
      "displayName" -> displayName.now,
      "info" -> info.serialize,
      "createTime" -> createTime
    ))
  }
  override def readFrom(path: Path): Unit = {
    slots.update(readJsonMap(path.resolve("slots"),
                             () => new SlotData(project))((x: Int) => x.toString).toSeq.sortBy(_._1).map(_._2))
    val js = readJson(path.resolve("info.json"))
    displayName.update((js \ "displayName").as[String])
    info.deserialize((js \ "info").as[JsValue])
    createTime = (js \ "createTime").as[Long]
  }
}

final class Project(val ctx: ControlContext, val gameId: String, val idData: GameIDData)
  extends CardSource with DirSerializable {

  val cards = Var(Map.empty[UUID, CardData])
  def newCard() = {
    val uuid = UUID.randomUUID()
    cards.update(cards.now + ((uuid, new CardData(this))))
    uuid
  }

  val pools = Var(Map.empty[UUID, CardPool])
  def newCardPool() = {
    val uuid = UUID.randomUUID()
    pools.update(pools.now + ((uuid, new CardPool(this))))
    uuid
  }

  override val info: CardSourceInfo = new CardSourceInfo(this)

  override val allCards: Rx[Seq[UUID]] = Rx.unsafe {
    cards().toSeq.sortBy(x => (x._2.createTime, x._1.toString)).map(_._1)
  }
  override val allSlots: Option[Rx[Seq[SlotData]]] = None

  val sources = Rx.unsafe { this +: pools().values.toSeq.sortBy(_.createTime) }

  // Main serialization entry point
  def writeTo(path: Path) = {
    writeJsonMap(path.resolve("cards"), cards.now)(_.toString)
    writeDirMap (path.resolve("pools"), pools.now)(_.toString)
    writeJson(path.resolve("info.json"), Json.obj(
      "info" -> info.serialize
    ))
    writeJson(path.resolve("metadata.json"), Json.obj(
      "gameId" -> gameId
    ))
    writeJson(path.resolve("version.json"), Json.obj(
      "version"   -> 1,
      "program"   -> Json.arr("PrincessEdit", VersionInfo.versionString),
      "writeTime" -> System.currentTimeMillis()
    ))
  }
  def readFrom(path: Path) = {
    val fileGameId = (readJson(path.resolve("metadata.json")) \ "gameId").as[String]
    if(fileGameId != gameId)
      sys.error(s"tried to load file for GameID '$fileGameId' in project for GameID '$gameId'")

    val version = (readJson(path.resolve("version.json")) \ "version").as[Int]
    if(version != 1) sys.error(s"unknown file format version $version")
    cards.update(readJsonMap(path.resolve("cards"), () => new CardData(this))(_.toString))
    pools.update(readDirMap (path.resolve("pools"), () => new CardPool(this))(_.toString))
    info.deserialize((readJson(path.resolve("info.json")) \ "info").as[JsValue])
  }
}
object Project {
  private def openPath[T](path: Path)(callback: Path => T): T =
    if(Files.isDirectory(path)) callback(path)
    else {
      val filesystem = IOUtils.openZip(path)
      try callback(filesystem.getPath("/"))
      finally filesystem.close()
    }

  def getProjectGameID(path: Path) =
    openPath(path)(x => (readJson(x.resolve("metadata.json")) \ "gameId").as[String])
  def loadProject(ctx: ControlContext, gameID: String, idData: GameIDData, path: Path) =
    openPath(path) { x =>
      val project = new Project(ctx, gameID, idData)
      project.readFrom(x)
      project
    }
}