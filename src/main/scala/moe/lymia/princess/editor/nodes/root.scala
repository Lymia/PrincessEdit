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

import java.util

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

import scala.collection.JavaConverters._
import scala.collection.mutable

final class NodeContext(val L: LuaState, val data: DataStore, val controlCtx: ControlContext, val i18n: I18N,
                        val prefixSeq: Seq[String] = Seq()) {
  val internal_L = L.newThread()

  val prefix = if(prefixSeq.isEmpty) "" else s"${prefixSeq.mkString(":")}:"

  private val setupData = new util.IdentityHashMap[TreeNode, SetupData].asScala
  private val currentSetup = new util.IdentityHashMap[TreeNode, Unit].asScala
  def setupNode(node: TreeNode)(implicit owner: Ctx.Owner): Unit = setupData.getOrElseUpdate(node, {
    if(currentSetup.contains(node))
      throw EditorException("Cycle in nodes!")
    try {
      currentSetup.put(node, ())
      node.setupNode(this, owner)
    } finally {
      currentSetup.remove(node)
    }
  })

  private val activatedRxes = new util.IdentityHashMap[FieldNode, Rx[Any]].asScala
  private val activatingRxes = new util.IdentityHashMap[FieldNode, Unit].asScala
  def activateNode(node: FieldNode)(implicit owner: Ctx.Owner): Rx[Any] = activatedRxes.getOrElseUpdate(node, {
    if(activatingRxes.contains(node))
      throw EditorException("Cycle in field nodes!")
    try {
      activatingRxes.put(node, ())
      node.createRx(this, owner)
    } finally {
      activatingRxes.remove(node)
    }
  })

  val activatedRoots = new util.IdentityHashMap[RootNode, Rx[ActiveRootNode]].asScala

  private val activatedCardFields = new mutable.HashMap[String, TreeNode]
  def activateCardField(name: String, node: TreeNode) =
    activatedCardFields.get(name) match {
      case Some(f) =>
        if(f ne node)
          throw EditorException(s"Cannot control card data field '${prefix+name}' with two different nodes.")
      case None => activatedCardFields.put(name, node)
    }

  def newUIContext(registerControlCallbacks: Control => Unit) =
    new UIContext(prefix, registerControlCallbacks)
}

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
        UIUtils.transparentStyle.apply(pane)
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

final class ActiveRootNode private (ctx: NodeContext, root: Option[ControlNode],
                                    fields: Option[Map[String, Rx[Any]]])(implicit owner: Ctx.Owner) {
  def renderUI(uiRoot: Composite, uiCtx: UIContext) =
    root.map(_.createControl(uiRoot)(ctx, uiCtx.newUIContext(ctx), owner))

  lazy val luaOutput = fields.map { fields => Rx { fields.map(x => x.copy(_2 = x._2())) } }
  lazy val luaOutputObj = Rx {
    luaOutput.fold(OutputTable.empty)(x => OutputTable(x())).toLua(ctx.internal_L)
  }
}
object ActiveRootNode {
  def apply(L: LuaState, data: DataStore, controlCtx: ControlContext, i18n: I18N, prefix: Seq[String],
            uiRoot: Option[ControlNode], fields: Option[Map[String, FieldNode]])(implicit owner: Ctx.Owner) = {
    val ctx = new NodeContext(L, data, controlCtx, i18n, prefix)
    uiRoot.foreach(ctx.setupNode)
    fields.foreach(_.values.foreach(ctx.setupNode))
    new ActiveRootNode(ctx, uiRoot, fields.map(_.mapValues(x => ctx.activateNode(x))))
  }
}

final case class RootNode(subtableName: Option[String], params: Seq[FieldNode], fn: LuaClosure)
  extends FieldNode with ControlNode {

  private def makeActiveNode(ctx: NodeContext)(implicit owner: Ctx.Owner) =
    ctx.activatedRoots.getOrElseUpdate(this, {
      val fields = params.map(x => ctx.activateNode(x))
      ctx.controlCtx.syncLuaExec {
        Rx {
          // unapply not used because apparently scala.rx's macros break on those
          val ret = ctx.L.newThread().call(fn, 2, fields.map(_() : LuaObject) : _*)
          val node = ActiveRootNode(ctx.L, ctx.data, ctx.controlCtx, ctx.i18n, ctx.prefixSeq ++ subtableName,
                                    ret.last.as[Option[ControlNode]], ret.head.as[Option[Map[String, FieldNode]]])
          node
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
    Rx { active().luaOutputObj() }
  }

  override def createControl(parent: Composite)(implicit ctx: NodeContext, uiCtx: UIContext, owner: Ctx.Owner) = {
    subtableName.foreach{ n =>
      ctx.activateCardField(n, this)
      uiCtx.activateCardField(n)
    }
    new RxPane(parent, ctx, uiCtx, makeActiveNode(ctx)).pane
  }
}