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

import scala.collection.mutable.ArrayBuffer

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
  case object BaseLayer        extends LayerType
  case object OverlayLayer     extends LayerType
  case object PremaskEffects   extends LayerType
  case object MaskLayer        extends LayerType
  case object MaskOverlayLayer extends LayerType
  case object PostmaskEffects  extends LayerType

  val layers: Seq[LayerType] =
    Seq(BaseLayer, OverlayLayer, PremaskEffects, MaskLayer, MaskOverlayLayer, PostmaskEffects)
}

class RendererList extends (ImageBuffer => Unit) {
  private val effects = new ArrayBuffer[ImageBuffer => Unit]
  def addEffect(b: ImageBuffer => Unit) = effects.append(b)
  def apply(b: ImageBuffer) = effects.foreach(_(b))
  def hasEffects = effects.nonEmpty
}
class ImageLayer(xPxSize: Int, yPxSize: Int, xCoordOffset: Int, yCoordOffset: Int, xCoordSize: Int, yCoordSize: Int) {
  def this(xPxSize: Int, yPxSize: Int) = this(xPxSize, yPxSize, 0, 0, xPxSize, yPxSize)

  private def makeBuffer(imageType: Int) =
    new ImageBuffer(xPxSize, yPxSize, xCoordOffset, yCoordOffset, xCoordSize, yCoordSize, imageType)

  private val layers = LayerType.layers.map(x => x -> new RendererList).toMap
  def getLayer(layer: LayerType) = layers.getOrElse(layer, sys.error(s"unknown layer type $layer"))
  def addRenderer(layer: LayerType)(effect: ImageBuffer => Unit) = getLayer(layer).addEffect(effect)
  private def applyLayer(layer: LayerType, image: ImageBuffer) = getLayer(layer)(image)

  private def applyMask(image: ImageBuffer) = {
    if(getLayer(LayerType.MaskLayer).hasEffects || getLayer(LayerType.MaskOverlayLayer).hasEffects) {
      val mask = makeBuffer(BufferedImage.TYPE_BYTE_GRAY)
      applyLayer(LayerType.MaskLayer       , mask)
      applyLayer(LayerType.MaskOverlayLayer, mask)

      val imgBuffer  = new Array[Int](image.getWidth)
      val maskBuffer = new Array[Int](image.getWidth)
      for(y <- 0 until image.getHeight) {
        image.buffer.getRGB(0, y, image.getWidth, 1, imgBuffer , 0, 0)
        mask .buffer.getRGB(0, y, image.getWidth, 1, maskBuffer, 0, 0)
        for(x <- 0 until image.getWidth) {
          val buffer = imgBuffer(x)
          imgBuffer(x) = (buffer & 0xFFFFFF) | ((((buffer >>> 24) * (maskBuffer(x) & 0xFF)) / 255) << 24)
        }
        image.buffer.setRGB(0, y, image.getWidth, 1, imgBuffer , 0, 0)
      }
    }
  }
  def render() = {
    val image = makeBuffer(BufferedImage.TYPE_4BYTE_ABGR)
    applyLayer(LayerType.BaseLayer      , image)
    applyLayer(LayerType.OverlayLayer   , image)
    applyLayer(LayerType.PremaskEffects , image)
    applyMask(image)
    applyLayer(LayerType.PostmaskEffects, image)
    image
  }
}