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

package moe.lymia.princess.ui

import moe.lymia.princess.core.PackageList
import moe.lymia.princess.lua.{LuaConsole, LuaState}

class CLI {
  var mode: () => Unit = cmd_default _
  var luaFile: String = _

  val parser = new scopt.OptionParser[Unit]("./PrincessEdit") {
    help("help").text("Shows this help message.")
    note("")
    cmd("lua").text("Runs a Lua file").foreach(_ => mode = cmd_lua _).children(
      arg[String]("file").foreach(luaFile = _)
    )
    note("")
    cmd("console").text("Run Lua console").foreach(_ => mode = cmd_console _)
    note("")
    cmd("listGameIDs").text("List available Game IDs").foreach(_ => mode = cmd_listGameIDs _)
  }

  def cmd_default() = { }

  def cmd_lua(): Unit = {
    val L = LuaState.makeSafeContext()
    L.loadFile(luaFile) match {
      case Left (x) => L.call(x, 0)
      case Right(x) => println(x)
    }
  }
  def cmd_console(): Unit = {
    LuaConsole.startConsole()
  }
  def cmd_listGameIDs(): Unit = {
    println("Game IDs:")
    for(gameId <- PackageList.defaultPath.gameIDs) println(s" - ${gameId._1}: ${gameId._2}")
  }

  def main(args: Seq[String]) = {
    if(parser.parse(args)) mode()
  }
}
