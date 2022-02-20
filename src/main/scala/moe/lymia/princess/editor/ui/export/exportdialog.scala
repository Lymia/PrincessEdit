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

package moe.lymia.princess.editor.ui.export

import com.coconut_palm_software.xscalawt.XScalaWT._
import moe.lymia.princess.editor.model.FullCardData
import moe.lymia.princess.editor.ui.mainframe.MainFrameState
import moe.lymia.princess.editor.utils.{DialogBase, HelpButton, UIUtils}
import org.eclipse.jface.dialogs.ProgressMonitorDialog
import org.eclipse.jface.layout.GridDataFactory
import org.eclipse.jface.viewers._
import org.eclipse.swt.SWT
import org.eclipse.swt.events.SelectionEvent
import org.eclipse.swt.layout._
import org.eclipse.swt.widgets._

import java.nio.file.Paths
import java.util.UUID

sealed abstract class ExportCardsDialogBase[Data](state: MainFrameState) extends DialogBase(state.shell, state.ctx) {
  protected val cardCount: Int

  override def configureShell(newShell: Shell): Unit = {
    super.configureShell(newShell)
    newShell.setText(state.i18n.system("_princess.export.title"))
  }

  protected val hasControls: Boolean
  protected def makeDataSettings(parent: Composite): () => Option[Data]
  protected def export[T](format: ExportFormat[T, _], options: T, data: Data)

  private var viewer      : ComboViewer = _
  private var dataSettings: () => Option[Data] = _

  private var format      : ExportFormat[_, _] = _
  private var options     : ExportControl[_] = _

  private def doExport() =
    if(format == null)
      UIUtils.openMessage(this, SWT.ICON_ERROR | SWT.OK, state.i18n, "_princess.export.noSelectedFormat")
    else options.getResult.foreach { options =>
      dataSettings().foreach { data =>
        export(format.asInstanceOf[ExportFormat[Any, _]], options.asInstanceOf[Any], data)
      }
    }

  override protected def frameContents(frame: Composite): Unit = {
    getShell.contains(
      _.setLayout(new FillLayout),
      _.setMinimumSize(450, 0)
    )

    frame.contains(
      gridLayout(columns = 3)(),
      composite(
        gridLayout(columns = 2)(_.marginWidth = 0, _.marginHeight = 0),
        label(
          state.i18n.system("_princess.export.exportFormat", this.cardCount),
          _.layoutData = new GridData(SWT.BEGINNING, SWT.CENTER, false, false)
        ),
        *[ComboViewer](SWT.BORDER | SWT.READ_ONLY) (
          this.viewer = _,
          _.setContentProvider(ArrayContentProvider.getInstance()),
          _.setLabelProvider(new LabelProvider {
            override def getText(element: scala.Any): String =
              state.i18n.system(element.asInstanceOf[ExportFormat[_, _]].displayName)
          }),
          _.setInput(ExportFormat.formats.toArray[Object]),
          _.getControl.layoutData = new GridData(SWT.FILL, SWT.CENTER, true, false)
        ),
        _.layoutData =
          GridDataFactory.createFrom(new GridData(SWT.FILL, SWT.CENTER, true, false)).span(3, 1).create()
      ),
      group(
        state.i18n.system("_princess.export.exportSettings"),
        gridLayout(columns = 3)(),
        modifiedFillGridData(_.span(3, 1)),
        group => dataSettings = makeDataSettings(group),
        group => {
          val mark = group.getChildren.length
          def regenerate() = {
            for(child <- group.getChildren.drop(mark)) child.dispose()

            format = viewer.getStructuredSelection.getFirstElement.asInstanceOf[ExportFormat[_, _]]
            if(format != null) options = format.makeControl(this, group, SWT.NONE, state)

            val exclude = !hasControls && (format == null || !format.hasControls)
            group.setVisible(!exclude)
            group.getLayoutData.asInstanceOf[GridData].exclude = exclude

            group.layout(true)
            if(getShell.isVisible) getShell.pack(true)
          }
          regenerate()
          viewer.addSelectionChangedListener(_ => regenerate())
        }
      ),
      label(
        if(cardCount > 1) state.i18n.system("_princess.export.cardCount", cardCount) else "",
        _.layoutData = new GridData(SWT.BEGINNING, SWT.CENTER, false, false)
      ),
      button(
        state.i18n.system("_princess.export.cancel"),
        (x : SelectionEvent) => this.close(),
        _.layoutData =
          GridDataFactory.createFrom(new GridData(SWT.END, SWT.CENTER, true, false)).indent(140, 0).create()
      ),
      button(
        state.i18n.system("_princess.export.export"),
        (x : SelectionEvent) => doExport(),
        _.layoutData = new GridData(SWT.END, SWT.CENTER, false, false)
      )
    )
  }

