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

package moe.lymia.lua

object LuaConsole {
  def startConsole(): Unit = {
    val L = LuaState.makeSafeContext()

    var continue = true

    val exit = L.newTable()
    val mt   = L.newTable()
    L.setMetatable(exit, mt)

    L.register(mt, "__call"    , () => { continue = false })
    L.register(mt, "__tostring", () => "Use exit() to exit.")

    L.setGlobal("exit", exit)

    def loadLine(s: String) =
      L.loadString(s"return $s", "@<console>").flatMap(_ => L.loadString(s, "@<console>"))
    val chunk = L.loadString(
      """
        local pcall = pcall
        local unpack = unpack
        local print = print
        local function runClosure(fn)
          local value = {pcall(fn)}
          local success = value[1]
          local ret = {unpack(value, 2)}
          if success then
            if #ret>0 then
              print(unpack(ret))
            end
          else
            print(ret[1])
          end
        end
        return runClosure
      """, "loader").fold(x => x, x => sys.error(s"Error compiling Lua executor: $x"))

    val runClosure = L.call(chunk, 1).head.as[LuaClosure]

    println("Use exit() to exit.")
    println()

    val in = new java.io.BufferedReader(new java.io.InputStreamReader(System.in))
    while(continue) {
      print(">>> ")
      val rawline = in.readLine()
      val line =
        if (rawline.startsWith("=")) "return "+rawline.substring(1)
        else rawline
      try {
        loadLine(line) match {
          case Left (x) => L.call(runClosure, 0, x)
          case Right(x) => println(x)
        }
      } catch {
        case e: Throwable => e.printStackTrace()
      }
    }
  }
}