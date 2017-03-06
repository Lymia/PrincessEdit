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
import moe.lymia.princess.lua.LuaObject
import moe.lymia.princess.util.SizedCache

final class GameManager(packages: PackageList, logger: Logger = DefaultLogger) {
  val gameId = packages.gameId
  val lua = new LuaContext(packages, logger)

  private val cache = SizedCache(128 * 1024 * 1024 /* About 128 MB */)

  def getExportKeys: Set[String] = packages.getExportKeys
  def getExports(key: String): Seq[Export] = packages.getExports(key)

  def resolve(path: String): Option[Path] = packages.resolve(path)
  def forceResolve(path: String): Path = packages.forceResolve(path)

  def getLuaExport(path: String): LuaObject = lua.getLuaExport(path)

  lazy val main = {
    val ep = getExports(StaticExportIDs.Main(gameId))
    if(ep.isEmpty) throw TemplateException(s"GameID '$gameId' has no entry point")
    else if(ep.length > 1) throw TemplateException(s"GameID '$gameId' has more than one entry point")
    else {
      val export = ep.head
      new LuaTemplate(export.path, packages, lua, getLuaExport(export.path), cache)
    }
  }
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