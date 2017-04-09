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
import moe.lymia.princess.editor.project.CardData
import moe.lymia.princess.editor.ui.export.ExportCardsDialog
import moe.lymia.princess.editor.utils.RxWidget
import org.eclipse.jface.action._
import org.eclipse.jface.layout.TableColumnLayout
import org.eclipse.jface.viewers._
import org.eclipse.swt.SWT
import org.eclipse.swt.events.{KeyEvent, KeyListener}
import org.eclipse.swt.graphics.{Image, Point}
import org.eclipse.swt.widgets._
import rx._

case class RowData(id: UUID, data: CardData, fields: Seq[String])
final class CardSelectorTableViewer(parent: Composite, state: EditorState)
  extends Composite(parent, SWT.NONE) with RxWidget {

  private val viewer = new TableViewer(this, SWT.MULTI | SWT.FULL_SELECTION | SWT.V_SCROLL | SWT.H_SCROLL)
  def getControl = viewer.getControl
  def getTable = viewer.getTable

  viewer.getTable.setHeaderVisible(true)
  viewer.getTable.setLinesVisible(true)

  // Setup columns
  private val cardsRx: Rx[Seq[RowData]] = Rx {
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
    col.setText(state.i18n.user(column.title))
    col.setMoveable(false)
    tableLayout.setColumnData(col, new ColumnPixelData(column.width, true))
  }
  setLayout(tableLayout)

  // Setup viewer providers
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
    viewer.setSelection(savedSelection, true)
    savedSelection = null
    lockSelection = false
    viewer.getTable.setFocus()
  }

  // Menu
  def getSelectedCards =
    viewer.getStructuredSelection.toArray.map(_.asInstanceOf[RowData])
  def setSelection(uuid: UUID) = {
    viewer.refresh()
    cardsRx.now.find(_.id == uuid).foreach(x => viewer.setSelection(new StructuredSelection(x), true))
    state.ctx.queueUpdate(state.currentCardSelection, Seq(uuid))
  }

  private val copy = new Action(state.i18n.system("_princess.editor.copyCard")) {
    setAccelerator(SWT.CTRL | 'C')
    override def run() = {
      val cards = getSelectedCards.map(_.data.serialize)
      state.ctx.clipboard.setContents(Array(CardTransferData(cards : _*)), Array(CardTransfer))
    }
  }
  private val paste = new Action(state.i18n.system("_princess.editor.copyCard")) {
    setAccelerator(SWT.CTRL | 'V')
    override def run() = state.ctx.clipboard.getContents(CardTransfer) match {
      case transfer: CardTransferData =>
        for(card <- transfer.json) {
          val uuid = state.project.newCard()
          val data = state.project.cards.now(uuid)
          data.deserialize(card)
          data.copied()
        }
        state.needsSaving()
      case _ =>
    }
  }
  private val export = new Action(state.i18n.system("_princess.editor.exportCard")) {
    override def run() =
      ExportCardsDialog.open(state, state.currentPool.now, getSelectedCards.map(x => x.id -> x.data) : _*)
  }
  private val addCard = new Action(state.i18n.system("_princess.editor.newCard")) {
    setAccelerator(SWT.CTRL | SWT.CR)
    override def run() = {
      setSelection(state.project.newCard())
      state.needsSaving()
    }
  }
  private val editCard = new Action(state.i18n.system("_princess.editor.editCard")) {
    setAccelerator(SWT.CR)
    override def run() = {
      setSelectionFromViewer()
      state.activateEditor()
    }
  }

  private var isHeaderClick = false
  private var isItemClick = false
  private val menuManager = new MenuManager()
  menuManager.setRemoveAllWhenShown(true)
  menuManager.addMenuListener{ _ =>
    if(isHeaderClick) {

    } else {
      val areItemsSelected = !viewer.getSelection.isEmpty

      copy.setEnabled(areItemsSelected)
      menuManager.add(copy)
      menuManager.add(paste)

      menuManager.add(new Separator)

      export.setEnabled(areItemsSelected)
      menuManager.add(export)

      menuManager.add(new Separator)

      menuManager.add(addCard)
      editCard.setEnabled(viewer.getStructuredSelection.size == 1)
      menuManager.add(editCard)
    }
  }
  viewer.getControl.setMenu(menuManager.createContextMenu(viewer.getControl))
  viewer.getControl.addMenuDetectListener(event => {
    val curLoc = state.ctx.display.map(null, viewer.getControl, new Point(event.x, event.y))
    val clientArea = viewer.getTable.getClientArea

    isHeaderClick = clientArea.y <= curLoc.y && curLoc.y < (clientArea.y + viewer.getTable.getHeaderHeight) &&
                    clientArea.x <= curLoc.x && curLoc.x < (clientArea.x + clientArea.width)
    isItemClick = viewer.getTable.getItem(curLoc) != null
  })

  // Events
  private def setSelectionFromViewer() = {
    val selection = viewer.getStructuredSelection.toArray.map(_.asInstanceOf[RowData].id)
    val currentSelection = state.currentCardSelection.now
    val (alreadySelected, newSelections) = selection.partition(currentSelection.contains)
    state.ctx.queueUpdate(state.currentCardSelection, (alreadySelected ++ newSelections).toSeq)
  }
  viewer.getTable.addKeyListener(new KeyListener {
    override def keyPressed(keyEvent: KeyEvent): Unit = {
      val ctrl = keyEvent.stateMask == SWT.CTRL

      val doit = keyEvent.doit
      keyEvent.doit = false

      if(ctrl && keyEvent.keyCode == SWT.CR) addCard.run()
      else if(ctrl && keyEvent.keyCode == 'c') copy.run()
      else if(ctrl && keyEvent.keyCode == 'v') paste.run()
      else if(ctrl && keyEvent.keyCode == 'a') viewer.getTable.selectAll()

      else keyEvent.doit = doit
    }
    override def keyReleased(keyEvent: KeyEvent): Unit = { }
  })
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