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

package moe.lymia.princess.editor.dialogs

import moe.lymia.princess.core.PackageManager
import moe.lymia.princess.editor.core._
import moe.lymia.princess.editor.lua.EditorModule
import moe.lymia.princess.editor.utils.WindowBase
import moe.lymia.princess.renderer.lua.RenderModule
import moe.lymia.princess.renderer.{RasterizeResourceLoader, RenderManager}
import org.eclipse.nebula.widgets.pgroup._
import org.eclipse.swt.SWT
import org.eclipse.swt.graphics.{Image, Point}
import org.eclipse.swt.layout._
import org.eclipse.swt.widgets._
import play.api.libs.json._

// TODO: This is only a temporary test UI
class MainFrame(ctx: ControlContext, gameId: String) extends WindowBase(ctx) {
  private val game = PackageManager.default.loadGameId(gameId)
  game.lua.loadModule(EditorModule, RenderModule)

  private val idData = new GameIDData(game, ctx)
  private val cardData = new CardData(idData)
  private val renderer = new RenderManager(game, ctx.cache)

  override def configureShell(shell: Shell): Unit = {
    super.configureShell(shell)
    shell.setText("PrincessEdit SWT Test")
  }

  override def getInitialSize: Point = new Point(800, 600)

  override def frameContents(frame: Composite) = {
    val layout = new GridLayout
    frame.setLayout(layout)
    layout.numColumns = 2

    val label = new Label(frame, SWT.NONE)
    label.setLayoutData(new GridData(SWT.CENTER, SWT.BEGINNING, false, false))

    val titled = new PGroup(frame, SWT.SMOOTH)
    titled.setToggleRenderer(new TreeNodeToggleRenderer)
    titled.setStrategy(new SimpleGroupStrategy)
    titled.setLayout(new FillLayout)
    cardData.root.createUI(titled)
    titled.setText("Card data")
    titled.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true))
    titled.layout(true)

    import rx.Ctx.Owner.Unsafe._
    val obs = cardData.root.luaData.foreach { d =>
      println(Json.prettyPrint(cardData.serialize))
      ctx.asyncRenderSwt(renderer.render(d, RasterizeResourceLoader), 250, 350) { imageData =>
        ctx.asyncUiExec {
          val image = new Image(ctx.display, imageData)
          if(label.getImage != null) label.getImage.dispose()
          label.setImage(image)
          frame.layout(true)
        }
      }
    }
  }
}