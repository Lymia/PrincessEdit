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

import java.awt.font.{TextAttribute, TextLayout}
import java.awt.{Color, Font}

import moe.lymia.princess.core.TemplateException
import moe.lymia.princess.core.lua._
import moe.lymia.princess.core.svg._
import moe.lymia.princess.lua._
import org.apache.batik.bridge.SVGTextElementBridge
import org.apache.batik.svggen.SVGGraphics2D

import scala.collection.JavaConverters._
import scala.collection.mutable

case class TextAttributes(font: Font, fontRelativeSize: Double, color: Color) {
  def toAttributeMap(manager: ComponentRenderManager, fontSize: Double) = Map(
    TextAttribute.FONT       -> manager.settings.scaleFont(font, fontSize * fontRelativeSize),
    TextAttribute.FOREGROUND -> color
  )
}
class FormattedStringBuffer {
  private val data            = new mutable.ArrayBuffer[Seq[Seq[(String, TextAttributes)]]]
  private val paragraphBuffer = new mutable.ArrayBuffer[Seq[(String, TextAttributes)]]
  private val lineBuffer      = new mutable.ArrayBuffer[(String, TextAttributes)]

  var font            : Font   = _
  var fontRelativeSize: Double = 1
  var color           : Color  = Color.BLACK

  def append(s: String, attributes: TextAttributes): Unit = lineBuffer.append((s,attributes))
  def append(s: String): Unit = {
    if(font eq null) throw TemplateException("No font set.")
    append(s, TextAttributes(font, fontRelativeSize, color))
  }
  def lineBreak(): Unit = {
    paragraphBuffer.append(lineBuffer.clone())
    lineBuffer.clear()
  }
  def paragraphBreak(): Unit = {
    lineBreak()
    data.append(paragraphBuffer.clone())
    paragraphBuffer.clear()
  }
  def finish() = new FormattedString(data :+ (paragraphBuffer :+ lineBuffer.clone()))
}

private object FormattedString extends SVGTextElementBridge {
  private final class AttributedStringBuffer extends SVGTextElementBridge.AttributedStringBuffer
}
class FormattedString(data: Seq[Seq[Seq[(String, TextAttributes)]]]) {
  override def toString = data.map(_.flatMap(_.map(_._1)).mkString("")).mkString("\n")
  def toAttributedString(manager: ComponentRenderManager, fontSize: Double) = {
    if(data.length != 1 || data.head.length != 1) throw TemplateException("String contains line breaks.")
    val buffer = new FormattedString.AttributedStringBuffer
    for(s <- data.head.head) buffer.append(s._1, s._2.toAttributeMap(manager, fontSize).asJava)
    buffer.toAttributedString
  }
  def toAttributedStrings(manager: ComponentRenderManager, fontSize: Double) = {
    data.map { paragraph =>
      paragraph.map { line =>
        val buffer = new FormattedString.AttributedStringBuffer
        for(s <- line) buffer.append(s._1, s._2.toAttributeMap(manager, fontSize).asJava)
        buffer.toAttributedString
      }
    }
  }
}

sealed abstract class SimpleTextComponentBase(protected var fontSize: Double) extends GraphicsComponent {
  def createLayout(manager: ComponentRenderManager, graphics: SVGGraphics2D): TextLayout
  def preRender(manager: ComponentRenderManager, graphics: SVGGraphics2D): Unit = { }

  def renderComponent(manager: ComponentRenderManager, graphics: SVGGraphics2D, table: LuaTable): Size = {
    val layout = createLayout(manager, graphics)
    val bounds = layout.getBounds
    preRender(manager, graphics)
    layout.draw(graphics, (0 - bounds.getMinX).toFloat, (0 - bounds.getMinY).toFloat)
    table.rawSet("baseline" , - bounds.getMinX)
    table.rawSet("baselineY", - bounds.getMinY)
    Size(bounds.getWidth, bounds.getHeight)
  }

  property("fontSize", L => fontSize, (L, newSize: Double) => fontSize = newSize)
}

class SimpleTextComponent(private var text: String, private var font: Font, fontSizeParam: Double,
                          private var color: Color)
  extends SimpleTextComponentBase(fontSizeParam) {

  def createLayout(manager: ComponentRenderManager, graphics: SVGGraphics2D) =
    new TextLayout(text, manager.settings.scaleFont(font, fontSize.toFloat), graphics.getFontRenderContext)

  override def preRender(manager: ComponentRenderManager, graphics: SVGGraphics2D): Unit = graphics.setColor(color)

  property("font" , L => font, (L, newFont: Font) => font = newFont)
  property("text" , L => text, (L, newText: String) => text = newText)
  property("color", L => color, (L, newColor: Color) => color = newColor)
}

class SimpleFormattedTextComponent(private var text: FormattedString, fontSizeParam: Double)
  extends SimpleTextComponentBase(fontSizeParam) {

  def createLayout(manager: ComponentRenderManager, graphics: SVGGraphics2D) = {
    val attributed = text.toAttributedString(manager, fontSize)
    new TextLayout(attributed.getIterator, graphics.getFontRenderContext)
  }

  property("text", L => text, (L, newText: FormattedString) => text = newText)
}