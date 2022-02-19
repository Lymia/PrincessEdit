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

import moe.lymia.princess.util.IOUtils
import org.eclipse.swt.graphics.ImageData

import java.nio.file.Path
import javax.imageio.ImageIO
import scala.xml.Elem

object ResvgConnectionFactory extends SVGRasterizerFactory {
  private[rasterizer] def resvg(args: String*) =
    new ProcessBuilder("resvg" +: args: _*).redirectError(ProcessBuilder.Redirect.INHERIT)

  def createRasterizer(): SVGRasterizer = {
    ResvgConnection
  }
}

private object ResvgConnection extends SVGRasterizer {
  private val lock = new Object
  private var disposed = false

  def rasterizeSVGToPNG(x: Int, y: Int, svg: Elem, out: Path): Unit = lock.synchronized {
    if (disposed) sys.error("instance already disposed")
    IOUtils.withTemporaryFile(extension = "svg") { svgFile =>
      IOUtils.writeFile(svgFile, svg.toString().getBytes("UTF-8"))
      var process = ResvgConnectionFactory.resvg("-w", x.toString, "-h", y.toString,
        svgFile.toString, out.toAbsolutePath.toString)
      process.start().waitFor()
    }
  }

  def rasterizeAwt(x: Int, y: Int, svg: Elem) = lock.synchronized {
    if (disposed) sys.error("instance already disposed")
    IOUtils.withTemporaryFile(extension = "png") { pngFile =>
      rasterizeSVGToPNG(x, y, svg, pngFile)
      ImageIO.read(pngFile.toFile)
    }
  }

  def rasterizeSwt(x: Int, y: Int, svg: Elem) = lock.synchronized {
    if (disposed) sys.error("instance already disposed")
    IOUtils.withTemporaryFile(extension = "png") { pngFile =>
      rasterizeSVGToPNG(x, y, svg, pngFile)
      new ImageData(pngFile.toFile.getAbsolutePath)
    }
  }

  def dispose() = lock.synchronized {
    if (!disposed) {
      disposed = true
    }
  }

  override def finalize(): Unit = dispose()
}