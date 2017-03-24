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

package moe.lymia.princess.editor.ui.mainframe

import java.util.UUID

import moe.lymia.nebula.compositetable._
import moe.lymia.princess.editor.core._

import org.eclipse.swt._
import org.eclipse.swt.layout.FillLayout
import org.eclipse.swt.widgets._

import rx._

final class CardSelectorState(val idData: GameIDData, val ctx: ControlContext,
                              val state: MainFrameState) extends ExtraBase {
  val columns = idData.columns
}

final class CardSelectorElement(val id: UUID, val card: CardData) extends ElementBase

final class CardSelectorHeader(parent: Composite, style: Int)
  extends NativeHeader[CardSelectorElement, CardSelectorState](parent, style) {

  override def init() = {
    val columnOrder = extra.idData.columns.defaultColumnOrder
    println("Setting table columns.")
    this.setWeights(columnOrder.indices.map(_ => 100).toArray)
    this.setColumnText(columnOrder.toArray)
    for(column <- this.getColumns) column.setMoveable(false)
  }
}

final class CardSelectorRow(parent: Composite, style: Int)
  extends EditorSelectableRow[CardSelectorElement, CardSelectorState](parent, style) {

  private var obses: Seq[Obs] = Seq()
  private def clearObses() = {
    for(obs <- obses) obs.kill()
    obses = Seq()
  }

  override protected def makeTableMode(): Unit = if(elem != null) {
    clearObses()

    setLayout(new ResizableGridRowLayout)
    val columnOrder = extra.idData.columns.defaultColumnOrder
    this.setColumnCount(columnOrder.length)

    if(hasElem) {
      import Ctx.Owner.Unsafe._
      obses = Seq(elem.card.columnData.foreach { map =>
        if(!this.isDisposed) extra.ctx.asyncUiExec {
          val controls = getControlList
          for((column, i) <- columnOrder.zipWithIndex) {
            val control = controls.get(i)
            if(!control.isDisposed) control.asInstanceOf[Label].setText(map(column))
          }
        }
      })
    }
  }

  override protected def makeEditorMode(): Unit = {
    clearObses()

    if(hasElem) {
      setLayout(new FillLayout)
      elem.card.root.createUI(this)
    }
  }

  addDisposeListener(event => clearObses())

  override def arrive(sender: CompositeTable, currentObjectOffset: Int, newRow: Control): Unit = {
    super.arrive(sender, currentObjectOffset, newRow)
    extra.ctx.queueUpdate(extra.state.currentCard, Some(elem.id))
  }
  override def depart(sender: CompositeTable, currentObjectOffset: Int, row: Control): Unit = {
    super.depart(sender, currentObjectOffset, row)
    extra.state.currentCard.now match {
      case Some(id) if id == elem.id => extra.ctx.queueUpdate(extra.state.currentCard, None)
      case _ =>
    }
  }
}

final class CardSelector(parent: Composite, project: Project, idData: GameIDData,
                         ctx: ControlContext, state: MainFrameState)
  extends ScalaCompositeTable[CardSelectorElement, CardSelectorState,
                              CardSelectorHeader, CardSelectorRow](parent, SWT.NONE) {

  protected def newState() = new CardSelectorState(idData, ctx, state)

  private var elements: IndexedSeq[CardSelectorElement] = IndexedSeq.empty
  override protected def getElements: IndexedSeq[CardSelectorElement] = elements

  import Ctx.Owner.Unsafe._
  private val rx = Rx {
    val cards = project.cards()
    val allCards = state.currentPool().allCards().flatMap(x => cards.get(x).map(x -> _))
    allCards.map(x => new CardSelectorElement(x._1, x._2)).toIndexedSeq
  }
  private val obs = rx.foreach { elements =>
    if(!this.isDisposed) ctx.asyncUiExec {
      this.elements = elements
      refreshElements()
    }
  }
}