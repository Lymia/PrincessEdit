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

package moe.lymia.princess.core.renderer

import java.awt.Font
import java.awt.image.BufferedImage
import java.io.{ByteArrayInputStream, StringWriter, Writer}
import java.nio.charset.StandardCharsets

import moe.lymia.princess.core._
import moe.lymia.princess.util.IOUtils
import org.apache.batik.anim.dom.SVGDOMImplementation
import org.apache.batik.dom.GenericDOMImplementation
import org.apache.batik.svggen.SVGGraphics2D
import org.apache.batik.transcoder._
import org.apache.batik.transcoder.image.ImageTranscoder
import org.apache.batik.util.SVGConstants
import org.w3c.dom.Document

import scala.collection.mutable
import scala.xml.dtd.{DocType, PublicID}
import scala.xml.{XML => _, _}

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
  private val id = GenID.makeId()
  private var layerId = 0
  private val definitions = new mutable.ArrayBuffer[NodeSeq]

  private def attribute(key: String, value: String) = Attribute(None, key, Text(value), Null)

  def createDefinition(name: String, elem: Elem) = {
    val resourceName = s"princess_def_${id}_${layerId}_${name.replace(" ", "_").replaceAll("[^a-zA-Z0-9_]", "")}"
    layerId = layerId + 1
    definitions.append(elem % attribute("id", resourceName))
    resourceName
  }
  def createDefinitionFromContainer(name: String, expectedSize: Size, elems: Elem) =
    SVGDefinitionReference(createDefinition(name,
      elems % attribute("width"              , expectedSize.width.toString)
            % attribute("height"             , expectedSize.height.toString)
            % attribute("preserveAspectRatio", "none")
    ), expectedSize)
  def createDefinitionFromFragment(name: String, expectedSize: Size, elems: NodeSeq, noViewport: Boolean = false) =
    createDefinitionFromContainer(name, expectedSize,
      if(!noViewport) <svg viewBox={s"0 0 ${expectedSize.width} ${expectedSize.height}"}>{elems}</svg>
      else            <svg>{elems}</svg>
    )
  def createDefinitionFromGraphics(name: String, expectedSize: Size)(fn: SVGGraphics2D => Unit) = {
    val renderer = new SVGGraphicsRenderer(settings)
    fn(renderer.gfx)
    createDefinitionFromContainer(name, expectedSize, renderer.renderXML())
  }

  private val stylesheetDefs = new mutable.ArrayBuffer[String]
  def addStylesheetDefinition(str: String) = stylesheetDefs.append(str)

  def renderSVGTag(root: SVGDefinitionReference) =
    <svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink"
         version="1.1" preserveAspectRatio="none"
         width={settings.size.widthString} height={settings.size.heightString}
         viewBox={s"0 0 ${settings.viewport.width} ${settings.viewport.height}"}>
      {stylesheetDefs.map(x => <style>{x}</style>)}
      <defs>{definitions}</defs>
      {root.include(0, 0, settings.viewport.width, settings.viewport.height)}
    </svg>
  def write(w: Writer, root: SVGDefinitionReference, encoding: String= "utf-8") = {
    w.write(s"<?xml version='1.0' encoding='$encoding'?>\n")
    w.write(s"${SVGBuilder.SVG11Doctype.toString}\n")
    w.write( "<!-- SVG generated by PrincessEdit -->\n")
    w.write(s"${SVGBuilder.prettyPrinter.format(renderSVGTag(root))}\n")
    w.close()
  }
  def renderImage(x: Int, y: Int, root: SVGDefinitionReference) = {
    val buffer = new StringWriter()
    write(buffer, root)
    val input = new TranscoderInput(new ByteArrayInputStream(buffer.toString.getBytes(StandardCharsets.UTF_8)))

    var imageOut: BufferedImage = null
    val output = new ImageTranscoder {
      override def createImage(width: Int, height: Int): BufferedImage = {
        new BufferedImage(width, height, BufferedImage.TYPE_4BYTE_ABGR)
      }
      override def writeImage(img: BufferedImage, output: TranscoderOutput): Unit = {
        imageOut = img
      }
    }

    val hints = new TranscodingHints()
    hints.put(SVGAbstractTranscoder.KEY_ALLOWED_SCRIPT_TYPES, "") // Disable scripting
    hints.put(SVGAbstractTranscoder.KEY_WIDTH, x.toFloat)
    hints.put(SVGAbstractTranscoder.KEY_HEIGHT, y.toFloat)
    hints.put(XMLAbstractTranscoder.KEY_XML_PARSER_VALIDATING, false)
    hints.put(XMLAbstractTranscoder.KEY_DOM_IMPLEMENTATION, SVGDOMImplementation.getDOMImplementation)
    hints.put(XMLAbstractTranscoder.KEY_DOCUMENT_ELEMENT_NAMESPACE_URI, SVGConstants.SVG_NAMESPACE_URI)
    hints.put(XMLAbstractTranscoder.KEY_DOCUMENT_ELEMENT, "svg")
    hints.put(SVGAbstractTranscoder.KEY_USER_STYLESHEET_URI,
              IOUtils.getResourceURL("core/set_render_quality.css").toURI.toString)

    output.setTranscodingHints(hints)
    output.transcode(input, null)

    imageOut
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
}