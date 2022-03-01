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

import sbt.Keys._
import sbt._

import scala.sys.process._

val version_baseVersion = "0.1.0"
val config_scalaVersion = "2.13.8"

// Process helper functions
def runProcess(p: Seq[Any], cd: File): Unit = {
  println(s"Running process ${p.map(_.toString).mkString(" ")} in $cd")
  val code = Process(p.map(_.toString), cd).!
  if (code != 0) sys.error(s"Process ${p.head} returned non-zero return value! (ret: $code)")
}
def runProcessResult(p: Seq[Any], cd: File): String = {
  println(s"Running process ${p.map(_.toString).mkString(" ")} in $cd")
  Process(p.map(_.toString), cd).!!
}

def catchOr[T](b: => T)(v: => T): T = try {
  v
} catch {
  case e: Exception =>
    e.printStackTrace()
    b
}

val osName = sys.props("os.name") match {
  case os if os.startsWith("Windows") => "windows"
  case "Mac OS X" => "macos"
  case "Linux" => "linux"
}

// Actual core definition
lazy val princessEdit = project in file(".") enablePlugins NativeImagePlugin settings (commonSettings ++ ResourceGenerators.settings ++ Seq(
  organization := "moe.lymia.princess",
  name := "princess-edit",

  nativeImageOptions ++= Seq(
    // basic image options
    "--no-fallback", "-H:-ParseRuntimeOptions",

    // configuration directory
    s"-H:ConfigurationFileDirectories=${baseDirectory.value / "native-image-configs" / osName}",

    // compile options
    "-H:CPUFeatures=CX8,CMOV,FXSR,MMX,SSE,SSE2,SSE3,SSE4A,SSE4_1,SSE4_2,POPCNT,TSC",

    // remove unneeded services and other code size optimizations
    "-H:-EnableSignalAPI", "-H:-EnableWildcardExpansion", "-R:-EnableSignalHandling", "-H:-EnableLoggingFeature",
    "-H:-IncludeMethodData",
  ),
  run / fork := true,
  run / envVars += ("PRINCESS_EDIT_SBT_LAUNCH_BASE_DIRECTORY", baseDirectory.value.toString),
  run / javaOptions ++= (if (osName == "macos") Seq("-XstartOnFirstThread") else Seq()),

  // Scala modules
  libraryDependencies += "org.scala-lang" % "scala-reflect" % config_scalaVersion,
  libraryDependencies += "org.scala-lang.modules" %% "scala-xml" % "2.0.1",

  // Utility libraries
  libraryDependencies += "commons-codec" % "commons-codec" % "1.15",
  libraryDependencies += "com.google.guava" % "guava" % "31.0.1-jre",
  libraryDependencies += "org.scalaz" %% "scalaz-core" % "7.3.6",
  libraryDependencies += "com.lihaoyi" %% "scalarx" % "0.4.3",

  // Parsing libraries
  libraryDependencies += "com.typesafe.play" %% "play-json" % "2.10.0-RC5",
  libraryDependencies += "tech.sparse" %% "toml-scala" % "0.2.2",

  // Misc libraries
  libraryDependencies += "org.jfree" % "jfreesvg" % "3.4.2",
  libraryDependencies += "com.github.scopt" %% "scopt" % "4.0.1",

  // JNA libraries
  libraryDependencies += "net.java.dev.jna" % "jna" % "5.10.0",
  libraryDependencies += "net.java.dev.jna" % "jna-platform" % "5.10.0",

  // Nebula widgets
  libraryDependencies += "bundle" % "org.eclipse.nebula.widgets.pgroup" % "1.0.0.202202012159",
  libraryDependencies += "bundle" % "org.eclipse.nebula.widgets.gallery" % "1.0.0.202202012159",
)) dependsOn swt dependsOn xscalawt dependsOn lua dependsOn native

