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

package moe.lymia.princess.editor.core

import java.util.UUID

import play.api.libs.json._
import rx._

final class CardData(idData: GameIDData) {
  val fields = new DataStore
  var createTime = System.currentTimeMillis()

  val root = idData.card.createRoot(fields, Seq())

  def serialize = Json.obj("fields" -> fields.serialize, "createTime" -> createTime)
  def deserialize(js: JsValue) = {
    fields.deserialize((js \ "fields").as[JsValue])
    createTime = (js \ "createTime").as[Long]
  }
}

final class SlotData(project: Project) {
  val cardRef = Var[Option[UUID]](None)
  val fields = new DataStore

  def serialize = Json.obj("cardRef" -> cardRef.now, "fields" -> fields.serialize)
  def deserialize(js: JsValue): Unit = {
    cardRef.update((js \ "fields").asOpt[UUID])
    fields.deserialize((js \ "fields").as[JsValue])
  }
}

final class CardSourceInfo(idData: GameIDData) {
  val fields = new DataStore
  val root = idData.set.createRoot(fields, Seq())

  def serialize = Json.obj("fields" -> fields.serialize)
  def deserialize(js: JsValue) = fields.deserialize((js \ "fields").as[JsValue])
}

trait CardSource {
  val info: CardSourceInfo
  val allCards: Rx[Seq[UUID]]
  val allSlots: Option[Rx[Seq[SlotData]]]

  def newCard(): UUID
}

final class CardPool(project: Project) extends CardSource {
  val displayName = Var[String]("")
  val slots = Var(Seq.empty[SlotData])
  var createTime = System.currentTimeMillis()

  override val info: CardSourceInfo = new CardSourceInfo(project.idData)

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

  def serialize = Json.obj(
    "displayName" -> displayName.now,
    "slots" -> slots.now.map(_.serialize),
    "info" -> info.serialize,
    "createTime" -> createTime
  )
  def deserialize(js: JsValue) = {
    displayName.update((js \ "displayName").as[String])
    slots.update((js \ "slots").as[Seq[JsValue]].map(v => {
      val slot = new SlotData(project)
      slot.deserialize(v)
      slot
    }))
    info.deserialize((js \ "info").as[JsValue])
    createTime = (js \ "createTime").as[Long]
  }
}

final class Project(val idData: GameIDData) extends CardSource {
  val cards = Var(Map.empty[UUID, CardData])
  def newCard() = {
    val uuid = UUID.randomUUID()
    cards.update(cards.now + ((uuid, new CardData(idData))))
    uuid
  }

  val pools = Var(Map.empty[UUID, CardPool])
  def newCardPool() = {
    val uuid = UUID.randomUUID()
    pools.update(pools.now + ((uuid, new CardPool(this))))
    uuid
  }

  override val info: CardSourceInfo = new CardSourceInfo(idData)

  override val allCards: Rx[Seq[UUID]] = Rx.unsafe { cards().toSeq.sortBy(_._2.createTime).map(_._1) }
  override val allSlots: Option[Rx[Seq[SlotData]]] = None

  val sources = Rx.unsafe { this +: pools().values.toSeq.sortBy(_.createTime) }
}