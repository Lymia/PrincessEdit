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

package moe.lymia.princess.rasterizer

import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

import moe.lymia.princess.util.IOUtils
import org.apache.batik.anim.dom.SVGDOMImplementation
import org.apache.batik.transcoder._
import org.apache.batik.transcoder.image.{ImageTranscoder, PNGTranscoder}
import org.apache.batik.util.SVGConstants
import org.eclipse.swt.graphics.{ImageData, PaletteData}

import scala.xml.Elem

object BatikRasterizerFactory extends SVGRasterizerFactory {
  override def createRasterizer(): SVGRasterizer = BatikRasterizer
}

object BatikRasterizer extends SVGRasterizer {
  private def newHints(x: Int, y: Int) = {
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
    hints
  }
  private def newInput(svg: Elem) = {
    new TranscoderInput(new ByteArrayInputStream(svg.toString().getBytes(StandardCharsets.UTF_8)))
  }

  override def rasterizeSVGToPNG(x: Int, y: Int, svg: Elem, out: Path): Unit = {
    val transcoder = new PNGTranscoder()
    transcoder.setTranscodingHints(newHints(x, y))
    val input = newInput(svg)
    val output = new TranscoderOutput(Files.newOutputStream(out))
    transcoder.transcode(input, output)
  }
  override def rasterizeAwt(x: Int, y: Int, svg: Elem): BufferedImage = {
    var imageOut: BufferedImage = null
    val output = new ImageTranscoder {
      override def createImage(width: Int, height: Int): BufferedImage = {
        new BufferedImage(width, height, BufferedImage.TYPE_4BYTE_ABGR)
      }
      override def writeImage(img: BufferedImage, output: TranscoderOutput): Unit = {
        imageOut = img
      }
    }
    val input = newInput(svg)
    output.transcode(input, null)
    imageOut
  }
  override def rasterizeSwt(x: Int, y: Int, svg: Elem): ImageData = {
    val image = rasterizeAwt(x, y, svg)

    val palette = new PaletteData(0xFF0000, 0xFF00, 0xFF)
    val data = new ImageData(image.getWidth, image.getHeight, 24, palette)
    val row = new Array[Int](data.width)
    for(y <- 0 until data.height) {
      image.getRGB(0, y, data.width, 1, row, 0, 1)
      for(x <- 0 until data.width) {
        data.setPixel(x, y, row(x) & 0xFFFFFF)
        data.setAlpha(x, y, row(x) >>> 24)
      }
    }

    data
  }

  override def dispose() = { }
}