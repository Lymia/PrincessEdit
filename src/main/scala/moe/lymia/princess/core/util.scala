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

package moe.lymia.princess.core

import moe.lymia.lua.LuaErrorMarker

final case class EditorException(message: String, ex: Throwable = null, context: Seq[String] = Seq(),
                                 suppressTrace: Boolean = false, noCause: Boolean = false)
  extends RuntimeException((context :+ message).mkString(": "), if(noCause) null else ex, suppressTrace, true)
  with    LuaErrorMarker {

  if(ex != null && suppressTrace) setStackTrace(ex.getStackTrace)
}
object EditorException {
  def context[T](contextString: String)(f: => T) = try {
    f
  } catch {
    case ex @ EditorException(msg, _, context, _, _) =>
      throw EditorException(msg, ex, s"While $contextString" +: context, suppressTrace = true, noCause = true)
  }
}
