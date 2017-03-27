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

val commonSettings = versionWithGit ++ Seq(
  // Organization configuration
  homepage := Some(url("https://github.com/Lymia/PrincessEdit")),
  licenses := Seq("MIT License" -> url("http://www.opensource.org/licenses/mit-license.php")),

  // Repositories
  resolvers += Resolver.mavenLocal,
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
  scalacOptions ++= "-Xlint -target:jvm-1.8 -opt:l:classpath -deprecation -unchecked".split(" ").toSeq
)

lazy val pesudoloc = project in file("modules/pseudolocalization-tool")

lazy val lua = project in file("modules/lua") settings (commonSettings ++ Seq(
  organization := "moe.lymia",
  name := "lua"
))

lazy val corePkg = project in file("modules/corepkg") settings (commonSettings ++ Seq(
  organization := "moe.lymia.princessedit",
  name := "prinecss-edit-corepkg"
))

val swtArtifact =
  "org.eclipse.swt." + ((sys.props("os.name"), sys.props("os.arch")) match {
    case ("Linux", "amd64" | "x86_64") => "gtk.linux.x86_64"
    case ("Linux", _) => "gtk.linux.x86"
    case ("Mac OS X", "amd64" | "x86_64") => "cocoa.macosx.x86_64"
    case (os, "amd64") if os.startsWith("Windows") => "win32.win32.x86_64"
    case (os, _) if os.startsWith("Windows") => "win32.win32.x86"
    case (os, arch) => sys.error("Cannot obtain lib for OS '" + os + "' and architecture '" + arch + "'")
  })
val swtDep =
  ("bundle" % swtArtifact % "3.105.2.v20161122-0613"
      exclude("package", "org.mozilla.xpcom")
      exclude("package", "org.eclipse.swt.accessibility2"))

lazy val swt = project in file("modules/swt") settings (commonSettings ++ Seq(
  organization := "moe.lymia.princessedit",
  name := "princess-edit-swt",

  libraryDependencies += swtDep % "compile"
))

lazy val jface = project in file("modules/jface") settings (commonSettings ++ Seq(
  organization := "moe.lymia.princessedit",
  name := "princess-edit-jface",

  libraryDependencies += "bundle" % "org.eclipse.osgi" % "3.11.3.v20170209-1843",
  libraryDependencies += "bundle" % "org.eclipse.equinox.common" % "3.8.0.v20160509-1230",
  libraryDependencies += "bundle" % "org.eclipse.jface" % "3.12.2.v20170113-2113"
    exclude("bundle", "org.eclipse.equinox.bidi")
)) dependsOn swt

lazy val core = project in file("modules/core") settings (commonSettings ++ Seq(
  organization := "moe.lymia.princessedit",
  name := "princess-edit-core",
  libraryDependencies +=  "org.ini4j" % "ini4j" % "0.5.2"
)) dependsOn corePkg dependsOn lua dependsOn pesudoloc

lazy val render = project in file("modules/render") settings (commonSettings ++ Seq(
  organization := "moe.lymia.princessedit",
  name := "princess-edit-render",

  libraryDependencies += "org.scala-lang.modules" %% "scala-xml" % "1.0.6",
  libraryDependencies += "org.jfree" % "jfreesvg" % "3.2"
)) dependsOn core dependsOn swt

lazy val editor = project in file("modules/editor") settings (commonSettings ++ Seq(
  organization := "moe.lymia.princessedit",
  name := "princess-edit-editor",

  libraryDependencies += "com.lihaoyi" %% "scalarx" % "0.3.2",
  libraryDependencies += "com.typesafe.play" %% "play-json" % "2.6.0-M5",

  libraryDependencies += "bundle" % "org.eclipse.nebula.widgets.pgroup" % "1.0.0.201703081533",
  libraryDependencies += "bundle" % "org.eclipse.nebula.widgets.gallery" % "1.0.0.201703081533"
)) dependsOn core dependsOn render dependsOn swt dependsOn jface

lazy val cli = project in file("modules/cli") settings (commonSettings ++ Seq(
  organization := "moe.lymia.princessedit",
  name := "princess-edit-cli",

  libraryDependencies += "com.github.scopt" %% "scopt" % "3.5.0"
)) dependsOn editor

lazy val loader = project in file("modules/loader") settings (commonSettings ++ Seq(
  organization := "moe.lymia.princessedit",
  name := "princess-edit-loader",
  javacOptions ++= Seq("-source", "1.8", "-target", "1.8"),

  autoScalaLibrary := false,
  crossPaths := false
))

lazy val princessEdit = project in file(".") settings (commonSettings ++ Seq(
  organization := "moe.lymia.princessedit",
  name := "princess-edit",

  fork in run := true,
  envVars in run += ("SWT_GTK3", "0"),

  mainClass in (Compile,run) := Some("moe.lymia.princess.PrincessEdit"),

  libraryDependencies += swtDep
)) dependsOn cli