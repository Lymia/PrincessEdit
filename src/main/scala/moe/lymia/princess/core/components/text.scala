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

import java.awt.font.TextAttribute
import java.awt.{Color, Font}

import moe.lymia.princess.core.svg._
import org.apache.batik.bridge.SVGTextElementBridge

import scala.collection.JavaConverters._
import scala.collection.mutable

case class TextAttributes(fontPath: String, fontSize: Float, attribute: Int, color: Color) {
  def toAttributeMap(resources: ResourceManager) = Map(
    TextAttribute.FONT       ->
      resources.loadFont(fontPath, fontSize).deriveFont(attribute),
    TextAttribute.FOREGROUND -> color
  )
}
class FormattedStringBuffer {
  private val data   = new mutable.ArrayBuffer[Seq[(String, TextAttributes)]]
  private val buffer = new mutable.ArrayBuffer[(String, TextAttributes)]

  var italics : Boolean = false
  var bold    : Boolean = false
  var color   : Color   = Color.BLACK
  var fontPath: String  = _
  var fontSize: Float   = 12f

  def append(s: String): Unit = {
    val attributes =
      TextAttributes(fontPath, fontSize, (if(italics) Font.ITALIC else 0) | (if(bold) Font.BOLD else 0), color)
    if(buffer.isEmpty || buffer.last._2 != attributes) buffer.append((s,attributes))
    else                                               buffer.append((s,buffer.last._2))
  }
  def paragraphBreak(): Unit = {
    data.append(buffer.clone())
    buffer.clear()
  }
  def finish() = new FormattedString(data :+ buffer)
}

private object FormattedString extends SVGTextElementBridge {
  private final class AttributedStringBuffer extends SVGTextElementBridge.AttributedStringBuffer
}
class FormattedString(data: Seq[Seq[(String, TextAttributes)]]) {
  override def toString = data.map(_.map(_._1).mkString("")).mkString("\n")
  def toAttributedStrings(resources: ResourceManager) = {
    data.map { paragraph =>
      val buffer = new FormattedString.AttributedStringBuffer
      for(s <- paragraph) buffer.append(s._1, s._2.toAttributeMap(resources).asJava)
      buffer.toAttributedString
    }
  }
}