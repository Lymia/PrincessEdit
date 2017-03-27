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

package moe.lymia.princess.util

import java.util.{Date, Properties}

trait VersionInfoSource {
  def apply(key: String, default: String): String
}
object VersionInfoSource {
  def getPropertySource(resource: String) = {
    val stream = IOUtils.getResource(resource)
    if(stream == null) NullSource else {
      val prop = new Properties()
      prop.load(stream)
      PropertiesSource(prop)
    }
  }
}

object NullSource extends VersionInfoSource {
  def apply(key: String, default: String) = default
}
final case class PropertiesSource(prop: Properties) extends VersionInfoSource {
  def apply(key: String, default: String) = {
    val p = prop.getProperty(key)
    if(p == null || p.trim.isEmpty) default else p
  }
}

class VersionInfo(properties: VersionInfoSource) {
  def this(resource: String) = this(VersionInfoSource.getPropertySource(resource))

  lazy val majorVersion  = properties("princessedit.version.major", "-1").toInt
  lazy val minorVersion  = properties("princessedit.version.minor", "-1").toInt
  lazy val patchVersion  = properties("princessedit.version.patch", "0").toInt

  lazy val versionSuffix = properties("princessedit.version.suffix", "0")
  lazy val commit        = properties("princessedit.version.commit", "<unknown>")
  lazy val treeStatus    = properties("build.treestatus", "<clean>")
  lazy val versionString = properties("princessedit.version.string", "<unknown>")
  lazy val isDirty       = properties("princessedit.version.clean", "false") == "false"

  lazy val buildDate     = new Date(properties("build.time", "0").toLong)
  lazy val buildUser     = properties("build.user", "<unknown>")
}
object VersionInfo extends VersionInfo("version.properties") {
  def loadFromResource(resource: String) = new VersionInfo(VersionInfoSource.getPropertySource(resource))
}