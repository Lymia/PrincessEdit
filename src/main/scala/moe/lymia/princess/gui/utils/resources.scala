/*
 * Copyright (c) 2017-2022 Lymia Alusyia <lymia@lymiahugs.com>
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

package moe.lymia.princess.gui.utils

import moe.lymia.princess.core.state.GuiContext
import moe.lymia.princess.svg.SVGRenderable
import moe.lymia.princess.util.IOUtils
import org.eclipse.jface.resource._
import org.eclipse.swt.graphics._

import scala.collection.mutable

final class IconData(val svgData: Array[Byte], val imageData: Map[Int, ImageData])
object IconData {
  private def getIconData(iconName: String) = {
    val loader = new ImageLoader()
    val imageDataMap = for (size <- Seq(16, 20, 22, 24, 32, 40, 48, 64, 96, 256)) yield {
      loader.load(IOUtils.getResource(s"res/icon-$iconName-$size.png"))
      size -> loader.data.head
    }
    new IconData(IOUtils.loadBinaryResource(s"res/icon-$iconName.svg"), imageDataMap.toMap)
  }

  lazy val AppIcon: IconData = getIconData("app")
  lazy val DocumentIcon: IconData = getIconData("document")
}

final class ExtendedResourceManager(underlying: ResourceManager, ctx: GuiContext) {
  def createImage(data: ImageData): Image =
    underlying.createImage(ImageDescriptor.createFromImageData(data))

  private lazy val imageCache = new mutable.HashMap[IconData, Array[Image]]
  def loadIcon(iconData: IconData): Array[Image] =
    imageCache.getOrElseUpdate(iconData, iconData.imageData.values.map(createImage).toArray)

  def createImageFromSVG(data: SVGRenderable, bx: Int, by: Int): Image = {
    val (x, y) = UIUtils.computeSizeFromRatio(new Point(bx,  by), data.size.width, data.size.height)
    createImage(ctx.syncRender(data.asSvg(), x, y))
  }

  // Proxy functions
  def createColor(descriptor: ColorDescriptor): Color = underlying.createColor(descriptor)
  def createColor(descriptor: RGB): Color = underlying.createColor(descriptor)
  def cancelDisposeExec(r: Runnable): Unit = underlying.cancelDisposeExec(r)
  def destroyFont(descriptor: FontDescriptor): Unit = underlying.destroyFont(descriptor)
  def disposeExec(r: Runnable): Unit = underlying.disposeExec(r)
  def find(deviceResourceDescriptor: DeviceResourceDescriptor): AnyRef = underlying.find(deviceResourceDescriptor)
  def destroyColor(descriptor: ColorDescriptor): Unit = underlying.destroyColor(descriptor)
  def destroy(deviceResourceDescriptor: DeviceResourceDescriptor): Unit = underlying.destroy(deviceResourceDescriptor)
  def destroyColor(descriptor: RGB): Unit = underlying.destroyColor(descriptor)
  def getDevice: Device = underlying.getDevice
  def createImageWithDefault(descriptor: ImageDescriptor): Image = underlying.createImageWithDefault(descriptor)
  def create(deviceResourceDescriptor: DeviceResourceDescriptor): AnyRef = underlying.create(deviceResourceDescriptor)
  def destroyImage(descriptor: ImageDescriptor): Unit = underlying.destroyImage(descriptor)
  def get(descriptor: DeviceResourceDescriptor): AnyRef = underlying.get(descriptor)
  def createFont(descriptor: FontDescriptor): Font = underlying.createFont(descriptor)
  def dispose(): Unit = underlying.dispose()
  def createImage(descriptor: ImageDescriptor): Image = underlying.createImage(descriptor)
}
