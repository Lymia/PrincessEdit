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

import java.nio.file.Path

import scala.collection.mutable

object StaticGameIDs {
  val System    = "princess/system"
  val HasGameID = "princess/hasgameid"
}

object StaticExportIDs {
  val GameID = "princess/gameid"
  def Template(gameId: String) = s"$gameId/template"
  object Predef {
    val System = "princess/predefs/system"
    def apply(gameId: String) = s"princess/predefs/$gameId"
  }
}

case class GameID(name: String, displayName: String, iconPath: Option[String])
object GameID {
  def loadGameID(path: Path) = TemplateException.context(s"loading GameID from $path") {
    val ini = INI.load(path)
    val section = ini.getOrElse("game", throw TemplateException("No section 'game' found"))
    GameID(section.getOrElse("name", throw TemplateException("No value 'name' found")).head,
           section.getOrElse("displayName", throw TemplateException("No value 'displayName' found")).head,
           section.getOrElse("icon", Seq()).headOption)
  }
  def loadGameIDs(resolver: PackageResolver) = {
    val packages = resolver.loadGameId(StaticGameIDs.HasGameID)
    val gameIDExports = packages.getExports(StaticExportIDs.GameID).map(_.path).map(packages.forceResolve)

    val idMap = new mutable.HashMap[String, GameID]
    for(id <- gameIDExports.map(loadGameID)) {
      if(idMap.contains(id.name)) throw TemplateException(s"Duplicate GameID '${id.name}' found")
      idMap.put(id.name, id)
    }
    idMap.toMap
  }
}