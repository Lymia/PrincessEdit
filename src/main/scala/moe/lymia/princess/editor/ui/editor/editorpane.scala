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

import moe.lymia.princess.editor.core._
import moe.lymia.princess.editor.ui.mainframe.{EditorTab, MainFrameState}

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

final class EditorListContainer(parent: Composite, state: EditorState) extends Composite(parent, SWT.BORDER) {
  private val stack = new StackLayout
  this.setLayout(stack)

  private val viewer = new CardSelectorTableViewer(this, state)
  stack.topControl = viewer

  private var currentEditor: Option[CardEditorPane] = None
  private def clearCurrentEditor() = {
    currentEditor match {
      case Some(x) => x.dispose()
      case None =>
    }
    currentEditor = None
    stack.topControl = viewer
  }

  private var editorActive = false
  def isEditorActive = editorActive
  def activateEditor(cardData: CardData) = {
    editorActive = true
    clearCurrentEditor()
    viewer.editorOpened()
    val editor = new CardEditorPane(this, state, cardData)
    currentEditor = Some(editor)
    stack.topControl = editor
    layout()
    editor.forceFocus()
    state.ctx.asyncUiExec { editor.traverse(SWT.TRAVERSE_TAB_NEXT) }
  }
  def deactivateEditor() = {
    editorActive = false
    clearCurrentEditor()
    viewer.editorClosed()
    layout()
    viewer.getTable.setFocus()
  }
}

final class EditorLayout(parent: Composite, state: EditorState) {
  var listContainer: EditorListContainer = _
  parent.contains(
    sashForm(
      _.setLayout(new FillLayout()),
      *[Composite](SWT.BORDER) (
        _.setLayout(new FillLayout()),
        x => new RendererPane(x, state)
      ),
      composite (
        _.setLayout(new FillLayout()),
        x => listContainer = new EditorListContainer(x, state)
      ),
      _.setWeights(Array(1000, 1618))
    )
  )
}

final class EditorState(parent: EditorPane, val mainFrameState: MainFrameState) {
  val currentCard = Var[Option[UUID]](None)
  val source = parent.source

  def currentCardData = currentCard.now.flatMap(mainFrameState.project.cards.now.get)

  def isEditorActive = parent.editorLayout.listContainer.isEditorActive
  def activateEditor() = currentCardData.foreach(card => parent.editorLayout.listContainer.activateEditor(card))
  def deactivateEditor() = parent.editorLayout.listContainer.deactivateEditor()
}

final class EditorPane(parent: Composite, val source: IShellProvider, mainState: MainFrameState)
  extends Composite(parent, SWT.NONE) with EditorTab {

  private val editorState = new EditorState(this, mainState)

  setLayout(new FillLayout())
  private[editor] val editorLayout = new EditorLayout(this, editorState)
}