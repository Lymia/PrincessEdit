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

import java.nio.file.{FileSystems, Path, Paths}
import java.util.UUID

import com.coconut_palm_software.xscalawt.XScalaWT._
import moe.lymia.princess.core.{GameID, I18NLoader, PackageManager}
import moe.lymia.princess.editor.core._
import moe.lymia.princess.editor.lua.EditorModule
import moe.lymia.princess.editor.project.{CardSource, Project}
import moe.lymia.princess.editor.ui.editor.EditorPane
import moe.lymia.princess.editor.utils._
import moe.lymia.princess.renderer.lua.RenderModule
import moe.lymia.princess.util.{IOUtils, VersionInfo}
import org.eclipse.jface.action.{Action, MenuManager}
import org.eclipse.jface.window.IShellProvider
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
  def setSaveLocation(path: Option[Path]) = {
    // TODO: Lock open files
    currentSaveLocation = path
  }
  setSaveLocation(projectSource.getSaveLocation)

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
  def getSaveLocation: Option[Path]
}
object ProjectSource {
  // TODO: Throw an error if the given GameID isn't installed
  case class OpenProject(path: Path) extends ProjectSource {
    override def getGameID: String = Project.getProjectGameID(path)
    override def openProject(ctx: ControlContext, gameID: String, idData: GameIDData): Project =
      Project.loadProject(ctx, gameID, idData, path)
    override def getSaveLocation: Option[Path] = Some(path)
  }
  case class NewProject(id: GameID) extends ProjectSource {
    override def getGameID: String = id.name
    override def openProject(ctx: ControlContext, gameID: String, idData: GameIDData): Project =
      new Project(ctx, gameID, idData)
    override def getSaveLocation: Option[Path] = None
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

  override def createMenuManager: MenuManager = {
    val manager = super.createMenuManager()

    val file = new MenuManager()
    file.setMenuText("File") // TODO I18N
    manager.add(file)

    val action = new Action() {
      setText("Save")
      override def run(): Unit = {
        val fs = IOUtils.openZip(Paths.get(s"test-save-${UUID.randomUUID()}.pedit-project"), create = true)
        try state.project.writeTo(fs.getPath("/")) finally fs.close()
      }
    }
    file.add(action)

    if(currentTab != null) currentTab.addMenuItems(manager)

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