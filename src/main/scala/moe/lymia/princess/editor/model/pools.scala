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

import java.nio.file.Path
import java.util.UUID

import play.api.libs.json._
import rx._

final class SlotData(protected val project: Project) extends JsonPathSerializable with HasModifyTimeDataStore {
  val cardRef = Var[Option[UUID]](None)

  override def serialize = super.serialize ++ Json.obj("cardRef" -> cardRef.now)
  override def deserialize(js: JsObject): Unit = {
    super.deserialize(js)
    cardRef.update((js \ "cardRef").asOpt[UUID])
  }
}

final class CardPoolInfo(protected val project: Project) extends JsonPathSerializable with HasModifyTimeDataStore {
  val root = project.ctx.syncLuaExec { project.idData.poolRoot.createRoot(fields, Seq()) }
  val name = Rx.unsafe { project.idData.poolData.computeName(root.luaData()) }
}

trait CardPool extends PathSerializable {
  protected val project: Project
  val info: CardPoolInfo = new CardPoolInfo(project)
  val name: Rx[String] = info.name

  def cardIdList: Rx[Set[UUID]]
  val fullCardList = Rx.unsafe { // TODO: See if I can do better than Rx.unsafe
    cardIdList().toSeq.flatMap(x => getFullCard(x)).sortBy(_.cardData.createTime)
  }

  def addCard(uuid: UUID)
  def removeCard(uuid: UUID)

  def getFullCard(uuid: UUID)(implicit ctx: Ctx.Data, owner: Ctx.Owner) = {
    val card = project.cards.get(uuid)
    card.map(x => FullCardData(uuid, project, x, info, Rx { None }))
  }

  override def writeTo(path: Path): Unit = {
    super.writeTo(path)
    info.writeTo(path.resolve("info.json"))
  }
  override def readFrom(path: Path): Unit = {
    super.readFrom(path)
    println("CardPool.readFrom")
    info.readFrom(path.resolve("info.json"))
  }
}

final class ListCardPool(protected val project: Project) extends CardPool with DirSerializable {
  val slots = Var(Seq.empty[SlotData])

  override lazy val cardIdList = Var(Set.empty[UUID]) // lazy val to fix initialization order issues

  def addCard(uuid: UUID) = if(!cardIdList.now.contains(uuid)) {
    project.cards.now.get(uuid).foreach { card =>
      card.ref()
    }
    cardIdList.update(cardIdList.now + uuid)
  }
  def removeCard(uuid: UUID) = if(cardIdList.now.contains(uuid)) {
    project.cards.now.get(uuid).foreach { card =>
      card.unref()
    }
    cardIdList.update(cardIdList.now - uuid)
  }

  override def writeTo(path: Path): Unit = {
    super.writeTo(path)
    SerializeUtils.writeJson(path.resolve("cards.json"), Json.toJson(cardIdList.now))
  }
  override def readFrom(path: Path): Unit = {
    super.readFrom(path)
    for(uuid <- cardIdList.now) removeCard(uuid)
    cardIdList.update(SerializeUtils.readJson(path.resolve("cards.json")).as[Set[UUID]])
    for(uuid <- Json.fromJson[Set[UUID]](SerializeUtils.readJson(path.resolve("cards.json"))).get) addCard(uuid)
  }
}