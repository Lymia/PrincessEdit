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

package moe.lymia.princess.editor.ui.export

import java.nio.file.{Path, Paths}
import java.util.UUID

import moe.lymia.princess.editor.core._
import moe.lymia.princess.editor.ui.mainframe.MainFrameState
import moe.lymia.princess.editor.utils.{DialogBase, HelpButton, Message}
import org.eclipse.jface.dialogs.ProgressMonitorDialog
import org.eclipse.jface.window.IShellProvider
import org.eclipse.swt.SWT
import org.eclipse.swt.layout._
import org.eclipse.swt.widgets._

sealed abstract class ExportCardsDialogBase[Options, Init, Data](parent: IShellProvider, state: MainFrameState,
                                                                 format: ExportFormat[Options, Init])
  extends DialogBase(parent, state.ctx) {

  protected val cardCount: Int

  override def configureShell(newShell: Shell): Unit = {
    super.configureShell(newShell)
    newShell.setText(state.i18n.system("_princess.export.title"))
  }

  protected def makeDataSettings(parent: Composite): () => Option[Data]
  protected def export(settings: Options, data: Data)

  override protected def frameContents(frame: Composite): Unit = {
    val parentGrid = new GridLayout()
    parentGrid.numColumns = 3
    frame.setLayout(parentGrid)

    val group = new Group(frame, SWT.NONE)
    group.setText(state.i18n.system("_princess.export.exportSettings"))
    val grid = new GridLayout
    grid.numColumns = 3
    group.setLayout(grid)
    val groupData = new GridData(SWT.FILL, SWT.FILL, true, true)
    groupData.horizontalSpan = 3
    group.setLayoutData(groupData)

    val data = makeDataSettings(group)
    val result = format.makeControl(this, group, SWT.NONE, state)

    val cardCount = new Label(frame, SWT.NONE)
    cardCount.setText(state.i18n.system("_princess.export.cardCount", this.cardCount))
    cardCount.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false))

    val cancelButton = new Button(frame, SWT.PUSH)
    cancelButton.setText(state.i18n.system("_princess.export.cancel"))
    cancelButton.addListener(SWT.Selection, event => {
      this.close()
    })

    def doExport() = result.getResult.foreach { options =>
      data().foreach { data =>
        export(options, data)
        this.close()
      }
    }
    def iterChildren(widget: Widget): Unit = widget match {
      case w: Text =>
        w.addListener(SWT.DefaultSelection, _ => doExport())
      case w: Button =>
        w.addListener(SWT.DefaultSelection, _ => doExport())
      case w: Composite =>
        for(w <- w.getChildren) iterChildren(w)
      case _ =>
    }
    iterChildren(frame)

    val cancelData = new GridData(SWT.END, SWT.CENTER, true, false)
    cancelData.horizontalIndent = 60
    cancelButton.setLayoutData(cancelData)

    val exportButton = new Button(frame, SWT.PUSH)
    exportButton.setText(state.i18n.system("_princess.export.export"))
    exportButton.addListener(SWT.Selection, _ => doExport())
    exportButton.setLayoutData(new GridData(SWT.END, SWT.CENTER, false, false))
  }

  override def open() = super.open()
}

final class ExportCardsDialogSingle[Options, Init](parent: IShellProvider, state: MainFrameState, pool: CardSource,
                                                   outFile: Path, format: ExportFormat[Options, Init],
                                                   exportTarget: (UUID, CardData))
  extends ExportCardsDialogBase[Options, Init, Unit](parent, state, format) {

  override protected val cardCount: Int = 1

  override protected def makeDataSettings(parent: Composite) = () => Some(())
  override protected def export(options: Options, data: Unit): Unit = {
    new ExportSingleTask(state, pool, outFile, format, options, exportTarget).run()
  }
}

final class ExportCardsDialogMulti[Options, Init](parent: IShellProvider, state: MainFrameState, pool: CardSource,
                                                  outPath: Path, format: ExportFormat[Options, Init],
                                                  exportTargets: Seq[(UUID, CardData)])
  extends ExportCardsDialogBase[Options, Init, LuaNameSpec](parent, state, format) {

  override protected val cardCount: Int = exportTargets.length

  override protected def makeDataSettings(parent: Composite) = {
    val label = new Label(parent, SWT.NONE)
    label.setText(state.i18n.system("_princess.export.nameFormat"))
    label.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false))

    val text = new Text(parent, SWT.SINGLE | SWT.BORDER)
    text.setText(state.idData.export.defaultNameFormat)
    text.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false))

    val help = new HelpButton(parent, SWT.NONE)
    help.setBalloonMessage(
      state.i18n.system("_princess.export.nameFormat.help")+
      (state.idData.export.helpText match {
        case Some(x) => "\n\n"+state.i18n.user(x)
        case None => ""
      }))
    help.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false))

    () => {
      LuaNameSpec(state.game.lua.L, text.getText, ExportMultiTask.NameSpecFieldNames : _*) match {
        case Left(x) => Some(x)
        case Right(x) =>
          Message.open(this.parent, SWT.ICON_ERROR | SWT.OK, state.i18n, "_princess.export.invalidNameFormat", x)
          None
      }
    }
  }
  override protected def export(options: Options, nameSpec: LuaNameSpec): Unit = {
    val runnable = new ExportMultiTask(state, pool, outPath, nameSpec, format, options, exportTargets)
    new ProgressMonitorDialog(parent.getShell).run(true, true, runnable)
    this.close()
  }
}

object ExportCardsDialog {
  private val formats = ExportFormat.formats.map { format =>
    (format.displayName, format.extension.map("*." + _).mkString(";"), format)
  }
  private val formatForExtension = ExportFormat.formats.flatMap(x =>
    x.extension.map(y => y -> x)).asInstanceOf[Seq[(String, ExportFormat[_, _])]].toMap

  // TODO: Error check the LuaNameSpec
  def open(parent: IShellProvider, state: MainFrameState, pool: CardSource, exportTargets: (UUID, CardData)*) =
    if(exportTargets.isEmpty) sys.error("No export targets!")
    else if(exportTargets.length == 1) {
      val exportTarget = exportTargets.head
      val selector = new FileDialog(parent.getShell, SWT.SAVE)

      val nameSpec =
        LuaNameSpec(state.game.lua.L, state.idData.export.defaultNameFormat, ExportMultiTask.NameSpecFieldNames : _*)
      val defaultName =
        nameSpec.left.get.makeName(exportTarget._1.toString, exportTarget._2.root.luaData.now,
                                   pool.info.root.luaData.now)

      selector.setFileName(s"${defaultName.left.get}")

      selector.setFilterNames(formats.map(x => state.i18n.system(x._1)).toArray)
      selector.setFilterExtensions(formats.map(_._2).toArray)

      // TODO: Make a custom override confirmation taking into account the renaming
      selector.setOverwrite(false)
      selector.open() match {
        case null =>
          // canceled
        case name =>
          val split = name.split("\\.")
          val format = formats(selector.getFilterIndex)._3
          val fullName =
            if(split.length != 1) {
              if(format.extension.contains(split.last)) name
              else s"${split.init.mkString(".")}.${format.extension.head}"
            } else s"$name.${format.extension.head}"

          val saveFile = Paths.get(fullName)
          if(format.hasControls)
            new ExportCardsDialogSingle(parent, state, pool, saveFile, format, exportTarget).open()
          else
            new ExportSingleTask(state, pool, saveFile, format.asInstanceOf[ExportFormat[Any, Any]],
                                 format.nullOptions.asInstanceOf[Any], exportTarget).run()
      }
    } else {
      ???
    }
}