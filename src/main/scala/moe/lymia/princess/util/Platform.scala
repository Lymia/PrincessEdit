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

package moe.lymia.princess.util

import com.sun.jna.platform.win32.{Shell32Util, ShlObj}

import java.nio.file.{Path, Paths}

sealed trait Platform {
  val configurationRoot: Path
  def getConfigDirectory(name: String) = configurationRoot.resolve(name)
}
object Platform {
  case object Windows extends Platform {
    override lazy val configurationRoot: Path =
      Paths.get(Shell32Util.getFolderPath(ShlObj.CSIDL_LOCAL_APPDATA))
  }
  case object MacOS extends Platform {
    override lazy val configurationRoot: Path =
      Paths.get(System.getProperty("user.home")).resolve("Library/Preferences")
  }
  case object Linux extends Platform {
    override lazy val configurationRoot: Path =
      System.getenv("XDG_CONFIG_HOME") match {
        case null => Paths.get(System.getProperty("user.home")).resolve(".config")
        case str  => Paths.get(str)
      }
  }

  lazy val platformOption = sys.props("os.name") match {
    case null => None
    case x if x.startsWith("Windows ") => Some(Windows)
    case x if x.startsWith("Mac ") => Some(MacOS)
    case "Linux" => Some(Linux)
    case _ => None
  }
  def platform = platformOption.get
}
