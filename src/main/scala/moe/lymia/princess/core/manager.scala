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

package moe.lymia.princess.core

import java.nio.file.{Path, Paths}

import moe.lymia.princess.core.lua.LuaContext
import moe.lymia.princess.lua.LuaTable
import moe.lymia.princess.util.SizedCache

final case class TemplateExport(path: String, manager: GameManager, packages: PackageList, cache: SizedCache,
                                displayName: String, icon: Option[String]) {
  lazy val template = new LuaTemplate(path, packages, manager.lua, manager.getLuaExport(path), cache)
}
object TemplateExport {
  def loadTemplateExport(e: Export, manager: GameManager, packages: PackageList, cache: SizedCache) =
    TemplateExport(e.path, manager, packages, cache,
                   e.metadata.getOrElse("displayName",
                                        throw TemplateException("No value 'displayName' found")).head,
                   e.metadata.getOrElse("icon", Seq()).headOption)
}

final class GameManager(packages: PackageList, logger: Logger = DefaultLogger) {
  val gameId = packages.gameId
  val lua = new LuaContext(packages, logger)

  private val cache = SizedCache(128 * 1024 * 1024 /* About 128 MB */)

  def getExportKeys: Set[String] = packages.getExportKeys
  def getExports(key: String): Seq[Export] = packages.getExports(key)

  def resolve(path: String): Option[Path] = packages.resolve(path)
  def forceResolve(path: String): Path = packages.forceResolve(path)

  def getLuaExport(path: String): LuaTable = lua.getLuaExport(path)

  lazy val templates =
    getExports(StaticExportIDs.Template(gameId)).map(x =>
      TemplateExport.loadTemplateExport(x, this, packages, cache))
}

final class PackageManager(packages: Path, systemPackages: Seq[Path] = Seq(), logger: Logger = DefaultLogger) {
  val resolver = PackageResolver.loadPackageDirectory(packages, systemPackages: _*)
  lazy val gameIDs = GameID.loadGameIDs(resolver)
  lazy val gameIDList = gameIDs.values.toSeq

  def loadGameId(gameId: String, logger: Logger = logger) =
    new GameManager(resolver.loadGameId(gameId), logger)
}
object PackageManager {
  lazy val default = new PackageManager(Paths.get("packages"), Seq(Paths.get("PrincessEdit.pedit-pkg")))
}