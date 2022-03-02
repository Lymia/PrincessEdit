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

package moe.lymia.princess.editor.nodes

import moe.lymia.lua._
import moe.lymia.princess.core.cardmodel.{DataField, DataFieldType}
import org.eclipse.swt.widgets._
import rx._

final case class DerivedFieldNode(params: Seq[FieldNode], fn: LuaClosure) extends FieldNode {
  override def setupNode(implicit ctx: NodeContext, owner: Ctx.Owner) = {
    params.foreach(ctx.setupNode)
    SetupData.none
  }

  override def createRx(implicit ctx: NodeContext, owner: Ctx.Owner) = {
    val fields = params.map(x => ctx.activateNode(x))
    Rx { ctx.L.newThread().call(fn, 1, fields.map(_() : LuaObject) : _*).head.as[Any] }
  }
}

final case class ConstFieldNode(data: Any) extends FieldNode {
  override def setupNode(implicit ctx: NodeContext, owner: Ctx.Owner) = SetupData.none
  override def createRx(implicit ctx: NodeContext, owner: Ctx.Owner): Rx[Any] = Rx { data }
}

final case class RxFieldNode(rx: Rx[Any]) extends FieldNode {
  override def setupNode(implicit ctx: NodeContext, owner: Ctx.Owner) = SetupData.none
  override def createRx(implicit ctx: NodeContext, owner: Ctx.Owner): Rx[Any] = rx
}

sealed trait InputFieldDefault
final case class InputFieldDerivedDefault(isDefault: FieldNode, field: FieldNode) extends InputFieldDefault
final case class InputFieldStaticDefault(fieldData: DataField) extends InputFieldDefault

final case class InputFieldNode(fieldName: String, control: ControlType, default: Option[InputFieldDefault])
  extends FieldNode with ControlNode {

  private val expected = control.expectedFieldType.asInstanceOf[DataFieldType[Any]]
  private val defaultField = default match {
    case Some(InputFieldStaticDefault(value)) => value
    case _ => control.defaultValue
  }

  private def checkDefault(ctx: NodeContext) = {
    val field = ctx.data.getDataField(ctx.prefix+fieldName, defaultField)
    if(field.now.t != expected) field.update(defaultField)
    field
  }

  override def setupNode(implicit ctx: NodeContext, owner: Ctx.Owner) = {
    ctx.activateCardField(fieldName, this)
    default match {
      case Some(InputFieldDerivedDefault(isDefaultNode, defaultFieldNode)) =>
        ctx.setupNode(isDefaultNode)
        ctx.setupNode(defaultFieldNode)

        val backing = checkDefault(ctx)
        val isDefaultRx = ctx.activateNode(isDefaultNode).map(_.fromLua[Boolean](ctx.internal_L))
        val fieldRx =
          ctx.activateNode(defaultFieldNode).map(x => DataField(expected, expected.fromLua(ctx.internal_L, x)))
        SetupData(obses = Seq(
          Rx { (isDefaultRx(), fieldRx()) }.foreach {
            case (isDefault, field) =>
              if(isDefault) ctx.controlCtx.queueUpdate(backing, field)
          }
        ))
      case _ => SetupData.none
    }
  }

  override def createRx(implicit ctx: NodeContext, owner: Ctx.Owner): Rx[Any] = {
    ctx.activateCardField(fieldName, this)

    val field = checkDefault(ctx)
    Rx { field().toLua(ctx.internal_L) }
  }

  override def createControl(parent: Composite)(implicit ctx: NodeContext, uiCtx: UIContext, owner: Ctx.Owner) = {
    ctx.activateCardField(fieldName, this)
    uiCtx.activateCardField(fieldName)

    val data = ControlData(ctx.L, ctx.internal_L, ctx.controlCtx, ctx.i18n, checkDefault(ctx),
                           default match {
                             case Some(InputFieldDerivedDefault(isDefaultNode, _)) =>
                               ctx.activateNode(isDefaultNode).map(y => !y.fromLua[Boolean](ctx.internal_L))
                             case _ => Rx(true)
                           })
    control.createComponent(parent, data)
  }
}