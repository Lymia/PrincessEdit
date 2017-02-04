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

class GameManager(val packages: PackageList) {
  val lua = new LuaContext(packages)

  def getExportKeys: Set[String] = packages.getExportKeys
  def getExports(key: String): Seq[Export] = packages.getExports(key)

  def resolve(path: String): Option[Path] = packages.resolve(path)
  def forceResolve(path: String): Path = packages.forceResolve(path)
}

class PackageManager(packages: Path, extraDirs: Path*) {
  val resolver = PackageResolver.loadPackageDirectory(packages, extraDirs: _*)
  lazy val gameIDs = GameID.loadGameIDs(resolver)

  def loadGameId(gameId: String) = new GameManager(resolver.loadGameId(gameId))
  def loadGameId(gameId: GameID) = new GameManager(resolver.loadGameId(gameId.name))
}
object PackageManager {
  lazy val default = new PackageManager(Paths.get("packages"), Paths.get("PrincessEdit.pkg"))
}