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

import java.nio.file.Paths

import moe.lymia.princess.editor.core._
import moe.lymia.princess.editor.ui.frontend.SplashScreen
import moe.lymia.princess.editor.ui.mainframe.MainFrame
import moe.lymia.princess.rasterizer._

class UIMain {
  private var loadFile: Option[String] = None
  trait ScoptArgs { this: scopt.OptionParser[Unit] =>
    def uiOptions() = {
      arg[String]("<project.pedit-project>").foreach(x => loadFile = Some(x)).hidden().optional()
    }
  }
  def main() = {
    val plaf = InkscapePlatform.instance
    val manager = new UIManager(plaf.locateBinary().head.createFactory())
    manager.mainLoop { ctx =>
      loadFile match {
        case Some(x) =>
          MainFrame.loadProject(null, ctx, Paths.get(x))
        case None =>
          new SplashScreen(ctx).open()
      }
    }
  }
}
