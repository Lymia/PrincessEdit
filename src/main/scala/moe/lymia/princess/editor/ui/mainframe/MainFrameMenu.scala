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

import java.nio.file.Paths
import java.util.UUID

import moe.lymia.princess.editor.ui.frontend.GameSelectorDialog
import moe.lymia.princess.util.IOUtils
import org.eclipse.jface.action.{Action, MenuManager}
import org.eclipse.swt.SWT
import org.eclipse.swt.widgets.FileDialog

// TODO: Support saving to directory
class MainFrameMenu(menu: MenuManager, frame: MainFrame, state: MainFrameState) {
  val file = new MenuManager()
  file.setMenuText(state.i18n.system("_princess.main.menu.file"))

  def chooseSaveLocation() = {
    val selector = new FileDialog(frame.getShell, SWT.SAVE)
    selector.setFileName("New Project.pedit-project")
    selector.setFilterNames(Array(state.i18n.system("_princess.main.project")))
    selector.setFilterExtensions(Array("*.pedit-project"))
    selector.setFilterPath(Paths.get(".").toAbsolutePath.toString)
    selector.setOverwrite(true)
    selector.open() match {
      case null => false
      case target =>
        state.setSaveLocation(Some(Paths.get(target)))
        true
    }
  }
  def doSave() = {
    val fs = IOUtils.openZip(state.getSaveLocation.get, create = true)
    try state.project.writeTo(fs.getPath("/")) finally fs.close()
  }

  private val newAction = new Action() {
    setText(state.i18n.system("_princess.main.menu.file.new"))
    override def run() = new GameSelectorDialog(frame, state.ctx).open()
    file.add(this)
  }
  private val openAction = new Action() {
    setText(state.i18n.system("_princess.main.menu.file.open"))
    override def run() = MainFrame.showOpenDialog(frame, state.ctx)
    file.add(this)
  }
  private val saveAction = new Action() {
    setText(state.i18n.system("_princess.main.menu.file.save"))
    override def run() = if(state.getSaveLocation.isDefined || chooseSaveLocation()) doSave()
    file.add(this)
  }
  private val saveAsAction = new Action() {
    setText(state.i18n.system("_princess.main.menu.file.saveAs"))
    override def run() = if(chooseSaveLocation()) doSave()
    file.add(this)
  }

  def updateMenu(): Unit = {
    menu.removeAll()
    menu.add(file)
    if(frame.currentTab != null) frame.currentTab.addMenuItems(menu)
  }
}
