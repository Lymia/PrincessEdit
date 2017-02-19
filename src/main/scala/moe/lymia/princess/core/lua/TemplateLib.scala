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

import moe.lymia.princess.core.svg._
import moe.lymia.princess.lua._

case class PhysicalScale(unPerViewport: Double, unit: PhysicalUnit)
trait LuaTemplateImplicits {
  implicit object LuaPhysicalScale extends LuaUserdataType[PhysicalScale] {
    metatable { (L, mt) =>
      L.register(mt, "__mul", (scale: Double, s: PhysicalScale) => PhysicalScale(scale * s.unPerViewport, s.unit))
    }
  }
  implicit object LuaPhysicalUnit extends LuaUserdataType[PhysicalUnit] {
    metatable { (L, mt) =>
      L.register(mt, "__mul", (scale: Double, unit: PhysicalUnit) => PhysicalScale(scale, unit))
    }
  }
}

object TemplateLib {
  def open(L: LuaState) = {
    val table = L.newLib("_princess", "PhysicalUnit")
    L.rawSet(table, "mm", PhysicalUnit.mm)
    L.rawSet(table, "in", PhysicalUnit.in)
  }
}