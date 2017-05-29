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

final class AllCardsView(protected val project: Project) extends CardPool {
  override def cardIdList: Rx[Set[UUID]] = Rx.unsafe { project.cards().keySet }
}

final class Project(val ctx: ControlContext, val gameId: String, val idData: GameIDData) extends DirSerializable {
  var uuid = UUID.randomUUID()

  type ModifyListener = () => Unit
  private val listeners = new mutable.ArrayBuffer[ModifyListener]()
  def addModifyListener(listener: ModifyListener) = listeners += listener
  def modified() = {
    listeners.foreach(_())
  }

  val cards = new UUIDMapVar(id => {
    ctx.assertLuaThread()
    new CardData(this)
  })
  val pools = new UUIDMapVar(id => {
    ctx.assertLuaThread()
    new ListCardPool(this)
  })

  val allCardsView = new AllCardsView(this)
  val allPools = Rx.unsafe {
    Map(
      StaticPoolID.AllCards -> allCardsView
    ) ++ pools()
  }

  val sources = Rx.unsafe { allCardsView +: pools().values.toSeq.sortBy(_.info.createTime) }

  // Main serialization entry point
  override def writeTo(path: Path) = {
    super.writeTo(path)

    cards.writeTo(path.resolve("cards"))
    pools.writeTo(path.resolve("pools"))
    allCardsView.writeTo(path.resolve("pools").resolve("all-cards"))

    writeJson(path.resolve("metadata.json"), Json.obj(
      "version"   -> Json.obj(
        "major" -> Project.VER_MAJOR,
        "minor" -> Project.VER_MINOR
      ),
      "gameId"    -> gameId,
      "program"   -> Json.arr("PrincessEdit", VersionInfo.versionString),
      "writeTime" -> System.currentTimeMillis(),
      "uuid"      -> uuid
    ))
  }
  override def readFrom(path: Path) = {
    ctx.assertLuaThread()

    super.readFrom(path)

    val metadata = readJson(path.resolve("metadata.json"))
    val fileGameId = (metadata \ "gameId").as[String]
    if(fileGameId != gameId)
      sys.error(s"tried to load file for GameID '$fileGameId' in project for GameID '$gameId'")

    val version = (metadata \ "version" \ "major").as[Int]
    if(version != Project.VER_MAJOR) sys.error(s"unknown file format version $version")

    cards.readFrom(path.resolve("cards"))
    pools.readFrom(path.resolve("pools"))
    allCardsView.writeTo(path.resolve("pools").resolve("all-cards"))

    uuid = (metadata \ "uuid").as[UUID]
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

object StaticPoolID {
  val AllCards = UUID.fromString("2d083912-441b-11e7-8b8b-e793e0991f26")
}