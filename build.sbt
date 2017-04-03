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

import java.util.Properties

import sbt._
import sbt.Keys._
import Config._

val commonSettings = versionWithGit ++ Seq(
  // Organization configuration
  homepage := Some(url("https://github.com/Lymia/PrincessEdit")),
  licenses := Seq("MIT License" -> url("http://www.opensource.org/licenses/mit-license.php")),

  // Misc configuration
  exportJars := true,

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

lazy val pesudoloc = project in file("modules/pseudolocalization-tool") settings (
  exportJars := true
)

lazy val lua = project in file("modules/lua") settings (commonSettings ++ Seq(
  organization := "moe.lymia",
  name := "lua"
))

lazy val corePkg = project in file("modules/corepkg") settings (commonSettings ++ Seq(
  organization := "moe.lymia.princessedit",
  name := "princess-edit-corepkg"
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
def swtDep(artifact: String) =
  ("bundle" % artifact % config_swt_version
    exclude("package", "org.mozilla.xpcom")
    exclude("package", "org.eclipse.swt.accessibility2"))

lazy val swt = project in file("modules/swt") settings (commonSettings ++ Seq(
  organization := "moe.lymia.princessedit",
  name := "princess-edit-swt",

  libraryDependencies += swtDep(swtArtifact),
  libraryDependencies += "bundle" % "org.eclipse.osgi" % "3.11.3.v20170209-1843",
  libraryDependencies += "bundle" % "org.eclipse.equinox.common" % "3.8.0.v20160509-1230",
  libraryDependencies += "bundle" % "org.eclipse.jface" % "3.12.2.v20170113-2113"
    exclude("bundle", "org.eclipse.equinox.bidi")
))

lazy val xscalawt = project in file("modules/xscalawt") settings (commonSettings ++ Seq(
  organization := "moe.lymia",
  name := "xscalawt",

  scalaSource in Compile := baseDirectory.value / "com.coconut_palm_software.xscalawt" / "src",

  excludeFilter in unmanagedSources := HiddenFileFilter || new FileFilter() {
    override def accept(pathname: File): Boolean = {
      val src = pathname.toString
      src.contains("XScalaWTBinding.scala") || src.replace('\\', '/').contains("/examples/")
    }
  },

  libraryDependencies += "org.scala-lang" % "scala-reflect" % scalaVersion.value
)) dependsOn swt

lazy val princessEdit = project in file(".") settings (commonSettings ++ VersionBuild.settings ++ Seq(
  organization := "moe.lymia.princessedit",
  name := "princess-edit",

  fork in run := true,
  envVars in run += ("SWT_GTK3", "0"),

  libraryDependencies += "org.ini4j" % "ini4j" % "0.5.2",
  libraryDependencies += "org.scala-lang.modules" %% "scala-xml" % "1.0.6",
  libraryDependencies += "org.jfree" % "jfreesvg" % "3.2",
  libraryDependencies += "com.lihaoyi" %% "scalarx" % "0.3.2",
  libraryDependencies += "com.typesafe.play" %% "play-json" % "2.6.0-M5",
  libraryDependencies += "com.github.scopt" %% "scopt" % "3.5.0",

  libraryDependencies += "bundle" % "org.eclipse.nebula.widgets.pgroup" % "1.0.0.201703081533",
  libraryDependencies += "bundle" % "org.eclipse.nebula.widgets.gallery" % "1.0.0.201703081533"
)) dependsOn corePkg dependsOn swt dependsOn xscalawt dependsOn lua dependsOn pesudoloc

lazy val loader = project in file("modules/loader") settings (commonSettings ++ Seq(
  organization := "moe.lymia.princessedit",
  name := "princess-edit-loader",
  javacOptions ++= Seq("-source", "1.8", "-target", "1.8"),

  autoScalaLibrary := false,
  crossPaths := false
))

lazy val dist = project in file("modules/dist") settings (commonSettings ++ Seq(
  libraryDependencies += swtDep("org.eclipse.swt.gtk.linux.x86_64"),
  libraryDependencies += swtDep("org.eclipse.swt.gtk.linux.x86"),
  libraryDependencies += swtDep("org.eclipse.swt.cocoa.macosx.x86_64"),
  libraryDependencies += swtDep("org.eclipse.swt.win32.win32.x86_64"),
  libraryDependencies += swtDep("org.eclipse.swt.win32.win32.x86"),
  autoScalaLibrary := false
))

Launch4JBuild.settings
Launch4JBuild.Keys.launch4jSourceJar := (packageBin in Compile in loader).value

InputKey[Unit]("dist") := {
  val distClasspath = (fullClasspath in Compile).value.filter(_.get(moduleID.key).get.name != swtArtifact)
  def getSWTJar(artifact: String) =
    (fullClasspath in Compile in dist).value.find(_.get(moduleID.key).get.name == artifact).get

  val classPaths = Map(
    "win32-x86"    -> (distClasspath :+ getSWTJar("org.eclipse.swt.win32.win32.x86")),
    "win32-x86_64" -> (distClasspath :+ getSWTJar("org.eclipse.swt.win32.win32.x86_64")),
    "mac-x86_64"   -> (distClasspath :+ getSWTJar("org.eclipse.swt.cocoa.macosx.x86_64")),
    "linux-x86"    -> (distClasspath :+ getSWTJar("org.eclipse.swt.gtk.linux.x86")),
    "linux-x86_64" -> (distClasspath :+ getSWTJar("org.eclipse.swt.gtk.linux.x86_64"))
  )
  val allJars: Set[Attributed[File]] = classPaths.flatMap(_._2).toSet

  def jarName(jar: Attributed[File]) = {
    val mod = jar.get(moduleID.key).get
    val org = if(mod.organization == "bundle") "" else s"${mod.organization}."
    s"$org${mod.name}-${mod.revision}.jar"
  }
  def classPathString(path: Seq[Attributed[File]]) = path.map(jarName).mkString(":")

  val path = crossTarget.value / "dist"
  IO.createDirectory(path)

  val zipOut = IO.withTemporaryDirectory { dir =>
    val dirName = s"princess-edit-${(version in princessEdit).value}"
    val zipOut = path / s"$dirName.zip"
    val outDir = dir / dirName

    IO.createDirectory(outDir)

    def fixEndings(s: String) = s.replace("\r\n", "\n").replace("\n", "\r\n")
    IO.write(outDir / "README.txt", fixEndings(IO.read(file("project/dist_README.md"))))
    IO.write(outDir / "LICENSE.txt", fixEndings(IO.read(file("project/LICENSE.md"))))

    IO.copyFile(Launch4JBuild.Keys.launch4jOutput.value, outDir / "PrincessEdit.exe")
    IO.write(outDir / "PrincessEdit.sh",
      """#!/bin/sh
        |SWT_GTK3=0 java -jar "$(dirname "$0")"/PrincessEdit.exe "$@"
      """.stripMargin)
    (outDir / "PrincessEdit.sh").setExecutable(true)

    IO.createDirectory(outDir / "lib")
    for(jar <- allJars) IO.copyFile(jar.data, outDir / "lib" / jarName(jar))

    val props = new Properties()
    for((name, path) <- classPaths) props.put(name, classPathString(path))
    props.put("main", (mainClass in (Compile, run)).value.get)
    IO.write(props, "Classpath configuration data for PrincessEdit", outDir / "lib" / "manifest.properties")

    IO.createDirectory(outDir / "packages")
    for(file <- IO.listFiles(file("packages")) if file.getName.endsWith(".pedit-pkg"))
      IO.copy(Path.allSubpaths(file).filter(!_._2.startsWith(".git/"))
                                    .map   (x => x.copy(_2 = outDir / "packages" / file.getName / x._2)))

    // we call out to zip to save the executable flag for *nix
    if(zipOut.exists) IO.delete(zipOut)
    Utils.runProcess(Seq("zip", "-r", zipOut, dirName), dir)

    zipOut
  }

  streams.value.log.info(s"Output written to: $zipOut")
}