val gitBaseVersion = TaskKey[String]("git-base-version")
val gitUncommittedChanges = TaskKey[Boolean]("git-uncommitted-changes")
val commonSettings = versionWithGit ++ Seq(
  // Organization configuration
  homepage := Some(url("https://github.com/Lymia/PrincessEdit")),
  licenses := Seq("MIT License" -> url("http://www.opensource.org/licenses/mit-license.php")),

  // Repositories
  ThisBuild / useCoursier := false,
  externalIvySettings(baseDirectory(_ / ".." / ".." / "ivysettings.xml")),
  resolvers += Resolver.mavenLocal,

  // Git versioning
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
  javacOptions ++= "-source 11 -target 17".split(" ").toSeq,
  scalacOptions ++= "-Xlint -target:17 -opt:l:inline -deprecation -unchecked".split(" ").toSeq,

  // JGit doesn't like some of the configurations the developer herself uses. :(
  gitBaseVersion := version_baseVersion,
  gitUncommittedChanges := catchOr(false) {
    runProcessResult(Seq("git", "status", "--porcelain"), baseDirectory.value).nonEmpty
  },
  version := catchOr(s"$version_baseVersion-dev_UNKNOWN") {
    val gitVersion = runProcessResult(Seq("git", "describe", "--always", "--dirty=-DIRTY"), baseDirectory.value)
    // blah
  },
)

lazy val lua = project in file("modules/lua") settings (commonSettings ++ Seq(
  organization := "moe.lymia",
  name := "lua"
))

lazy val swt = project in file("modules/swt") settings (commonSettings ++ Seq(
  organization := "moe.lymia.princess",
  name := "princess-edit-swt",

  libraryDependencies += swtDep("org.eclipse.swt"),
  libraryDependencies += swtDep(s"org.eclipse.swt.$swtPlafSuffix"),

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
def swtDep(artifact: String) =
  ("bundle" % artifact % "3.118.0.v20211123-0851"
    exclude("package", "org.mozilla.xpcom")
    exclude("package", "org.eclipse.swt.accessibility2"))
val swtPlafSuffix = sys.props("os.name") match {
  case os if os.startsWith("Windows") => "win32.win32.x86_64"
  case "Mac OS X" => "cocoa.macosx.x86_64"
  case "Linux" => "gtk.linux.x86_64"
}

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

lazy val native = project in file("modules/native") settings (commonSettings ++ Seq(
  organization := "moe.lymia.princess",
  name := "princess-edit-native",

  libraryDependencies += "commons-codec" % "commons-codec" % "1.15",

  Compile / resourceGenerators += Def.task {
    val (cargoOut, fileName) = osName match {
      case "windows" => ("princessedit_native.dll", "princessedit_native.x86_64.dll")
      case "macos" => ("libprincessedit_native.dylib", "libprincessedit_native.x86_64.dylib")
      case "linux" => ("libprincessedit_native.so", "libprincessedit_native.x86_64.so")
    }
    runProcess(Seq("cargo", "build", "--release"), baseDirectory.value / "src" / "native")
    val sourcePath = baseDirectory.value / "src" / "native" / "target" / "release" / cargoOut
    val targetPath = (Compile / resourceManaged).value / "moe" / "lymia" / "princess" / "native" / fileName
    if (!sourcePath.exists()) sys.error(s"rustc did not produce a binary?")
    IO.copyFile(sourcePath, targetPath)
    Seq(targetPath)
  },
  cleanFiles += baseDirectory.value / "src" / "native" / "target",
))

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
    IO.copyFile(nativeImageFile, outDir / nativeImageFile.name.replace("princess-edit", "PrincessEdit"))

    IO.createDirectory(outDir / "lib")
    runProcess(Seq("zip", "-r", outDir / "lib/core.pedit-pkg", "core.pedit-pkg"), baseDirectory.value / "lib")

    IO.createDirectory(outDir / "packages")
    for (pkg <- Seq("cards-against-humanity.pedit-pkg"))
      IO.copyDirectory(file("packages") / pkg, outDir / "packages" / pkg)

    // we call out to zip to save the executable flag for *nix
    if (zipOut.exists) IO.delete(zipOut)
    runProcess(Seq("zip", "-r", zipOut, dirName), dir)

    zipOut
  }

  streams.value.log.info(s"Output written to: $zipOut")
}