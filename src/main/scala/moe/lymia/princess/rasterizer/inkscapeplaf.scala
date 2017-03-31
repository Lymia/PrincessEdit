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

package moe.lymia.princess.rasterizer

import java.net.URI
import java.nio.file.{Files, Path, Paths}

import moe.lymia.princess.util.IOUtils

trait InkscapePlatform {
  protected def getPathFromProperty(prop: String) = sys.props(prop) match {
    case null => Paths.get(".")
    case url => Paths.get(new URI(url))
  }

  def locateBinary(): Seq[String]
}

// TODO: Store these in some configuration file
// TODO: Proper GUI error messages
object InkscapePlatform {
  val instance: InkscapePlatform = sys.props("os.name") match {
    case null => ???
    case "Linux" => InkscapeLinuxPlatform
    case x if x.startsWith("Windows ") => InkscapeWindowsPlatform
    case _ => ???
  }
}

object InkscapeWindowsPlatform extends InkscapePlatform {
  private val searchDirs = Seq(
    Paths.get(System.getenv("ProgramFiles(X86)")),
    Paths.get(System.getenv("ProgramFiles")),
    Paths.get(System.getenv("ProgramFiles").replace(" (x86)", "")),
    getPathFromProperty("moe.lymia.princess.rootDirectory"),
    getPathFromProperty("moe.lymia.princess.libDirectory")
  ).filter(x => Files.exists(x))

  private def checkDirectory(path: Path) =
    Files.exists(path.resolve("inkscape.exe")) && Files.exists(path.resolve("inkscape.com"))
  override lazy val locateBinary =
    for(dir <- searchDirs if Files.isDirectory(dir);
        search <- IOUtils.list(dir) if checkDirectory(search))
      yield (".\\Inkscape.exe", search.toAbsolutePath.toString)
}

object InkscapeLinuxPlatform extends InkscapePlatform {
  override val locateBinary = Seq(("inkscape", "."))
}