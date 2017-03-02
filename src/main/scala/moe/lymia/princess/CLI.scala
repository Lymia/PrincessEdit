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

import moe.lymia.princess.core.PackageManager
import moe.lymia.princess.core.builder.{ExportResourceLoader, RasterizeResourceLoader}
import moe.lymia.princess.core.svg.InkscapeConnectionFactory
import moe.lymia.princess.lua._
import moe.lymia.princess.util.IOUtils

private case class CLIException(message: String) extends Exception

class CLI {
  private var mode: () => Unit = cmd_default _
  private var luaFile: String = _

  private var gameId: String = _
  private var template: String = _
  private var cardData: String = _

  private var link: Boolean = false
  private var dirty: Boolean = false

  private var x: Int = _
  private var y: Int = _
  private var out: String = _

  private def error(s: String) = throw CLIException(s)

  private val parser = new scopt.OptionParser[Unit]("./PrincessEdit") {
    help("help").text("Shows this help message.")
    note("")
    cmd("lua").text("Runs a Lua file").foreach(_ => mode = cmd_lua _).children(
      arg[String]("<file>").foreach(luaFile = _).hidden()
    )
    note("")
    cmd("console").text("Run Lua console").foreach(_ => mode = cmd_console _)
    note("")
    cmd("packageStatus").text("Print package status").foreach(_ => mode = cmd_packageStatus _)
    note("")
    cmd("render").text("Renders a template").foreach(_ => mode = cmd_render _).children(
      arg[Int   ]("<x>"       ).foreach(x        = _).hidden(),
      arg[Int   ]("<y>"       ).foreach(y        = _).hidden(),
      arg[String]("<gameId>"  ).foreach(gameId   = _).hidden(),
      arg[String]("<template>").foreach(template = _).hidden(),
      arg[String]("<cardData>").foreach(cardData = _).hidden(),
      arg[String]("<out>"     ).foreach(out      = _).hidden()
    )
    note("")
    cmd("renderSVG").text("Renders a template to SVG").foreach(_ => mode = cmd_renderSVG _).children(
      arg[String]("<gameId>"  ).foreach(gameId   = _).hidden(),
      arg[String]("<template>").foreach(template = _).hidden(),
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

  private def cmd_default() = { }

  private def cmd_lua(): Unit = {
    val L = LuaState.makeSafeContext()
    L.loadFile(luaFile) match {
      case Left (x) => L.call(x, 0)
      case Right(x) => println(x)
    }
  }
  private def cmd_console(): Unit = {
    LuaConsole.startConsole()
  }
  private def cmd_packageStatus(): Unit = {
    println("Known game IDs:")
    for(id <- PackageManager.default.gameIDList) println(s" - ${id.displayName} (${id.name})")
    println()
    for(id <- PackageManager.default.gameIDList) {
      val game = PackageManager.default.loadGameId(id.name)
      println(s"GameID ${id.name}:")
      for(template <- game.templates) {
        println(s" - Found template: ${template.displayName} (${template.path})")
      }
      println()
    }
  }

  private def renderCommon() = time("Loaded packages") {
    val game = PackageManager.default.loadGameId(gameId)
    val templateObj = game.templates.find(_.path == template) match {
      case Some(x) => x.template
      case None => error(s"No such template $template")
    }
    val card = if(cardData.startsWith("@")) IOUtils.readFileAsString(Paths.get(cardData.substring(1))) else cardData
    val cardDataTable = game.lua.L.loadString(s"return $card", "@<card data>") match {
      case Left (x) => game.lua.L.call(x, 1).head.as[LuaTable]
      case Right(e) => error(e)
    }
    (templateObj, cardDataTable)
  }
  private def cmd_render(): Unit = {
    val (template, cardData) = renderCommon()
    val renderer = time("Creating renderer instance") { new InkscapeConnectionFactory("inkscape").createRenderer() }
    try {
      val image = time("Renderered image") { template.renderImage(renderer, x, y, cardData) }
      time("Encoding image") { ImageIO.write(image, "png", new FileOutputStream(out)) }
    } finally {
      renderer.destroy()
    }
  }
  private def cmd_renderSVG(): Unit = {
    val (template, cardData) = renderCommon()
    time("Renderered SVG") {
      template.write(new FileWriter(out), cardData, pretty = !dirty, res =
        if(dirty) RasterizeResourceLoader else ExportResourceLoader)
    }
  }

  def main(args: Seq[String]) = try {
    if(parser.parse(args)) mode()
  } catch {
    case CLIException(e) => println(e)
    case e: Exception => e.printStackTrace()
  }
}