  override def open() = {
    state.ctx.asyncUiExec {
      viewer.setSelection(new StructuredSelection(ExportFormat.formats.head))
    }
    super.open()
  }
}

final class ExportCardsDialogSingle(state: MainFrameState, exportTarget: (UUID, FullCardData))
  extends ExportCardsDialogBase[Unit](state) {

  override protected val cardCount: Int = 1

  override protected val hasControls: Boolean = false
  override protected def makeDataSettings(parent: Composite) = () => Some(())
  override protected def export[T](format: ExportFormat[T, _], options: T, data: Unit): Unit = {
    val selector = new FileDialog(getShell, SWT.SAVE)

    // TODO: Error check the LuaNameSpec
    val nameSpec =
      LuaNameSpec(state.game.lua.L, state.idData.export.defaultNameFormat, ExportMultiTask.NameSpecFieldNames : _*)
    val defaultName =
      nameSpec.left.get.makeName(exportTarget._1.toString, exportTarget._2.luaData.now)

    selector.setFileName(format.addExtension(defaultName.left.get))

    selector.setFilterNames(Array(state.i18n.system(format.displayName)))
    selector.setFilterExtensions(Array(format.extension.map(x => s"*.$x").mkString(";")))

    selector.setOverwrite(false)
    selector.open() match {
      case null =>
        // canceled
      case name =>
        new ExportSingleTask(state, Paths.get(name), format, options, exportTarget).run()
        this.close()
    }
  }
}

final class ExportCardsDialogMulti(state: MainFrameState, exportTargets: Seq[(UUID, FullCardData)])
  extends ExportCardsDialogBase[LuaNameSpec](state) {

  override protected val cardCount: Int = exportTargets.length

  private var nameSpec: Text = _

  override protected val hasControls: Boolean = true
  override protected def makeDataSettings(parent: Composite) = {
    parent.contains(
      label(
        state.i18n.system("_princess.export.nameFormat"),
        _.layoutData = new GridData(SWT.BEGINNING, SWT.CENTER, false, false)
      ),
      *[Text](SWT.SINGLE | SWT.BORDER) (
        this.nameSpec = _,
        state.idData.export.defaultNameFormat,
        _.layoutData = new GridData(SWT.FILL, SWT.CENTER, true, false)
      ),
      *[HelpButton](SWT.NONE) (
        _.setBalloonMessage(
          state.i18n.system("_princess.export.nameFormat.help")+
          (state.idData.export.helpText match {
            case Some(x) => "\n\n"+state.i18n.user(x)
            case None => ""
          })
        ),
        _.layoutData = new GridData(SWT.CENTER, SWT.CENTER, false, false)
      )
    )

    () => {
      LuaNameSpec(state.game.lua.L, nameSpec.getText, ExportMultiTask.NameSpecFieldNames : _*) match {
        case Left(x) => Some(x)
        case Right(x) =>
          UIUtils.openMessage(state.shell, SWT.ICON_ERROR | SWT.OK, state.i18n,
                              "_princess.export.invalidNameFormat", x)
          None
      }
    }
  }

  override protected def export[T](format: ExportFormat[T, _], options: T, nameSpec: LuaNameSpec): Unit = {
    val selector = new DirectoryDialog(getShell, SWT.NONE)
    selector.open() match {
      case null =>
      case name =>
        this.close()
        val runnable = new ExportMultiTask(state, Paths.get(name), nameSpec, format, options, exportTargets)
        new ProgressMonitorDialog(state.shell.getShell).run(true, true, runnable)
    }
  }
}

object ExportCardsDialog {
  def open(state: MainFrameState, exportTargets: (UUID, FullCardData)*) =
    if(exportTargets.isEmpty) sys.error("No export targets!")
    else if(exportTargets.length == 1) new ExportCardsDialogSingle(state, exportTargets.head).open()
    else new ExportCardsDialogMulti(state, exportTargets).open()
}