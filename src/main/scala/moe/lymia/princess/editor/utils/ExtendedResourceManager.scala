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

package moe.lymia.princess.editor.utils

import moe.lymia.princess.editor.ControlContext
import moe.lymia.princess.renderer.SVGRenderable
import org.eclipse.jface.resource._
import org.eclipse.swt.graphics._

final class ExtendedResourceManager(underlying: ResourceManager, ctx: ControlContext) {
  def createImage(data: ImageData) =
    underlying.createImage(ImageDescriptor.createFromImageData(data))

  def createImageFromSVG(data: SVGRenderable, bx: Int, by: Int) = {
    val (x, y) = UIUtils.computeSizeFromRatio(new Point(bx,  by), data.size.width, data.size.height)
    createImage(ctx.syncRenderSwt(data, x, y))
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
