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

package moe.lymia.princess.editor.controls

import javax.swing._
import javax.swing.event.{DocumentEvent, DocumentListener}
import javax.swing.text.JTextComponent

import moe.lymia.princess.editor.data._
import rx._

private trait StringJTextComponent { this: JTextComponent =>
  val data: ControlData
  implicit val owner: Ctx.Owner

  getDocument.addDocumentListener(new DocumentListener {
    override def insertUpdate(e: DocumentEvent) = {
      data.ctx.needsSaving()
      data.ctx.queueUpdate(data.backing, DataField(DataFieldType.String, getText))
    }
    override def removeUpdate(e: DocumentEvent) = insertUpdate(e)
    override def changedUpdate(e: DocumentEvent) = { }
  })

  val obs = data.default map { default =>
    val rx = Rx { (default.isDefault(), default.field()) }
    rx.foreach { case (isDefault : Boolean, field : DataField) =>
      SwingUtilities.invokeLater { () =>
        setEnabled(!isDefault)
        if(isDefault) setText(field.value.toString)
      }
      if(isDefault) {
        data.ctx.needsSaving()
        data.ctx.queueUpdate(data.backing, field)
      }
    }
  }
}

trait StringControlType extends ControlType {
  override def expectedFieldType: DataFieldType[_] = DataFieldType.String
  override def defaultValue: DataField = DataField.EmptyString
}

private case class TextAreaComponent(data: ControlData)(implicit val owner: Ctx.Owner)
  extends JTextArea with StringJTextComponent
case object TextAreaControlType extends StringControlType {
  override def createComponent(data: ControlData)(implicit owner: Ctx.Owner): JComponent =
    new JScrollPane(TextAreaComponent(data))
}

private case class TextFieldComponent(data: ControlData)(implicit val owner: Ctx.Owner)
  extends JTextField with StringJTextComponent
case object TextFieldControlType extends StringControlType {
  override def createComponent(data: ControlData)(implicit owner: Ctx.Owner): JComponent =
    TextFieldComponent(data)
}