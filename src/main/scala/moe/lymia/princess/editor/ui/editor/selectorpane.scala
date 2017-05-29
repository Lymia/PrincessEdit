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

import moe.lymia.princess.editor.TableColumnData
import moe.lymia.princess.editor.model.FullCardData
import moe.lymia.princess.editor.ui.export.ExportCardsDialog
import moe.lymia.princess.editor.utils.RxWidget
import org.eclipse.jface.action._
import org.eclipse.jface.layout.TableColumnLayout
import org.eclipse.jface.viewers._
import org.eclipse.swt.SWT
import org.eclipse.swt.events.{KeyEvent, KeyListener, SelectionAdapter, SelectionEvent}
import org.eclipse.swt.graphics.{Image, Point}
import org.eclipse.swt.widgets._
import play.api.libs.json.JsObject
import rx._

import scala.collection.JavaConverters._

// TODO: Factor out the table sort management code, etc
// TODO: Make sorting code more efficient (don't run it in the editor)
case class RowData(id: UUID, data: FullCardData, fields: Seq[String])
final class CardSelectorTableViewer(parent: Composite, state: EditorState)
  extends Composite(parent, SWT.NONE) with RxWidget {

  private val viewer = new TableViewer(this, SWT.MULTI | SWT.FULL_SELECTION | SWT.V_SCROLL | SWT.H_SCROLL)
  def getControl = viewer.getControl
  def getTable = viewer.getTable

  viewer.getTable.setHeaderVisible(true)
  viewer.getTable.setLinesVisible(true)

  // Setup columns
  private val activeColumns = Var(state.idData.columns.columns.filter(_.isDefault))
  private val sortOrder = Var[Option[Ordering[RowData]]](None)
  def setColumnOrder(columns: Seq[TableColumnData]) = {
    viewer.getTable.setRedraw(false)
    activeColumns.update(columns)
    refreshColumns()
  }
  def refreshColumns() = {
    viewer.getTable.setRedraw(false)

    viewer.getTable.setSortColumn(null)
    viewer.getTable.setSortDirection(SWT.DOWN)

    for(column <- viewer.getTable.getColumns) column.dispose()

    val tableLayout = new TableColumnLayout()
    for((column, i) <- activeColumns.now.zipWithIndex) {
      val col = new TableColumn(viewer.getTable, SWT.NONE)
      col.setText(state.i18n.user(column.title))
      col.setMoveable(false)
      col.addSelectionListener(new SelectionAdapter {
        override def widgetSelected(e: SelectionEvent): Unit = {
          if(viewer.getTable.getSortColumn eq col) {
            viewer.getTable.setSortDirection(viewer.getTable.getSortDirection match {
              case SWT.NONE => SWT.UP
              case SWT.UP   => SWT.DOWN
              case SWT.DOWN => SWT.NONE
            })
            viewer.refresh()
          } else {
            viewer.getTable.setSortColumn(col)
            viewer.getTable.setSortDirection(SWT.UP)
          }

          if(viewer.getTable.getSortDirection == SWT.NONE) state.ctx.queueUpdate(sortOrder, None)
          else {
            val multiplier = viewer.getTable.getSortDirection match {
              case SWT.UP   => 1
              case SWT.DOWN => -1
            }
            state.ctx.queueUpdate(sortOrder, Some(
              ((a, b) => multiplier * (column.sortFn match {
                case None =>
                  // TODO: Ensure this is safe
                  a.fields(i).compare(b.fields(i))
                case Some(fn) =>
                  column.L.newThread().call(fn, 1, a.data.luaData.now, b.data.luaData.now).head.as[Int]
              })) : Ordering[RowData]
            ))
          }
        }
      })
      tableLayout.setColumnData(col, new ColumnPixelData(column.width, true))
    }
    setLayout(tableLayout)

    viewer.getTable.setRedraw(true)
    viewer.refresh()
  }
  refreshColumns()

  // Setup cards rx
  private val cardsRx: Rx[Seq[RowData]] = Rx {
    state.currentPool().fullCardList().map(card => {
      val columns = card.columnData()
      RowData(card.uuid, card, activeColumns().map(columns))
    })
  }
  private val sortedCards: Rx[Seq[RowData]] = state.ctx.syncLuaExec {
    Rx {
      sortOrder() match {
        case None => cardsRx()
        case Some(ordering) => cardsRx().sorted(ordering)
      }
    }
  }
  private val obs = sortedCards.foreach { _ =>
    state.ctx.asyncUiExec { viewer.refresh() }
  }
  addDisposeListener(_ => {
    cardsRx.kill()
    sortedCards.kill()
    obs.kill()
  })

  // Setup viewer providers
  viewer.setContentProvider(new IStructuredContentProvider {
    override def getElements(o: scala.Any): Array[AnyRef] = sortedCards.now.toArray
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
  private var savedSelection: Seq[UUID] = _
  private var lockSelection = false
  def editorOpened() = {
    savedSelection = viewer.getStructuredSelection.iterator().asScala.toSeq.map(_.asInstanceOf[RowData].id)
    lockSelection = true
  }
  def editorClosed() = {
    setSelection(savedSelection : _*)
    savedSelection = null
    lockSelection = false
    viewer.getTable.setFocus()
  }

  // Menu
  def getSelectedCards =
    viewer.getStructuredSelection.toArray.map(_.asInstanceOf[RowData])
  def setSelection(uuid: UUID*) = {
    viewer.refresh()
    val uuidSet = uuid.toSet
    val selection = new StructuredSelection(sortedCards.now.filter(x => uuidSet.contains(x.id)).toArray[Object])
    viewer.setSelection(selection, true)
    state.ctx.queueUpdate(state.currentCardSelection, uuid)
  }

  private val copy = new Action(state.i18n.system("_princess.editor.copyCard")) {
    setAccelerator(SWT.CTRL | 'C')
    override def run() = {
      val cards = getSelectedCards.map(_.data.cardData.serialize)
      state.ctx.clipboard.setContents(Array(CardTransferData(cards : _*)), Array(CardTransfer))
    }
  }
  private val paste = new Action(state.i18n.system("_princess.editor.pasteCard")) {
    setAccelerator(SWT.CTRL | 'V')
    override def run() = state.ctx.clipboard.getContents(CardTransfer) match {
      case transfer: CardTransferData =>
        val ids = state.ctx.syncLuaExec {
          for(card <- transfer.json) yield {
            val (uuid, data) = state.project.cards.create()
            data.deserialize(card.as[JsObject])
            uuid
          }
        }
        setSelection(ids : _*)
      case _ =>
    }
  }
  private val export = new Action(state.i18n.system("_princess.editor.exportCard")) {
    override def run() =
      ExportCardsDialog.open(state, getSelectedCards.map(x => x.id -> x.data) : _*)
  }
  private val addCard = new Action(state.i18n.system("_princess.editor.newCard")) {
    setAccelerator(SWT.CTRL | SWT.CR)
    override def run() = setSelection(state.ctx.syncLuaExec(state.project.cards.create()._1))
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

      if(ctrl) keyEvent.keyCode match {
        case SWT.CR => addCard.run()
        case 'c'    => copy.run()
        case 'v'    => paste.run()
        case 'a'    => viewer.getTable.selectAll()
        case _ =>
      }

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
}