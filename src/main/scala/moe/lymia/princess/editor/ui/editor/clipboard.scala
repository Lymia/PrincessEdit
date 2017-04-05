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

package moe.lymia.princess.editor.ui.editor

import java.nio.charset.StandardCharsets

import moe.lymia.princess.editor.utils.MimeType
import org.eclipse.swt.dnd.{ByteArrayTransfer, Transfer, TransferData}
import play.api.libs.json._

case class CardTransferData(json: JsValue*)
object CardTransfer extends ByteArrayTransfer {
  private val id = Transfer.registerType(MimeType.CardData)

  override val getTypeNames: Array[String] = Array(MimeType.CardData)
  override val getTypeIds: Array[Int] = Array(id)

  override def javaToNative(obj: Any, transferData: TransferData): Unit = {
    if (obj == null || !obj.isInstanceOf[CardTransferData]) return
    if (isSupportedType(transferData)) {
      val transfer = obj.asInstanceOf[CardTransferData]
      super.javaToNative(Json.prettyPrint(Json.toJson(transfer.json)).getBytes(StandardCharsets.UTF_8), transferData)
    }
  }

  override def nativeToJava(transferData: TransferData) = {
    if (isSupportedType(transferData)) {
      val buffer = super.nativeToJava(transferData).asInstanceOf[Array[Byte]]
      if (buffer == null) null
      else try {
        CardTransferData(Json.parse(new String(buffer, StandardCharsets.UTF_8)).as[Seq[JsValue]] : _*)
      } catch {
        case e: Exception => null
      }
    } else null
  }
}