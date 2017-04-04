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

import com.coconut_palm_software.xscalawt.XScalaWT._
import moe.lymia.lua._
import moe.lymia.princess.editor.ui.export.ExportCardsDialog
import moe.lymia.princess.editor.utils.RxWidget
import moe.lymia.princess.renderer._
import org.eclipse.jface.action.{Action, MenuManager}
import org.eclipse.swt.SWT
import org.eclipse.swt.events.{MouseEvent, MouseListener}
import org.eclipse.swt.graphics.{GC, Image, Point}
import org.eclipse.swt.layout._
import org.eclipse.swt.widgets._
import rx._

final class CardSelectorCanvas(parent: Composite, state: EditorState) extends Canvas(parent, SWT.NONE) with RxWidget {
  private def computeSizeFromRatio(canvasSize: Point, width: Double, height: Double) = {
    val (cx, cy) = (if(canvasSize.x == 0) 1 else canvasSize.x, if(canvasSize.y == 0) 1 else canvasSize.y)
    val size = (cx, math.round((cx.toDouble / width) * height).toInt)
    if(size._2 > cy) (math.round((cy.toDouble / height) * width).toInt, cy)
    else size
  }

  private var currentImage: Image = _
  private val currentCardData: Rx[Option[Seq[LuaObject]]] = Rx {
    val card = state.currentCardData().map(_.root.luaData())
    val pool = state.currentPool().info.root.luaData()
    card.map(cardData => Seq(cardData, pool))
  }
  private val obs = currentCardData.foreach { d =>
    if(!this.isDisposed) d match {
      case Some(data) =>
        state.ctx.asyncRenderSwt (this, {
          val (componentSize, rendered) =
            state.ctx.syncUiLuaExec(this.getSize, state.idData.renderer.render(data, RasterizeResourceLoader))
          val (x, y) = computeSizeFromRatio(componentSize, rendered.size.width, rendered.size.height)
          (rendered, x, y)
        }) { imageData =>
          state.ctx.asyncUiExec {
            val image = new Image(state.ctx.display, imageData)
            if(currentImage ne null) currentImage.dispose()
            currentImage = image
          this.redraw()
          }
        }
      case None =>
        state.ctx.asyncUiExec {
          if(currentImage ne null) currentImage.dispose()
          currentImage = null
          this.redraw()
        }
    }
  }

  addPaintListener { event =>
    if(currentImage != null) {
      val bounds = currentImage.getBounds
      val (rx, ry) = computeSizeFromRatio(getSize, bounds.width, bounds.height)
      event.gc.drawImage(currentImage, 0, 0, bounds.width, bounds.height, 0, 0, rx, ry)
    }
  }

  this.addListener(SWT.Resize, { event =>
    obs.thunk()
  })
}

class RendererPane(parent: Composite, state: EditorState) extends Composite(parent, SWT.NONE) {
  private val fill = new FillLayout()
  fill.marginWidth = 5
  fill.marginHeight = 5
  this.setLayout(fill)

  private val canvas = new CardSelectorCanvas(this, state)

  private val menuManager = new MenuManager()
  private val export = new Action(state.i18n.system("_princess.editor.exportCard")) {
    override def run() = {
      val id   = state.currentCard.now.get
      val data = state.project.cards.now(id)
      ExportCardsDialog.open(state.source, state, state.currentPool.now, id -> data)
    }
  }
  menuManager.add(export)
  menuManager.addMenuListener(_ => {
    export.setEnabled(state.currentCard.now.isDefined)
  })
  canvas.setMenu(menuManager.createContextMenu(canvas))

  canvas.addMouseListener(new MouseListener {
    override def mouseDown(mouseEvent: MouseEvent): Unit =
      if(!state.isEditorActive && mouseEvent.button == 1) state.activateEditor()
    override def mouseDoubleClick(mouseEvent: MouseEvent): Unit = { }
    override def mouseUp(mouseEvent: MouseEvent): Unit = { }
  })
}