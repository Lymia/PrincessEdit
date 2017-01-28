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

package moe.lymia.princess.lua

import java.nio.file.Paths

object LuaConsole {
  def startConsole() {
    var L: LuaState = LuaState.makeSafeContext(Paths.get("resources"), Paths.get("."))

    var continue = true

    val exit = L.newTable()
    val mt   = L.newTable()
    L.setMetatable(exit, mt)

    L.register(mt, "__call"    , () => { continue = false })
    L.register(mt, "__tostring", () => LuaRet("Use exit() to exit."))

    L.setGlobal("exit", exit)

    val chunk = L.loadString(
      """
        local loadstring = loadstring
        local pcall = pcall
        local unpack = unpack
        local print = print
        local function loadLine(string)
          local fn, err = loadstring("return "..string)
          if not fn then
            local fn, err = loadstring(string)
            if not fn then
              return fn, err
            else
              return fn
            end
          else
            return fn
          end
        end
        local function runClosure(string)
          local fn, err = loadLine(string)
          if fn then
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
          else
            print(err)
          end
        end
        return runClosure
      """, "loader").fold(x => x, x => sys.error(s"Error compiling Lua executor: $x"))

    L.push(chunk)
    L.call(0, 1)
    val runClosure = L.popTop().as[LuaClosure](L)

    println("Type exit() to exit.")
    println()

    val in = new java.io.BufferedReader(new java.io.InputStreamReader(System.in))
    while(continue) {
      print(">>> ")
      val rawline = in.readLine()
      val line =
        if (rawline.startsWith("=")) "return "+rawline.substring(1)
        else rawline
      try {
        L.push(runClosure)
        L.push(line)
        L.call(1, 0)
      } catch {
        case e: Throwable => e.printStackTrace()
      }
    }
  }
}