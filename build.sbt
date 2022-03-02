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

import java.io.FileInputStream
import java.net.InetAddress
import java.text.DateFormat
import java.util.{Locale, Properties, UUID}
import scala.sys.process._

/*****************\
|* Configuration *|
\*****************/

val version_baseVersion = "0.1.0"
val config_scalaVersion = "2.13.8"

// Project-specific keys
val gitDir = SettingKey[File]("git-dir")
val buildNativeLib = TaskKey[File]("build-native-lib")

/*************\
|* Utilities *|
\*************/

// Process helper functions
def runProcess(p: Seq[Any], cd: File): Unit = {
  println(s"Running process ${p.map(_.toString).mkString(" ")} in $cd")
  val code = Process(p.map(_.toString), cd).!
  if (code != 0) sys.error(s"Process ${p.head} returned non-zero return value! (ret: $code)")
}
def runProcessStr(p: Seq[Any], cd: File): String = {
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
val osArch = sys.props("os.arch") match {
  case "amd64" | "x86_64" => "x86_64"
  case "x86" | "i386" | "i486" | "i586" | "i686" => "x86"
  case "aarch64" => "aarch64"
}
val osTarget = s"$osName-$osArch"

def swtDep(artifact: String) =
  ("bundle" % artifact % "3.118.0.v20211123-0851"
    exclude("package", "org.mozilla.xpcom")
    exclude("package", "org.eclipse.swt.accessibility2"))
val swtPlafSuffix = sys.props("os.name") match {
  case os if os.startsWith("Windows") => "win32.win32.x86_64"
  case "Mac OS X" => "cocoa.macosx.x86_64"
  case "Linux" => "gtk.linux.x86_64"
}

def jarName(jar: Attributed[File]) = {
  val mod = jar.get(moduleID.key).get
  val org = if(mod.organization == "bundle") "" else s"${mod.organization}."
  s"$org${mod.name}-${mod.revision}.jar"
}
def classPathString(path: Seq[Attributed[File]]) = path.map(jarName).mkString(":")

def fixEndings(s: String) = s.replace("\r\n", "\n").replace("\n", "\r\n")

/*********************\
|* Core Build Script *|
\*********************/

lazy val commonSettings = Seq(
  gitDir := baseDirectory.value / ".." / "..",
  exportJars := true,

  // Organization configuration
  homepage := Some(url("https://github.com/Lymia/PrincessEdit")),
  licenses := Seq("MIT License" -> url("http://www.opensource.org/licenses/mit-license.php")),
  version := catchOr(s"0.0.0-UNKNOWN") {
    val raw = runProcessStr(Seq("git", "describe", "--dirty=-DIRTY", "--broken=-BROKEN", "--match=v*"), gitDir.value)
    raw.substring(1).trim
  },

  // Repositories
  ThisBuild / useCoursier := false,
  externalIvySettings(baseDirectory(_ / ".." / ".." / "ivysettings.xml")),
  resolvers += Resolver.mavenLocal,

  // Scala configuration
  scalaVersion := config_scalaVersion,
  javacOptions ++= "-source 11 -target 11".split(" ").toSeq,
  scalacOptions ++= "-Xlint -target:11 -opt:l:inline -deprecation -unchecked".split(" ").toSeq,
)

lazy val princessEdit = project in file("modules/princess-edit") settings (commonSettings ++ Seq(
  organization := "moe.lymia.princess",
  name := "princess-edit",

  run / fork := true,
  run / envVars += ("PRINCESS_EDIT_SBT_LAUNCH_BASE_DIRECTORY", baseDirectory.value.toString),

  run / javaOptions ++= (if (osName == "macos") Seq("-XstartOnFirstThread") else Seq()),
  run / javaOptions += s"-Dprincessedit.native.bin=${(native / buildNativeLib).value}",

  gitDir := baseDirectory.value,

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

  // Generate versioning information
  Compile / resourceGenerators += Def.task {
    val properties = new java.util.Properties
    val dateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.US)
    val data = Map(
      "princessedit.version.string" -> version.value,
      "princessedit.version.commit" -> catchOr("<unknown>") {
        runProcessStr(Seq("git", "rev-parse", "HEAD"), gitDir.value).trim
      },
      "princessedit.version.clean"  -> catchOr(false) {
        runProcessStr(Seq("git", "status", "--porcelain"), gitDir.value).nonEmpty
      },

      "build.id"                    -> UUID.randomUUID().toString,
      "build.user"                  -> catchOr("<unknown>") {
        System.getProperty("user.name")+"@"+ InetAddress.getLocalHost.getHostName
      },
      "build.time"                  -> new java.util.Date().getTime.toString,
      "build.timestr"               -> dateFormat.format(new java.util.Date()),
    )
    for((k, v) <- data) properties.put(k, v.toString)

    val versionPropertiesPath =
      (Compile / resourceManaged).value / "moe" / "lymia" / "princess" / "version.properties"
    IO.write(properties, "PrincessEdit version information", versionPropertiesPath)

    Seq(versionPropertiesPath)
  }.taskValue
)) dependsOn swt dependsOn xscalawt dependsOn lua dependsOn native

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

