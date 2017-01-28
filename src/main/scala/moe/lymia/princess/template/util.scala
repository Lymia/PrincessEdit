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

final case class TemplateException(message: String) extends RuntimeException(message)

final case class Size(xSize: Int, ySize: Int) {
  def toRenderArea = RenderArea(0, 0, xSize - 1,  ySize - 1)
}
final case class RenderArea(x0: Int, y0: Int, x1: Int, y1: Int) {
  val xSize = x1 - x0 + 1
  val ySize = y1 - y0 + 1
}

final case class RenderSettings(renderScale: Double, coordUnitsPerIn: Double) {
  def scale(d: Double): Int = math.round(d * renderScale).toInt
  def scale(size: Size): Size = Size(scale(size.xSize), scale(size.ySize))
  def scale(renderArea: RenderArea): RenderArea =
    RenderArea(scale(renderArea.x0), scale(renderArea.y0), scale(renderArea.x1), scale(renderArea.y1))

  def scaleFont(font: Font, ptSize: Double) =
    font.deriveFont((ptSize * (coordUnitsPerIn / 72.0)).toFloat)
}
