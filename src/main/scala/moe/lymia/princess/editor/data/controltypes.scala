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

package moe.lymia.princess.editor.data

import javax.swing._

import moe.lymia.princess.lua.LuaState

import rx._

class DefaultBox(checkVar: Var[DataField])(implicit owner: Ctx.Owner) extends JCheckBox("Default") {
  val defaultRx = Rx { checkVar() == DataField.True }
  addItemListener(e => checkVar.update(DataField.fromBool(isSelected)))
}

case object TextAreaControlType extends ControlType {
  override def expectedFieldType: DataFieldType[_] = DataFieldType.String
  override protected[data] def createControl(L: LuaState, data: ControlData)(implicit owner: Ctx.Owner) = ???
}

case object TextFieldControlType extends ControlType {
  override def expectedFieldType: DataFieldType[_] = DataFieldType.String
  override protected[data] def createControl(L: LuaState, data: ControlData)(implicit owner: Ctx.Owner) = ???
}