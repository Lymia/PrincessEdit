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

package moe.lymia.princess.editor.lua

import org.eclipse.swt.SWT
import org.eclipse.swt.layout._

import moe.lymia.princess.core._
import moe.lymia.princess.editor.controls._
import moe.lymia.princess.editor.data._
import moe.lymia.princess.lua._

import scala.collection.mutable

object ContainerUtils {
  case class Align(align: Int)

  class Setups[T](setups: Seq[T => Unit], newValue: => T) extends (() => T) {
    def apply() = {
      val value = newValue
      setups.foreach(_(value))
      value
    }
  }
  class SetupsBuilder[T](L: LuaState, table: LuaTable, newValue: => T) {
    val setups = new mutable.ArrayBuffer[T => Unit]()
    def flag(s: String, setup: T => Unit) =
      if(L.rawGet(table, s).as[Boolean]) setups += setup
    def value[U : FromLua](s: String, setup: (T, U) => Unit) =
      L.rawGet(table, s).as[Option[U]].foreach(v => setups += (d => setup(d, v)))
    def getFn = new Setups(setups, newValue) // try to avoid capturing things we don't want in the object
  }

  def ContainerType(L: LuaState, constants: (String, LuaObject)*)(s: ScalaLuaClosure) = {
    val ud = new LuaUserdata(())
    val mt = L.newTable()
    ud.setMetatable(mt)
    L.register(mt, "__call", s)
    val idx = L.newTable()
    for((n, o) <- constants) L.rawSet(idx, n, o)
    L.rawSet(mt, "__index", idx)
    ud
  }
}
import ContainerUtils._

trait LuaContainerImplicits {
  implicit object LuaAlign extends LuaUserdataType[Align]
}

object ContainerLib extends LuaLibrary {
  override def open(L: LuaState, table: LuaTable): Unit = {
    val node = L.newLib(table, "Node")
    L.rawSet(node, "Grid", ContainerType(L,
      "BEGINNING" -> Align(SWT.BEGINNING),
      "CENTER"    -> Align(SWT.CENTER   ),
      "END"       -> Align(SWT.END      ),
      "FILL"      -> Align(SWT.FILL     )
    ) { (L: LuaState, _: LuaUserdata, data: LuaTable) =>
      val components = for(i <- 1 to L.objLen(data)) yield {
        val row = L.rawGet(data, i).as[LuaTable]

        val component = L.rawGet(row, "component").as[Option[ControlNode]].getOrElse(L.rawGet(row, 1).as[ControlNode])

        val x = L.rawGet(row, "x").as[Int]
        val y = L.rawGet(row, "y").as[Int]

        val setups = new SetupsBuilder(L, row, new GridData)

        setups.flag("xFill"  , _.horizontalAlignment       = SWT.FILL)
        setups.flag("yFill"  , _.verticalAlignment         = SWT.FILL)
        setups.flag("xExpand", _.grabExcessHorizontalSpace = true)
        setups.flag("yExpand", _.grabExcessVerticalSpace   = true)

        setups.value[Align]("xAlign" , (d, v) => d.horizontalAlignment = v.align)
        setups.value[Align]("yAlign" , (d, v) => d.verticalAlignment   = v.align)
        setups.value[Int  ]("xSpan"  , (d, v) => d.horizontalSpan      = v)
        setups.value[Int  ]("ySpan"  , (d, v) => d.verticalSpan        = v)
        setups.value[Int  ]("xIndent", (d, v) => d.horizontalIndent    = v)
        setups.value[Int  ]("yIndent", (d, v) => d.verticalIndent      = v)
        setups.value[Int  ]("xMin"   , (d, v) => d.minimumWidth        = v)
        setups.value[Int  ]("yMin"   , (d, v) => d.minimumHeight       = v)
        setups.value[Int  ]("xHint"  , (d, v) => d.widthHint           = v)
        setups.value[Int  ]("yHint"  , (d, v) => d.heightHint          = v)

        GridComponent(component, x, y, setups.getFn)
      }

      val setups = new SetupsBuilder(L, table, new GridLayout())

      setups.flag("equalColumns", _.makeColumnsEqualWidth = true)

      setups.value[Int]("xSpacing", (d, v) => d.horizontalSpacing = v)
      setups.value[Int]("ySpacing", (d, v) => d.verticalSpacing = v)
      setups.value[Int]("xMargin" , (d, v) => d.marginWidth = v)
      setups.value[Int]("yMargin" , (d, v) => d.marginHeight = v)

      setups.value[Int]("marginTop"   , (d, v) => d.marginTop = v)
      setups.value[Int]("marginBottom", (d, v) => d.marginBottom = v)
      setups.value[Int]("marginLeft"  , (d, v) => d.marginLeft = v)
      setups.value[Int]("marginRight" , (d, v) => d.marginRight = v)

      GridNode(components, setups.getFn) : ControlNode
    })
  }
}