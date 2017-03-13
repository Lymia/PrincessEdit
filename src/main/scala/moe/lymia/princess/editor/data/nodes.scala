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

package moe.lymia.princess.editor.data

import javax.swing._

import moe.lymia.princess.lua._
import rx._

sealed trait TreeNode {
  protected[data] def managesCardField: Set[String] = Set()
  protected[data] def deps: Set[TreeNode] = Set()

  override val hashCode = super.hashCode
}

final case class ControlData(backing: Rx[DataField], default: Option[Rx[DataField]])
trait ControlType {
  protected[data] def expectedFieldType: DataFieldType[_]
  def createControl(L: LuaState, data: ControlData): JComponent
}

case class ControlContext(L: LuaState, data: DataStore, state: TreeState)
trait ControlNode extends TreeNode {
  protected[data] def createControl(implicit ctx: ControlContext): JComponent
}

trait FieldNode extends TreeNode {
  protected[data] def createRx(L: LuaState, data: DataStore, state: TreeState): Rx[Any]
}

final case class DebugInputFieldNode(fieldName: String) extends FieldNode {
  override protected[data] def createRx(L: LuaState, data: DataStore, state: TreeState): Rx[Any] = {
    val field = data.getDataField(state.prefix+fieldName)
    Rx.unsafe { field().toLua(L) }
  }
}

final case class DerivedFieldNode(params: Seq[FieldNode], fn: LuaClosure) extends FieldNode {
  override protected[data] def deps = params.toSet

  override protected[data] def createRx(L: LuaState, data: DataStore, state: TreeState) = {
    val fields = params.map(state.activatedRxes)
    Rx.unsafe { L.newThread().call(fn, 1, fields.map(_() : LuaObject) : _*).head.as[Any] }
  }
}

final case class ConstFieldNode(data: Any) extends FieldNode {
  override protected[data] def createRx(L: LuaState, data: DataStore, state: TreeState): Rx[Any] =
    Rx.unsafe { data }
}

final case class InputFieldNode(fieldName: String, defaultValue: DataField, control: ControlType,
                                default: Option[FieldNode])
  extends FieldNode with ControlNode {

  private val expected = control.expectedFieldType.asInstanceOf[DataFieldType[Any]]

  override protected[data] def managesCardField: Set[String] = Set(fieldName)
  override protected[data] def deps = default.toSet

  private def checkDefault(data: DataStore, state: TreeState) = {
    val field = data.getDataField(state.prefix+fieldName, defaultValue)
    if(field.now.t != expected) field.update(defaultValue)
    field
  }

  override protected[data] def createRx(L: LuaState, data: DataStore, state: TreeState): Rx[Any] = {
    state.activateCardField(fieldName, this, isUi = false)

    val field = checkDefault(data, state)
    Rx.unsafe { field().toLua(L) }
  }

  override protected[data] def createControl(implicit ctx: ControlContext): JComponent = {
    ctx.state.activateCardField(fieldName, this, isUi = true)

    import rx.Ctx.Owner.Unsafe._
    val data = ControlData(checkDefault(ctx.data, ctx.state),
                           default.map(ctx.state.activatedRxes)
                                  .map(_.map { x => DataField(expected, expected.fromLua(ctx.L, x)) }))
    ctx.state.tracker.add(data.backing)
    data.default.foreach(x => ctx.state.tracker.add(x))
    control.createControl(ctx.L, data)
  }
}