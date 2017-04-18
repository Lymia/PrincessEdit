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

package moe.lymia.princess.editor

import java.nio.file.Paths

import moe.lymia.princess.editor.ui.frontend.SplashScreen
import moe.lymia.princess.editor.ui.mainframe.MainFrame
import moe.lymia.princess.rasterizer._

private case class CLIException(message: String) extends Exception

class CLI {
  private var command: () => Unit = cmd_default _
  private var loadFile: Option[String] = None
  val parser = new scopt.OptionParser[Unit]("./PrincessEdit") {
    help("help").text("Shows this help message.")
    note("")
    arg[String]("<project.pedit-project>").foreach(x => loadFile = Some(x)).hidden().optional()
  }

  private def error(s: String) = throw CLIException(s)
  private def time[T](what: String)(v: => T) = {
    val time = System.currentTimeMillis()
    val res = v
    println(s"$what in ${System.currentTimeMillis() - time}ms.")
    res
  }

  def cmd_default() = {
    val plaf = InkscapePlatform.instance
    val manager = new UIManager(plaf.locateBinary().head.createFactory())
    manager.mainLoop { ctx =>
      loadFile match {
        case Some(x) =>
          MainFrame.loadProject(null, ctx, Paths.get(x))
        case None =>
          new SplashScreen(ctx).open()
      }
    }
  }

  def main(args: Seq[String]) = try {
    if(parser.parse(args)) command()
  } catch {
    case CLIException(e) => println(e)
    case e: Exception => e.printStackTrace()
  }
}

