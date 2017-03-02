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

package moe.lymia.princess.core.lua

import moe.lymia.princess.core._
import moe.lymia.princess.lua._

trait LuaCoreImplicits {
  implicit case object LuaLogLevel extends LuaUserdataType[LogLevel]
}

final case class CoreLib(logger: Logger) {
  def open(L: LuaState) = {
    val princess = L.newLib("_princess")

    L.register(princess, "trimString", (s: String) => s.trim)
    L.register(princess, "splitString", (s: String, on: String) => s.split(on).toSeq)

    L.register(princess, "Object", () => new LuaLookup { } : HasLuaMethods)

    val levels = L.newLib("_princess", "LogLevel")
    L.rawSet(levels, "TRACE", LogLevel.TRACE)
    L.rawSet(levels, "DEBUG", LogLevel.DEBUG)
    L.rawSet(levels, "INFO" , LogLevel.INFO )
    L.rawSet(levels, "WARN" , LogLevel.WARN )
    L.rawSet(levels, "ERROR", LogLevel.ERROR)

    val tostringFn = L.getGlobal("tostring").as[LuaClosure]
    def tostring(L: LuaState, o: LuaObject) = L.call(tostringFn, 1, o).head.as[String]

    def log(L: LuaState, level: LogLevel): Unit = {
      logger.log(level, {
        val buffer = new StringBuilder()
        for(i <- 2 to L.getTop) {
          val str = tostring(L, L.value(i))
          if(i != 2) buffer.append("\t")
          buffer.append(str)
        }
        buffer.toString()
      })
    }
    L.register(princess, "log", log _)
  }
}
