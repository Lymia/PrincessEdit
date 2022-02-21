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

package moe.lymia.princess.gui.ui.frontend

import com.coconut_palm_software.xscalawt.XScalaWT._
import moe.lymia.princess.core.packages.PackageManager
import moe.lymia.princess.gui.ControlContext
import moe.lymia.princess.gui.utils.DialogBase
import org.eclipse.jface.window.Window
import org.eclipse.swt.SWT
import org.eclipse.swt.graphics.Point
import org.eclipse.swt.widgets.{Composite, Shell}

final class InkscapeSelectorDialog(parent: Window, ctx: ControlContext, closeParent: Boolean = false)
  extends DialogBase(parent, ctx) {

  setShellStyle(getShellStyle | SWT.RESIZE)

  override def configureShell(shell: Shell): Unit = {
    super.configureShell(shell)
    shell.setText(PackageManager.systemI18N.system("_princess.gameSelector.title"))
  }

  override def getInitialSize: Point = new Point(800, 600)

  override protected def frameContents(frame: Composite): Unit = frame.contains(

  )
}