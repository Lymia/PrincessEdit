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

package moe.lymia.princess

import com.sun.jna.platform.win32.{Shell32Util, ShlObj}

import java.io.File
import java.nio.file.{Path, Paths}

sealed trait Platform {
  val configurationRoot: Path
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

  lazy val platformOption: Option[Platform] = sys.props("os.name") match {
    case null => None
    case x if x.startsWith("Windows ") => Some(Windows)
    case x if x.startsWith("Mac ") => Some(MacOS)
    case "Linux" => Some(Linux)
    case _ => None
  }
  def platform: Platform = platformOption.get
}

object Environment {
  val isNativeImage: Boolean = System.getProperty("org.graalvm.nativeimage.kind") != null
  private lazy val isSbtLaunch: Boolean =
    System.getProperty("princessedit.baseDirectory") != null &&
    System.getProperty("princessedit.native.bin") != null
  private lazy val isUniversalLaunch: Boolean =
    System.getProperty("moe.lymia.princess.startedFromLoader") != null &&
    System.getProperty("moe.lymia.princess.rootDirectory") != null &&
    System.getProperty("moe.lymia.princess.nativeBinary") != null

  private lazy val configurationRoot = Platform.platform.configurationRoot
  def configDirectory(name: String): Path = configurationRoot.resolve(name)

  private lazy val nativeImageExecutableDirectory: Path =
    new File(PrincessEdit.getClass.getProtectionDomain.getCodeSource.getLocation.toURI).toPath.getParent
  private lazy val sbtBaseDirectory =
    Paths.get(System.getProperty("princessedit.baseDirectory"))
  private lazy val universalRootDirectory =
    Paths.get(System.getProperty("moe.lymia.princess.rootDirectory"))

  lazy val rootDirectory: Path =
    if (isNativeImage) nativeImageExecutableDirectory
    else if (isUniversalLaunch) universalRootDirectory
    else if (isSbtLaunch) sbtBaseDirectory
    else sys.error("Could not locate root directory!")

  lazy val libDirectory: Path =
    if (isNativeImage) nativeImageExecutableDirectory
    else if (isUniversalLaunch) universalRootDirectory.resolve("lib")
    else if (isSbtLaunch) sbtBaseDirectory.resolve("modules")
    else sys.error("Could not locate library directory!")

  lazy val nativeLibrary: Path =
    if (isNativeImage) nativeImageExecutableDirectory.resolve(Platform.platform match {
      case Platform.Windows => "princessedit_native.windows.x86_64.dll"
      case Platform.MacOS => "libprincessedit_native.macos.x86_64.dylib"
      case Platform.Linux => "libprincessedit_native.linux.x86_64.so"
    })
    else if (isUniversalLaunch) Paths.get(System.getProperty("moe.lymia.princess.nativeBinary"))
    else if (isSbtLaunch) Paths.get(System.getProperty("princessedit.native.bin"))
    else sys.error("Could not locate native library!")
}
