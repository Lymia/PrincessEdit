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

sealed trait TreeNode

trait ControlContext {
  def queueUpdate[T](rxVar: Var[T], newValue: T)
}

final case class ControlDataDefault(isDefault: Rx[Boolean], field: Rx[DataField])
final case class ControlData(L: LuaState, internal_L: LuaState, ctx: ControlContext,
                             backing: Var[DataField], default: Option[ControlDataDefault])
trait ControlType {
  def expectedFieldType: DataFieldType[_]
  def defaultValue: DataField
  def createComponent(data: ControlData)(implicit owner: Ctx.Owner): JComponent
}

trait ControlNode extends TreeNode {
  def createComponent(implicit ctx: NodeContext, owner: Ctx.Owner): JComponent
}

trait FieldNode extends TreeNode {
  protected[data] def createRx(ctx: NodeContext)(implicit owner: Ctx.Owner): Rx[Any]
  override val hashCode = super.hashCode
}

final case class DerivedFieldNode(params: Seq[FieldNode], fn: LuaClosure) extends FieldNode {
  override protected[data] def createRx(ctx: NodeContext)(implicit owner: Ctx.Owner) = {
    val fields = params.map(x => ctx.activateNode(x))
    Rx { ctx.L.newThread().call(fn, 1, fields.map(_() : LuaObject) : _*).head.as[Any] }
  }
}

final case class ConstFieldNode(data: Any) extends FieldNode {
  override protected[data] def createRx(ctx: NodeContext)(implicit owner: Ctx.Owner): Rx[Any] =
    Rx { data }
}

final case class RxFieldNode(rx: Rx[Any]) extends FieldNode {
  override protected[data] def createRx(ctx: NodeContext)(implicit owner: Ctx.Owner): Rx[Any] = rx
}

final case class InputFieldDefault(isDefault: FieldNode, field: FieldNode)
final case class InputFieldNode(fieldName: String, control: ControlType, default: Option[InputFieldDefault])
  extends FieldNode with ControlNode {

  private val expected = control.expectedFieldType.asInstanceOf[DataFieldType[Any]]

  private def checkDefault(ctx: NodeContext) = {
    val field = ctx.data.getDataField(ctx.prefix+fieldName, control.defaultValue)
    if(field.now.t != expected) field.update(control.defaultValue)
    field
  }

  override protected[data] def createRx(ctx: NodeContext)(implicit owner: Ctx.Owner): Rx[Any] = {
    ctx.activateCardField(fieldName, this, isUi = false)

    val field = checkDefault(ctx)
    Rx { field().toLua(ctx.internal_L) }
  }

  override def createComponent(implicit ctx: NodeContext, owner: Ctx.Owner): JComponent = {
    ctx.activateCardField(fieldName, this, isUi = true)

    val data = ControlData(ctx.L, ctx.internal_L, ctx.controlCtx, checkDefault(ctx),
                           default.map(v => ControlDataDefault(
                             ctx.activateNode(v.isDefault).map(_.fromLua[Boolean](ctx.internal_L)),
                             ctx.activateNode(v.field).map(x =>
                               DataField(expected, expected.fromLua(ctx.internal_L, x)))
                           )))
    control.createComponent(data)
  }
}