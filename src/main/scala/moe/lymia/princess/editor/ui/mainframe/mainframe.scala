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

import java.nio.file.{FileSystems, Files, Path, Paths}
import java.util.UUID

import com.coconut_palm_software.xscalawt.XScalaWT._
import moe.lymia.princess.PrincessEdit
import moe.lymia.princess.core.{GameID, I18NLoader, PackageManager}
import moe.lymia.princess.editor.core._
import moe.lymia.princess.editor.lua.EditorModule
import moe.lymia.princess.editor.project.{CardSource, Project}
import moe.lymia.princess.editor.ui.editor.EditorPane
import moe.lymia.princess.editor.utils._
import moe.lymia.princess.renderer.lua.RenderModule
import moe.lymia.princess.util.{FileLock, IOUtils, VersionInfo}
import org.eclipse.jface.action.{Action, MenuManager}
import org.eclipse.jface.window.{IShellProvider, Window}
import org.eclipse.swt.SWT
import org.eclipse.swt.graphics.{Image, Point}
import org.eclipse.swt.layout._
import org.eclipse.swt.widgets._
import rx._

final class MainFrameState(mainFrame: MainFrame, val ctx: ControlContext, projectSource: ProjectSource) {
  val gameId = projectSource.getGameID
  val game = PackageManager.default.loadGameId(gameId)
  val i18n = new I18NLoader(game).i18n
  game.lua.loadModule(EditorModule(i18n), RenderModule)

  val idData = new GameIDData(game, ctx, i18n)
  val project = projectSource.openProject(ctx, gameId, idData)

  val currentPool = Var[CardSource](project)

  private var currentSaveLocation: Option[Path] = None
  private var lock: Option[FileLock] = None
  def getSaveLocation = currentSaveLocation
  def setSaveLocation(path: Option[Path], preLock: Option[FileLock] = None) = {
    path match {
      case Some(currentPath) =>
        preLock.orElse(MainFrame.lockFile(currentPath)) match {
          case Some(x) =>
            lock.foreach(_.release())
            lock = Some(x)
            currentSaveLocation = Some(currentPath)
            true
          case None =>
            UIUtils.openMessage(mainFrame, SWT.ICON_ERROR | SWT.OK, i18n, "_princess.main.projectLocked")
            false
        }
      case None =>
        currentSaveLocation = None
        lock.foreach(_.release())
        true
    }
  }
  projectSource.setSaveLocation(this)

  val shell: IShellProvider = mainFrame

  def dispose() = {
    currentPool.kill()
  }
}

trait PrincessEditTab { this: Control =>
  def addMenuItems(m: MenuManager)

  def tabText (): String = ""
  def tabImage(): Image  = null
}

sealed trait ProjectSource {
  def getGameID: String
  def openProject(ctx: ControlContext, gameID: String, idData: GameIDData): Project
  def setSaveLocation(state: MainFrameState): Unit
}
object ProjectSource {
  // TODO: Throw an error if the given GameID isn't installed
  case class OpenProject(path: Path, lock: FileLock) extends ProjectSource {
    override def getGameID: String = Project.getProjectGameID(path)
    override def openProject(ctx: ControlContext, gameID: String, idData: GameIDData): Project =
      Project.loadProject(ctx, gameID, idData, path)
    override def setSaveLocation(state: MainFrameState) = state.setSaveLocation(Some(path), Some(lock))
  }
  case class NewProject(id: GameID) extends ProjectSource {
    override def getGameID: String = id.name
    override def openProject(ctx: ControlContext, gameID: String, idData: GameIDData): Project =
      new Project(ctx, gameID, idData)
    override def setSaveLocation(state: MainFrameState) = state.setSaveLocation(None, None)
  }
}

case class TabData(tab: PrincessEditTab)
final class MainFrame(ctx: ControlContext, projectSource: ProjectSource) extends WindowBase(ctx) {
  private val state = new MainFrameState(this, ctx, projectSource)

  override def configureShell(shell: Shell): Unit = {
    super.configureShell(shell)
    shell.setText(s"PrincessEdit v${VersionInfo.versionString}")
  }

  var currentTab: PrincessEditTab = _

  var menu: MainFrameMenu = _
  override def createMenuManager: MenuManager = {
    val manager = super.createMenuManager()
    menu = new MainFrameMenu(manager, this, state)
    menu.updateMenu()
    manager
  }
  this.addMenuBar()

  override def getInitialSize: Point = new Point(800, 600)

  override def frameContents(frame: Composite) = {
    val fill = new FillLayout
    fill.marginHeight = 1
    fill.marginWidth = 1
    frame.setLayout(fill)

    currentTab = new EditorPane(frame, state)
  }
}
object MainFrame {
  private[mainframe] def lockFile(path: Path) = {
    val lockPath = IOUtils.lock(IOUtils.mapFileName(path, "." + _ + ".lock"))
    if(!Files.exists(path)) sys.error("can't lock file that doesn't exist")
    IOUtils.lock(path)
  }

  def showOpenDialog(parent: Window, ctx: ControlContext, closeOnAccept: Boolean = false) = {
    val selector = new FileDialog(parent.getShell, SWT.OPEN)
    selector.setFilterNames(Array(PackageManager.systemI18N.system("_princess.main.project")))
    selector.setFilterExtensions(Array("*.pedit-project"))
    selector.setFilterPath(Paths.get(".").toAbsolutePath.toString)
    selector.open() match {
      case null =>
      case target =>
        val path = Paths.get(target)
        lockFile(path) match {
          case Some(lock) =>
            new MainFrame(ctx, ProjectSource.OpenProject(path, lock)).open()
            if(closeOnAccept) parent.close()
          case None =>
            UIUtils.openMessage(parent, SWT.ICON_ERROR | SWT.OK,
                                PackageManager.systemI18N, "_princess.main.projectLocked")
        }
    }
  }
}