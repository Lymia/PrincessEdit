/*
 * Copyright (c) 2017-2022 Lymia Alusyia <lymia@lymiahugs.com>
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

package moe.lymia.princess.svg.scripting

import moe.lymia.lua._
import moe.lymia.princess.core.LuaLibrary
import moe.lymia.princess.core.packages.PackageList
import moe.lymia.princess.svg._
import moe.lymia.princess.svg.components._

import java.awt.{Color, Font}
import java.nio.file.Files

trait LuaTextImplicits {
  implicit object LuaFont extends PropertiesUserdataType[Font] {
    unboundMethod("italic")((f: Font, b: Boolean) => if(b) f.deriveFont(f.getStyle | Font.ITALIC)
                                                     else  f.deriveFont(f.getStyle & ~Font.ITALIC))
    unboundMethod("bold"  )((f: Font, b: Boolean) => if(b) f.deriveFont(f.getStyle | Font.BOLD)
                                                     else  f.deriveFont(f.getStyle & ~Font.BOLD))
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

  implicit object LuaFormattedString extends LuaUserdataType[FormattedString]
  implicit object LuaFormattedStringBuffer extends PropertiesUserdataType[FormattedStringBuffer] {
    property("font"        , (L, a) => a.font            , (L, a, v : Font  ) => a.font             = v)
    property("relativeSize", (L, a) => a.fontRelativeSize, (L, a, v : Double) => a.fontRelativeSize = v)
    property("color"       , (L, a) => a.color           , (L, a, v : Color ) => a.color            = v)

    unboundMethod("append") { (a: FormattedStringBuffer, str: String) => a.append(str) }
    unboundMethod("appendWithAttributes") { (L: LuaState, a: FormattedStringBuffer, str: String, attr: LuaTable) =>
      val textAttrs = TextAttributes(L.rawGet(attr, "font").as[Option[Font]].getOrElse(a.font),
                                     L.rawGet(attr, "relativeSize").as[Option[Double]].getOrElse(a.fontRelativeSize),
                                     L.rawGet(attr, "color").as[Option[Color]].getOrElse(a.color))
      a.append(str, textAttrs)
    }

    unboundMethod("getFormattedString")((a: FormattedStringBuffer) => a.finish())
    unboundMethod("paragraphBreak"    )((a: FormattedStringBuffer) => a.paragraphBreak())
    unboundMethod("lineBreak"         )((a: FormattedStringBuffer) => a.lineBreak())
    unboundMethod("bulletStop"        )((a: FormattedStringBuffer) => a.bulletStop())
    unboundMethod("noStartLineHint"   )((a: FormattedStringBuffer) => a.noStartLineHint())
  }
}

case class TextLib(packages: PackageList) extends LuaLibrary {
  def open(L: LuaState, table: LuaTable) = {
    L.register(table, "FormattedStringBuffer", () => new FormattedStringBuffer())
    L.register(table, "SimpleText", (str: String, font: Font, size: Double, color: Option[Color]) =>
      new SimpleTextComponent(str, font, size, color.getOrElse(Color.BLACK)).ref)
    L.register(table, "SimpleFormattedText", (str: FormattedString, size: Double) =>
      new SimpleFormattedTextComponent(str, size).ref)
    L.register(table, "TextLayout", (bounds: Bounds) => new TextLayoutComponent(bounds).ref)

    L.register(table, "loadFont", (path: String) =>
      Font.createFont(Font.TRUETYPE_FONT, Files.newInputStream(packages.forceResolve(path))))
  }
}