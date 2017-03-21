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
import Config._
import ProguardBuild.Keys._

val commonSettings = versionWithGit ++ Seq(
  // Organization configuration
  organization := "moe.lymia",
  homepage := Some(url("https://github.com/Lymia/PrincessEdit")),
  licenses := Seq("MIT License" -> url("http://www.opensource.org/licenses/mit-license.php")),

  // Repositories
  resolvers += Resolver.mavenLocal,

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
  proguardConfig := "config.pro",

  fork in run := true,
  envVars in run += ("SWT_GTK3", "0"),

  libraryDependencies += "org.scala-lang.modules" %% "scala-xml" % "1.0.6",
  libraryDependencies += "org.scala-lang" % "scala-reflect" % config_scalaVersion,
  libraryDependencies += "org.jfree" % "jfreesvg" % "3.2",
  libraryDependencies += "com.github.scopt" %% "scopt" % "3.5.0",
  libraryDependencies += "com.lihaoyi" %% "scalarx" % "0.3.2",
  libraryDependencies += "com.typesafe.play" %% "play-json" % "2.6.0-M3",
  libraryDependencies +=  "org.ini4j" % "ini4j" % "0.5.2",

  // SWT Resolver
  // Some code from http://stackoverflow.com/a/12509004/1733590
  resolvers ++= {
    def updateSiteResolver(name: String, url: String) = {
      val resolver = new org.apache.ivy.osgi.updatesite.UpdateSiteResolver
      resolver.setName(name)
      resolver.setUrl(url)
      new RawRepository(resolver)
    }
    Seq(updateSiteResolver("Eclipse updates site", "http://download.eclipse.org/eclipse/updates/4.6"),
        updateSiteResolver("Nebula update site", "http://download.eclipse.org/nebula/releases/1.2.0/"))
  },
  libraryDependencies += {
    val os = (sys.props("os.name"), sys.props("os.arch")) match {
      case ("Linux", "amd64" | "x86_64") => "gtk.linux.x86_64"
      case ("Linux", _) => "gtk.linux.x86"
      case ("Mac OS X", "amd64" | "x86_64") => "cocoa.macosx.x86_64"
      case ("Mac OS X", _) => "cocoa.macosx.x86"
      case (os, "amd64") if os.startsWith("Windows") => "win32.win32.x86_64"
      case (os, _) if os.startsWith("Windows") => "win32.win32.x86"
      case (os, arch) => sys.error("Cannot obtain lib for OS '" + os + "' and architecture '" + arch + "'")
    }
    val artifact = "org.eclipse.swt." + os
    ("bundle" % artifact % "3.105.2.v20161122-0613"
      exclude("package", "org.mozilla.xpcom")
      exclude("package", "org.eclipse.swt.accessibility2"))
  },
  libraryDependencies += "bundle" % "org.eclipse.osgi" % "3.11.2.v20161107-1947",
  libraryDependencies += "bundle" % "org.eclipse.equinox.common" % "3.8.0.v20160509-1230",
  libraryDependencies += "bundle" % "org.eclipse.jface" % "3.12.1.v20160923-1528"
    exclude("bundle", "org.eclipse.equinox.bidi"),
  libraryDependencies += "bundle" % "org.eclipse.nebula.widgets.pgroup" % "1.0.0.201703081533",
  libraryDependencies += "bundle" % "org.eclipse.nebula.widgets.compositetable" % "1.0.0.201703081533",
  libraryDependencies += "bundle" % "org.eclipse.nebula.widgets.gallery" % "1.0.0.201703081533",
  ignoreDuplicate += "^\\.api_description$"
) ++ VersionBuild.settings ++ CodeGeneration.settings)

Launch4JBuild.settings
Launch4JBuild.Keys.launch4jSourceJar := (ProguardKeys.proguard in Proguard in princessEdit).value.head

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

    IO.copyFile(file("project/dist_README.md"), outDir / "README.txt")
    IO.copyFile(file("project/LICENSE.md"), outDir / "LICENSE.txt")
    IO.copyFile(file("project/NOTICE.md"), outDir / "NOTICE.txt")

    IO.copyFile(Launch4JBuild.Keys.launch4jOutput.value, outDir / "PrincessEdit.exe")
    IO.write(outDir / "PrincessEdit.sh",
      """#!/bin/sh
        |cd "$(dirname "$0")"
        |java -jar PrincessEdit.exe "$@"
      """.stripMargin)
    (outDir / "PrincessEdit.sh").setExecutable(true)

    for(file <- IO.listFiles(file("packages")) if file.isDirectory)
      IO.copy(Path.allSubpaths(file).filter(!_._2.startsWith(".git/"))
                                    .map   (x => x.copy(_2 = outDir / "packages" / file.getName / x._2)))
    IO.zip(Path.allSubpaths(file("PrincessEdit.pedit-pkg")), outDir / "PrincessEdit.pedit-pkg")

    // we call out to zip to save the executable flag for *nix
    if(zipOut.exists) IO.delete(zipOut)
    Utils.runProcess(Seq("zip", "-r", zipOut, dirName), dir)

    zipOut
  }

  streams.value.log.info(s"Output written to: $zipOut")
}