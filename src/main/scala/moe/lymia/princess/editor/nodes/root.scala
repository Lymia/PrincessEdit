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
import moe.lymia.princess.core._
import moe.lymia.princess.editor.ControlContext
import moe.lymia.princess.editor.lua._
import moe.lymia.princess.editor.model.DataStore
import moe.lymia.princess.editor.utils.UIUtils
import org.eclipse.swt.SWT
import org.eclipse.swt.layout._
import org.eclipse.swt.widgets._
import rx._

final class RxPane(uiRoot: Composite, context: NodeContext, uiCtx: UIContext, rootRx: Rx[ActiveRootNode])
                  (implicit owner: Ctx.Owner) {
  val pane = new Composite(uiRoot, SWT.NONE)
  pane.setLayout(new FillLayout())
  pane.setData(this)

  private var lastComponent: Option[Control] = None
  private val componentObs: Obs = rootRx.foreach { currentRoot =>
    context.controlCtx.asyncUiExec {
      if(!pane.isDisposed) {
        lastComponent.foreach(_.dispose())
        lastComponent = currentRoot.renderUI(pane, uiCtx)
        pane.layout(true)
        UIUtils.listBackgroundStyle.apply(pane)
      } else componentObs.kill()
    }
  }
}

private case class OutputTable(v: Map[String, Any])
private object OutputTable {
  val empty = OutputTable(Map())
  implicit object LuaOutputTable extends LuaUserdataType[OutputTable] {
    metatable { (L, mt) =>
      L.register(mt, "__index", (m: OutputTable, s: String) => m.v.getOrElse(s, Lua.NIL))
    }
  }
}
class ActiveTable private[nodes] (ctx: NodeContext, fields: Map[String, Rx[Any]])(implicit owner: Ctx.Owner) {
  lazy val luaOutput = Rx { fields.map(x => x.copy(_2 = x._2())).toLua(ctx.internal_L) }
}

private sealed trait ActiveSetup {
  def setup(ctx: NodeContext)(implicit owner: Ctx.Owner)
  def activate(ctx: NodeContext)(implicit owner: Ctx.Owner): Rx[Any]
}
private case class LeafSetup(field: FieldNode) extends ActiveSetup {
  override def setup(ctx: NodeContext)(implicit owner: Ctx.Owner) = ctx.setupNode(field)
  override def activate(ctx: NodeContext)(implicit owner: Ctx.Owner): Rx[Any] = ctx.activateNode(field)
}
private case class TableSetup(fields: Map[String, ActiveSetup]) extends ActiveSetup {
  override def setup(ctx: NodeContext)(implicit owner: Ctx.Owner) = fields.values.foreach(_.setup(ctx))
  override def activate(ctx: NodeContext)(implicit owner: Ctx.Owner): Rx[Any] =
    new ActiveTable(ctx, fields.map(v => v.copy(_2 = v._2.activate(ctx)))).luaOutput
}

final class ActiveRootNode private (ctx: NodeContext, root: Option[ControlNode], fields: Map[String, Rx[Any]])
                                   (implicit owner: Ctx.Owner)
  extends ActiveTable(ctx, fields) {

  def renderUI(uiRoot: Composite, uiCtx: UIContext) =
    root.map(_.createControl(uiRoot)(ctx, uiCtx.newUIContext(ctx), owner))
}
object ActiveRootNode {
  def apply(L: LuaState, data: DataStore, controlCtx: ControlContext, i18n: I18N, prefix: Seq[String],
            uiRoot: Option[ControlNode], fields: Map[String, ActiveSetup])(implicit owner: Ctx.Owner) = {
    val ctx = new NodeContext(L, data, controlCtx, i18n, prefix)
    uiRoot.foreach(ctx.setupNode)
    fields.values.foreach(x => x.setup(ctx))
    new ActiveRootNode(ctx, uiRoot, fields.map(x => x.copy(_2 = x._2.activate(ctx))))
  }
}

final case class RootNode(subtableName: Option[String], params: Seq[FieldNode], fn: LuaClosure)
  extends FieldNode with ControlNode {

  private def makeSetup(ctx: NodeContext, v: Any, prefix: Seq[String]): ActiveSetup = v match {
    case ud: LuaUserdata =>
      LeafSetup(ud.fromLua[FieldNode](ctx.L))
    case table: LuaTable =>
      val data = table.fromLua[Map[String, Any]](ctx.L)
      TableSetup(data.map(t => t.copy(_2 = makeSetup(ctx, t._2, prefix :+ t._1))))
    case _ =>
      typerror(ctx.L.L, Some(s"field ${(ctx.prefixSeq ++ prefix).mkString(".")}"), v, "FieldNode or table")
  }
  private def makeActiveNode(ctx: NodeContext)(implicit owner: Ctx.Owner) =
    ctx.activatedRoots.getOrElseUpdate(this, {
      val fields = params.map(x => ctx.activateNode(x))
      ctx.controlCtx.syncLuaExec {
        Rx {
          // unapply not used because apparently scala.rx's macros break on those
          val ret = ctx.L.newThread().call(fn, 2, fields.map(_() : LuaObject) : _*)
          val setups = ret.head.as[Map[String, Any]].map(t => t.copy(_2 = makeSetup(ctx, t._2, Seq(t._1))))
          ActiveRootNode(ctx.L, ctx.data, ctx.controlCtx, ctx.i18n, ctx.prefixSeq ++ subtableName,
                         ret.last.as[Option[ControlNode]], setups)
        }
      }
    })

  override def setupNode(implicit ctx: NodeContext, owner: Ctx.Owner) = {
    params.foreach(ctx.setupNode)
    SetupData.none
  }

  override def createRx(implicit ctx: NodeContext, owner: Ctx.Owner): Rx[Any] = {
    subtableName.foreach(n => ctx.activateCardField(n, this))

    val active = makeActiveNode(ctx)
    Rx { active().luaOutput() }
  }

  override def createControl(parent: Composite)(implicit ctx: NodeContext, uiCtx: UIContext, owner: Ctx.Owner) = {
    subtableName.foreach{ n =>
      ctx.activateCardField(n, this)
      uiCtx.activateCardField(n)
    }
    new RxPane(parent, ctx, uiCtx, makeActiveNode(ctx)).pane
  }
}