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

package moe.lymia.princess.svg

import java.awt.Font
import java.awt.geom.Rectangle2D
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicInteger
import javax.xml.parsers.SAXParserFactory
import scala.xml.factory.XMLLoader
import scala.xml.{Elem, SAXParser}

case class PhysicalUnit(svgName: String, unPerInch: Double)
object PhysicalUnit {
  val mm = PhysicalUnit("mm", 25.4)
  val in = PhysicalUnit("in", 1)
}

trait SizeBase {
  val width: Double
  val height: Double
}

final case class PhysicalSize(width: Double, height: Double, unit: PhysicalUnit) extends SizeBase {
  val widthString  = s"$width${unit.svgName}"
  val heightString = s"$height${unit.svgName}"
}

final case class RenderSettings(viewport: Size, unPerViewport: Double, physicalUnit: PhysicalUnit) {
  val size            = PhysicalSize(viewport.width * unPerViewport, viewport.height * unPerViewport, physicalUnit)
  val coordUnitsPerIn = size.unit.unPerInch / unPerViewport
  def scaleFont(font: Font, ptSize: Double) =
    font.deriveFont((ptSize * (coordUnitsPerIn / 72.0)).toFloat)
}

final case class Size(width: Double, height: Double) extends SizeBase
final case class Bounds(minX: Double, minY: Double, maxX: Double, maxY: Double) extends SizeBase {
  val width  = maxX - minX
  val height = maxY - minY

  lazy val size = Size(width, height)
  def translate(x: Double, y: Double) = Bounds(minX + x, minY + y, maxX + x, maxY + y)

  def toRectangle2D = new Rectangle2D.Double(minX, minY, width, height)
}
object Bounds {
  def apply(width: Double, height: Double) = new Bounds(0, 0, width, height)
  def apply(size: Size) = new Bounds(0, 0, size.width, size.height)
  def apply(rectangle: Rectangle2D) = new Bounds(rectangle.getMinX, rectangle.getMinY,
                                                 rectangle.getMaxX, rectangle.getMaxY)
}

private[svg] object GenID {
  private val globalId = new AtomicInteger(0)
  private def makeGlobalId() = globalId.incrementAndGet() & 0x7FFFFFFF

  private val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
  private lazy val rng = new SecureRandom()
  def makeRandomString() =
    new String((for(i <- 0 until 16) yield chars.charAt(math.abs(rng.nextInt() % chars.length))).toArray)
  def makeId() =
    s"${makeGlobalId()}_${makeRandomString()}"
}

private[svg] object XML extends XMLLoader[Elem] {
  override def parser: SAXParser = {
    val factory = SAXParserFactory.newInstance()
    factory.setNamespaceAware(false)
    factory.setValidating(false)
    for(feature <- Seq(// DTD loading for SVG is way too slow, unfortunely
                       "http://apache.org/xml/features/nonvalidating/load-dtd-grammar",
                       "http://apache.org/xml/features/nonvalidating/load-external-dtd",
                       // No thousands smiles
                       "http://xml.org/sax/features/external-general-entities",
                       "http://xml.org/sax/features/external-parameter-entities")) try {
      factory.setFeature(feature, false)
    } catch {
      case _: Exception =>
    }
    factory.newSAXParser()
  }
}