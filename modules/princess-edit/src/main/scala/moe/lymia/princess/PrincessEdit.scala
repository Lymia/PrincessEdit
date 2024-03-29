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

object PrincessEdit {
  def main(args: Array[String]): Unit = {
    println(s"Princess Edit v${VersionInfo.versionString} (${VersionInfo.buildDateStr}) by Lymia")
    println("Released under the MIT license")
    println("")
    println(s"Commit: ${VersionInfo.commit}")
    println(s"Build ID: ${VersionInfo.buildId}")
    println(s"Java runtime: ${System.getProperty("java.vm.version")}")
    println("")

    // Set a fake java.home on native-image builds.
    if (Environment.isNativeImage && !System.getProperties.contains("java.home"))
      System.getProperties.setProperty("java.home", Environment.rootDirectory.toString)

    // Load native library from paths
    DefaultLogger.trace("Loading native library...")
    System.load(Environment.nativeLibrary.toFile.toString)

    new CLI().main(args)
  }
}

object AppName {
  val PrincessEdit = "Lymia.PrincessEdit.PrincessEdit"
}

object MimeType {
  val CardData = "application/vnd.princessedit-cards+json"
  val Project = "application/vnd.princessedit-project+zip"
}

