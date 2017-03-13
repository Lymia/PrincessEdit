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

private class WeakReferenceList[T] {
  private var queue = new ReferenceQueue[T]
  private val list  = new mutable.HashSet[Reference[_ <: T]]

  def cleanup() = while(queue.poll() != null) list.remove(queue.remove())
  def add(t: T) = list.add(new WeakReference(t, queue))
  def foreach(f: T => Unit) = list.foreach(x => x.get match {
    case null =>
    case t => f(t)
  })
  def clear() = {
    queue = new ReferenceQueue[T]
    list.clear()
  }
}
private[data] class RxTracker {
  private val rxs   = new WeakReferenceList[Rx[_]]
  private val obses = new WeakReferenceList[Obs]

  def cleanup() = {
    rxs.cleanup()
    obses.cleanup()
  }

  def add[T](rx: Rx[T]) = {
    rxs.add(rx)
    cleanup()
  }
  def add(obs: Obs) = {
    obses.add(obs)
    cleanup()
  }

  def kill() = {
    rxs.foreach(_.kill())
    obses.foreach(_.kill())

    rxs.clear()
    obses.clear()
  }
}
private[data] class TreeState(val prefixSeq: Seq[String] = Seq()) {
  val prefix = if(prefixSeq.isEmpty) "" else s"${prefixSeq.mkString(":")}:"

  val activatedRxes = new mutable.HashMap[FieldNode, Rx[Any]]
  val activatedRoots = new mutable.HashMap[RootNode, Rx[ActiveRootNode]]

  val tracker = new RxTracker

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

case class RxPane(componentRx: Rx[JComponent], tracker: RxTracker) extends JPanel {
  import rx.Ctx.Owner.Unsafe._

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
  tracker.add(componentObs)
}

class ActiveRootNode private (context: ControlContext, uiRoot: Option[ControlNode],
                              fields: Option[Map[String, Rx[Any]]]) {
  lazy val uiComponent = uiRoot.map(_.createControl(context))
  lazy val luaOutput = {
    val out = Rx.unsafe { fields.map(_.mapValues(_())) }
    context.state.tracker.add(out)
    out
  }
}
object ActiveRootNode {
  def apply(L: LuaState, data: DataStore, prefix: Seq[String],
            uiRoot: Option[ControlNode], fields: Option[Map[String, FieldNode]]) = {
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

    for(node <- resolveLoadOrder(findSpecFields((uiRoot ++ fields.toSeq.flatMap(_.values)).toSet, Set()), Seq())) {
      for(name <- node.managesCardField) state.activateCardField(name, node, isUi = false)
      node match {
        case field: FieldNode =>
          val curRx = field.createRx(L, data, state)
          state.activatedRxes.put(field, curRx)
          state.tracker.add(curRx)
        case _ =>
      }
    }

    new ActiveRootNode(ControlContext(L, data, state), uiRoot, fields.map(_.mapValues(state.activatedRxes)))
  }
}

case class RootNode(subtableName: String, params: Seq[FieldNode], fn: LuaClosure)
  extends FieldNode with ControlNode {

  override protected[data] def managesCardField: Set[String] = Set(subtableName)
  override protected[data] def deps = params.toSet

  private def makeActiveNode(L: LuaState, data: DataStore, state: TreeState) =
    state.activatedRoots.getOrElseUpdate(this, {
      val fields = params.map(state.activatedRxes)
      val rx = Rx.unsafe {
        // unapply not used because apparently scala.rx's macros break on those
        val ret = L.newThread().call(fn, 2, fields.map(_() : LuaObject) : _*)
        ActiveRootNode(L, data, state.prefixSeq :+ subtableName,
                       ret.last.as[Option[ControlNode]], ret.head.as[Option[Map[String, FieldNode]]])
      }
      state.tracker.add(rx)
      rx
    })

  override protected[data] def createRx(L: LuaState, data: DataStore, state: TreeState): Rx[Any] = ???
  override protected[data] def createControl(implicit ctx: ControlContext): JComponent = ???
}