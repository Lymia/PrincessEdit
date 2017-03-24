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

package moe.lymia.princess.editor.nodes

import moe.lymia.princess.editor.core._
import moe.lymia.lua._

import org.eclipse.swt._
import org.eclipse.swt.events._
import org.eclipse.swt.widgets._

import rx._

trait BasicControlType[T <: Control] extends ControlType {
  def create(parent: Composite, data: ControlData): T
  def registerListener(t: T, data: ControlData, fn: () => Unit)
  def setValue(t: T, data: ControlData, field: DataField)
  def getValue(t: T, data: ControlData): DataField

  override def createComponent(parent: Composite, data: ControlData)(implicit owner: Ctx.Owner) = {
    val component = create(parent, data)

    setValue(component, data, data.backing.now)
    registerListener(component, data, () => {
      data.ctx.needsSaving()
      data.ctx.queueUpdate(data.backing, getValue(component, data))
    })

    data.default.map { default =>
      val rx = Rx { (default.isDefault(), default.field()) }
      rx.foreach { case (isDefault : Boolean, field : DataField) =>
        data.ctx.asyncUiExec {
          if(!component.isDisposed) {
            component.setEnabled(!isDefault)
            if(isDefault) setValue(component, data, field)
          }
        }
        if(isDefault) {
          data.ctx.needsSaving()
          data.ctx.queueUpdate(data.backing, field)
        }
      }
    }

    component
  }
}

case class CheckBoxControlType(title: Option[String]) extends BasicControlType[Button] {
  override def expectedFieldType: DataFieldType[_] = DataFieldType.Boolean
  override def defaultValue: DataField = DataField.False

  override def create(parent: Composite, data: ControlData): Button =
    new Button(parent, SWT.CHECK)
  override def registerListener(button: Button, data: ControlData, fn: () => Unit): Unit =
    button.addSelectionListener(new SelectionListener {
      override def widgetSelected(selectionEvent: SelectionEvent) = fn()
      override def widgetDefaultSelected(selectionEvent: SelectionEvent) = fn()
    })
  override def setValue(button: Button, data: ControlData, field: DataField): Unit =
    button.setSelection(Lua.toBoolean(data.backing.now.toLua(data.internal_L)))
  override def getValue(button: Button, data: ControlData): DataField =
    DataField.fromBool(button.getSelection)
}

case class TextControlType(isMulti: Boolean) extends BasicControlType[Text] {
  override def expectedFieldType: DataFieldType[_] = DataFieldType.String
  override def defaultValue: DataField = DataField.EmptyString

  override def create(parent: Composite, data: ControlData) =
    new Text(parent, (if(isMulti) SWT.MULTI else SWT.SINGLE) | SWT.BORDER)
  override def registerListener(text: Text, data: ControlData, fn: () => Unit) =
    text.addModifyListener(_ => fn())
  override def setValue(text: Text, data: ControlData, field: DataField): Unit =
    text.setText(data.internal_L.toPrintString(data.backing.now.toLua(data.internal_L)))
  override def getValue(text: Text, data: ControlData): DataField =
    DataField(DataFieldType.String, text.getText)
}