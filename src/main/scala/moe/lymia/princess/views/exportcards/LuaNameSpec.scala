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

package moe.lymia.princess.views.exportcards

import moe.lymia.lua._

final class LuaNameSpec(L: LuaState, chunk: LuaClosure) {
  val fn = L.newThread().call(chunk, 1).head.as[LuaClosure]
  def makeName(data: LuaObject*) = L.newThread().pcall(fn, 1, data : _*).left.map(_.head.as[String])
}

object LuaNameSpec {
  def makeFragment(L: LuaState, s: String, args: String*) = {
    val buffer = new StringBuilder

    var isInMatch  = false
    var matchLevel = 0

    buffer.append("[[")
    for(char <- s) if(!isInMatch) char match {
      case '{' =>
        buffer.append("]]..tostring(")
        isInMatch = true
        matchLevel = 0
      case '}' =>
        L.error("} with no matching {")
      case ']' => buffer.append("]]..\"]\"..[[")
      case x => buffer.append(x)
    } else char match {
      case '{' =>
        buffer.append('{')
        matchLevel += 1
      case '}' if matchLevel == 0 =>
        buffer.append(")..[[")
        isInMatch = false
      case '}' =>
        buffer.append('}')
        matchLevel -= 1
      case x => buffer.append(x)
    }
    buffer.append("]]")

    s"return function(${args.mkString(", ")}) return ${buffer.toString()} end"
  }

  def apply(L: LuaState, spec: String, args: String*) =
    L.loadString(makeFragment(L, spec, args : _*), "@<fragment>").left.map(fn => new LuaNameSpec(L, fn))
}
