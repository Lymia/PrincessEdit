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

package moe.lymia.princess.renderer.components

import java.awt.font.TextLayout
import java.awt.{Color, Font}
import java.text.AttributedString

import moe.lymia.lua._
import moe.lymia.princess.core._
import moe.lymia.princess.renderer._
import moe.lymia.princess.renderer.lua._
import org.jfree.graphics2d.svg.SVGGraphics2D


sealed abstract class SimpleTextComponentBase(protected var fontSize: Double) extends GraphicsComponent {
  def createLayout(manager: ComponentRenderManager, graphics: SVGGraphics2D): TextLayout
  def preRender(manager: ComponentRenderManager, graphics: SVGGraphics2D): Unit = { }

  def renderComponent(manager: ComponentRenderManager, graphics: SVGGraphics2D, table: LuaTable): Bounds = {
    val layout = createLayout(manager, graphics)
    val bounds = layout.getBounds
    preRender(manager, graphics)
    layout.draw(graphics, 0, 0)
    Bounds(bounds.getMinX, bounds.getMinY, bounds.getMaxX, bounds.getMaxY)
  }

  property("fontSize", L => fontSize     , (L, newSize: Double ) => fontSize      = newSize)
}

class SimpleTextComponent(private var text: String, private var font: Font, fontSizeParam: Double,
                          private var color: Color)
  extends SimpleTextComponentBase(fontSizeParam) {

  def createLayout(manager: ComponentRenderManager, graphics: SVGGraphics2D) =
    new TextLayout(text, manager.settings.scaleFont(font, fontSize.toFloat), graphics.getFontRenderContext)

  override def preRender(manager: ComponentRenderManager, graphics: SVGGraphics2D): Unit = graphics.setColor(color)

  property("font" , L => font , (L, newFont : Font  ) => font  = newFont )
  property("text" , L => text , (L, newText : String) => text  = newText )
  property("color", L => color, (L, newColor: Color ) => color = newColor)
}

class SimpleFormattedTextComponent(private var text: FormattedString, fontSizeParam: Double)
  extends SimpleTextComponentBase(fontSizeParam) {

  def createLayout(manager: ComponentRenderManager, graphics: SVGGraphics2D) = {
    var str: AttributedString = null
    text.execute(manager, fontSize) {
      case FormatInstruction.RenderString(s) => str = s
      case _ => throw EditorException("invalid render string in SimpleFormattedText object")
    }
    new TextLayout(str.getIterator, graphics.getFontRenderContext)
  }

  property("text", L => text, (L, newText: FormattedString) => text = newText)
}