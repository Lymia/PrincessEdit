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

package moe.lymia.princess.editor.ui

import java.util.UUID

import moe.lymia.lua._
import moe.lymia.princess.core.{GameManager, PackageManager}
import moe.lymia.princess.editor.core._
import moe.lymia.princess.editor.lua.EditorModule
import moe.lymia.princess.editor.utils._
import moe.lymia.princess.renderer.lua.RenderModule
import moe.lymia.princess.renderer.{RasterizeResourceLoader, RenderManager}
import moe.lymia.princess.util.VersionInfo

import rx._

import org.eclipse.swt.SWT
import org.eclipse.swt.custom.SashForm
import org.eclipse.swt.graphics.{Image, Point}
import org.eclipse.swt.layout._
import org.eclipse.swt.widgets._

class MainFrameState(project: Project) {
  val currentPool = Var[CardSource](project)
  val currentCard = Var[Option[UUID]](None)
}

class RendererPane(parent: Composite, ctx: ControlContext, game: GameManager,
                   state: MainFrameState, project: Project) extends Composite(parent, SWT.NONE) {
  import rx.Ctx.Owner.Unsafe._

  private val renderer = new RenderManager(game, ctx.cache)

  private val currentCardData: Rx[Seq[LuaObject]] = Rx {
    val card = state.currentCard().flatMap(project.cards.now.get).map(_.root.luaData())
    val root = state.currentPool().info.root.luaData()
    Seq(card, root)
  }

  val grid = new GridLayout()
  this.setLayout(grid)

  private val label = new Label(this, SWT.NONE)
  label.setLayoutData(new GridData(SWT.CENTER, SWT.BEGINNING, false, false))

  private val obs = currentCardData.foreach { d =>
    ctx.asyncRenderSwt (this, {
      val (componentSize, rendered) = ctx.syncLuaUiExec(this.getSize, renderer.render(d, RasterizeResourceLoader))

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
      ctx.asyncUiExec {
        val image = new Image(ctx.display, imageData)
        if(label.getImage != null) label.getImage.dispose()
        label.setImage(image)
        this.layout(Array(label : Control))
      }
    }
  }

  this.addListener(SWT.Resize, { event =>
    obs.thunk()
  })
}

// TODO: This is only a temporary test UI
class MainFrame(ctx: ControlContext, gameId: String) extends WindowBase(ctx) {
  private val game = PackageManager.default.loadGameId(gameId)
  game.lua.loadModule(EditorModule, RenderModule)

  private val idData = new GameIDData(game, ctx)
  private val project = new Project(idData)
  private val cardId = project.newCard()

  private val state = new MainFrameState(project)
  state.currentCard.update(Some(cardId))
  private val cardData = project.cards.now(cardId)

  override def configureShell(shell: Shell): Unit = {
    super.configureShell(shell)
    shell.setText(s"PrincessEdit v${VersionInfo.versionString}")
  }

  override def getInitialSize: Point = new Point(800, 600)

  override def frameContents(frame: Composite) = {
    val fill = new FillLayout
    fill.marginHeight = 5
    fill.marginWidth = 5
    frame.setLayout(fill)

    val sash = new SashForm(frame, SWT.HORIZONTAL | SWT.SMOOTH)
    sash.setLayout(new FillLayout)

    val sash1 = new Composite(sash, SWT.BORDER)
    sash1.setLayout(new FillLayout)
    val renderer = new RendererPane(sash1, ctx, game, state, project)

    val sash2 = new Composite(sash, SWT.BORDER)
    sash2.setLayout(new FillLayout)
    val ui = cardData.root.createUI(sash2)

    sash.setWeights(Array(1000, 1618))
  }
}