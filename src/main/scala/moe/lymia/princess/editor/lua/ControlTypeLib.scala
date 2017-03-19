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
import moe.lymia.princess.editor.nodes._
import moe.lymia.princess.lua._

case class ControlTypeApWrapper(default: ControlType, call: LuaClosure)

trait LuaControlTypeImplicits {
  implicit object LuaInControlType extends FromLua[ControlType] {
    override def fromLua(L: Lua, v: Any, source: => Option[String]): ControlType = v match {
      case u: LuaUserdata =>
        u.getUserdata match {
          case wrapper: ControlTypeApWrapper => wrapper.default
          case control: ControlType => control
          case _ => typerror(L, source, L.getClass.getName, classOf[ControlType].getName)
        }
      case _ => typerror(L, source, v, Lua.TUSERDATA)
    }
  }
  implicit object LuaOutControlType extends LuaUserdataOutputType[ControlType]
  implicit object LuaOutControlTypeApWrapper extends LuaUserdataType[ControlTypeApWrapper] {
    metatable { (L, mt) =>
      L.register(mt, "__call", ScalaLuaClosure.withState { (L: LuaState) =>
        val wrapper = L.value(1).as[ControlTypeApWrapper]
        val args = L.valueRange(2)
        L.push(L.call(wrapper.call, 1, args.map(x => x : LuaObject) : _*).head)
        1
      })
    }
  }
}

object ControlTypeLib extends LuaLibrary {
  override def open(L: LuaState, table: LuaTable): Unit = {
    val controlTypes = L.newTable()
    L.rawSet(controlTypes, "TextArea", TextControlType(true) : ControlType)
    L.rawSet(controlTypes, "TextField", TextControlType(false) : ControlType)
    L.rawSet(controlTypes, "CheckBox",
      ControlTypeApWrapper(CheckBoxControlType(None),
                           LuaClosure { (s: Option[String]) => CheckBoxControlType(s) : ControlType }))
    L.rawSet(table, "ControlType", controlTypes)
  }
}