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

import java.nio.file.{Files, Path}
import java.util.UUID

import moe.lymia.lua._
import moe.lymia.princess.editor.core.{CardData, CardSource}
import moe.lymia.princess.editor.ui.mainframe.MainFrameState
import moe.lymia.princess.renderer.RasterizeResourceLoader
import moe.lymia.princess.util.IOUtils

import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.jface.operation.IRunnableWithProgress

final class ExportSingleTask[Options, Init](state: MainFrameState, pool: CardSource, outFile: Path,
                                            exportFormat: ExportFormat[Options, Init], exportData: Options,
                                            exportTarget: (UUID, CardData)) {
  def run() = {
    val init = exportFormat.initRender(state)
    try {
      val (id, target) = exportTarget
      val rendered = state.ctx.syncLuaExec(state.idData.renderer.render(
        Seq(target.root.luaData.now, pool.info.root.luaData.now), RasterizeResourceLoader
      ))
      exportFormat.export(rendered, exportData, init, outFile)
    } finally {
      exportFormat.finalizeRender(init)
    }
  }
}
final class ExportMultiTask[Options, Init](state: MainFrameState, pool: CardSource, outDir: Path, name: LuaNameSpec,
                                           exportFormat: ExportFormat[Options, Init], exportData: Options,
                                           exportTargets: Seq[(UUID, CardData)])
  extends IRunnableWithProgress {

  // TODO: Add extra error checking
  override def run(progress: IProgressMonitor): Unit = {
    progress.beginTask(state.i18n.system("_princess.export.taskName"), exportTargets.length)
    val init = exportFormat.initRender(state)
    try {
      IOUtils.withTemporaryDirectory("princess-edit-export-") { temp =>
        for((id, target) <- exportTargets) {
          val name = exportFormat.addExtension(state.ctx.syncLuaExec(
            this.name.makeName(id.toString, target.root.luaData.now, pool.info.root.luaData.now)
          ).left.get)
          progress.subTask(state.i18n.system("_princess.export.exportingTo", name))

          val rendered = state.ctx.syncLuaExec(state.idData.renderer.render(
            Seq(target.root.luaData.now, pool.info.root.luaData.now), RasterizeResourceLoader
          ))

          exportFormat.export(rendered, exportData, init, temp.resolve(name))

          progress.worked(1)
        }

        for(file <- IOUtils.list(temp)) Files.move(file, outDir.resolve(file.getFileName))
      }
      progress.done()
    } finally {
      exportFormat.finalizeRender(init)
    }
  }
}
object ExportMultiTask {
  val NameSpecFieldNames = Seq("internalId", "card", "set")
}