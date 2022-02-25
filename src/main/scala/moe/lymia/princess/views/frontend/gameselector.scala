/*
 * Copyright (c) 2017-2022 Lymia Alusyia <lymia@lymiahugs.com>
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

package moe.lymia.princess.views.frontend

import com.coconut_palm_software.xscalawt.XScalaWT._
import moe.lymia.princess.core.state.ControlContext
import moe.lymia.princess.core.gamedata.{GameId, GameIdLoader}
import moe.lymia.princess.gui.utils.{DialogBase, UIUtils}
import moe.lymia.princess.views.mainframe.{MainFrame, ProjectSource}
import org.eclipse.jface.viewers.{ITreeContentProvider, LabelProvider}
import org.eclipse.jface.window.Window
import org.eclipse.nebula.jface.galleryviewer.GalleryTreeViewer
import org.eclipse.nebula.widgets.gallery.NoGroupRenderer
import org.eclipse.swt.SWT
import org.eclipse.swt.events.SelectionEvent
import org.eclipse.swt.graphics.{Image, Point}
import org.eclipse.swt.layout.{FillLayout, GridData}
import org.eclipse.swt.widgets.{Composite, Shell}

import scala.collection.mutable

// TODO: Render icons only when needed
private final class GameSelectorViewer(parent: Composite, dialog: GameSelectorDialog,
                                       ctx: ControlContext) extends Composite(parent, SWT.BORDER) {
  private val iconSize = 175

  this.setLayout(new FillLayout())

  private val manager = GameIdLoader.default

  private val resources = ctx.newResourceManager()
  private val images = new mutable.HashMap[String, Image]
  private val noIconImage =
    resources.createImageFromSVG(UIUtils.loadSVGFromResource("res/no-icon.svg"), iconSize, iconSize)
  private def getIcon(path: String) =
    images.getOrElseUpdate(path, resources.createImageFromSVG(UIUtils.loadSVGFromPath(
      manager.gameIdManager.forceResolve(path)), iconSize, iconSize))

  private val gallery = new GalleryTreeViewer(this, SWT.V_SCROLL)
  private val renderer = new NoGroupRenderer
  renderer.setItemSize(iconSize, iconSize)
  renderer.setExpanded(true)
  gallery.getGallery.setGroupRenderer(renderer)

  gallery.setContentProvider(new ITreeContentProvider {
    override def getParent(o: scala.Any): AnyRef = null
    override def hasChildren(o: scala.Any): Boolean = o == this
    override def getChildren(o: scala.Any): Array[AnyRef] = manager.gameIdList.toArray
    override def getElements(o: scala.Any): Array[AnyRef] = Array(this)
  })
  gallery.setLabelProvider(new LabelProvider {
    override def getText(element: scala.Any): String = element match {
      case id: GameId => manager.gameIdI18N(id.displayName)
      case _ => ""
    }
    override def getImage(element: scala.Any): Image = element match {
      case id: GameId =>
        id.iconPath.fold(noIconImage)(getIcon)
      case _ => null
    }
  })
  gallery.setInput(this)

  def getGameID: Option[GameId] =
    gallery.getStructuredSelection.getPaths.headOption.toSeq
      .flatMap(x => (0 until x.getSegmentCount).map(x.getSegment))
      .find(_.isInstanceOf[GameId]).map(_.asInstanceOf[GameId])

  gallery.addDoubleClickListener { event => dialog.newProject() }

  addDisposeListener(_ => resources.dispose())
}

final class GameSelectorDialog(parent: Window, ctx: ControlContext, closeParent: Boolean = false)
  extends DialogBase(parent, ctx) {

  setShellStyle(getShellStyle | SWT.RESIZE)

  override def configureShell(shell: Shell): Unit = {
    super.configureShell(shell)
    shell.setText(GameIdLoader.systemI18N.system("_princess.gameSelector.title"))
  }

  override def getInitialSize: Point = new Point(800, 600)

  def newProject() = {
    selector.getGameID match {
      case Some(x) =>
        new MainFrame(ctx, ProjectSource.NewProject(x)).open()
        close()
        if(closeParent) parent.close()
      case None =>
        UIUtils.openMessage(this, SWT.ICON_ERROR | SWT.OK,
                            GameIdLoader.systemI18N, "_princess.gameSelector.noGameSelected")
    }
  }

  private var selector: GameSelectorViewer = _
  override protected def frameContents(frame: Composite): Unit = frame.contains(
    gridLayout()(),
    *[GameSelectorViewer](this, ctx) (
      selector = _,
      _.layoutData = new GridData(SWT.FILL, SWT.FILL, true, true)
    ),
    button(
      GameIdLoader.systemI18N.system("_princess.gameSelector.create"),
      (event: SelectionEvent) => newProject(),
      _.layoutData = new GridData(SWT.END, SWT.CENTER, false, false)
    )
  )
}