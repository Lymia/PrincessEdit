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

package moe.lymia.princess

import moe.lymia.princess.core.state.{GuiContext, GuiLoop}
import moe.lymia.princess.views.frontend.SplashScreen
import moe.lymia.princess.views.mainframe.MainFrame
import org.eclipse.swt.widgets.Display

import java.nio.file.Paths

private case class CLIException(message: String) extends Exception

class CLI {
  private val logger = DefaultLogger.bind("CLI")

  private type CommandFn = () => Unit

  private var command: Option[CommandFn] = None
  private var loadTarget: Option[String] = None
  private val parser = new scopt.OptionParser[Unit]("./PrincessEdit") {
    help("help").text("Shows this help message.")
    note("")
    arg[String]("<project.pedit-project>").foreach(x => loadTarget = Some(x)).hidden().optional()
    note("")
    opt[String]('l', "loadProject").valueName("<project.pedit-project>").text("Loads a project.").foreach{ x =>
      setCmd(cmd_load _)
      loadTarget = Some(x)
    }
    if (!Environment.isNativeImage) opt[Unit]("nativeImageGenConfig").foreach { x =>
      setCmd(cmd_nativeImageGenConfig _)
    }
    if (!Environment.isNativeImage) opt[Unit]("nativeImageProcessConfig").foreach{ x =>
      setCmd(cmd_nativeImageProcessConfig _)
    }
  }

  private def error(s: String) = throw CLIException(s)
  private def time[T](what: String)(v: => T) = {
    val time = System.currentTimeMillis()
    val res = v
    logger.info(s"$what in ${System.currentTimeMillis() - time}ms.")
    res
  }
  private def setCmd(cmd: () => Unit): Unit = {
    if(command.isDefined) error("Command already set!")
    command = Some(cmd)
  }
  private def mainLoop[T](f: GuiContext => Unit): Unit = new GuiLoop().mainLoop(f)

  private def cmd_default(): Unit = {
    mainLoop { ctx =>
      loadTarget match {
        case Some(x) =>
          MainFrame.loadProject(null, ctx, Paths.get(loadTarget.get))
        case None =>
          new SplashScreen(ctx).open()
      }
    }
  }
  private def cmd_load(): Unit = {
    mainLoop { ctx =>
      MainFrame.loadProject(null, ctx, Paths.get(loadTarget.get))
    }
  }
  private def cmd_nativeImageGenConfig(): Unit = {
    assert(!Environment.isNativeImage)
    mainLoop { ctx =>
      time("Generated configurations for native-image") {
        new NativeImageGenConfig(ctx).execute()
      }
    }
  }
  private def cmd_nativeImageProcessConfig(): Unit = {
    assert(!Environment.isNativeImage)
    NativeImageProcessConfig.processConfigs()
  }

  def main(args: Seq[String]): Unit = {
    try {
      Display.setAppName(AppName.PrincessEdit)
      if(parser.parse(args, ()).isDefined) command.getOrElse(cmd_default _)()
    } catch {
      case CLIException(e) => println(e)
      case e: Exception => e.printStackTrace()
    }
  }
}