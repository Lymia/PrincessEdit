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

import java.lang.ref._
import javax.swing._

import moe.lymia.princess.core._
import moe.lymia.princess.editor.lua._
import moe.lymia.princess.lua._
import rx._

import scala.annotation.tailrec
import scala.collection.mutable

private[data] final class TreeState(val prefixSeq: Seq[String] = Seq()) {
  val prefix = if(prefixSeq.isEmpty) "" else s"${prefixSeq.mkString(":")}:"

  val activatedRxes = new mutable.HashMap[FieldNode, Rx[Any]]
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
    val state = new TreeState(prefix)

    @tailrec def findSpecFields(gen: Set[TreeNode], found: Set[TreeNode]): Set[TreeNode] = {
      val allDeps  = gen.flatMap(_.deps)
      val combined = gen ++ found
      val newDeps  = allDeps -- combined
      if(newDeps.isEmpty) combined else findSpecFields(newDeps, combined)
    }
    @tailrec def resolveLoadOrder(unresolved: Set[TreeNode], resolved: Seq[TreeNode]): Seq[TreeNode] = {
      val (newUnresolved, newResolved) = unresolved.partition(_.deps.forall(resolved.contains))
      if(newResolved.isEmpty) sys.error("cycle in tree nodes")
      val combined = resolved ++ newResolved
      if(newUnresolved.isEmpty) combined else resolveLoadOrder(newUnresolved, combined)
    }

    val ctx = NodeContext(L, data, state)

    for(node <- resolveLoadOrder(findSpecFields((uiRoot ++ fields.toSeq.flatMap(_.values)).toSet, Set()), Seq())) {
      for(name <- node.managesCardField) state.activateCardField(name, node, isUi = false)
      node match {
        case field: FieldNode =>
          val curRx = field.createRx(ctx)
          state.activatedRxes.put(field, curRx)
        case _ =>
      }
    }

    new ActiveRootNode(ctx, uiRoot, fields.map(_.mapValues(state.activatedRxes)))
  }
}

final case class RootNode(subtableName: String, params: Seq[FieldNode], fn: LuaClosure)
  extends FieldNode with ControlNode {

  override protected[data] def managesCardField: Set[String] = Set(subtableName)
  override protected[data] def deps = params.toSet

  private def makeActiveNode(ctx: NodeContext)(implicit owner: Ctx.Owner) =
    ctx.state.activatedRoots.getOrElseUpdate(this, {
      val fields = params.map(ctx.state.activatedRxes)
      Rx {
        // unapply not used because apparently scala.rx's macros break on those
        val ret = ctx.L.newThread().call(fn, 2, fields.map(_() : LuaObject) : _*)
        val node = ActiveRootNode(ctx.L, ctx.data, ctx.state.prefixSeq :+ subtableName,
                                  ret.last.as[Option[ControlNode]], ret.head.as[Option[Map[String, FieldNode]]])

        node
      }
    })

  override protected[data] def createRx(ctx: NodeContext)(implicit owner: Ctx.Owner): Rx[Any] = {
    ctx.state.activateCardField(subtableName, this, isUi = false)

    val active = makeActiveNode(ctx)
    Rx { active().luaOutput.fold[Any](Lua.NIL)(_().toLua(ctx.L)) }
  }

  override protected[data] def createControl(implicit ctx: NodeContext, owner: Ctx.Owner): JComponent = {
    ctx.state.activateCardField(subtableName, this, isUi = false)

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