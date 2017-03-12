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
import moe.lymia.princess.editor._
import moe.lymia.princess.lua._

case class DerivedDSLWrapper(elements: Seq[CardSpecField])

trait LuaCardSpecImplicits {
  implicit object LuaInputCardSpecField extends LuaUserdataInput[CardSpecField]
  implicit object LuaCardSpecDerivedField extends LuaCardSpecFieldBase[CardSpecDerivedField]

  implicit object LuaDerivedDSLWrapper extends LuaUserdataType[DerivedDSLWrapper] {
    metatable { (L, mt) =>
      L.register(mt, "__add", (wrapper: DerivedDSLWrapper, field: CardSpecField) =>
        DerivedDSLWrapper(wrapper.elements :+ field))
      L.register(mt, "__call", (L: LuaState, wrapper: DerivedDSLWrapper, fn: LuaClosure) =>
        CardSpecDerivedField(wrapper.elements, L, fn))
    }
  }
  trait LuaCardSpecFieldBase[T] extends LuaUserdataType[T] {
    metatable { (L, mt) =>
      L.register(mt, "__add", (field1: CardSpecField, field2: CardSpecField) =>
        DerivedDSLWrapper(Seq(field1, field2)))
      L.register(mt, "__call", (L: LuaState, field: CardSpecField, fn: LuaClosure) =>
        CardSpecDerivedField(Seq(field), L, fn))
    }
  }
  implicit object LuaCardSpecInputField extends LuaCardSpecFieldBase[CardSpecInputField] {
    metatable { (L, mt) =>
      L.register(mt, "__index", (field: CardSpecInputField, k: String) =>
        field.copy(fieldName = if(field.fieldName.isEmpty) k else s"${field.fieldName}.$k"))
    }
  }
}