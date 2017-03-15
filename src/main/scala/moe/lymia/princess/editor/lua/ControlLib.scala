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

import java.awt.{GridBagConstraints, Insets}

import moe.lymia.princess.core._
import moe.lymia.princess.editor.controls._
import moe.lymia.princess.editor.data._
import moe.lymia.princess.lua._

case class ControlTypeApWrapper(default: ControlType, call: LuaClosure)
case class Anchor(anchor: Int)

trait LuaControlNodeImplicits {
  implicit object LuaControlNode extends LuaUserdataType[ControlNode]
  implicit object LuaAnchorValue extends LuaUserdataType[Anchor]

  implicit object LuaInControlType extends FromLua[ControlType] {
    override def fromLua(L: Lua, v: Any, source: => Option[String]): ControlType = v match {
      case u: LuaUserdata =>
        u.getUserdata match {
          case wrapper: ControlTypeApWrapper => wrapper.default
          case control: ControlType => control
          case _ => typerror(L, source, L.getClass.getName, classOf[ControlType].getName)
        }
      case _ => typerror(L, source, v, Lua.TUSERDATA)
    }
  }
  implicit object LuaOutControlType extends LuaUserdataOutputType[ControlType]
  implicit object LuaOutControlTypeApWrapper extends LuaUserdataType[ControlTypeApWrapper] {
    metatable { (L, mt) =>
      L.register(mt, "__call", ScalaLuaClosure.withState { (L: LuaState) =>
        val wrapper = L.value(1).as[ControlTypeApWrapper]
        val args = L.valueRange(2)
        L.push(L.call(wrapper.call, 1, args.map(x => x : LuaObject) : _*).head)
        1
      })
    }
  }
}

