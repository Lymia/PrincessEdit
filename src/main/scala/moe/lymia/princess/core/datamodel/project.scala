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

package moe.lymia.princess.core.datamodel

import moe.lymia.princess.VersionInfo
import moe.lymia.princess.core.context.ControlContext
import moe.lymia.princess.core.datamodel.SerializeUtils._
import moe.lymia.princess.gui.GameIDData
import moe.lymia.princess.util.IOUtils
import play.api.libs.json._
import rx._

import java.nio.file.{Files, Path}
import java.util.UUID

trait SyntheticView extends CardView {
  override val isStatic: Boolean = true
  override def addCard(uuid: UUID) = { }
  override def removeCard(uuid: UUID) = { } // TODO: Make UI aware of views with different remove semantics
}

final class AllCardsView(protected val project: Project) extends CardView with SyntheticView {
  override def cardIdList: Rx[Set[UUID]] = Rx.unsafe { project.cards().keySet }
  override val name: Rx[String] = Rx.unsafe { "All Cards" } // TODO I18N
}

final class DeletedCardsView(protected val project: Project) extends CardView with SyntheticView {
  override def cardIdList: Rx[Set[UUID]] = Rx.unsafe { project.cards().filter(_._2.refCount == 0).keySet }
  override val name: Rx[String] = Rx.unsafe { "Deleted Cards" } // TODO I18N
}

final class Project(val ctx: ControlContext, val gameId: String, val idData: GameIDData)
  extends JsonSerializable with DirSerializable with TrackModifyTime {
  var uuid = UUID.randomUUID()

  val cards = new UUIDMapVar(id => {
    ctx.assertLuaThread()
    val data = new CardData(this)
    data.addModifyListener(this)
    data
  })
  val views = new UUIDMapVar(id => {
    ctx.assertLuaThread()
    val data = new ListCardView(this)
    data.info.addModifyListener(this)
    data
  })

  val allCardsView = new AllCardsView(this)
  val deletedCardsView = new DeletedCardsView(this)

  private val staticViews = Map(
    StaticViewID.AllCards -> allCardsView,
    StaticViewID.DeletedCards -> deletedCardsView
  )
  for(view <- staticViews.values) view.info.addModifyListener(this)

  val allViews = Rx.unsafe { staticViews ++ views() }
  val sources = Rx.unsafe { allCardsView +: views().values.toSeq.sortBy(_.info.createTime) }

  // Main serialization entry point
  override def writeTo(path: Path) = {
    super.writeTo(path)

    cards.writeTo(path.resolve("cards"))
    views.writeTo(path.resolve("views"))
    allCardsView.writeTo(path.resolve("views").resolve("all-cards"))
    deletedCardsView.writeTo(path.resolve("views").resolve("deleted-cards"))

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
    views.readFrom(path.resolve("views"))
    allCardsView.readFrom(path.resolve("views").resolve("all-cards"))
    deletedCardsView.readFrom(path.resolve("views").resolve("deleted-cards"))

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

object StaticViewID {
  val AllCards     = UUID.fromString("2d083912-441b-11e7-8b8b-e793e0991f26")
  val DeletedCards = UUID.fromString("7e676322-4615-11e7-b815-161899dad660")
}