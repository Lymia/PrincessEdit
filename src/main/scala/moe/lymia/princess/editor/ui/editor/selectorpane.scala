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

package moe.lymia.princess.editor.ui.editor

import java.util.UUID

import moe.lymia.princess.editor.core._
import org.eclipse.jface.layout.TableColumnLayout
import org.eclipse.jface.viewers._
import org.eclipse.swt.SWT
import org.eclipse.swt.graphics.Image
import org.eclipse.swt.layout.FillLayout
import org.eclipse.swt.widgets._
import rx._

case class RowData(id: UUID, data: CardData, fields: Seq[String])
final class CardSelectorTableViewer(parent: Composite, state: EditorState) extends Composite(parent, SWT.NONE) {
  private val viewer = new TableViewer(this, SWT.FULL_SELECTION | SWT.V_SCROLL | SWT.H_SCROLL)
  def getControl = viewer.getControl
  def getTable = viewer.getTable

  val currentCard = Var[Option[UUID]](None)

  viewer.getTable.setHeaderVisible(true)
  viewer.getTable.setLinesVisible(true)

  import Ctx.Owner.Unsafe._

  private val cardsRx: Rx[Seq[RowData]] = Rx.unsafe {
    val cards = state.project.cards()
    state.currentPool().allCards().map(id => {
      val info = cards(id)
      val columns = info.columnData()
      RowData(id, info, state.idData.columns.defaultColumnOrder.map(columns))
    })
  }

  private val tableLayout = new TableColumnLayout()
  for(column <- state.idData.columns.defaultColumnOrder.toArray) {
    val col = new TableColumn(viewer.getTable, SWT.NONE)
    col.setText(column)
    col.setMoveable(false)
    tableLayout.setColumnData(col, new ColumnWeightData(100, true))
  }
  setLayout(tableLayout)

  viewer.setContentProvider(new IStructuredContentProvider {
    override def getElements(o: scala.Any): Array[AnyRef] = cardsRx.now.toArray
  })
  viewer.setLabelProvider(new LabelProvider with ITableLabelProvider {
    override def getColumnText(o: scala.Any, i: Int): String = {
      val row = o.asInstanceOf[RowData]
      row.fields(i)
    }
    override def getColumnImage(o: scala.Any, i: Int): Image = null
  })
  viewer.setInput(this)

  // This is a hack to deal with the fact that this panel losing focus sets the current card to None
  private var savedSelection: ISelection = _
  private var lockSelection = false

  def editorOpened() = {
    savedSelection = viewer.getStructuredSelection
    lockSelection = true
  }
  def editorClosed() = {
    viewer.setSelection(savedSelection)
    savedSelection = null
    lockSelection = false
  }

  private def setSelectionFromViewer() = {
    val selection = viewer.getStructuredSelection
    if(selection.isEmpty) state.currentCard.update(None)
    else {
      val data = selection.getFirstElement.asInstanceOf[RowData]
      state.currentCard.update(Some(data.id))
    }
  }
  viewer.addSelectionChangedListener { event =>
    if(!lockSelection) setSelectionFromViewer()
  }
  viewer.addDoubleClickListener { event =>
    setSelectionFromViewer()
    state.activateEditor()
  }

  private val obs = cardsRx.foreach { _ =>
    state.ctx.asyncUiExec { viewer.refresh() }
  }
}