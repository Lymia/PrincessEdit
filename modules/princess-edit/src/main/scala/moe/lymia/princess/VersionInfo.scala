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

import moe.lymia.princess.util.IOUtils

import java.util.{Date, Properties}

class VersionInfo private (properties: VersionInfoSource) {
  private def this(resource: String) = this(VersionInfoSource.getPropertySource(resource))

  lazy val commit: String = properties("princessedit.version.commit", "<unknown>")
  lazy val versionString: String = properties("princessedit.version.string", "<unknown>")
  lazy val isDirty: Boolean = properties("princessedit.version.clean", "false") == "false"

  lazy val buildId: String = properties("build.id", "<unknown>")
  lazy val buildDate: Date = new Date(properties("build.time", "0").toLong)
  lazy val buildDateStr: String = properties("build.timestr", "<unknown>")
  lazy val buildUser: String = properties("build.user", "<unknown>")
}
object VersionInfo extends VersionInfo("version.properties") {
  def loadFromResource(resource: String) = new VersionInfo(VersionInfoSource.getPropertySource(resource))
}

private trait VersionInfoSource {
  def apply(key: String, default: String): String
}
private object VersionInfoSource {
  def getPropertySource(resource: String): VersionInfoSource = {
    val stream = IOUtils.getResource(resource)
    if(stream == null) NullSource else {
      val prop = new Properties()
      prop.load(stream)
      PropertiesSource(prop)
    }
  }
}
private object NullSource extends VersionInfoSource {
  def apply(key: String, default: String): String = default
}
private final case class PropertiesSource(prop: Properties) extends VersionInfoSource {
  def apply(key: String, default: String): String = {
    val p = prop.getProperty(key)
    if(p == null || p.trim.isEmpty) default else p
  }
}