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

package moe.lymia.princess.editor.lua

import moe.lymia.princess.core._
import moe.lymia.princess.editor.data._
import moe.lymia.princess.lua._

trait LuaControlNodeImplicits {
  implicit object LuaControlType extends LuaUserdataType[ControlType]
  implicit object LuaControlNode extends LuaUserdataType[ControlNode]
}

object ControlLib extends LuaLibrary {
  override def open(L: LuaState, table: LuaTable): Unit = {
    val controlTypes = L.newTable()
    L.rawSet(controlTypes, "TextArea", TextAreaControlType : ControlType)
    L.rawSet(controlTypes, "TextField", TextFieldControlType : ControlType)
    L.rawSet(table, "ControlType", controlTypes)
  }
}