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

import moe.lymia.princess.editor.nodes._

import play.api.libs.json._
import rx._

final class CardData(idData: GameIDData) {
  val fields = new DataStore
  val root = idData.card.createRoot(fields, Seq())

  def serialize = Json.obj("fields" -> fields.serialize)
  def deserialize(js: JsValue) = fields.deserialize((js \ "fields").as[JsValue])
}

final class SlotData(idData: GameIDData) {
  val cardRef = Var[Option[UUID]](None)
  val fields = new DataStore
  val root = idData.card.createRoot(fields, Seq())

  def serialize = Json.obj("cardRef" -> cardRef.now, "fields" -> fields.serialize)
  def deserialize(js: JsValue): Unit = {
    cardRef.update((js \ "fields").asOpt[UUID])
    fields.deserialize((js \ "fields").as[JsValue])
  }
}

final class SetData(file: CardFile) {
  val displayName = Var[String]("")
  val slots = Var(Seq.empty[SlotData])
  val fields = new DataStore

  def addSlot(slot: SlotData): Unit = slots.update(slots.now :+ slot)
  def addSlot(card: UUID): Unit = addSlot({
    val slot = new SlotData(file.idData)
    slot.cardRef.update(Some(card))
    slot
  })

  def removeSlot(slot: SlotData): Unit = slots.update(slots.now.filter(_ != slot))

  def serialize = Json.obj(
    "displayName" -> displayName.now,
    "slots" -> slots.now.map(_.serialize),
    "fields" -> fields.serialize
  )
  def deserialize(js: JsValue) = {
    displayName.update((js \ "displayName").as[String])
    slots.update((js \ "slots").as[Seq[JsValue]].map(v => {
      val slot = new SlotData(file.idData)
      slot.deserialize(v)
      slot
    }))
    fields.deserialize((js \ "fields").as[JsValue])
  }
}

final class CardFile(val idData: GameIDData) {

}