lazy val lua = project in file("modules/lua") settings (commonSettings ++ Seq(
  organization := "moe.lymia",
  name := "lua"
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

lazy val native = project in file("modules/native") settings (commonSettings ++ Seq(
  organization := "moe.lymia.princess",
  name := "princess-edit-native",

  libraryDependencies += "commons-codec" % "commons-codec" % "1.15",

  buildNativeLib := {
    val (cargoOut, fileName) = osName match {
      case "windows" => ("princessedit_native.dll", "princessedit_native.windows.x86_64.dll")
      case "macos" => ("libprincessedit_native.dylib", "libprincessedit_native.macos.x86_64.dylib")
      case "linux" => ("libprincessedit_native.so", "libprincessedit_native.linux.x86_64.so")
    }
    val targetPath = target.value / fileName
    if (System.getenv("PRINCESS_EDIT_DO_NOT_BUILD_NATIVE") != null) {
      runProcess(Seq("cargo", "build", "--release"), baseDirectory.value / "src" / "native")
      val sourcePath = baseDirectory.value / "src" / "native" / "target" / "release" / cargoOut
      if (!sourcePath.exists()) sys.error(s"rustc did not produce a binary?")
      IO.copyFile(sourcePath, targetPath)
    }
    targetPath
  },
  cleanFiles += baseDirectory.value / "src" / "native" / "target",
))

lazy val loader = project in file("modules/loader") settings (commonSettings ++ Seq(
  organization := "moe.lymia.princess",
  name := "princess-edit-loader",
  crossPaths := false,
))

/*********************************\
|* Distribution-related Projects *|
\*********************************/

val classPathInfo = TaskKey[(Properties, File)]("class-path-info")

lazy val princessEditFull = project in file("target/princess-edit-full") settings (commonSettings ++ Seq(
  organization := "moe.lymia.princess",
  name := "princess-edit-full",

  libraryDependencies += swtDep("org.eclipse.swt.win32.win32.x86_64"),
  libraryDependencies += swtDep("org.eclipse.swt.cocoa.macosx.x86_64"),
  libraryDependencies += swtDep("org.eclipse.swt.cocoa.macosx.aarch64"),
  libraryDependencies += swtDep("org.eclipse.swt.gtk.linux.x86_64"),
  libraryDependencies += swtDep("org.eclipse.swt.gtk.linux.aarch64"),
)) dependsOn princessEdit dependsOn loader

lazy val princessEditClasspath = project in file("target/princess-edit-classpath") enablePlugins NativeImagePlugin settings (commonSettings ++ Seq(
  organization := "moe.lymia.princess",
  name := "princess-edit-classpath",

  classPathInfo := (if (System.getenv("PRINCESS_EDIT_PREBUILT_CLASS_PATH") != null) {
    val outDir = target.value / "class-path-dump"
    val props = new Properties()
    props.load(new FileInputStream(outDir / "manifest.properties"))
    (props, outDir)
  } else {
    val outDir = target.value / "class-path-dump"
    IO.createDirectory(outDir)

    // gather classpath information
    val rawClasspath = (princessEditFull / Compile / fullClasspath).value.sortBy(jarName)
    val distClasspath = rawClasspath
      .filter(!_.get(moduleID.key).get.name.startsWith("org.eclipse.swt."))
      .filter(!_.get(moduleID.key).get.name.contains("princess-edit-loader"))
      .distinct
    def getJar(artifact: String) = rawClasspath.find(_.get(moduleID.key).get.name == artifact).get

    val classPaths = Map(
      "windows-x86_64" -> (distClasspath :+ getJar("org.eclipse.swt.win32.win32.x86_64")),
      "macos-x86_64"   -> (distClasspath :+ getJar("org.eclipse.swt.cocoa.macosx.x86_64")),
      "macos-aarch64"  -> (distClasspath :+ getJar("org.eclipse.swt.cocoa.macosx.aarch64")),
      "linux-x86_64"   -> (distClasspath :+ getJar("org.eclipse.swt.gtk.linux.x86_64")),
      "linux-aarch64"  -> (distClasspath :+ getJar("org.eclipse.swt.gtk.linux.aarch64")),
    )
    val allJars: Seq[Attributed[File]] = classPaths.flatMap(_._2).toSet.toSeq

    // copy lib files
    for (jar <- rawClasspath) IO.copyFile(jar.data, outDir / jarName(jar))

    val props = new Properties()
    for ((name, path) <- classPaths) props.put(s"$name.classpath", classPathString(path))
    props.put("main", (princessEdit / Compile / run / mainClass).value.get)
    props.put("allJars", classPathString(allJars))
    props.put("loaderName", jarName(getJar("princess-edit-loader")))

    runProcess(Seq("zip", "-r", outDir / "core.pedit-pkg", "core.pedit-pkg"), baseDirectory.value / "../../modules")
    props.put("corePackage", "core.pedit-pkg")

    // copy native binaries, if they exist
    val nativeBinaries = Map(
      "windows-x86_64" -> "princessedit_native.windows.x86_64.dll",
      "macos-x86_64"   -> "libprincessedit_native.macos.x86_64.dylib",
      "macos-aarch64"  -> "libprincessedit_native.macos.aarch64.dylib",
      "linux-x86_64"   -> "libprincessedit_native.linux.x86_64.so",
      "linux-aarch64"  -> "libprincessedit_native.linux.aarch64.so",
    )
    for ((name, path) <- nativeBinaries) props.put(s"$name.nativeBin", path)
    props.put("allNatives", nativeBinaries.values.mkString(":"))

    // write property file
    IO.write(props, "Classpath configuration data for PrincessEdit", outDir / "manifest.properties")

    (props, outDir)
  }),

  // native image configuration
  Compile / unmanagedClasspath := {
    val (properties, classDir) = classPathInfo.value
    val rawClasspath = properties.get(s"$osTarget.classpath").toString.split(":")
    rawClasspath.map(x => Attributed.blank(classDir / x))
  },
  (Compile / mainClass) := Some(classPathInfo.value._1.get("main").toString),
  nativeImageOptions := Seq(
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
))

InputKey[Unit]("precompileClassPath") := {
  (princessEditClasspath / classPathInfo).value
  ()
}

InputKey[Unit]("dist") := {
  val path = crossTarget.value / "dist"
  IO.createDirectory(path)

  val zipOut = IO.withTemporaryDirectory { dir =>
    val dirName = s"princess-edit-${(princessEdit / version).value}-${osName}"
    val zipOut = path / s"$dirName.zip"
    val outDir = dir / dirName

    IO.createDirectory(outDir)

    IO.write(outDir / "README.txt", fixEndings(IO.read(file("project/dist_README.md"))))
    IO.write(outDir / "NOTICE.txt", fixEndings(IO.read(file("project/dist_NOTICE.md"))))

    val (properties, classDir) = (princessEditClasspath / classPathInfo).value
    IO.copyFile((princessEditClasspath / nativeImage).value, outDir / "PrincessEdit")

    runProcess(Seq("zip", "-r", outDir / "core.pedit-pkg", "core.pedit-pkg"), baseDirectory.value / "modules")
    val nativeBinName = properties.get(s"$osTarget.nativeBin").toString
    IO.copyFile(classDir / nativeBinName, baseDirectory.value / "modules" / "native" / "target" / nativeBinName)

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

InputKey[Unit]("distUniversal") := {
  val path = crossTarget.value / "dist"
  IO.createDirectory(path)

  val zipOut = IO.withTemporaryDirectory { dir =>
    val dirName = s"princess-edit-${(princessEdit / version).value}-universal"
    val zipOut = path / s"$dirName.zip"
    val outDir = dir / dirName

    IO.createDirectory(outDir)

    IO.write(outDir / "README.txt", fixEndings(IO.read(file("project/dist_README.md"))))
    IO.write(outDir / "NOTICE.txt", fixEndings(IO.read(file("project/dist_NOTICE.md"))))

    // copy data from classpath information
    IO.createDirectory(outDir / "lib")
    val (properties, classDir) = (princessEditClasspath / classPathInfo).value

    IO.copyFile(classDir / "manifest.properties", outDir / "lib" / "manifest.properties")

    val loader = properties.get("loaderName").toString
    IO.copyFile(classDir / loader, outDir / "PrincessEdit.jar")

    val corePackage = properties.get("corePackage").toString
    IO.copyFile(classDir / corePackage, outDir / "lib" / corePackage)

    for (jar <- properties.get("allJars").toString.split(":"))
      IO.copyFile(classDir / jar, outDir / "lib" / jar)

    for (native <- properties.get("allNatives").toString.split(":")) {
      val nativePath = baseDirectory.value / "modules" / "native" / "target" / native
      if (nativePath.exists) IO.copyFile(nativePath, outDir / "lib" / native)
    }

    // we call out to zip to save the executable flag for *nix
    if (zipOut.exists) IO.delete(zipOut)
    runProcess(Seq("zip", "-r", zipOut, dirName), dir)

    zipOut
  }

  streams.value.log.info(s"Output written to: $zipOut")
}