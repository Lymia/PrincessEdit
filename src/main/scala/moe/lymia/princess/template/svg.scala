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

package moe.lymia.princess.template

import java.awt.Font
import java.io.{StringWriter, Writer}
import java.security.SecureRandom

import org.apache.batik.anim.dom.SVGDOMImplementation
import org.apache.batik.dom.GenericDOMImplementation
import org.apache.batik.svggen.SVGGraphics2D
import org.w3c.dom.Document

import scala.collection.mutable
import scala.xml.dtd.{DocType, PublicID}
import scala.xml._

final class SVGGraphics2DExtension(document: Document, settings: RenderSettings) extends SVGGraphics2D(document) {
  override def setFont(f: Font) = super.setFont(settings.scaleFont(f, f.getSize2D))
}
final class SVGGraphicsRenderer(settings: RenderSettings) {
  val gfx = {
    val doc = SVGGraphicsRenderer.domImpl.createDocument(SVGDOMImplementation.SVG_NAMESPACE_URI, "svg", null)
    new SVGGraphics2DExtension(doc, settings)
  }

  def renderXML() = {
    val out = new StringWriter()
    gfx.stream(out, true)
    XML.loadString(out.toString)
  }
}
object SVGGraphicsRenderer {
  lazy val domImpl = GenericDOMImplementation.getDOMImplementation
}

final case class SVGDefinitionReference(name: String, expectedSize: Size) {
  def include(x: Double, y: Double): Elem =
    <use x={x.toString} y={y.toString} xlink:href={s"#$name"}/>
  def include(x: Double, y: Double, height: Double, width: Double): Elem =
    if(Size(height, width) == expectedSize) include(x, y)
    else <use x={x.toString} y={y.toString} xlink:href={s"#$name"}
              transform={s"translate(-$x -$y) "+
                         s"scale(${width/expectedSize.width} ${height/expectedSize.height}) "+
                         s"translate($x $y)"}/>
}
final class SVGBuilder(val settings: RenderSettings) {
  private val id = SVGBuilder.makeId()
  private var layerId = 0
  private val definitions = new mutable.ArrayBuffer[NodeSeq]

  private def attribute(key: String, value: String) = Attribute(None, key, Text(value), Null)

  def createDefinition(name: String, elem: Elem) = {
    val resourceName = s"princessedit_def_${id}_${layerId}_${name.replace(" ", "_").replaceAll("[^a-zA-Z0-9_]", "")}"
    layerId = layerId + 1
    definitions.append(elem % attribute("id", resourceName))
    resourceName
  }
  def createDefinitionFromContainer(name: String, expectedSize: Size, elems: Elem) =
    SVGDefinitionReference(createDefinition(name,
      elems % attribute("width"              , expectedSize.width.toString )
            % attribute("height"             , expectedSize.height.toString)
            % attribute("preserveAspectRatio", "none"                      )
    ), expectedSize)
  def createDefinitionFromFragment(name: String, expectedSize: Size, elems: NodeSeq) =
    createDefinitionFromContainer(name, expectedSize, <svg>{elems}</svg>)
  def createDefinitionFromGraphics(name: String, expectedSize: Size)(fn: SVGGraphics2D => Unit) = {
    val renderer = new SVGGraphicsRenderer(settings)
    fn(renderer.gfx)
    createDefinitionFromContainer(name, expectedSize, renderer.renderXML())
  }

  def renderSVGTag(root: SVGDefinitionReference) =
    <svg xmlns="http://www.w3.org/2000/svg" version="1.1" preserveAspectRatio="none"
         width={settings.size.widthString} height={settings.size.heightString}
         viewBox={s"0 0 ${settings.viewport.width} ${settings.viewport.height}"}>
      <use>{definitions}</use>
      {root.include(0, 0, settings.viewport.width, settings.viewport.height)}
    </svg>
  def write(w: Writer, root: SVGDefinitionReference, encoding: String= "utf-8") = {
    w.write(s"<?xml version='1.0' encoding='$encoding'?>\n")
    w.write(s"${SVGBuilder.SVG11Doctype.toString}\n")
    w.write(s"${SVGBuilder.prettyPrinter.format(renderSVGTag(root))}\n")
  }
}
object SVGBuilder {
  private val SVG11Doctype = DocType(
    "svg",
    PublicID("-//W3C//DTD SVG 1.1//EN",
    "http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd"),
    Nil
  )
  private val prettyPrinter = new PrettyPrinter(Int.MaxValue, 2)

  private var globalId = 0
  private val globalIdLock = new Object
  private def makeGlobalId() = globalIdLock synchronized {
    val id = globalId
    globalId = globalId + 1
    id
  }

  private val      chars = "abcdefghijklmnopqrstuvwxyz0123456789"
  private lazy val rng   = new SecureRandom()
  private def makeId() =
    makeGlobalId()+"_"+new String((for(i <- 0 until 16) yield chars.charAt(rng.nextInt(chars.length))).toArray)
}