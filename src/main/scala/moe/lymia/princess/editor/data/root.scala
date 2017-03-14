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

import moe.lymia.princess.core._
import moe.lymia.princess.editor.lua._
import moe.lymia.princess.lua._
import rx._

import scala.collection.mutable

private[data] final class NodeContext(val L: LuaState, val data: DataStore, val prefixSeq: Seq[String] = Seq()) {
  val prefix = if(prefixSeq.isEmpty) "" else s"${prefixSeq.mkString(":")}:"

  private val activatedRxes = new mutable.HashMap[FieldNode, Rx[Any]]
  private val activatingRxes = new mutable.HashSet[FieldNode]
  def activateNode(node: FieldNode)(implicit owner: Ctx.Owner): Rx[Any] = activatedRxes.getOrElseUpdate(node, {
    if(activatingRxes.contains(node))
      throw EditorException("Cycle in field nodes!")
    try {
      activatingRxes.add(node)
      node.createRx(this)
    } finally {
      activatingRxes.remove(node)
    }
  })

  val activatedRoots = new mutable.HashMap[RootNode, Rx[ActiveRootNode]]

  private val activatedCardFields = new mutable.HashMap[String, TreeNode]
  private val uiActivatedCardField = new mutable.HashSet[String]
  def activateCardField(name: String, node: TreeNode, isUi: Boolean) = {
    activatedCardFields.get(name) match {
      case Some(f) =>
        if(f ne node)
          throw EditorException(s"Cannot control card data field '${prefix+name}' with two different nodes.")
      case None => activatedCardFields.put(name, node)
    }
    if(isUi) {
      if(uiActivatedCardField.contains(name))
        throw EditorException(s"Cannot reuse UI element controlling card data field '${prefix+name}'!")
    }
  }
}

final class RxPane(componentRx: Rx[JComponent])(implicit owner: Ctx.Owner) extends JPanel {
  private def updateContents(): Unit = {
    val contents = componentRx.now
    for(comp <- this.getComponents) this.remove(comp)
    this.add(contents)

    this.setMinimumSize(contents.getMinimumSize)
    this.setPreferredSize(contents.getPreferredSize)
    this.setMaximumSize(contents.getMaximumSize)
  }
  updateContents()

  private val componentObs = componentRx.trigger(updateContents())
}

final class ActiveRootNode private (context: NodeContext, uiRoot: Option[ControlNode],
                                    fields: Option[Map[String, Rx[Any]]])(implicit owner: Ctx.Owner) {
  lazy val uiComponent = uiRoot.map(_.createControl(context, owner))
  lazy val luaOutput = fields.map { fields => Rx { fields.mapValues(_()) } }
}
object ActiveRootNode {
  def apply(L: LuaState, data: DataStore, prefix: Seq[String],
            uiRoot: Option[ControlNode], fields: Option[Map[String, FieldNode]])(implicit owner: Ctx.Owner) = {
    val ctx = new NodeContext(L, data, prefix)
    new ActiveRootNode(ctx, uiRoot, fields.map(_.mapValues(x => ctx.activateNode(x))))
  }
}

final case class RootNode(subtableName: String, params: Seq[FieldNode], fn: LuaClosure)
  extends FieldNode with ControlNode {

  private def makeActiveNode(ctx: NodeContext)(implicit owner: Ctx.Owner) =
    ctx.activatedRoots.getOrElseUpdate(this, {
      val fields = params.map(x => ctx.activateNode(x))
      Rx {
        // unapply not used because apparently scala.rx's macros break on those
        val ret = ctx.L.newThread().call(fn, 2, fields.map(_() : LuaObject) : _*)
        val node = ActiveRootNode(ctx.L, ctx.data, ctx.prefixSeq :+ subtableName,
                                  ret.last.as[Option[ControlNode]], ret.head.as[Option[Map[String, FieldNode]]])
        node
      }
    })

  override protected[data] def createRx(ctx: NodeContext)(implicit owner: Ctx.Owner): Rx[Any] = {
    ctx.activateCardField(subtableName, this, isUi = false)

    val active = makeActiveNode(ctx)
    Rx { active().luaOutput.fold[Any](Lua.NIL)(_().toLua(ctx.L)) }
  }

  override protected[data] def createControl(implicit ctx: NodeContext, owner: Ctx.Owner): JComponent = {
    ctx.activateCardField(subtableName, this, isUi = false)

    val active = makeActiveNode(ctx)
    val componentRx = Rx {
      active().uiComponent.getOrElse({
        val area = new JTextArea()
        area.setText("No UI component given!")
        area.setEditable(false)
        area
      })
    }
    new RxPane(componentRx)
  }
}