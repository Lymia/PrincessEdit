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

import java.awt.Color

import moe.lymia.princess.core._
import moe.lymia.princess.core.svg._
import moe.lymia.princess.lua._

import scala.collection.mutable

trait LuaComponentImplicits {
  implicit object LuaParameterSize extends LuaParameter[Size] {
    override def toLua(size: Size) = new LuaObject(LuaExecWrapper(L => {
      val t = L.newTable()
      L.rawSet(t, 1, size.width)
      L.rawSet(t, 2, size.height)
      t
    }))
    override def fromLua(L: Lua, v: Any, source: => Option[String]): Size = v match {
      case t: LuaTable =>
        val Ls = new LuaState(L)
        Size(Ls.getTable(t, 1).as[Double], Ls.getTable(t, 2).as[Double])
      case _ => typerror(L, source, v, "Size")
    }
  }
  implicit object LuaParameterColor extends LuaParameter[Color] {
    override def toLua(color: Color) = new LuaObject(LuaExecWrapper(L => {
      val t = L.newTable()
      L.rawSet(t, 1, color.getRed)
      L.rawSet(t, 2, color.getGreen)
      L.rawSet(t, 3, color.getBlue)
      t
    }))
    override def fromLua(L: Lua, v: Any, source: => Option[String]): Color = v match {
      case t: LuaTable =>
        val Ls = new LuaState(L)
        new Color(Ls.getTable(t, 1).as[Int], Ls.getTable(t, 2).as[Int], Ls.getTable(t, 3).as[Int])
      case _ => typerror(L, source, v, "Color")
    }
  }

  implicit object LuaComponentReference extends LuaUserdataType[ComponentReference] {
    metatable { (L, mt) =>
      L.register(mt, "__index"   , (L: LuaState, ref: ComponentReference, k: String) =>
        k match {
          case "deref" => LuaRet(ref.deref)
          case n => ref.component.getField(L, k).toLua(L)
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
  implicit object LuaFormattedString extends LuaUserdataType[FormattedString]
  implicit object LuaFormattedStringBuffer extends LuaUserdataType[FormattedStringBuffer] {
    metatable { (L, mt) =>
      L.register(mt, "__index", (attributed: FormattedStringBuffer, k: String) =>
        k match {
          case "italic"             => attributed.italics
          case "bold"               => attributed.bold
          case "color"              => attributed.color
          case "fontPath"           => attributed.fontPath
          case "fontSize"           => attributed.fontSize
          case "append"             => LuaClosure((str: String) => { attributed.append(str); () }).fn
          case "getFormattedString" => LuaClosure(() => attributed.finish()).fn
          case "paragraphBreak"     => LuaClosure(() => attributed.paragraphBreak()).fn
          case n => throw TemplateException(s"no such field '$n'")
        }
      )
      L.register(mt, "__newindex", (L: LuaState, attributed: FormattedStringBuffer, k: String, o: Any) => {
        k match {
          case "italic"             => attributed.italics  = o.fromLua[Boolean](L)
          case "bold"               => attributed.bold     = o.fromLua[Boolean](L)
          case "color"              => attributed.color    = o.fromLua[Color  ](L)
          case "fontPath"           => attributed.fontPath = o.fromLua[String ](L)
          case "fontSize"           => attributed.fontSize = o.fromLua[Float  ](L)
          case "append"             |
               "getFormattedString" |
               "paragraphBreak"     => throw TemplateException(s"property '$k' is immutable")
          case n => throw TemplateException(s"no such field '$n'")
        }
        ()
      })
    }
  }
}

case class ComponentLib(packages: PackageList) {
  private val xmlTemplateDataCache = new mutable.HashMap[String, XMLTemplateData]
  private def getXMLTemplateData(string: String) =
    xmlTemplateDataCache.getOrElseUpdate(string, XMLTemplateData.loadTemplate(packages, string))

  def open(L: LuaState) = {
    L.registerGlobal("ComponentManager", () => new ComponentManager())
    L.registerGlobal("FormattedStringBuffer", () => new FormattedStringBuffer())

    val component = L.newTable()

    L.register(component, "Template", (s: String, size: Size) =>
      new XMLTemplateComponent(size, getXMLTemplateData(s)).ref)
    L.register(component, "Resource", (s: String, size: Size) =>
      new ResourceComponent(size, s).ref)
    L.register(component, "BaseLayout", (L: LuaState) =>
      new LayoutComponent(L).ref)
    L.register(component, "SimpleText", (str: FormattedString) =>
      new SimpleTextComponent(str).ref)

    L.setGlobal("component", component)
  }
}