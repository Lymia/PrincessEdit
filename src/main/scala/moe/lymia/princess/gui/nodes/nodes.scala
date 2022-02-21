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

package moe.lymia.princess.gui.nodes

import moe.lymia.lua.LuaState
import moe.lymia.princess.core.datamodel.{DataField, DataFieldType, DataStore}
import moe.lymia.princess.core.{EditorException, I18N}
import moe.lymia.princess.gui.ControlContext
import org.eclipse.swt.widgets.{Composite, Control}
import rx.{Ctx, Obs, Rx, Var}

import java.util
import scala.collection.JavaConverters._
import scala.collection.mutable

case class SetupData(obses: Seq[Obs] = Seq.empty)
object SetupData {
  val none = SetupData()
}

sealed trait TreeNode {
  def setupNode(implicit ctx: NodeContext, owner: Ctx.Owner): SetupData
}

final case class ControlData(L: LuaState, internal_L: LuaState, ctx: ControlContext,
                             i18n: I18N, backing: Var[DataField], isEnabled: Rx[Boolean])
trait ControlType {
  def expectedFieldType: DataFieldType[_]
  def defaultValue: DataField
  def createComponent(parent: Composite, data: ControlData)(implicit owner: Ctx.Owner, uiCtx: UIContext): Control
}
trait ControlNode extends TreeNode {
  def createControl(parent: Composite)(implicit ctx: NodeContext, uiCtx: UIContext, owner: Ctx.Owner): Control
}

trait FieldNode extends TreeNode {
  def createRx(implicit ctx: NodeContext, owner: Ctx.Owner): Rx[Any]
  override val hashCode = super.hashCode
}

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
