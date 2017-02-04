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

import java.io.FileOutputStream
import java.util.jar.{JarFile, Pack200}
import java.util.jar.Pack200.Packer
import java.util.zip.{GZIPOutputStream, Deflater}

import sbt._
import sbt.Keys._
import Config._
import ProguardBuild.Keys._
import com.typesafe.sbt.SbtProguard._

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
  scalacOptions ++= "-Xlint -target:jvm-1.8 -opt:l:classpath -deprecation -unchecked".split(" ").toSeq,
  crossPaths := false
)

lazy val princessEdit = project in file(".") settings (commonSettings ++ ProguardBuild.settings ++ Seq(
  name := "princess-edit",

  excludePatterns     += "META-INF/services/.*",

  libraryDependencies += "org.scala-lang.modules" %% "scala-xml" % "1.0.6",
  excludeFiles        += "rootdoc.txt",

  libraryDependencies += "org.apache.xmlgraphics" % "batik-swing"      % config_batikVersion,
  libraryDependencies += "org.apache.xmlgraphics" % "batik-svggen"     % config_batikVersion,
  libraryDependencies += "org.apache.xmlgraphics" % "batik-transcoder" % config_batikVersion,
  ignoreDuplicate     += "org/w3c/dom/.*",

  libraryDependencies += "com.github.scopt" %% "scopt" % "3.5.0",
  libraryDependencies += "org.ini4j" % "ini4j" % "0.5.2",

  proguardConfig := "config.pro"
) ++ VersionBuild.settings)

lazy val loader = project in file("loader") settings (commonSettings ++ Seq(
  name := "princess-edit-loader",
  autoScalaLibrary := false,
  javacOptions ++= Seq("-source", "1.7", "-target", "1.7")
))

Launch4JBuild.settings
Launch4JBuild.Keys.launch4jSourceJar := (Keys.`package` in Compile in loader).value

// Build distribution file
InputKey[Unit]("dist") := {
  val path = crossTarget.value / "dist"
  IO.createDirectory(path)

  val zipOut = IO.withTemporaryDirectory { dir =>
    val dirName = s"princess-edit-${(version in princessEdit).value}"
    val zipOut = path / s"$dirName.zip"
    val outDir = dir / dirName

    IO.createDirectory(outDir)
    IO.createDirectory(outDir / "packages")

    val packer = Pack200.newPacker()
    val p      = packer.properties()
    p.put(Packer.EFFORT, "9")
    p.put(Packer.SEGMENT_LIMIT, "-1")
    p.put(Packer.KEEP_FILE_ORDER, Packer.FALSE)
    p.put(Packer.MODIFICATION_TIME, Packer.LATEST)
    p.put(Packer.UNKNOWN_ATTRIBUTE, Packer.ERROR)

    val gzipOut = new GZIPOutputStream(new FileOutputStream(outDir / "PrincessEdit.pack.gz")) {
      this.`def`.setLevel(Deflater.BEST_COMPRESSION)
    }
    packer.pack(new JarFile((ProguardKeys.proguard in Proguard in princessEdit).value.head), gzipOut)
    gzipOut.finish()

    IO.copyFile(Launch4JBuild.Keys.launch4jOutput.value, outDir / "PrincessEdit.exe")
    IO.write(outDir / "PrincessEdit.sh",
      """#!/bin/sh
        |cd "$(dirname "$0")"
        |java -jar PrincessEdit.exe "$@"
      """.stripMargin)
    (outDir / "PrincessEdit.sh").setExecutable(true)

    IO.zip(Path.allSubpaths(file("PrincessEdit.pkg")), outDir / "PrincessEdit.pkg")

    // we call out to zip to save the executable flag for *nix
    if(zipOut.exists) IO.delete(zipOut)
    Utils.runProcess(Seq("zip", "-r", zipOut, dirName), dir)

    zipOut
  }

  streams.value.log.info(s"Output written to: $zipOut")
}