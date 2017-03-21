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

package moe.lymia.princess.editor.nodes

import java.awt.image.BufferedImage

import moe.lymia.princess.editor.core.{DataField, DataFieldType}
import moe.lymia.princess.lua._
import moe.lymia.princess.renderer.SVGData

import org.eclipse.swt.SWT
import org.eclipse.swt.graphics.ImageData
import org.eclipse.swt.widgets._

import rx._

sealed trait TreeNode

trait ControlContext {
  val display: Display

  def newShell(style: Int = SWT.SHELL_TRIM): Shell

  def needsSaving()
  def queueUpdate[T](rxVar: Var[T], newValue: T)

  def asyncRenderAwt(svg: SVGData, x: Int, y: Int)(callback: BufferedImage => Unit)
  def syncRenderAwt(svg: SVGData, x: Int, y: Int): BufferedImage

  def asyncRenderSwt(svg: SVGData, x: Int, y: Int)(callback: ImageData => Unit)
  def syncRenderSwt(svg: SVGData, x: Int, y: Int): ImageData

  def asyncLuaExec(f: => Unit)
  def syncLuaExec[T](f: => T): T

  def asyncUiExec(f: => Unit)
  def syncUiExec[T](f: => T): T
}

final case class ControlDataDefault(isDefault: Rx[Boolean], field: Rx[DataField])
final case class ControlData(L: LuaState, internal_L: LuaState, ctx: ControlContext,
                             backing: Var[DataField], default: Option[ControlDataDefault])
trait ControlType {
  def expectedFieldType: DataFieldType[_]
  def defaultValue: DataField
  def createComponent(parent: Composite, data: ControlData)(implicit owner: Ctx.Owner): Control
}

trait ControlNode extends TreeNode {
  def createControl(parent: Composite)(implicit ctx: NodeContext, uiCtx: UIContext, owner: Ctx.Owner): Control
}

trait FieldNode extends TreeNode {
  def createRx(implicit ctx: NodeContext, owner: Ctx.Owner): Rx[Any]
  override val hashCode = super.hashCode
}

final case class DerivedFieldNode(params: Seq[FieldNode], fn: LuaClosure) extends FieldNode {
  override def createRx(implicit ctx: NodeContext, owner: Ctx.Owner) = {
    val fields = params.map(x => ctx.activateNode(x))
    Rx { ctx.L.newThread().call(fn, 1, fields.map(_() : LuaObject) : _*).head.as[Any] }
  }
}

final case class ConstFieldNode(data: Any) extends FieldNode {
  override def createRx(implicit ctx: NodeContext, owner: Ctx.Owner): Rx[Any] =
    Rx { data }
}

final case class RxFieldNode(rx: Rx[Any]) extends FieldNode {
  override def createRx(implicit ctx: NodeContext, owner: Ctx.Owner): Rx[Any] = rx
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

  override def createRx(implicit ctx: NodeContext, owner: Ctx.Owner): Rx[Any] = {
    ctx.activateCardField(fieldName, this)

    val field = checkDefault(ctx)
    Rx { field().toLua(ctx.internal_L) }
  }

  override def createControl(parent: Composite)(implicit ctx: NodeContext, uiCtx: UIContext, owner: Ctx.Owner) = {
    ctx.activateCardField(fieldName, this)
    uiCtx.activateCardField(fieldName)

    val data = ControlData(ctx.L, ctx.internal_L, ctx.controlCtx, checkDefault(ctx),
                           default.map(v => ControlDataDefault(
                             ctx.activateNode(v.isDefault).map(_.fromLua[Boolean](ctx.internal_L)),
                             ctx.activateNode(v.field).map(x =>
                               DataField(expected, expected.fromLua(ctx.internal_L, x)))
                           )))
    control.createComponent(parent, data)
  }
}