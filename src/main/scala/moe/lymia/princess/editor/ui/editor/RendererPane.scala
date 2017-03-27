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

import moe.lymia.lua._
import moe.lymia.princess.renderer._

import org.eclipse.swt.SWT
import org.eclipse.swt.graphics.Image
import org.eclipse.swt.layout._
import org.eclipse.swt.widgets._

import rx._

class RendererPane(parent: Composite, state: EditorState) extends Composite(parent, SWT.NONE) {
  import Ctx.Owner.Unsafe._

  private val renderer = new RenderManager(state.game, state.ctx.cache)

  val grid = new GridLayout()
  this.setLayout(grid)

  private val label = new Label(this, SWT.NONE)
  label.setLayoutData(new GridData(SWT.CENTER, SWT.BEGINNING, false, false))

  private val currentCardData: Rx[Option[Seq[LuaObject]]] = Rx {
    val card = state.currentCard().flatMap(state.project.cards.now.get).map(_.root.luaData())
    val root = state.currentPool().info.root.luaData()
    card.map(cardData => Seq(cardData, root))
  }
  private val obs = currentCardData.foreach { d =>
    if(!this.isDisposed) d match {
      case Some(data) =>
        state.ctx.asyncRenderSwt (this, {
          val (componentSize, rendered) =
            state.ctx.syncUiLuaExec(this.getSize, renderer.render(data, RasterizeResourceLoader))

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
            if(label.getImage != null) label.getImage.dispose()
            label.setText("")
            label.setImage(image)
            this.layout(true)
          }
        }
      case None =>
        state.ctx.asyncUiExec {
          if(label.getImage != null) label.getImage.dispose()
          label.setText(state.i18n.system("_princess.editor.noSelection"))
          label.setImage(null)
          this.layout(true)
        }
    }
  }

  this.addListener(SWT.Dispose, { event =>
    currentCardData.kill()
    obs.kill()
  })
  this.addListener(SWT.Resize, { event =>
    obs.thunk()
  })
}