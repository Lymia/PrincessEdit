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

package moe.lymia.princess.core.packages

import moe.lymia.princess.core.{EditorException, GameManager, PackageManager}
import moe.lymia.princess.util.IOUtils
import toml.Toml
import toml.Codecs._

import java.nio.file.Path
import scala.collection.mutable

case class GameId(name: String, displayName: String, iconPath: Option[String])
object GameId {
  def loadGameId(path: Path) = EditorException.context(s"loading GameID from $path") {
    case class RawGameId(name: String, displayName: String, icon: Option[String])
    case class RawGameIdContainer(game: RawGameId)

    val raw = Toml.parseAs[RawGameIdContainer](IOUtils.readFileAsString(path)).checkErr
    GameId(raw.game.name, raw.game.displayName, raw.game.icon)
  }

  def loadGameIdManager(manager: PackageManager) = manager.loadGameId(StaticGameIDs.DefinesGameID)
  def loadGameIds(game: GameManager) = {
    val gameIDExports = game.getExports(StaticExportIDs.GameID).map(_.path).map(game.forceResolve)

    val idMap = new mutable.HashMap[String, GameId]
    for(id <- gameIDExports.map(loadGameId)) {
      if(idMap.contains(id.name)) throw EditorException(s"Duplicate GameID '${id.name}' found")
      idMap.put(id.name, id)
    }
    idMap.toMap
  }
}

object StaticGameIDs {
  val System        = "_princess/system-package"
  val DefinesGameID = "defines-gameid"
}

object StaticExportIDs {
  val GameID = "gameid"
  val ProtectedPath = "_princess/protected-path"
  val IgnoredPath = "_princess/ignored-path"
  def Predef(t: String) = s"_princess/predef/$t"
  def EntryPoint(t: String, ep: String) = s"$t/entry-point/$ep"
  def I18N(t: String, language: String, country: String) = s"$t/i18n/${language}_$country"
}
