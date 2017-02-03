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

package moe.lymia.princess.core

import moe.lymia.princess.lua.{LuaRet, LuaState, LuaUserdataType}

trait LuaTemplateImplicits {
  implicit object LuaComponentReference extends LuaUserdataType[ComponentReference] {
    metatable { (L, mt) =>
      L.register(mt, "__index"   , (L: LuaState, ref: ComponentReference, k: String) =>
        k match {
          case "deref" => LuaRet(ref.deref)
          case n => ref.component.getLuaMetatable.getField(L, k)
        }
      )
      L.register(mt, "__newindex", (L: LuaState, ref: ComponentReference, k: String, o: Any) => {
        k match {
          case "deref" => L.error("field 'deref' is immutable")
          case n => ref.component.getLuaMetatable.setField(L, k, o)
        }
        ()
      })
      L.register(mt, "__tostring", (ref: ComponentReference) => LuaRet(ref.name))
    }
  }
  implicit object LuaComponentManager extends LuaUserdataType[ComponentManager] {
    metatable { (L, mt) =>
      L.register(mt, "__index", (manager: ComponentManager, k: String) =>
        LuaRet(manager.getComponentReference(k))
      )
      L.register(mt, "__newindex", (manager: ComponentManager, k: String, v: ComponentReference) => {
        manager.setComponent(k, v.component)
        ()
      })
    }
  }
}
object LuaTemplateImplicits extends LuaTemplateImplicits
