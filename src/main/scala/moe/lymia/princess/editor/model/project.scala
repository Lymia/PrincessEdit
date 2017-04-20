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

import moe.lymia.princess.editor.model.SerializeUtils._
import moe.lymia.princess.editor.{ControlContext, GameIDData}
import moe.lymia.princess.util.{IOUtils, VersionInfo}
import play.api.libs.json._
import rx._

import scala.collection.mutable

// TODO: Cleanup this file, maybe split it or factor out more traits

final class CardData(protected val project: Project) extends JsonSerializable with HasDataStore {
  val root = project.ctx.syncLuaExec { project.idData.card.createRoot(fields, Seq()) }

  val columnData = project.ctx.syncLuaExec {
    Rx.unsafe {
      val fields = root.luaData()
      project.idData.columns.columns.map { f =>
        f -> f.L.newThread().call(f.fn, 1, fields).head.as[String]
      }.toMap
    }
  }

  def serialize = serializeDataStore
  def deserialize(js: JsValue) = deserializeDataStore(js)
}

final class SlotData(protected val project: Project) extends JsonSerializable with HasDataStore {
  val cardRef = Var[Option[UUID]](None)

  def serialize = Json.obj("cardRef" -> cardRef.now, "data" -> serializeDataStore)
  def deserialize(js: JsValue): Unit = {
    cardRef.update((js \ "cardRef").asOpt[UUID])
    deserializeDataStore((js \ "data").as[JsValue])
  }
}

final class CardSourceInfo(protected val project: Project) extends JsonSerializable with HasDataStore {
  val root = project.ctx.syncLuaExec { project.idData.set.createRoot(fields, Seq()) }

  def serialize = serializeDataStore
  def deserialize(js: JsValue) = deserializeDataStore(js)
}

trait CardSource {
  def uuid: UUID

  val info: CardSourceInfo
  val allCards: Rx[Seq[UUID]]
  val allSlots: Option[Rx[Seq[SlotData]]]

  def newCard(): UUID
}

final class CardPool(val uuid: UUID, project: Project) extends CardSource with DirSerializable {
  val displayName = Var[String]("")
  val slots = Var(Seq.empty[SlotData])

  override val info: CardSourceInfo = new CardSourceInfo(project)

  override val allCards = Rx.unsafe { slots().flatMap(_.cardRef()) }
  override val allSlots = Some(slots)

  def addSlot(slot: SlotData): Unit = slots.update(slots.now :+ slot)
  def addSlot(card: UUID): Unit = addSlot({
    val slot = new SlotData(project)
    slot.cardRef.update(Some(card))
    project.modified()
    slot
  })

  override def newCard(): UUID = {
    val uuid = project.newCard()
    addSlot(uuid)
    uuid
  }

  def removeSlot(slot: SlotData): Unit = slots.update(slots.now.filter(_ != slot))

  override def writeTo(path: Path): Unit = {
    writeJsonMap(path.resolve("slots"), slots.now.zipWithIndex.map(x => (x._2 + 1) -> x._1).toMap)(_.toString)
    writeJson(path.resolve("info.json"), Json.obj(
      "displayName" -> displayName.now,
      "poolInfo" -> info.serialize
    ))
  }
  override def readFrom(path: Path): Unit = {
    slots.update(readJsonMap(path.resolve("slots"))
                            ((_: Int) => new SlotData(project), (x: Int) => x.toString).toSeq.sortBy(_._1).map(_._2))
    val js = readJson(path.resolve("info.json"))
    displayName.update((js \ "displayName").as[String])
    info.deserialize((js \ "poolInfo").as[JsValue])
  }
}

final class Project(val ctx: ControlContext, val gameId: String, val idData: GameIDData)
  extends CardSource with DirSerializable {

  var uuid = UUID.randomUUID()
  override val info: CardSourceInfo = new CardSourceInfo(this)

  type ModifyListener = () => Unit
  private val listeners = new mutable.ArrayBuffer[ModifyListener]()
  def addModifyListener(listener: ModifyListener) = listeners += listener
  def modified() = {
    listeners.foreach(_())
    info.modified()
  }

  val cards = Var(Map.empty[UUID, CardData])
  def newCard() = {
    val uuid = UUID.randomUUID()
    cards.update(cards.now + ((uuid, new CardData(this))))
    modified()
    uuid
  }

  val pools = Var(Map.empty[UUID, CardPool])
  def newCardPool() = {
    val uuid = UUID.randomUUID()
    pools.update(pools.now + ((uuid, new CardPool(uuid, this))))
    modified()
    uuid
  }

  override val allCards: Rx[Seq[UUID]] = Rx.unsafe {
    cards().toSeq.sortBy(x => (x._2.createTime, x._1.toString)).map(_._1)
  }
  override val allSlots: Option[Rx[Seq[SlotData]]] = None

  val sources = Rx.unsafe { this +: pools().values.toSeq.sortBy(_.info.createTime) }

  // Main serialization entry point
  def writeTo(path: Path) = {
    writeJsonMap(path.resolve("cards"), cards.now)(_.toString)
    writeDirMap (path.resolve("pools"), pools.now)(_.toString)
    writeJson(path.resolve("info.json"), Json.obj(
      "uuid" -> uuid,
      "poolInfo" -> info.serialize
    ))
    writeJson(path.resolve("metadata.json"), Json.obj(
      "version"   -> Json.obj(
        "major" -> Project.VER_MAJOR,
        "minor" -> Project.VER_MINOR
      ),
      "gameId" -> gameId,
      "program"   -> Json.arr("PrincessEdit", VersionInfo.versionString),
      "writeTime" -> System.currentTimeMillis()
    ))
  }
  def readFrom(path: Path) = {
    val metadata = readJson(path.resolve("metadata.json"))
    val fileGameId = (metadata \ "gameId").as[String]
    if(fileGameId != gameId)
      sys.error(s"tried to load file for GameID '$fileGameId' in project for GameID '$gameId'")

    val version = (metadata \ "version" \ "major").as[Int]
    if(version != Project.VER_MAJOR) sys.error(s"unknown file format version $version")
    cards.update(readJsonMap(path.resolve("cards"))(_ => new CardData(   this), _.toString))
    pools.update(readDirMap (path.resolve("pools"))(k => new CardPool(k, this), _.toString))

    val infoJson = readJson(path.resolve("info.json"))
    uuid = (infoJson \ "uuid").as[UUID]
    info.deserialize((infoJson \ "poolInfo").as[JsValue])
  }
}

case class ProjectMetadata(gameId: String, versionMajor: Int, versionMinor: Int, createdBy: String)
object Project {
  val VER_MAJOR = 1
  val VER_MINOR = 0

  private def openPath[T](path: Path)(callback: Path => T): T =
    if(Files.isDirectory(path)) callback(path)
    else {
      val filesystem = IOUtils.openZip(path)
      try callback(filesystem.getPath("/"))
      finally filesystem.close()
    }

  def getProjectMetadata(path: Path) =
    openPath(path)(x => {
      val metadata = readJson(x.resolve("metadata.json"))
      ProjectMetadata(
        (metadata \ "gameId").as[String],
        (metadata \ "version" \ "major").as[Int],
        (metadata \ "version" \ "minor").as[Int],
        (metadata \ "program").as[Seq[String]].mkString(" ")
      )
    })
  def loadProject(ctx: ControlContext, gameID: String, idData: GameIDData, path: Path) =
    openPath(path) { x =>
      val project = new Project(ctx, gameID, idData)
      project.readFrom(x)
      project
    }
}