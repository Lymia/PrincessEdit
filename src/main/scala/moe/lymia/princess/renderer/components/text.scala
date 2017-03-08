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

import java.awt.font.TextAttribute
import java.awt.{Color, Font}
import java.text.{AttributedCharacterIterator, AttributedString}

import moe.lymia.princess.core._

import scala.collection.JavaConverters._
import scala.collection.mutable

private[components] sealed trait FormatInstruction
private[components] sealed trait RawFormatInstruction {
  def reify(manager: ComponentRenderManager, fontSize: Double): FormatInstruction
}
private[components] sealed trait SharedFormatInstruction extends FormatInstruction with RawFormatInstruction {
  override def reify(manager: ComponentRenderManager, fontSize: Double): FormatInstruction = this
}

private[components] object FormatInstruction {
  case class RenderString(str: AttributedString) extends FormatInstruction
  case object NewLine extends SharedFormatInstruction
  case object NewParagraph extends SharedFormatInstruction
  case object BulletStop extends SharedFormatInstruction
  case object NoStartLineHint extends SharedFormatInstruction
}
private[components] object RawFormatInstruction {
  case class RenderString(data: Seq[(String, TextAttributes)]) extends RawFormatInstruction {
    override def reify(manager: ComponentRenderManager, fontSize: Double): FormatInstruction = {
      val buffer = new AttributedStringBuffer
      for(s <- data) buffer.append(s._1, s._2.toAttributeMap(manager, fontSize).asJava)
      FormatInstruction.RenderString(buffer.toAttributedString)
    }
  }
}

case class TextAttributes(font: Font, fontRelativeSize: Double, color: Color) {
  def toAttributeMap(manager: ComponentRenderManager, fontSize: Double) = Map(
    TextAttribute.FONT       -> manager.settings.scaleFont(font, fontSize * fontRelativeSize),
    TextAttribute.FOREGROUND -> color
  ) : Map[AttributedCharacterIterator.Attribute, Object]
}
class FormattedStringBuffer {
  private val data       = new mutable.ArrayBuffer[RawFormatInstruction]
  private var lineBuffer = new mutable.ArrayBuffer[(String, TextAttributes)]

  var font            : Font   = _
  var fontRelativeSize: Double = 1
  var color           : Color  = Color.BLACK

  def append(s: String, attributes: TextAttributes): Unit = lineBuffer.append((s,attributes))
  def append(s: String): Unit = {
    if(font eq null) throw EditorException("No font set.")
    append(s, TextAttributes(font, fontRelativeSize, color))
  }
  private def outputBuffer() = if(lineBuffer.nonEmpty) {
    data.append(RawFormatInstruction.RenderString(lineBuffer))
    lineBuffer = new mutable.ArrayBuffer[(String, TextAttributes)]()
  }
  def lineBreak(): Unit = {
    outputBuffer()
    data.append(FormatInstruction.NewLine)
  }
  def paragraphBreak(): Unit = {
    outputBuffer()
    data.append(FormatInstruction.NewParagraph)
  }
  def bulletStop(): Unit = {
    outputBuffer()
    data.append(FormatInstruction.BulletStop)
  }
  def noStartLineHint(): Unit = {
    outputBuffer()
    data.append(FormatInstruction.NoStartLineHint)
  }
  def finish() =
    FormattedString(if(lineBuffer.isEmpty) data else data :+ RawFormatInstruction.RenderString(lineBuffer.clone()))
}

case class FormattedString(data: Seq[RawFormatInstruction]) {
  def toSimpleString = data.map {
    case RawFormatInstruction.RenderString(str) => str.map(_._1).mkString("")
    case _ => ""
  }.mkString("\n")

  private[components] def execute(manager: ComponentRenderManager, fontSize: Double)(f: FormatInstruction => Unit) =
    for(instruction <- data) f(instruction.reify(manager, fontSize))
}
