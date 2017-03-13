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

case class DeriveList(elements: Seq[FieldNode])

trait LuaFieldNodeImplicits {
  implicit object LuaInputFieldNode extends LuaUserdataInput[FieldNode]

  implicit object LuaDerivedFieldNode extends LuaFieldNodeBase[DerivedFieldNode]

  implicit object LuaDerivedDSLWrapper extends LuaUserdataType[DeriveList] {
    metatable { (L, mt) =>
      L.register(mt, "__add", (wrapper: DeriveList, node: FieldNode) => DeriveList(wrapper.elements :+ node))
      L.register(mt, "__call", (wrapper: DeriveList, fn: LuaClosure) => DerivedFieldNode(wrapper.elements, fn))
    }
  }
  trait LuaFieldNodeBase[T] extends LuaUserdataType[T] {
    metatable { (L, mt) =>
      L.register(mt, "__add", (n1: FieldNode, n2: FieldNode) => DeriveList(Seq(n1, n2)))
      L.register(mt, "__call", (n: FieldNode, fn: LuaClosure) => DerivedFieldNode(Seq(n), fn))
    }
  }
}

object FieldLib extends LuaLibrary {
  override def open(L: LuaState, table: LuaTable): Unit = {

  }
}