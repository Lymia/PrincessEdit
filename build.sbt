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
import sbt.Keys._
import sbt._

val osName = sys.props("os.name") match {
  case os if os.startsWith("Windows") => "windows.x86_64"
  case "Mac OS X" => "macosx.x86_64"
  case "Linux" => "linux.x86_64"
}
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
  ("bundle" % artifact % "3.118.0.v20211123-0851"
    exclude("package", "org.mozilla.xpcom")
    exclude("package", "org.eclipse.swt.accessibility2"))

lazy val lua = project in file("modules/lua") settings (commonSettings ++ Seq(
  organization := "moe.lymia",
  name := "lua"
))
lazy val swt = project in file("modules/swt") settings (commonSettings ++ Seq(
  organization := "moe.lymia.princessedit",
  name := "princess-edit-swt",

  libraryDependencies += swtDep(swtArtifact),
  libraryDependencies += "bundle" % "org.eclipse.osgi" % "3.17.100.v20211104-1730",
  libraryDependencies += "bundle" % "org.eclipse.osgi.services" % "3.10.200.v20210723-0643"
    // provided by default in JDK 8
    exclude("package", "javax.xml.parsers"),
  libraryDependencies += "bundle" % "org.eclipse.equinox.common" % "3.15.100.v20211021-1418",
  libraryDependencies += "bundle" % "org.eclipse.jface" % "3.24.0.v20211110-1517"
    exclude("bundle", "org.eclipse.equinox.bidi")
    // provided by default in JDK 8
    exclude("package", "javax.xml.parsers")
    exclude("package", "org.w3c.dom")
    exclude("package", "org.xml.sax"),
))
lazy val xscalawt = project in file("modules/xscalawt") settings (commonSettings ++ Seq(
  organization := "moe.lymia",
  name := "xscalawt",

  Compile / scalaSource := baseDirectory.value / "com.coconut_palm_software.xscalawt" / "src",

  unmanagedSources / excludeFilter := HiddenFileFilter || new FileFilter() {
    override def accept(pathname: File): Boolean = {
      val src = pathname.toString
      src.contains("XScalaWTBinding.scala") || src.replace('\\', '/').contains("/examples/")
    }
  },

  libraryDependencies += "org.scala-lang" % "scala-reflect" % scalaVersion.value
)) dependsOn swt
lazy val princessEdit = project in file(".") enablePlugins NativeImagePlugin settings (commonSettings ++ ResourceGenerators.settings ++ Seq(
  organization := "moe.lymia.princessedit",
  name := "princess-edit",

  nativeImageOptions += "--no-fallback",
  nativeImageOptions += s"-H:ConfigurationFileDirectories=${baseDirectory.value / "config-dir"}",

  run / fork := true,
  run / envVars += ("SWT_GTK3", "0"),

  libraryDependencies += "org.scala-lang" % "scala-reflect" % config_scalaVersion,
  libraryDependencies += "org.scala-lang.modules" %% "scala-xml" % "2.0.1",
  libraryDependencies += "org.jfree" % "jfreesvg" % "3.4.2",
  libraryDependencies += "com.lihaoyi" %% "scalarx" % "0.4.3",
  libraryDependencies += "com.typesafe.play" %% "play-json" % "2.10.0-RC5",
  libraryDependencies += "com.github.scopt" %% "scopt" % "4.0.1",
  libraryDependencies += "tech.sparse" %% "toml-scala" % "0.2.2",
  libraryDependencies += "org.scalaz" %% "scalaz-core" % "7.3.6",

  // Apache Commons
  libraryDependencies += "commons-codec" % "commons-codec" % "1.15",

  // JNA libraries
  libraryDependencies += "net.java.dev.jna" % "jna" % "5.10.0",
  libraryDependencies += "net.java.dev.jna" % "jna-platform" % "5.10.0",

  // Nebula widgets
  libraryDependencies += "bundle" % "org.eclipse.nebula.widgets.pgroup" % "1.0.0.202202012159",
  libraryDependencies += "bundle" % "org.eclipse.nebula.widgets.gallery" % "1.0.0.202202012159",
)) dependsOn swt dependsOn xscalawt dependsOn lua

val commonSettings = versionWithGit ++ Seq(
  // Organization configuration
  homepage := Some(url("https://github.com/Lymia/PrincessEdit")),
  licenses := Seq("MIT License" -> url("http://www.opensource.org/licenses/mit-license.php")),

  // Misc configuration
  exportJars := true,

  // Repositories
  ThisBuild / useCoursier := false,
  externalIvySettings(baseDirectory(_ / ".." / ".." / "ivysettings.xml")),
  resolvers += Resolver.mavenLocal,

  // Git versioning
  git.baseVersion := version_baseVersion,
  git.uncommittedSignifier := Some("DIRTY"),
  git.formattedShaVersion := {
    val base = git.baseVersion.?.value
    val suffix = git.makeUncommittedSignifierSuffix(git.gitUncommittedChanges.value, git.uncommittedSignifier.value)
    git.gitHeadCommit.value map { rawSha =>
      val sha = "dev_" + rawSha.substring(0, 8)
      git.defaultFormatShaVersion(base, sha, suffix)
    }
  },

  // Scala configuration
  scalaVersion := config_scalaVersion,
  scalacOptions ++= "-Xlint -target:jvm-1.8 -opt:l:inline -deprecation -unchecked".split(" ").toSeq
)

externalIvySettings(baseDirectory(_ / "ivysettings.xml"))

InputKey[Unit]("dist") := {
  val path = crossTarget.value / "dist"
  IO.createDirectory(path)

  val zipOut = IO.withTemporaryDirectory { dir =>
    val dirName = s"princess-edit-${(princessEdit / version).value}-${osName}"
    val zipOut = path / s"$dirName.zip"
    val outDir = dir / dirName

    IO.createDirectory(outDir)

    def fixEndings(s: String) = s.replace("\r\n", "\n").replace("\n", "\r\n")
    IO.write(outDir / "README.txt", fixEndings(IO.read(file("project/dist_README.md"))))
    IO.write(outDir / "NOTICE.txt", fixEndings(IO.read(file("project/dist_NOTICE.md"))))

    val nativeImageFile = (princessEdit / nativeImage).value
    IO.copyFile(nativeImageFile, outDir / nativeImageFile.name)

    IO.createDirectory(outDir / "lib")
    Utils.runProcess(Seq("zip", "-r", outDir / "lib/core.pedit-pkg", "core.pedit-pkg"), baseDirectory.value / "lib")

    IO.createDirectory(outDir / "packages")
    for (pkg <- Seq("cards-against-humanity.pedit-pkg"))
      IO.copyDirectory(file("packages") / pkg, outDir / "packages" / pkg)

    // we call out to zip to save the executable flag for *nix
    if (zipOut.exists) IO.delete(zipOut)
    Utils.runProcess(Seq("zip", "-r", zipOut, dirName), dir)

    zipOut
  }

  streams.value.log.info(s"Output written to: $zipOut")
}