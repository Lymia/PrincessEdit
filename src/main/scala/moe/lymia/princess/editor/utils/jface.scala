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

package moe.lymia.princess.editor.utils

import moe.lymia.princess.editor.ControlContext
import org.eclipse.jface.dialogs.Dialog
import org.eclipse.jface.window.{ApplicationWindow, IShellProvider, Window}
import org.eclipse.swt.SWT
import org.eclipse.swt.layout.FillLayout
import org.eclipse.swt.widgets.{Composite, Control, Shell}

trait JFaceWindowBase extends Window {
  protected val ctx: ControlContext

  ctx.wm.add(this)

  protected def frameContents(frame: Composite)

  override def configureShell(newShell: Shell): Unit = {
    super.configureShell(newShell)
    newShell.setImages(ctx.resources.loadIcon(IconData.AppIcon))
    // TODO: Manage shell title
  }

  override protected final def createContents(parent: Composite): Control = {
    parent.setLayout(new FillLayout())

    val contents = new Composite(parent, SWT.NONE)
    frameContents(contents)
    contents
  }
}

trait WithResources extends JFaceWindowBase {
  val resourceManager = ctx.newResourceManager()

  override def configureShell(newShell: Shell): Unit = {
    super.configureShell(newShell)
    resourceManager.dispose()
  }
}

abstract class WindowBase(protected val ctx: ControlContext) extends ApplicationWindow(null) with JFaceWindowBase {
  override def showTopSeperator(): Boolean = false
}

abstract class DialogBase(parent: IShellProvider, protected val ctx: ControlContext)
  extends Dialog(if(parent == null) null else parent.getShell) with JFaceWindowBase
