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

package moe.lymia.princess.views.exportcards

import moe.lymia.lua._
import moe.lymia.princess.core.cardmodel.FullCardData
import moe.lymia.princess.svg.RasterizeResourceLoader
import moe.lymia.princess.util.IOUtils
import moe.lymia.princess.views.mainframe.MainFrameState
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.jface.operation.IRunnableWithProgress

import java.nio.file.{Files, Path}
import java.util.UUID

final class ExportSingleTask[Options, Init](state: MainFrameState, outFile: Path,
                                            exportFormat: ExportFormat[Options, Init], exportData: Options,
                                            exportTarget: (UUID, FullCardData)) {
  def run() = {
    val init = exportFormat.initRender(state)
    val (id, target) = exportTarget
    val rendered = state.ctx.syncLuaExec(state.idData.renderer.render(
      Seq(target.luaData.now), RasterizeResourceLoader
    ))
    exportFormat.export(rendered, exportData, init, outFile)
  }
}
final class ExportMultiTask[Options, Init](state: MainFrameState, outDir: Path, name: LuaNameSpec,
                                           exportFormat: ExportFormat[Options, Init], exportData: Options,
                                           exportTargets: Seq[(UUID, FullCardData)])
  extends IRunnableWithProgress {

  // TODO: Add extra error checking
  override def run(progress: IProgressMonitor): Unit = {
    progress.beginTask(state.i18n.system("_princess.export.taskName"), exportTargets.length)
    val init = exportFormat.initRender(state)

    IOUtils.withTemporaryDirectory("princess-edit-export-") { temp =>
      for((id, target) <- exportTargets) {
        val name = exportFormat.addExtension(state.ctx.syncLuaExec(
          this.name.makeName(id.toString, target.luaData.now)
        ).left.get)
        progress.subTask(state.i18n.system("_princess.export.exportingTo", name))

        val rendered = state.ctx.syncLuaExec(state.idData.renderer.render(
          Seq(target.luaData.now), RasterizeResourceLoader
        ))

        exportFormat.export(rendered, exportData, init, temp.resolve(name))

        progress.worked(1)
      }

      for(file <- IOUtils.list(temp)) Files.move(file, outDir.resolve(file.getFileName))
    }
    progress.done()
  }
}
object ExportMultiTask {
  val NameSpecFieldNames = Seq("internalId", "card", "set")
}