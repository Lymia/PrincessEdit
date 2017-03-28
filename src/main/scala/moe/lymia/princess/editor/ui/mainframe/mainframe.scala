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

package moe.lymia.princess.editor.ui.mainframe

import moe.lymia.princess.core.{I18NLoader, PackageManager}
import moe.lymia.princess.editor.core._
import moe.lymia.princess.editor.lua.EditorModule
import moe.lymia.princess.editor.ui.editor.EditorPane
import moe.lymia.princess.editor.utils._
import moe.lymia.princess.renderer.lua.RenderModule
import moe.lymia.princess.util.VersionInfo
import org.eclipse.jface.window.IShellProvider
import org.eclipse.swt.graphics.Point
import org.eclipse.swt.layout._
import org.eclipse.swt.widgets._
import rx._

class MainFrameState(mainFrame: MainFrame, val ctx: ControlContext, val gameId: String) {
  val game = PackageManager.default.loadGameId(gameId)
  val i18n = new I18NLoader(game).i18n
  game.lua.loadModule(EditorModule(i18n), RenderModule)

  val idData = new GameIDData(game, ctx, i18n)
  val project = new Project(ctx, idData)

  val currentPool = Var[CardSource](project)

  def openDialog(fn: IShellProvider => Dialog) = fn(mainFrame)
}

trait EditorTab {

}

// TODO: This is only a temporary test UI
class MainFrame(ctx: ControlContext, gameId: String) extends WindowBase(ctx) {
  private val state = new MainFrameState(this, ctx, gameId)

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

    new EditorPane(frame, state)
  }
}