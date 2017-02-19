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

case class TemplateExport(path: String, manager: GameManager, packages: PackageList,
                          displayName: String, icon: Option[String]) {
  lazy val template = new LuaTemplate(path, packages, manager.lua, manager.getLuaExport(path))
}
object TemplateExport {
  def loadTemplateExport(e: Export, manager: GameManager, packages: PackageList) =
    TemplateExport(e.path, manager, packages,
                   e.metadata.getOrElse("displayName",
                                        throw TemplateException("No value 'displayName' found")).head,
                   e.metadata.getOrElse("icon", Seq()).headOption)
}

class GameManager(packages: PackageList) {
  val gameId = packages.gameId
  val lua = new LuaContext(packages)

  def getExportKeys: Set[String] = packages.getExportKeys
  def getExports(key: String): Seq[Export] = packages.getExports(key)

  def resolve(path: String): Option[Path] = packages.resolve(path)
  def forceResolve(path: String): Path = packages.forceResolve(path)

  def getLuaExport(path: String): LuaTable = lua.getLuaExport(path)

  lazy val templates =
    getExports(StaticExportIDs.Template(gameId)).map(x => TemplateExport.loadTemplateExport(x, this, packages))
}

class PackageManager(packages: Path, extraDirs: Path*) {
  val resolver = PackageResolver.loadPackageDirectory(packages, extraDirs: _*)
  lazy val gameIDs = GameID.loadGameIDs(resolver)
  lazy val gameIDList = gameIDs.values.toSeq

  def loadGameId(gameId: String) = new GameManager(resolver.loadGameId(gameId))
  def loadGameId(gameId: GameID) = new GameManager(resolver.loadGameId(gameId.name))
}
object PackageManager {
  lazy val default = new PackageManager(Paths.get("packages"))
}