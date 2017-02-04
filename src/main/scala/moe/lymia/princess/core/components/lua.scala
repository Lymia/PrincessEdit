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

package moe.lymia.princess.core.components

import moe.lymia.princess.core._
import moe.lymia.princess.core.renderer._
import moe.lymia.princess.lua._

import scala.collection.mutable

trait LuaComponentImplicits {
  implicit object LuaComponentReference extends LuaUserdataType[ComponentReference] {
    metatable { (L, mt) =>
      L.register(mt, "__index"   , (L: LuaState, ref: ComponentReference, k: String) =>
        k match {
          case "deref" => LuaRet(ref.deref)
          case n => ref.component.getField(L, k)
        }
      )
      L.register(mt, "__newindex", (L: LuaState, ref: ComponentReference, k: String, o: Any) => {
        k match {
          case "deref" => L.error("field 'deref' is immutable")
          case n => ref.component.setField(L, k, o)
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
  implicit object LuaParameterSize extends LuaParameter[Size] {
    override def toLua(size: Size) = new LuaObject(LuaExecWrapper(L => {
      val t = L.newTable()
      L.rawSet(t, 1, size.width)
      L.rawSet(t, 2, size.height)
      t
    }))
    override def fromLua(L: Lua, v: Any, source: String): Size = v match {
      case t: LuaTable =>
        def checkTableInt(i: Int) = L.getTable(t, i) match {
          case x: java.lang.Double => x
          case _ => typerror(L, source, v, "size")
        }
        Size(checkTableInt(1), checkTableInt(2))
      case _ => typerror(L, source, v, "size")
    }
  }
}

case class ComponentLib(packages: PackageList) {
  private val xmlTemplateDataCache = new mutable.HashMap[String, XMLTemplateData]
  private def getXMLTemplateData(string: String) =
    xmlTemplateDataCache.getOrElseUpdate(string, XMLTemplateData.loadTemplate(packages, string))

  def open(L: LuaState) = {
    val component = L.newTable()

    L.register(component, "fromTemplate", (s: String, size: Size) =>
      new XMLTemplateComponent(size, getXMLTemplateData(s)).ref)
    L.register(component, "fromResource", (s: String, size: Size) =>
      new ResourceComponent(size, s).ref)
    L.register(component, "newLayout", (L: LuaState, size: Size) =>
      new LayoutComponent(L, size).ref)

    L.setGlobal("component", component)
  }
}