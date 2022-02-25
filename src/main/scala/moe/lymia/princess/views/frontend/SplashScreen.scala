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
import moe.lymia.princess.core.gamedata.GameIdLoader
import moe.lymia.princess.core.state.GuiContext
import moe.lymia.princess.gui.utils.WindowBase
import moe.lymia.princess.views.mainframe.MainFrame
import org.eclipse.swt.events.SelectionEvent
import org.eclipse.swt.widgets._

class SplashScreen(ctx: GuiContext) extends WindowBase(ctx) {
  override def configureShell(shell: Shell): Unit = {
    super.configureShell(shell)
    shell.setText(GameIdLoader.systemI18N.system("_princess.frontend.title"))
  }

  override def frameContents(frame: Composite) = {
    frame.contains(
      gridLayout()(),
      button(
        "New Project", // TODO: I18N
        (event: SelectionEvent) => new GameSelectorDialog(this, ctx, true).open()
      ),
      button(
        "Load Project",
        (event: SelectionEvent) => MainFrame.showOpenDialog(this, ctx, true)
      )
    )
  }
}