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

import moe.lymia.princess.core._
import moe.lymia.princess.editor.core._
import moe.lymia.princess.editor.lua._
import moe.lymia.lua._

import org.eclipse.swt.SWT
import org.eclipse.swt.layout._
import org.eclipse.swt.widgets._

import rx._

import scala.collection.mutable
import scala.collection.JavaConverters._

final class UIContext(prefix: String, val registerControlCallbacks: Control => Unit) {
  private val uiActivatedCardField = new mutable.HashSet[String]
  def activateCardField(name: String) = {
      if(uiActivatedCardField.contains(name))
        throw EditorException(s"Cannot reuse UI element controlling card data field '${prefix+name}'!")
      uiActivatedCardField.add(name)
  }

  def newUIContext(ctx: NodeContext) = new UIContext(ctx.prefix, registerControlCallbacks)
}

final class NodeContext(val L: LuaState, val data: DataStore, val controlCtx: ControlContext, val i18n: I18N,
                        val prefixSeq: Seq[String] = Seq()) {
  val internal_L = L.newThread()

  val prefix = if(prefixSeq.isEmpty) "" else s"${prefixSeq.mkString(":")}:"

  private val activatedRxes = new mutable.HashMap[FieldNode, Rx[Any]]
  private val activatingRxes = new mutable.HashSet[FieldNode]
  def activateNode(node: FieldNode)(implicit owner: Ctx.Owner): Rx[Any] = activatedRxes.getOrElseUpdate(node, {
    if(activatingRxes.contains(node))
      throw EditorException("Cycle in field nodes!")
    try {
      activatingRxes.add(node)
      node.createRx(this, owner)
    } finally {
      activatingRxes.remove(node)
    }
  })

  val activatedRoots = new util.IdentityHashMap[RootNode, Rx[ActiveRootNode]].asScala

  private val activatedCardFields = new mutable.HashMap[String, TreeNode]
  private val uiActivatedCardField = new mutable.HashSet[String]
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
        println("Creating UI")
        lastComponent.foreach(_.dispose())
        lastComponent = currentRoot.renderUI(pane, uiCtx)
        pane.layout(true)
      } else componentObs.kill()
    }
  }
}

final class ActiveRootNode private (context: NodeContext, root: Option[ControlNode],
                                    fields: Option[Map[String, Rx[Any]]])(implicit owner: Ctx.Owner) {
  def renderUI(uiRoot: Composite, uiCtx: UIContext) =
    root.map(_.createControl(uiRoot)(context, uiCtx.newUIContext(context), owner))
  lazy val luaOutput = fields.map { fields => Rx { fields.map(x => x.copy(_2 = x._2())) } }
}
object ActiveRootNode {
  def apply(L: LuaState, data: DataStore, controlCtx: ControlContext, i18n: I18N, prefix: Seq[String],
            uiRoot: Option[ControlNode], fields: Option[Map[String, FieldNode]])(implicit owner: Ctx.Owner) = {
    val ctx = new NodeContext(L, data, controlCtx, i18n, prefix)
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

  override def createRx(implicit ctx: NodeContext, owner: Ctx.Owner): Rx[Any] = {
    subtableName.foreach(n => ctx.activateCardField(n, this))

    val active = makeActiveNode(ctx)
    // TODO: Consider optimizing this to not copy the entire table to a Lua one
    Rx { active().luaOutput.fold[Any](ctx.internal_L.newTable().toLua(ctx.internal_L))(_().toLua(ctx.internal_L)) }
  }

  override def createControl(parent: Composite)(implicit ctx: NodeContext, uiCtx: UIContext, owner: Ctx.Owner) = {
    subtableName.foreach{ n =>
      ctx.activateCardField(n, this)
      uiCtx.activateCardField(n)
    }
    new RxPane(parent, ctx, uiCtx, makeActiveNode(ctx)).pane
  }
}