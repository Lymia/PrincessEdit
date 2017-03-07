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

    L.register(princess, "where", (L: LuaState, i: Int) => {
      val where = L.where(i + 1)
      if(where.isEmpty) None else Some(where.replace(": ", ""))
    })

    L.register(princess, "trimString", (s: String) => s.trim)
    L.register(princess, "splitString", (s: String, on: String) => s.split(on).toSeq)

    L.register(princess, "Object", () => new LuaLookup { } : HasLuaMethods)

    val levels = L.newLib("_princess", "LogLevel")
    L.rawSet(levels, "TRACE", LogLevel.TRACE)
    L.rawSet(levels, "DEBUG", LogLevel.DEBUG)
    L.rawSet(levels, "INFO" , LogLevel.INFO )
    L.rawSet(levels, "WARN" , LogLevel.WARN )
    L.rawSet(levels, "ERROR", LogLevel.ERROR)

    L.register(princess, "log", (L: LuaState, level: LogLevel, source: Option[String], fn: LuaClosure) =>
      logger.log(level, source, L.call(fn, 1).head.as[String]))
  }
}
