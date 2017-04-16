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

import java.nio.file.{Files, Path, Paths}

import moe.lymia.princess.core.{GameID, I18NLoader, PackageManager}
import moe.lymia.princess.editor.core._
import moe.lymia.princess.editor.lua.EditorModule
import moe.lymia.princess.editor.project.{CardSource, Project, ProjectMetadata}
import moe.lymia.princess.editor.ui.editor.EditorPane
import moe.lymia.princess.editor.utils._
import moe.lymia.princess.renderer.lua.RenderModule
import moe.lymia.princess.util.{FileLock, IOUtils, VersionInfo}
import org.eclipse.jface.action.MenuManager
import org.eclipse.jface.dialogs.MessageDialog
import org.eclipse.jface.window.{IShellProvider, Window}
import org.eclipse.swt.SWT
import org.eclipse.swt.graphics.{Image, Point}
import org.eclipse.swt.layout._
import org.eclipse.swt.widgets._
import rx._

final case class UnsavedChanges(since: Long)
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
            mainFrame.updateTitle()
            true
          case None =>
            UIUtils.openMessage(mainFrame, SWT.ICON_ERROR | SWT.OK, i18n, "_princess.main.projectLocked")
            false
        }
      case None =>
        currentSaveLocation = None
        mainFrame.updateTitle()
        lock.foreach(_.release())
        true
    }
  }
  projectSource.setSaveLocation(this)

  def getSaveName = currentSaveLocation.fold(i18n.system("_princess.main.untitledProject"))(_.getFileName.toString)

  @volatile private var unsavedChangesExist: Boolean = false
  @volatile private var lastUnsavedChange: Long = 0
  def hasUnsavedChanges = if(unsavedChangesExist) Some(UnsavedChanges(lastUnsavedChange)) else None
  def needsSaving() = {
    if(!unsavedChangesExist) lastUnsavedChange = System.currentTimeMillis()
    unsavedChangesExist = true
    mainFrame.updateTitle()
  }

  private def chooseSaveLocation() = {
    val selector = new FileDialog(mainFrame.getShell, SWT.SAVE)
    selector.setFileName(getSaveName)
    selector.setFilterNames(Array(i18n.system("_princess.main.project")))
    selector.setFilterExtensions(Array("*.pedit-project"))
    selector.setFilterPath(currentSaveLocation.fold(Paths.get(".").toAbsolutePath.toString)
                                                   (_.toAbsolutePath.getParent.toString))
    selector.setOverwrite(true)
    selector.open() match {
      case null => false
      case target =>
        setSaveLocation(Some(Paths.get(target)))
    }
  }
  private def doSave() = {
    val fs = IOUtils.openZip(getSaveLocation.get, create = true)
    try project.writeTo(fs.getPath("/")) finally fs.close()
    unsavedChangesExist = false
    mainFrame.updateTitle()
  }
  def save() = if(getSaveLocation.isDefined || chooseSaveLocation()) {
    doSave()
    true
  } else false
  def saveAs() = if(chooseSaveLocation()) {
    doSave()
    true
  } else false

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
  case class OpenProject(path: Path, meta: ProjectMetadata, lock: FileLock) extends ProjectSource {
    override def getGameID: String = meta.gameId
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
    shell.addListener(SWT.Dispose, event => state.dispose())
    updateTitle(shell)
  }

  def updateTitle(shell: Shell = getShell) =
    if(shell != null)
      shell.setText(s"${if(state.hasUnsavedChanges.isDefined) "*" else ""}${state.getSaveName} - PrincessEdit")

  private val platformButtonOrder = SWT.getPlatform match {
    case "win32" => Array(2, 0, 1)
    case _ => Array(0, 1, 2)
  }
  private val platformReverseOrder = (0 to 2).map(platformButtonOrder.indexOf)
  override def handleShellCloseEvent(): Unit = {
    state.hasUnsavedChanges match {
      case Some(UnsavedChanges(since)) =>
        val timeSpan = System.currentTimeMillis() - since
        val timeName = if(timeSpan > 1000 * 60 * 60)
          state.i18n.system("_princess.main.confirmSave.hours", math.ceil(timeSpan.toDouble / (1000 * 60 * 60)).toLong)
        else
          state.i18n.system("_princess.main.confirmSave.minutes", math.ceil(timeSpan.toDouble / (1000 * 60)).toLong)

        val result = UIUtils.openMessage(this, MessageDialog.WARNING, state.i18n,
          platformButtonOrder.map(Seq(
              "_princess.main.confirmSave.closeWithoutSaving", "_princess.main.confirmSave.cancel",
              if(state.getSaveLocation.isDefined) "_princess.main.confirmSave.save"
              else "_princess.main.confirmSave.saveAs")), platformReverseOrder(2),
          "_princess.main.confirmSave", state.getSaveName, timeName)
        platformButtonOrder(result) match {
          case SWT.DEFAULT | 1 => // cancel
          case 0 => super.handleShellCloseEvent() // close without saving
          case 2 => if(state.save()) super.handleShellCloseEvent()
        }
      case None =>
        super.handleShellCloseEvent()
    }
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
  private[mainframe] def lockFile(path: Path) =
    IOUtils.lock(IOUtils.mapFileName(path, "." + _ + ".lock"))

  def loadProject(parent: Window, ctx: ControlContext, path: Path, closeOnAccept: Boolean = false) =
    lockFile(path) match {
      case Some(lock) =>
        val meta = Project.getProjectMetadata(path)
        lazy val errorSeq = Seq(meta.createdBy, meta.versionMajor, meta.versionMinor,
                                s"PrincessEdit ${VersionInfo.versionString}", Project.VER_MAJOR, Project.VER_MINOR)

        if(meta.versionMajor <= Project.VER_MAJOR) {
          if(meta.versionMinor <= Project.VER_MINOR ||
             UIUtils.openMessage(parent, SWT.ICON_WARNING | SWT.YES | SWT.NO, PackageManager.systemI18N,
                                 "_princess.main.incompatibleMinorVersion", errorSeq : _*) == SWT.YES) {
            new MainFrame(ctx, ProjectSource.OpenProject(path, meta, lock)).open()
            if(closeOnAccept) parent.close()
          } else lock.release()
        } else {
          UIUtils.openMessage(parent, SWT.ICON_ERROR | SWT.OK, PackageManager.systemI18N,
                              "_princess.main.incompatibleMajorVersion", errorSeq : _*)
          lock.release()
        }
      case None =>
        UIUtils.openMessage(parent, SWT.ICON_ERROR | SWT.OK,
                            PackageManager.systemI18N, "_princess.main.projectLocked")
    }
  def showOpenDialog(parent: Window, ctx: ControlContext, closeOnAccept: Boolean = false) = {
    val selector = new FileDialog(parent.getShell, SWT.OPEN)
    selector.setFilterNames(Array(PackageManager.systemI18N.system("_princess.main.project")))
    selector.setFilterExtensions(Array("*.pedit-project"))
    selector.setFilterPath(Paths.get(".").toAbsolutePath.toString)
    selector.open() match {
      case null =>
      case target =>
        loadProject(parent, ctx, Paths.get(target), closeOnAccept)
    }
  }
}