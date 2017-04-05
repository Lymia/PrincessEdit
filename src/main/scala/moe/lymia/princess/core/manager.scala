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

import java.net.URI
import java.nio.file.{Path, Paths}

import moe.lymia.lua.LuaObject
import moe.lymia.princess.core.pkg.CorePkg

final class GameManager(packages: PackageList, val logger: Logger = DefaultLogger, modules: Seq[LuaModule] = Seq()) {
  val gameId = packages.gameId
  lazy val lua = new LuaContext(packages, logger.bind("LuaContext"), modules)

  def getExportKeys: Set[String] = packages.getExportKeys
  def getExports(key: String): Seq[Export] = packages.getExports(key)
  def getSystemExports(key: String): Seq[Export] = packages.getSystemExports(key)
  def getExports(key: String, system: Boolean): Seq[Export] =
    if(system) packages.getSystemExports(key) else packages.getExports(key)

  def resolve(path: String): Option[Path] = packages.resolve(path)
  def forceResolve(path: String): Path = packages.forceResolve(path)

  def getLuaExport(path: String): LuaObject = lua.getLuaExport(path)

  def getEntryPoint(entryPoint: String): Option[LuaObject] = {
    val export = StaticExportIDs.EntryPoint(gameId, entryPoint)
    val ep = getExports(export)
    if(ep.isEmpty) {
      val system = getSystemExports(StaticExportIDs.EntryPoint("_princess", entryPoint))
      if(system.nonEmpty) Some(getLuaExport(system.head.path))
      else None
    } else if(ep.length > 1) throw EditorException(s"GameID '$gameId' has more than one entry point of type '$export'")
    else Some(getLuaExport(ep.head.path))
  }
  def getRequiredEntryPoint(export: String) =
    getEntryPoint(export).getOrElse(throw EditorException(s"GameID '$gameId' has no entry point of type '$export'"))
}

final class PackageManager(packages: Path, systemPackages: Seq[Path] = Seq(), logger: Logger = DefaultLogger) {
  val resolver = PackageResolver.loadPackageDirectory(packages, systemPackages: _*)

  val gameIdManager = GameID.loadGameIDManager(this)
  val gameIds = GameID.loadGameIDs(gameIdManager)
  val gameIdList = gameIds.values.toSeq
  val gameIdI18N = new I18NLoader(gameIdManager).i18n.user

  def loadGameId(gameId: String, logger: Logger = logger, modules: Seq[LuaModule] = Seq()) =
    new GameManager(resolver.loadGameId(gameId), logger, modules)
}
object PackageManager {
  private lazy val defaultPath = System.getProperty("moe.lymia.princess.rootDirectory") match {
    case null => Paths.get("packages")
    case url => Paths.get(new URI(url)).resolve("packages")
  }
  lazy val default = new PackageManager(defaultPath, Seq(CorePkg.packagePath))

  lazy val systemI18N = {
    val id = default.loadGameId("_princess")
    new I18NLoader(id).i18n
  }
}