object ControlLib extends LuaLibrary {
  override def open(L: LuaState, table: LuaTable): Unit = {
    val controlTypes = L.newTable()
    L.rawSet(controlTypes, "TextArea", TextAreaControlType : ControlType)
    L.rawSet(controlTypes, "TextField", TextFieldControlType : ControlType)
    L.rawSet(controlTypes, "CheckBox",
      ControlTypeApWrapper(CheckBoxControlType(None),
                           LuaClosure { (s: String) => CheckBoxControlType(Some(s)) : ControlType }))
    L.rawSet(table, "ControlType", controlTypes)

    val node = L.newLib(table, "Node")
    L.register(node, "Label", (text: String) => LabelNode(text) : ControlNode)
    L.register(node, "Visibility",
      (node: FieldNode, contents: ControlNode) => VisibilityNode(node, contents) : ControlNode)

    val gridUd = new LuaUserdata(())
    val gridMt = L.newTable()
    gridUd.setMetatable(gridMt)
    L.register(gridMt, "__call", (L: LuaState, data: Seq[LuaTable]) => {
      val components = for(row <- data) yield {
        val component = L.rawGet(row, "component").as[Option[ControlNode]].getOrElse(L.rawGet(row, 1).as[ControlNode])

        val constraints = new GridBagConstraints()
        L.rawGet(row, "x"      ).as[Option[Int   ]].foreach(v => constraints.gridx = v)
        L.rawGet(row, "y"      ).as[Option[Int   ]].foreach(v => constraints.gridy = v)
        L.rawGet(row, "xSpan"  ).as[Option[Int   ]].foreach(v => constraints.gridwidth = v)
        L.rawGet(row, "ySpan"  ).as[Option[Int   ]].foreach(v => constraints.gridheight = v)
        L.rawGet(row, "xWeight").as[Option[Double]].foreach(v => constraints.weightx = v)
        L.rawGet(row, "yWieght").as[Option[Double]].foreach(v => constraints.weighty = v)
        L.rawGet(row, "xPad"   ).as[Option[Int   ]].foreach(v => constraints.ipadx = v)
        L.rawGet(row, "yPad"   ).as[Option[Int   ]].foreach(v => constraints.ipady = v)
        L.rawGet(row, "anchor" ).as[Option[Anchor]].foreach(v => constraints.anchor = v.anchor)

        val xFill = L.rawGet(row, "xFill").as[Boolean]
        val yFill = L.rawGet(row, "yFill").as[Boolean]

        constraints.anchor =
          if(xFill && yFill) GridBagConstraints.BOTH
          else if(xFill)     GridBagConstraints.HORIZONTAL
          else if(yFill)     GridBagConstraints.VERTICAL
          else               GridBagConstraints.NONE

        constraints.insets = new Insets(
          L.rawGet(row, "topInset"   ).as[Option[Int]].getOrElse(0),
          L.rawGet(row, "leftInset"  ).as[Option[Int]].getOrElse(0),
          L.rawGet(row, "bottomInset").as[Option[Int]].getOrElse(0),
          L.rawGet(row, "rightInset" ).as[Option[Int]].getOrElse(0)
        )

        GridBagComponent(component, constraints)
      }
      GridNode(components) : ControlNode
    })
    val anchors = L.newTable()

    L.rawSet(anchors, "NORTHWEST", Anchor(GridBagConstraints.NORTHWEST))
    L.rawSet(anchors, "NORTH", Anchor(GridBagConstraints.NORTH))
    L.rawSet(anchors, "NORTHEAST", Anchor(GridBagConstraints.NORTHEAST))
    L.rawSet(anchors, "EAST", Anchor(GridBagConstraints.EAST))
    L.rawSet(anchors, "CENTER", Anchor(GridBagConstraints.CENTER))
    L.rawSet(anchors, "WEST", Anchor(GridBagConstraints.WEST))
    L.rawSet(anchors, "SOUTHEAST", Anchor(GridBagConstraints.SOUTHEAST))
    L.rawSet(anchors, "SOUTH", Anchor(GridBagConstraints.SOUTH))
    L.rawSet(anchors, "SOUTHWEST", Anchor(GridBagConstraints.SOUTHWEST))

    L.rawSet(anchors, "PAGE_START", Anchor(GridBagConstraints.PAGE_START))
    L.rawSet(anchors, "PAGE_END", Anchor(GridBagConstraints.PAGE_END))
    L.rawSet(anchors, "LINE_START", Anchor(GridBagConstraints.LINE_START))
    L.rawSet(anchors, "LINE_END", Anchor(GridBagConstraints.LINE_END))
    L.rawSet(anchors, "FIRST_LINE_START", Anchor(GridBagConstraints.FIRST_LINE_START))
    L.rawSet(anchors, "FIRST_LINE_END", Anchor(GridBagConstraints.FIRST_LINE_END))
    L.rawSet(anchors, "LAST_LINE_START", Anchor(GridBagConstraints.LAST_LINE_START))
    L.rawSet(anchors, "LAST_LINE_END", Anchor(GridBagConstraints.LAST_LINE_END))

    L.rawSet(anchors, "ABOVE_BASELINE_LEADING", Anchor(GridBagConstraints.ABOVE_BASELINE_LEADING))
    L.rawSet(anchors, "ABOVE_BASELINE", Anchor(GridBagConstraints.ABOVE_BASELINE))
    L.rawSet(anchors, "ABOVE_BASELINE_TRAILING", Anchor(GridBagConstraints.ABOVE_BASELINE_TRAILING))
    L.rawSet(anchors, "BASELINE_LEADING", Anchor(GridBagConstraints.BASELINE_LEADING))
    L.rawSet(anchors, "BASELINE", Anchor(GridBagConstraints.BASELINE))
    L.rawSet(anchors, "BASELINE_TRAILING", Anchor(GridBagConstraints.BASELINE_TRAILING))
    L.rawSet(anchors, "BELOW_BASELINE_LEADING", Anchor(GridBagConstraints.BELOW_BASELINE_LEADING))
    L.rawSet(anchors, "BELOW_BASELINE", Anchor(GridBagConstraints.BELOW_BASELINE))
    L.rawSet(anchors, "BELOW_BASELINE_TRAILING", Anchor(GridBagConstraints.BELOW_BASELINE_TRAILING))

    L.rawSet(gridMt, "__index", anchors)
    L.rawSet(node, "Grid", gridUd)
  }
}