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

import sbt._
import sbt.Keys._
import com.typesafe.sbt.SbtProguard._

import Config._
import Utils._

import ProguardBuild.Keys._

// Additional keys

val commonSettings = versionWithGit ++ Seq(
  // Organization configuration
  organization := "moe.lymia",
  homepage := Some(url("https://github.com/Lymia/PrincessEdit")),
  licenses := Seq("MIT License" -> url("http://www.opensource.org/licenses/mit-license.php")),

  // Git versioning
  git.baseVersion := version_baseVersion,
  git.uncommittedSignifier := Some("DIRTY"),
  git.formattedShaVersion := {
    val base = git.baseVersion.?.value
    val suffix = git.makeUncommittedSignifierSuffix(git.gitUncommittedChanges.value, git.uncommittedSignifier.value)
    git.gitHeadCommit.value map { rawSha =>
      val sha = "dev_"+rawSha.substring(0, 8)
      git.defaultFormatShaVersion(base, sha, suffix)
    }
  },

  // Scala configuration
  scalaVersion := config_scalaVersion,
  scalacOptions ++= ("-Xlint -target:jvm-1.7 -optimize -deprecation -unchecked").split(" ").toSeq,
  crossPaths := false
)

lazy val princessEdit = project in file(".") settings (commonSettings ++ ProguardBuild.settings ++ Seq(
  name := "princess-edit",

  libraryDependencies += "org.scala-lang.modules" %% "scala-xml" % "1.0.5",
  shadeMappings       += "scala.**" -> "moe.lymia.princess.lib.scala.@1",

  excludeFiles   := Set("library.properties", "rootdoc.txt", "scala-xml.properties"),
  proguardConfig := "config.pro"
) ++ VersionBuild.settings)

// Build distribution file
InputKey[Unit]("dist") := {
  val path = crossTarget.value / "dist"
  def copy(source: File) = {
    val output = path / source.getName
    IO.copyFile(source, output)
    output
  }
  streams.value.log.info(s"Output written to: ${copy((Keys.`package` in Compile in princessEdit).value)}")
}