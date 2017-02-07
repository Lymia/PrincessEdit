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

import moe.lymia.princess.core.renderer._
import org.apache.batik.bridge.SVGTextElementBridge

import scala.collection.JavaConverters._
import scala.collection.mutable

case class LuaTextAttributes(fontPath: String, fontSize: Float, attribute: Int, color: Color) {
  def toAttributeMap(resources: ResourceManager) = Map(
    TextAttribute.FONT       ->
      resources.loadFont(fontPath, fontSize).deriveFont(attribute),
    TextAttribute.FOREGROUND -> color
  )
}
class AttributedStringBuffer {
  private val underlying = new mutable.ArrayBuffer[(String, LuaTextAttributes)]

  var italics : Boolean = false
  var bold    : Boolean = false
  var color   : Color   = Color.BLACK
  var fontPath: String  = _
  var fontSize: Float   = 12f

  def append(s: String) =
    underlying.append((s, LuaTextAttributes(
      fontPath, fontSize, (if(italics) Font.ITALIC else 0) | (if(bold) Font.BOLD else 0), color)))
  def finish() = new AttributedStringData(underlying.clone())
}

private object AttributedStringData extends SVGTextElementBridge {
  private final class BufferType extends SVGTextElementBridge.AttributedStringBuffer
}
class AttributedStringData(underlying: Seq[(String, LuaTextAttributes)]) {
  override def toString = underlying.map(_._1).mkString("")
  def toAttributedString(resources: ResourceManager) = {
    val buffer = new AttributedStringData.BufferType
    for(s <- underlying) buffer.append(s._1, s._2.toAttributeMap(resources).asJava)
    buffer.toAttributedString
  }
}