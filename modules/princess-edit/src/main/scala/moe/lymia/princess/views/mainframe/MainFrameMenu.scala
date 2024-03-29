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

package moe.lymia.princess.views.mainframe

import moe.lymia.princess.core.cardmodel.StaticViewID
import moe.lymia.princess.views.editor.{EditorTab, EditorTabData}
import moe.lymia.princess.views.frontend.GameSelectorDialog
import org.eclipse.jface.action.{Action, MenuManager}
import org.eclipse.swt.SWT
import org.eclipse.swt.program.Program
import org.eclipse.swt.widgets.{Display, Listener}

// TODO: Support saving to directory
class MainFrameMenu(menu: MenuManager, frame: MainFrame, state: MainFrameState) {
  val file = new MenuManager()
  file.setMenuText(state.i18n.system("_princess.main.menu.file"))

  private val newAction = new Action() {
    setAccelerator(SWT.CTRL | 'N')
    setText(state.i18n.system("_princess.main.menu.file.new"))
    override def run() = new GameSelectorDialog(frame, state.ctx).open()
    file.add(this)
  }
  private val openAction = new Action() {
    setAccelerator(SWT.CTRL | 'O')
    setText(state.i18n.system("_princess.main.menu.file.open"))
    override def run() = MainFrame.showOpenDialog(frame, state.ctx)
    file.add(this)
  }
  private val saveAction = new Action() {
    setAccelerator(SWT.CTRL | 'S')
    setText(state.i18n.system("_princess.main.menu.file.save"))
    override def run() = state.save()
    file.add(this)
  }
  private val saveAsAction = new Action() {
    SWT.getPlatform match {
      case "win32" => setAccelerator(SWT.F12) // windows
      case _ => setAccelerator(SWT.CTRL | SWT.SHIFT | 'S') // mac os x, linux
    }
    setText(state.i18n.system("_princess.main.menu.file.saveAs"))
    override def run() = state.saveAs()
    file.add(this)
  }

  val views = new MenuManager()
  views.setMenuText(state.i18n.system("_princess.main.menu.views"))

  private val createView = new Action() {
    setText(state.i18n.system("_princess.main.menu.views.createView"))
    override def run() = {
      val (uuid, _) = state.ctx.syncLuaExec(state.project.views.create())
      state.openTab(EditorTab, EditorTabData(uuid))
    }
    views.add(this)
  }
  private val allCardsView = new Action() {
    setText(state.i18n.system("_princess.main.menu.views.allCardsView"))
    override def run() = state.openTab(EditorTab, EditorTabData(StaticViewID.AllCards))
    views.add(this)
  }
  private val deletedCardsView = new Action() {
    setText(state.i18n.system("_princess.main.menu.views.deletedCardsView"))
    override def run() = state.openTab(EditorTab, EditorTabData(StaticViewID.DeletedCards))
    views.add(this)
  }

  val help = new MenuManager()
  help.setMenuText(state.i18n.system("_princess.main.menu.help"))

  private val website = new Action() {
    setText(state.i18n.system("_princess.main.menu.help.website"))
    override def run() = Program.launch("https://github.com/Lymia/PrincessEdit")
    help.add(this)
  }
  private val reportBug = new Action() {
    setText(state.i18n.system("_princess.main.menu.help.reportBug"))
    override def run() = Program.launch("https://github.com/Lymia/PrincessEdit/issues")
    help.add(this)
  }
  private val about = new Action() {
    setText(state.i18n.system("_princess.main.menu.help.about"))
    override def run() = new AboutDialog(frame, state).open()
    help.add(this)
  }

  private val display = Display.getCurrent
  private val filter: Listener = event =>
    if(frame.getShell.isDisposed) display.removeFilter(SWT.KeyDown, filter)
    else if(display.getActiveShell eq frame.getShell) {
      val ctrl = event.stateMask == SWT.CTRL

      event.doit = false
      if     (ctrl && event.keyCode == 'n') newAction.run()
      else if(ctrl && event.keyCode == 'o') openAction.run()
      else if(ctrl && event.keyCode == 's') saveAction.run()
      else if(event.keyCode == SWT.F12 ||
              (event.stateMask == SWT.ALT && event.keyCode == SWT.F2) ||
        (event.stateMask == (SWT.CTRL | SWT.SHIFT) && event.keyCode == 's')) saveAsAction.run()
      else event.doit = true
    }
  display.addFilter(SWT.KeyDown, filter)

  def updateMenu(): Unit = {
    menu.removeAll()
    menu.add(file)
    frame.tabFolder.currentTab.now.foreach(_.addMenuItems(menu))
    menu.add(views)
    menu.add(help)
    menu.update(true)
  }
}
