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

import Config._
import Utils._
import com.typesafe.sbt.SbtGit._
import sbt.Keys._
import sbt._

import java.net.InetAddress
import java.text.DateFormat
import java.util.{Locale, UUID}
import scala.collection.mutable.ArrayBuffer
import scala.sys.process._

object ResourceGenerators {
  private def tryProperty(s: => String) = try {
    val str = s
    if(str == null) "<null>" else str
  } catch {
    case t: Throwable =>
      t.printStackTrace()
      s"<unknown>"
  }
  private def propertyFromProcess(proc: String*) = tryProperty {
    val output = new ArrayBuffer[String]()
    val logger = new ProcessLogger {
      override def buffer[T](f: => T): T = f
      override def err(s: => String): Unit = output += s
      override def out(s: => String): Unit = output += s
    }
    assertProcess(proc ! logger)
    output.mkString("\n")
  }

  object Keys {
    val versionData = TaskKey[Map[String, String]]("resource-version-data")
    val versionFile = TaskKey[File]("resource-version-file")
  }
  import Keys._

  val settings = Seq(
    versionData := {
      val VersionRegex(major, minor, _, patch, _, suffix) = version.value
      val dateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.US)
      Map(
        "princessedit.version.string" -> version.value,
        "princessedit.version.major"  -> major,
        "princessedit.version.minor"  -> minor,
        "princessedit.version.patch"  -> patch,
        "princessedit.version.suffix" -> suffix,
        "princessedit.version.commit" -> git.gitHeadCommit.value.getOrElse("<unknown>"),
        "princessedit.version.clean"  -> (!git.gitUncommittedChanges.value).toString,

        "princessedit.url"            -> config_home_url,

        "build.id"                    -> UUID.randomUUID().toString,
        "build.os"                    -> tryProperty { System.getProperty("os.name") },
        "build.user"                  -> tryProperty { System.getProperty("user.name")+"@"+
                                                       InetAddress.getLocalHost.getHostName },
        "build.time"                  -> new java.util.Date().getTime.toString,
        "build.timestr"               -> dateFormat.format(new java.util.Date()),
        "build.path"                  -> baseDirectory.value.getAbsolutePath,
        "build.treestatus"            -> propertyFromProcess("git", "status", "--porcelain"),

        "build.version.sbt"           -> sbtVersion.value
      )
    },
    versionFile := {
      val path = crossTarget.value / "version-resource-cache.properties"

      val properties = new java.util.Properties
      for((k, v) <- versionData.value) properties.put(k, v)
      IO.write(properties, "PrincessEdit build information", path)

      path
    },
    Compile / resourceGenerators += Def.task {
      val versionPropertiesPath =
        (Compile / resourceManaged).value / "moe" / "lymia" / "princess" / "version.properties"
      IO.copyFile(versionFile.value, versionPropertiesPath)

      val licenseFilePath =
        (Compile / resourceManaged).value / "moe" / "lymia" / "princess" / "LICENSE.md"
      IO.copyFile(new File("LICENSE.md"), licenseFilePath)

      val readmeFilePath =
        (Compile / resourceManaged).value / "moe" / "lymia" / "princess" / "README.md"
      IO.copyFile(new File("README.md"), readmeFilePath)

      val icoFiles =
        for(file <- IO.listFiles(baseDirectory.value / "project") if file.getName.startsWith("icon-")) yield {
          val target =
            (Compile / resourceManaged).value / "moe" / "lymia" / "princess" / "editor" / "res" / file.getName
          IO.copyFile(file, target)
          target
        }

      Seq(versionPropertiesPath, licenseFilePath, readmeFilePath) ++ icoFiles
    }.taskValue
  )
}
