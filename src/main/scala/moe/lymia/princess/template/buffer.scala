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

import java.awt.image.BufferedImage
import java.io.{File, OutputStream}
import javax.imageio.ImageIO

import scala.collection.mutable

case class ImageType(extension: String, format: String, name: String)
object ImageType {
  val PNG = ImageType("png", "png", "PNG images")
  val JPG = ImageType("jpg", "jpg", "JPEG images")
  val BMP = ImageType("bmp", "bmp", "Windows bitmaps")
  val ALL_TYPES = Seq(PNG, JPG, BMP)
}

class ImageBuffer(xPxSize: Int, yPxSize: Int, xCoordOffset: Int, yCoordOffset: Int, xCoordSize: Int, yCoordSize: Int,
                  imageType: Int = BufferedImage.TYPE_4BYTE_ABGR) {
  def this(xPxSize: Int, yPxSize: Int) = this(xPxSize, yPxSize, 0, 0, xPxSize, yPxSize)

  def getWidth  = xPxSize
  def getHeight = yPxSize

  val buffer = new BufferedImage(xPxSize, yPxSize, imageType)

  def createGraphics() = {
    val gfx = buffer.createGraphics()
    if(xCoordOffset != 0 || yCoordOffset != 0)
      gfx.translate(-xCoordOffset, -yCoordOffset)
    if(xPxSize != xCoordSize || yPxSize != yCoordSize)
      gfx.scale(xPxSize.toDouble / xCoordSize.toDouble, yPxSize.toDouble / yCoordSize.toDouble)
    gfx
  }

  lazy val gfx = createGraphics()

  def writeTo(f  : File        , t: ImageType) = ImageIO.write(buffer, t.format, f  )
  def writeTo(out: OutputStream, t: ImageType) = ImageIO.write(buffer, t.format, out)
}

sealed trait LayerType
object LayerType {
  case object BaseLayer    extends LayerType
  case object OverlayLayer extends LayerType
  case object EffectsLayer extends LayerType
  case object MaskLayer    extends LayerType

  val layers: Seq[LayerType] =
    Seq(BaseLayer, OverlayLayer, EffectsLayer, MaskLayer)
}

class RenderContext(manager: RenderManager) {

}

class RendererList extends ((ImageBuffer, RenderContext) => Unit) {
  private val effects = new mutable.ArrayBuffer[(ImageBuffer, RenderContext) => Unit]
  def addEffect(b: (ImageBuffer, RenderContext) => Unit) = effects.append(b)
  def apply(b: ImageBuffer, context: RenderContext) = effects.foreach(_(b, context))
  def hasEffects = effects.nonEmpty
}
class ImageLayer(xPxSize: Int, yPxSize: Int, xCoordOffset: Int, yCoordOffset: Int, xCoordSize: Int, yCoordSize: Int,
                 imageType: Int = BufferedImage.TYPE_4BYTE_ABGR) {
  def this(xPxSize: Int, yPxSize: Int) = this(xPxSize, yPxSize, 0, 0, xPxSize, yPxSize)

  def layerFormat = imageType

  private val layers = LayerType.layers.map(x => x -> new RendererList).toMap
  def getLayer(layer: LayerType) = layers.getOrElse(layer, sys.error(s"unknown layer type $layer"))
  def addRenderer(layer: LayerType)(effect: (ImageBuffer, RenderContext) => Unit) = getLayer(layer).addEffect(effect)
  private def applyLayer(layer: LayerType, image: ImageBuffer, context: RenderContext) =
    getLayer(layer)(image, context)

  def render(context: RenderContext) = {
    val image = new ImageBuffer(xPxSize, yPxSize, xCoordOffset, yCoordOffset, xCoordSize, yCoordSize, imageType)
    LayerType.layers.foreach(layer => applyLayer(layer, image, context))
    image
  }
}

class RenderManager(xPxSize: Int, yPxSize: Int, xCoordSize: Int, yCoordSize: Int) {
  private val layers = new mutable.HashMap[String, ImageLayer]

  private val xRatio = xPxSize.toDouble / xCoordSize.toDouble
  private val yRatio = yPxSize.toDouble / yCoordSize.toDouble

  private def toPx(x: Int, y: Int) = (math.round(x * xRatio).toInt, math.round(y * yRatio).toInt)

  def createContext() = new RenderContext(this)
  def createLayer(name: String, xOffset: Int, yOffset: Int, xCoordSize: Int, yCoordSize: Int,
                  imageType: Int = BufferedImage.TYPE_4BYTE_ABGR) = {
    if(layers.contains(name)) throw TemplateError("layerexists", name)

    val (xPxOffset, yPxOffset) = toPx(xOffset, yOffset)
    val (xPxEnd   , yPxEnd   ) = toPx(xOffset + xCoordSize - 1, yOffset + yCoordSize - 1)

    layers(name) = new ImageLayer(xPxEnd - xPxOffset + 1, yPxEnd- yPxOffset + 1, xOffset, yOffset,
                                  xCoordSize, yCoordSize, imageType)
  }
  def getLayer(name: String) = layers.getOrElse(name, throw TemplateError("nosuchlayer", name))
}