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

import com.coconut_palm_software.xscalawt.XScalaWT._
import moe.lymia.princess.editor.project.CardData
import moe.lymia.princess.editor.ui.mainframe.{MainFrameState, PrincessEditTab}
import moe.lymia.princess.editor.utils.RxOwner
import org.eclipse.jface.action.MenuManager
import org.eclipse.jface.window.IShellProvider
import org.eclipse.swt._
import org.eclipse.swt.custom._
import org.eclipse.swt.layout._
import org.eclipse.swt.widgets._
import rx._

final class CardEditorPane(parent: Composite, state: EditorState, cardData: CardData)
  extends Composite(parent, SWT.NONE) {

  setLayout(new FillLayout())

  private var deactivated = false
  addTraverseListener(event =>
    if(event.detail == SWT.TRAVERSE_ESCAPE) {
      event.doit = false
      if(!deactivated) state.deactivateEditor()
      deactivated = true
    }
  )

  val ui = cardData.root.createUI(this)
  setTabList(Array(ui.control))
  addDisposeListener(_ => ui.kill())
}

final class EditorState(parent: EditorPane, val mainFrameState: MainFrameState) extends RxOwner {
  val currentCardSelection = Var(Seq.empty[UUID])
  val source = parent.source

  val currentCard = Rx { currentCardSelection().lastOption }
  val currentCardData = Rx { currentCard().flatMap(mainFrameState.project.cards.now.get) }

  def isEditorActive = parent.isEditorActive

  def activateEditor() = currentCardData.now.foreach(card => parent.activateEditor(card))
  def deactivateEditor() = parent.deactivateEditor()

  override def kill(): Unit = {
    super.kill()
    currentCardSelection.kill()
  }
}

final class EditorPane(parent: Composite, val source: IShellProvider, mainState: MainFrameState)
  extends Composite(parent, SWT.NONE) with PrincessEditTab {

  private val editorState = new EditorState(this, mainState)

  private val stack = new StackLayout
  private var listContainer: Composite = _
  private var selector: CardSelectorTableViewer = _

  this.contains(
    _.setLayout(new FillLayout()),
    sashForm(
      _.setLayout(new FillLayout()),
      *[Composite](SWT.BORDER) (
        _.setLayout(new FillLayout()),
        x => new RendererPane(x, editorState)
      ),
      *[Composite](SWT.BORDER) (
        _.setLayout(stack),
        listContainer = _,
        x => selector = new CardSelectorTableViewer(x, editorState)
      ),
      _.setWeights(Array(1000, 1618))
    )
  )

  stack.topControl = selector

  private var currentEditor: Option[CardEditorPane] = None
  private def clearCurrentEditor() = {
    currentEditor match {
      case Some(x) => x.dispose()
      case None =>
    }
    currentEditor = None
    stack.topControl = selector
  }

  private var editorActive = false
  def isEditorActive = editorActive
  def activateEditor(cardData: CardData) = {
    editorActive = true
    clearCurrentEditor()
    selector.editorOpened()
    val editor = new CardEditorPane(listContainer, editorState, cardData)
    currentEditor = Some(editor)
    stack.topControl = editor
    listContainer.layout()
    editor.forceFocus()
    editorState.ctx.asyncUiExec { editor.traverse(SWT.TRAVERSE_TAB_NEXT) }
  }
  def deactivateEditor() = {
    editorActive = false
    clearCurrentEditor()
    selector.editorClosed()
    listContainer.layout()
    selector.getTable.setFocus()
  }

  addDisposeListener(_ => editorState.kill())

  override def addMenuItems(m: MenuManager): Unit = {
    // TODO
  }
}