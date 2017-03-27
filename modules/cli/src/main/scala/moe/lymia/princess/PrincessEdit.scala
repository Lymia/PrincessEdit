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

package moe.lymia.princess

import java.io.{FileOutputStream, FileWriter}
import java.nio.file.Paths
import javax.imageio.ImageIO
import javax.swing.UIManager

import moe.lymia.lua._
import moe.lymia.princess.core._
import moe.lymia.princess.rasterizer._
import moe.lymia.princess.renderer._
import moe.lymia.princess.util._

private case class CLIException(message: String) extends Exception

class CLI {
  private var mode: () => Unit = cmd_default _
  private var luaFile: Option[String] = None

  private var gameId: String = _
  private var cardData: String = _

  private var link: Boolean = false
  private var dirty: Boolean = false

  private var xSize: Int = _
  private var ySize: Int = _
  private var out: String = _

  private def error(s: String) = throw CLIException(s)

  private val uiMain = new UIMain
  private val parser = new scopt.OptionParser[Unit]("./PrincessEdit") with uiMain.ScoptArgs {
    help("help").text("Shows this help message.")
    note("")
    uiOptions()
    note("")
    cmd("lua").text("Runs a Lua file or starts a Lua console").foreach(_ => mode = cmd_lua _).children(
      arg[String]("<file>").foreach(x => luaFile = Some(x)).optional().hidden()
    )
    note("")
    cmd("listPackages").text("Print package status").foreach(_ => mode = cmd_listPackages _)
    note("")
    cmd("rasterize").text("Renders a template").foreach(_ => mode = cmd_rasterize _).children(
      arg[Int   ]("<x>"       ).foreach(xSize    = _).hidden(),
      arg[Int   ]("<y>"       ).foreach(ySize    = _).hidden(),
      arg[String]("<gameId>"  ).foreach(gameId   = _).hidden(),
      arg[String]("<cardData>").foreach(cardData = _).hidden(),
      arg[String]("<out>"     ).foreach(out      = _).hidden()
    )
    note("")
    cmd("render").text("Renders a template to SVG").foreach(_ => mode = cmd_render _).children(
      arg[String]("<gameId>"  ).foreach(gameId   = _).hidden(),
      arg[String]("<cardData>").foreach(cardData = _).hidden(),
      arg[String]("<out>"     ).foreach(out      = _).hidden(),
      opt[Unit  ]("link"      ).text("Link instead of include resources").foreach(_ => link = true),
      opt[Unit  ]("dirty"     ).text("Output version of SVG used for direct rendering. Implies --link").
        foreach(_ => dirty = true)
    )
  }

  private def time[T](what: String)(v: => T) = {
    val time = System.currentTimeMillis()
    val res = v
    println(s"$what in ${System.currentTimeMillis() - time}ms.")
    res
  }

  private def cmd_default() = {
    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName)
    uiMain.main()
  }

  private def cmd_lua(): Unit = {
    luaFile match {
      case Some(file) =>
        val L = LuaState.makeSafeContext()
        L.loadString(IOUtils.readFileAsString(Paths.get(file)), s"@$file") match {
          case Left (x) => L.call(x, 0)
          case Right(x) => println(x)
        }
      case None =>
        LuaConsole.startConsole()
    }
  }
  private def cmd_listPackages(): Unit = {
    println("Known game IDs:")
    for(id <- PackageManager.default.gameIDList) println(s" - ${id.displayName} (${id.name})")
  }

  private def renderCommon() = time("Loaded Lua context") {
    val game = PackageManager.default.loadGameId(gameId)
    val renderer = new RenderManager(game, NullCache)
    val card = if(cardData.startsWith("@")) IOUtils.readFileAsString(Paths.get(cardData.substring(1))) else cardData
    val cardDataTable = game.lua.L.loadString(s"return $card", "@<card data>") match {
      case Left (x) => game.lua.L.call(x, 1).head
      case Right(e) => error(e)
    }
    (renderer, cardDataTable)
  }
  private def cmd_rasterize(): Unit = {
    val (mainObj, cardData) = renderCommon()
    val rasterizer = time("Creating rasterizer instance") {
      new InkscapeConnectionFactory("inkscape").createRasterizer()
    }
    try {
      val data = time("Executing components") { mainObj.render(Seq(cardData), RasterizeResourceLoader) }
      val image = time("Rasterized image") { data.rasterizeAwt(rasterizer, xSize, ySize) }
      time("Writing .png") { ImageIO.write(image, "png", new FileOutputStream(out)) }
    } finally {
      rasterizer.dispose()
    }
  }
  private def cmd_render(): Unit = {
    val (mainObj, cardData) = renderCommon()
    val data = time("Executing components") {
      mainObj.render(Seq(cardData), if(dirty) RasterizeResourceLoader else ExportResourceLoader)
    }
    time("Renderered SVG") { data.write(new FileWriter(out), pretty = !dirty) }
  }

  def main(args: Seq[String]) = try {
    if(parser.parse(args)) mode()
  } catch {
    case CLIException(e) => println(e)
    case e: Exception => e.printStackTrace()
  }
}

object PrincessEdit {
  def main(args: Array[String]) = {
    println(s"Princess Edit v${VersionInfo.versionString} by Lymia")
    println("Released under the MIT license")
    println("")

    new CLI().main(args)
  }
}
