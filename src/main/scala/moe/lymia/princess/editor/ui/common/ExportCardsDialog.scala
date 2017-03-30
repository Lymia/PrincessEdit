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

package moe.lymia.princess.editor.ui.common

import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger}
import javax.imageio.ImageIO

import moe.lymia.princess.editor.core._
import moe.lymia.princess.editor.ui.mainframe.MainFrameState
import moe.lymia.princess.editor.utils.{DialogBase, Message}
import moe.lymia.princess.renderer.{RasterizeResourceLoader, SVGData}
import moe.lymia.princess.util.{AtomicList, Condition, IOUtils, RequestBuffer}
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.jface.dialogs.ProgressMonitorDialog
import org.eclipse.jface.operation.IRunnableWithProgress
import org.eclipse.jface.window.IShellProvider
import org.eclipse.swt.SWT
import org.eclipse.swt.layout._
import org.eclipse.swt.widgets._

final class ExportTask(state: MainFrameState, pool: CardSource, dpi: Int, exportTargets: Seq[(UUID, CardData)])
  extends IRunnableWithProgress {

  // TODO: Add extra error checking
  override def run(progress: IProgressMonitor): Unit = {
    @volatile var renderDone = false
    val rasterizeCondition = new Condition()
    @volatile var rasterizeDone = false
    val buffer = new AtomicList[SVGData]

    val renderManagementThread = new Thread() {
      override def run(): Unit = {
        try {
          for((_, target) <- exportTargets) buffer.add(state.ctx.syncLuaExec {
            val rendered = state.idData.renderer.render(
              Seq(target.root.luaData.now, pool.info.root.luaData.now), RasterizeResourceLoader
            )
            progress.worked(1)
            if(progress.isCanceled) return
            rendered
          })
        } finally {
          renderDone = true
        }
      }
    }
    val rasterizeManagementThread = new Thread() {
      override def run() = {
        val rasterizer = state.ctx.createRasterizer()
        var i = 0
        try {
          while({
            buffer.pullOne() match {
              case Some(r) =>
                val (x, y) = r.bestSizeForDPI(dpi)
                val image = r.rasterizeAwt(rasterizer, x, y)
                ImageIO.write(image, "png", new File(s"render-$i.png"))
                progress.worked(1)
                i += 1
              case None =>
                buffer.waitFor(1)
            }
            (buffer.nonEmpty || !renderDone) && !progress.isCanceled
          }) { }
        } finally {
          rasterizeDone = true
          rasterizer.dispose()
          rasterizeCondition.done()
        }
      }
    }

    progress.beginTask(state.i18n.system("_princess.export.taskName"), exportTargets.length * 2)
    renderManagementThread.start()
    rasterizeManagementThread.start()
    while(!renderDone || !rasterizeDone) rasterizeCondition.waitFor(1)
    progress.done()
  }
}

final class ExportCardsDialog(parent: IShellProvider, state: MainFrameState, pool: CardSource,
                              exportTargets: Seq[(UUID, CardData)]) extends DialogBase(parent, state.ctx) {
  override def configureShell(newShell: Shell): Unit = {
    super.configureShell(newShell)
    newShell.setText(state.i18n.system("_princess.export.title"))
  }

  override protected def frameContents(frame: Composite): Unit = {
    val parentGrid = new GridLayout()
    parentGrid.numColumns = 3
    frame.setLayout(parentGrid)

    val group = new Group(frame, SWT.NONE)
    group.setText(state.i18n.system("_princess.export.renderSettings"))
    val grid = new GridLayout
    grid.numColumns = 2
    group.setLayout(grid)
    val groupData = new GridData(SWT.FILL, SWT.FILL, true, true)
    groupData.horizontalSpan = 3
    group.setLayoutData(groupData)

    val svgLabel = new Label(group, SWT.NONE)
    svgLabel.setText(state.i18n.system("_princess.export.svg"))
    svgLabel.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false))

    val svgCheck = new Button(group, SWT.CHECK)
    svgCheck.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false))

    val dpiLabel = new Label(group, SWT.NONE)
    dpiLabel.setText(state.i18n.system("_princess.export.dpi"))
    dpiLabel.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false))

    val dpiField = new Text(group, SWT.SINGLE | SWT.BORDER)
    dpiField.setText("150")
    dpiField.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false))

    svgCheck.addListener(SWT.Selection, event => {
      dpiField.setEnabled(!svgCheck.getSelection)
    })

    val cardCount = new Label(frame, SWT.NONE)
    cardCount.setText(state.i18n.system("_princess.export.cardCount", exportTargets.length))
    cardCount.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false))

    val cancelButton = new Button(frame, SWT.PUSH)
    cancelButton.setText(state.i18n.system("_princess.export.cancel"))
    cancelButton.addListener(SWT.Selection, event => {
      this.close()
    })
    val cancelData = new GridData(SWT.END, SWT.CENTER, true, false)
    cancelData.horizontalIndent = 60
    cancelButton.setLayoutData(cancelData)

    val exportButton = new Button(frame, SWT.PUSH)
    exportButton.setText(state.i18n.system("_princess.export.export"))
    exportButton.addListener(SWT.Selection, event => {
      val dpi = try {
        val dpi = dpiField.getText.toInt
        if(dpi <= 0) throw new NumberFormatException()

        val runnable = new ExportTask(state, pool, dpi, exportTargets)
        new ProgressMonitorDialog(parent.getShell).run(true, true, runnable)
        this.close()
      } catch {
        case e: NumberFormatException =>
          Message.open(this, SWT.ICON_WARNING | SWT.OK, state.i18n, "_princess.export.dpiNotNumber")
      }
    })
    exportButton.setLayoutData(new GridData(SWT.END, SWT.CENTER, false, false))
  }

  override def open(): Int = {
    super.open()
  }
}
