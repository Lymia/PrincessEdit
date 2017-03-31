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
import moe.lymia.princess.renderer._
import org.eclipse.jface.action.{Action, MenuManager}
import org.eclipse.swt.SWT
import org.eclipse.swt.events.{MouseEvent, MouseListener}
import org.eclipse.swt.graphics.Image
import org.eclipse.swt.layout._
import org.eclipse.swt.widgets._
import rx._

class RendererPane(parent: Composite, state: EditorState) extends Composite(parent, SWT.NONE) {
  import Ctx.Owner.Unsafe._

  val grid = new GridLayout()
  this.setLayout(grid)

  private val render = label(
    _.layoutData = new GridData(SWT.CENTER, SWT.BEGINNING, false, false)
  )(this)

  private val currentCardData: Rx[Option[Seq[LuaObject]]] = Rx {
    val card = state.currentCard().flatMap(state.project.cards.now.get).map(_.root.luaData())
    val pool = state.currentPool().info.root.luaData()
    card.map(cardData => Seq(cardData, pool))
  }
  private val obs = currentCardData.foreach { d =>
    if(!this.isDisposed) d match {
      case Some(data) =>
        state.ctx.asyncRenderSwt (this, {
          val (componentSize, rendered) =
            state.ctx.syncUiLuaExec(this.getSize, state.idData.renderer.render(data, RasterizeResourceLoader))

          val (rcx, rcy) =
            (componentSize.x - grid.marginLeft - grid.marginRight - grid.marginWidth * 2,
             componentSize.y - grid.marginTop - grid.marginBottom - grid.marginHeight * 2)
          val (cx, cy) = (if(rcx == 0) 1 else rcx, if(rcy == 0) 1 else rcy)
          val (x, y) = {
            val size =
              (cx, math.round((cx.toDouble / rendered.size.width) * rendered.size.height).toInt)
            if(size._2 > cy)
              (math.round((cy.toDouble / rendered.size.height) * rendered.size.width).toInt, cy)
            else size
          }

          (rendered, x, y)
        }) { imageData =>
          state.ctx.asyncUiExec {
            val image = new Image(state.ctx.display, imageData)
            if(render.getImage != null) render.getImage.dispose()
            render.setText("")
            render.setImage(image)
            this.layout(true)
          }
        }
      case None =>
        state.ctx.asyncUiExec {
          if(render.getImage != null) render.getImage.dispose()
          render.setText(state.i18n.system("_princess.editor.noSelection"))
          render.setImage(null)
          this.layout(true)
        }
    }
  }

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
  render.setMenu(menuManager.createContextMenu(render))

  render.addMouseListener(new MouseListener {
    override def mouseDown(mouseEvent: MouseEvent): Unit =
      if(!state.isEditorActive && mouseEvent.button == 1) state.activateEditor()
    override def mouseDoubleClick(mouseEvent: MouseEvent): Unit = { }
    override def mouseUp(mouseEvent: MouseEvent): Unit = { }
  })
  this.addListener(SWT.Dispose, { event =>
    currentCardData.kill()
    obs.kill()
  })
  this.addListener(SWT.Resize, { event =>
    obs.thunk()
  })
}