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
import moe.lymia.princess.editor.model.FullCardData
import moe.lymia.princess.editor.ui.mainframe._
import moe.lymia.princess.editor.utils.{RxOwner, RxWidget, UIUtils}
import moe.lymia.princess.editor.{DataRoot, UIData}
import org.eclipse.jface.action.MenuManager
import org.eclipse.swt._
import org.eclipse.swt.custom._
import org.eclipse.swt.events.SelectionEvent
import org.eclipse.swt.layout._
import org.eclipse.swt.widgets._
import play.api.libs.json.Json
import rx._

final class DataRootEditorPane(parent: Composite, state: EditorState, root: DataRoot)
  extends Composite(parent, SWT.NONE) {

  private val grid = new GridLayout()
  grid.marginWidth = 0
  grid.marginHeight = 0
  setLayout(grid)

  private var deactivated = false
  private def deactivate() = {
    if(!deactivated) state.deactivateEditor()
    deactivated = true
  }
  addTraverseListener(event =>
    if(event.detail == SWT.TRAVERSE_ESCAPE) {
      event.doit = false
      deactivate()
    }
  )

  private var scrolled: ScrolledComposite = _
  private var uiContainer: Composite = _
  private var ui: UIData = _

  this.contains (
    *[ScrolledComposite](SWT.V_SCROLL | SWT.BORDER) (
      scrolled = _,
      composite(
        uiContainer = _,
        fillLayout(
          _.marginWidth = 5,
          _.marginHeight = 5
        ),
        x => ui = root.createUI(x)
      ),
      _.setContent(uiContainer),
      _.setExpandVertical(true),
      _.setExpandHorizontal(true),
      _.layoutData = new GridData(SWT.FILL, SWT.FILL, true, true)
    ),
    button (
      state.i18n.system("_princess.editor.back"),
      (event: SelectionEvent) => deactivate(),
      _.layoutData = new GridData(SWT.FILL, SWT.NONE, true, false)
    ),
    UIUtils.listBackgroundStyle.apply
  )

  private def updateScrolledComposite() = {
    scrolled.setMinSize(uiContainer.computeSize(SWT.DEFAULT, SWT.DEFAULT))
    scrolled.layout(true)
  }
  updateScrolledComposite()
  ui.control.addListener(SWT.Resize, _ => updateScrolledComposite())

  addDisposeListener(_ => ui.kill())
}

sealed trait EditorAPI {
  def data: EditorTabData
  def activateEditor()
  def activatePoolDataEditor()
  def deactivateEditor()
}
final class EditorState(parent: EditorTab, val data: EditorTabData, val mainFrameState: MainFrameState)
  extends RxOwner with EditorAPI {

  val currentCardSelection = Var(Seq.empty[UUID])

  val currentPool = Rx { mainFrameState.project.allPools().getOrElse(data.setId, sys.error("Unknown pool")) }
  val currentCard = Rx { currentCardSelection().lastOption }
  val currentCardData = Rx { currentCard().flatMap(currentPool().getFullCard) }

  def isEditorActive = parent.isEditorActive

  def activateEditor() = currentCardData.now.foreach(card => parent.activateEditor(card))
  def activatePoolDataEditor() = parent.activatePoolDataEditor()
  def deactivateEditor() = parent.deactivateEditor()

  override def kill(): Unit = {
    super.kill()
    currentCardSelection.kill()
  }
}

final case class EditorTabData(setId: UUID)
final class EditorTab(parent: Composite, data: EditorTabData, mainState: MainFrameState)
  extends Composite(parent, SWT.NONE) with PrincessEditTab with RxWidget {

  private val editorState = new EditorState(this, data, mainState)

  private val stack = new StackLayout

  private var listContainer: Composite = _
  private var selectorContainer: Composite = _
  private var selector: CardSelectorTableViewer = _

  this.contains(
    fillLayout(),
    sashForm (
      fillLayout(),
      *[Composite](SWT.BORDER) (
        fillLayout(),
        x => new RendererPane(x, editorState)
      ),
      composite (
        _.setLayout(stack),
        *[Composite](SWT.BORDER) (
          fillLayout(),
          x => selector = new CardSelectorTableViewer(x, editorState),
          selectorContainer = _
        ),
        listContainer = _
      ),
      _.setWeights(Array(1000, 1618))
    ),
    UIUtils.listBackgroundStyle.apply
  )

  stack.topControl = selectorContainer

  private var currentEditor: Option[Control] = None
  private def clearCurrentEditor() = {
    currentEditor match {
      case Some(x) => x.dispose()
      case None =>
    }
    currentEditor = None
    stack.topControl = selectorContainer
  }

  private var editorActive = false
  def isEditorActive = editorActive
  private def activatePane(pane: => Control) = {
    editorActive = true
    clearCurrentEditor()
    selector.editorOpened()
    val newPane = pane
    currentEditor = Some(newPane)
    stack.topControl = newPane
    listContainer.layout()
    newPane.forceFocus()
    editorState.ctx.asyncUiExec { newPane.traverse(SWT.TRAVERSE_TAB_NEXT) }
  }
  def activateEditor(cardData: FullCardData) =
    activatePane(new DataRootEditorPane(listContainer, editorState, cardData.cardData.root))
  def activatePoolDataEditor() =
    activatePane(new DataRootEditorPane(listContainer, editorState, editorState.currentPool.now.info.root))
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

  override val tabName = Rx { editorState.currentPool().name() }
}
object EditorTab {
  private implicit val editorTabDataFormats = Json.format[EditorTabData]
  val id = new TabID[EditorTabData, EditorTab, EditorAPI](UUID.fromString("64a35118-343a-11e7-956d-3afa38669cf4")) {
    override def extractData(tab: EditorTab): EditorAPI = tab.editorState
  }
}

class EditorTabProvider extends TabProvider {
  tabId(EditorTab.id)((parent, data, state) => new EditorTab(parent, data, state))
}