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

package moe.lymia.princess.editor

import org.eclipse.swt._
import org.eclipse.swt.graphics._
import org.eclipse.swt.layout._
import org.eclipse.swt.widgets._
import org.eclipse.nebula.widgets.pgroup._

import moe.lymia.princess.core._
import moe.lymia.princess.editor.core._
import moe.lymia.princess.editor.data._
import moe.lymia.princess.rasterizer._
import moe.lymia.princess.renderer._


import play.api.libs.json.Json

// TODO: This is only a temporary test UI
class UIMain {
  private var gameId: String = _
  trait ScoptArgs { this: scopt.OptionParser[Unit] =>
    def uiOptions() = {
      arg[String]("<gameId>").foreach(gameId = _).hidden()
    }
  }
  def main() = {
    val game = PackageManager.default.loadGameId(gameId)
    val manager = new UIManager(game, new InkscapeConnectionFactory("inkscape"))
    val ep = new LuaNodeSource(game, "card-form", "cardForm")
    val data = new DataStore

    manager.mainLoop { (display, shell, ctx) =>
      shell.setText("PrincessEdit SWT Test")
      shell.setSize(800, 600)

      val layout = new GridLayout
      shell.setLayout(layout)
      layout.numColumns = 2

      val label = new Label(shell, SWT.NONE)
      label.setLayoutData(new GridData(SWT.CENTER, SWT.BEGINNING, false, false))

      val titled = new PGroup(shell, SWT.SMOOTH)
      titled.setToggleRenderer(new TreeNodeToggleRenderer)
      titled.setStrategy(new SimpleGroupStrategy)
      titled.setLayout(new FillLayout)
      val root = ep.createRoot(game.lua.L, titled, data, "card", ctx, Seq())
      titled.setText("Card data")
      titled.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true))
      titled.layout(true)

      import rx.Ctx.Owner.Unsafe._
      val obs = root.luaData.foreach { d =>
        println(Json.prettyPrint(data.serialize))
        ctx.asyncUiExec {
          val image =
            new Image(display, ctx.syncRenderSwt(manager.render.render(d, RasterizeResourceLoader), 250, 350))
          if(label.getImage != null) label.getImage.dispose()
          label.setImage(image)
          shell.layout(true)
        }
      }
    }
  }
}
