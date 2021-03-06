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

import java.io._
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO

import moe.lymia.princess.util.IOUtils
import org.eclipse.swt.graphics.ImageData

import scala.xml.Elem

class InkscapeConnectionFactory(command: Seq[String], path: Path) extends SVGRasterizerFactory {
  private[rasterizer] def inkscape(args: String*) =
    new ProcessBuilder(command ++ args : _*).directory(path.toFile).redirectError(ProcessBuilder.Redirect.INHERIT)

  private var pathChecked = false
  def checkPath() = try {
    val initCheck = inkscape("-z", "--version").start()
    initCheck.getOutputStream.close()
    val str = IOUtils.loadFromStream(initCheck.getInputStream).trim
    if(!str.startsWith("Inkscape ")) Right("Process does not appear to be Inkscape.")
    println(s"Found Inkscape: $str")
    pathChecked = true
    Left(str)
  } catch {
    case e: Exception => Right(s"${e.getClass.getName}: ${e.getMessage}")
  }

  def createRasterizer() = {
    if(!pathChecked) checkPath() match {
      case Right(error) => sys.error(s"Invalid Inkscape path: $error")
      case _ =>
    }
    new InkscapeConnection(this)
  }
}

class InkscapeConnection(parent: InkscapeConnectionFactory) extends SVGRasterizer {
  private val inkscapeProcess =
    parent.inkscape("-z", "--shell").start()
  private val stdout  = inkscapeProcess.getInputStream
  private val stdout_r = new BufferedReader(new InputStreamReader(stdout, StandardCharsets.UTF_8), 1024*4)
  private val stdin   = inkscapeProcess.getOutputStream
  private val stdin_w = new PrintWriter(new OutputStreamWriter(stdin, StandardCharsets.UTF_8))

  private val lock = new Object
  private var disposed = false

  private def handleUntilCommandEnd(): Unit = {
    while(true) {
      val char = stdout_r.read()
      if(char == '>' || char == -1) return
      stdout_r.readLine()
    }
  }

  handleUntilCommandEnd()
  private def command(str: String*) = {
    val cmd = str.map(x => s"'${x.replace("'", "'\\''")}'").mkString(" ")
    stdin_w.println(cmd)
    stdin_w.flush()
    handleUntilCommandEnd()
  }

  def rasterizeSVGToPNG(x: Int, y: Int, svg: Elem, out: Path): Unit = lock.synchronized {
    if(disposed) sys.error("instance already disposed")
    IOUtils.withTemporaryFile(extension = "svg") { svgFile =>
      IOUtils.writeFile(svgFile, svg.toString().getBytes("UTF-8"))
      command(svgFile.toFile.getAbsolutePath, "-w", x.toString, "-h", y.toString,
              "--export-png", out.toAbsolutePath.toString)
    }
  }

  def rasterizeAwt(x: Int, y: Int, svg: Elem) = lock.synchronized {
    if(disposed) sys.error("instance already disposed")
    IOUtils.withTemporaryFile(extension = "png") { pngFile =>
      rasterizeSVGToPNG(x, y, svg, pngFile)
      ImageIO.read(pngFile.toFile)
    }
  }
  def rasterizeSwt(x: Int, y: Int, svg: Elem) = lock.synchronized {
    if(disposed) sys.error("instance already disposed")
    IOUtils.withTemporaryFile(extension = "png") { pngFile =>
      rasterizeSVGToPNG(x, y, svg, pngFile)
      new ImageData(pngFile.toFile.getAbsolutePath)
    }
  }

  def dispose() = lock.synchronized {
    if(!disposed) {
      disposed = true
      stdin_w.println("quit")
      stdin_w.flush()
      inkscapeProcess.waitFor(1, TimeUnit.SECONDS)
      if(inkscapeProcess.isAlive) inkscapeProcess.destroyForcibly()
    }
  }

  override def finalize(): Unit = dispose()
}