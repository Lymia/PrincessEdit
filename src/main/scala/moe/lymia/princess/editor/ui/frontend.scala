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

import moe.lymia.princess.core.PackageManager
import moe.lymia.princess.editor.core._
import moe.lymia.princess.editor.nodes._

import org.eclipse.swt.SWT
import org.eclipse.swt.layout._
import org.eclipse.swt.widgets._

class FrontEndFrame(manager: UIManager, controlCtx: ControlContext) {
  val shell = controlCtx.newShell()

  shell.setText("PrincessEdit Game Selection")

  val layout = new GridLayout
  shell.setLayout(layout)
  layout.numColumns = 2

  val label = new Label(shell, SWT.NONE)
  label.setText("Game")
  label.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false))

  val combo = new Combo(shell, SWT.BORDER | SWT.READ_ONLY)
  combo.setItems(PackageManager.default.gameIDs.keys.toSeq : _*)
  combo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false))

  val button = new Button(shell, SWT.PUSH)
  button.setText("Start Editor Test")
  val data = new GridData(SWT.END, SWT.CENTER, false, false)
  data.horizontalSpan = 2
  button.setLayoutData(data)

  button.addListener(SWT.Selection, event => {
    new MainFrame(manager, controlCtx, combo.getItem(combo.getSelectionIndex))
    shell.dispose()
  })

  shell.pack()
  shell.setSize(math.max(shell.getSize.x, 500), shell.getSize.y)
  shell.setVisible(true)
}

object FrontEnd {

}