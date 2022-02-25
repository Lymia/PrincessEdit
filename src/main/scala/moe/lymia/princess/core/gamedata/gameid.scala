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

package moe.lymia.princess.core.gamedata

import moe.lymia.lua.LuaObject
import moe.lymia.princess.core.EditorException
import moe.lymia.princess.core.state.{LuaContext, LuaModule}
import moe.lymia.princess.util.IOUtils
import moe.lymia.princess.{DefaultLogger, Environment, Logger}
import toml.Toml
import toml.Codecs._

import java.nio.file.Path
import scala.collection.mutable

case class GameId(name: String, displayName: String, iconPath: Option[String])
object GameId {
  def loadGameId(path: Path): GameId = EditorException.context(s"loading GameID from $path") {
    case class RawGameId(name: String, displayName: String, icon: Option[String])
    case class RawGameIdContainer(game: RawGameId)

    val raw = Toml.parseAs[RawGameIdContainer](IOUtils.readFileAsString(path)).checkErr
    GameId(raw.game.name, raw.game.displayName, raw.game.icon)
  }

  def loadGameIdManager(manager: GameIdLoader): GameData = manager.loadGameData(StaticGameIds.DefinesGameId)
  def loadGameIds(game: GameData): Map[String, GameId] = {
    val gameIDExports = game.getExports(StaticExportIds.GameId).map(_.path).map(game.forceResolve)

    val idMap = new mutable.HashMap[String, GameId]
    for(id <- gameIDExports.map(loadGameId)) {
      if(idMap.contains(id.name)) throw EditorException(s"Duplicate GameID '${id.name}' found")
      idMap.put(id.name, id)
    }
    idMap.toMap
  }
}

final class GameData(packages: PackageList, val logger: Logger = DefaultLogger, modules: Seq[LuaModule] = Seq()) {
  val gameId: String = packages.gameId
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
    val export = StaticExportIds.EntryPoint(gameId, entryPoint)
    val ep = getExports(export)
    if(ep.isEmpty) {
      val system = getSystemExports(StaticExportIds.EntryPoint("_princess", entryPoint))
      if(system.nonEmpty) Some(getLuaExport(system.head.path))
      else None
    } else if(ep.length > 1) throw EditorException(s"GameID '$gameId' has more than one entry point of type '$export'")
    else Some(getLuaExport(ep.head.path))
  }
  def getRequiredEntryPoint(export: String): LuaObject =
    getEntryPoint(export).getOrElse(throw EditorException(s"GameID '$gameId' has no entry point of type '$export'"))
}

final class GameIdLoader(packages: Option[Path], systemPackages: Seq[Path] = Seq(), logger: Logger = DefaultLogger) {
  // TODO: Encapsulate this better.

  val resolver: PackageResolver = PackageResolver.loadPackageDirectory(packages, systemPackages: _*)

  val gameIdManager: GameData = GameId.loadGameIdManager(this)
  val gameIds: Map[String, GameId] = GameId.loadGameIds(gameIdManager)
  val gameIdList: Seq[GameId] = gameIds.values.toSeq
  val gameIdI18N: MarkedI18NSource = new I18NLoader(gameIdManager).i18n.user

  def loadGameData(gameId: String, logger: Logger = logger, modules: Seq[LuaModule] = Seq()): GameData =
    new GameData(resolver.loadGameId(gameId), logger, modules)
}
object GameIdLoader {
  private def rootPath = Environment.rootDirectory
  private def corePkgPath = rootPath.resolve("lib/core.pedit-pkg")

  lazy val default = new GameIdLoader(Some(rootPath.resolve("packages")), Seq(corePkgPath))
  lazy val systemI18N: I18N = {
    val id = default.loadGameData("_princess")
    new I18NLoader(id).i18n
  }
}