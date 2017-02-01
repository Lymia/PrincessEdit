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

package moe.lymia.princess.core

import java.nio.file.{FileSystems, Files, Path, Paths}

import moe.lymia.princess.util.IOUtils

import scala.collection.mutable
import scala.collection.JavaConverters._

case class DepVersion(minMajor: Int, maxMajor: Int, minor: Int) {
  override def toString =
    if(minMajor == maxMajor) { if(minor == 0) s"$minMajor.x" else s"$minMajor.$minor+" }
    else                     { if(minor == 0) s"$minMajor.x-$maxMajor.x" else s"$minMajor.$minor-$maxMajor.x"}
}
object DepVersion {
  def parse(s: String) = try {
    if(s == "*" || s == "any") None
    else s.split("-") match {
      case Array(v) =>
        val Array(major, minor) = v.split("\\.")
        Some(DepVersion(major.toInt, major.toInt, if(minor == "x") 0 else minor.toInt))
      case Array(min, max) =>
        val Array(minMajor, minor) = min.split("\\.")
        val Array(maxMajor, minorB) = max.split("\\.")
        if(minorB != "x") sys.error("max version ending not 'x'")
        Some(DepVersion(minMajor.toInt, maxMajor.toInt, if(minor == "x") 0 else minor.toInt))
      case _ => sys.error("too many '-'")
    }
  } catch {
    case e: Exception => throw TemplateException(s"Invalid dependency version '$s'", e)
  }
}

case class Version(major: Int, minor: Int, patch: Int) {
  def >=(ver: DepVersion): Boolean = major >= ver.minMajor && major <= ver.minMajor &&
                                     (major != ver.minMajor || minor >= ver.minor)
  def >=(ver: Option[DepVersion]): Boolean = ver.fold(true)(this >= _)
  override def toString = s"$major.$minor.$patch"
}
object Version {
  def parse(s: String) = try {
    val Array(major, minor, patch) = s.split("\\.")
    Version(major.toInt, minor.toInt, patch.toInt)
  } catch {
    case e: Exception => throw TemplateException(s"Invalid version number '$s'", e)
  }
}

case class GameId(gameName: String, displayName: String, iconPath: Option[Path])
object GameId {
  def loadGameId(loaded: LoadedPackages, path: Path) = TemplateException.context(s"loading GameId from 's$path'") {
    val idData = IOUtils.loadIni(path)
    val gameSection = idData.getOrElse("game", throw TemplateException(s"No game section in manifest"))
    GameId(gameSection.getOrElse("name", throw TemplateException("No GameId name")).head,
           gameSection.getOrElse("displayName", throw TemplateException("No GameId displayName")).head,
           gameSection.getOrElse("icon", Seq()).headOption.flatMap(x => loaded.resolve(x)))
  }
}

case class Dependency(name: String, version: Option[DepVersion]) {
  override def toString = s"$name${version.fold("")(x => s" v$x")}"
}
case class Package(name: String, version: Version, gameIds: Set[String], rootPath: Path,
                   dependencies: Seq[Dependency], exports: Map[String, Seq[String]])
object Package {
  private def loadPackageFromPath(path: Path) = {
    val manifest = path.resolve("package.ini")
    if(!Files.exists(manifest) || !Files.isRegularFile(manifest))
      throw TemplateException(s"No manifest found in package")
    val ini = IOUtils.loadIni(manifest)

    val packageSection = ini.getOrElse("package", throw TemplateException(s"'package' section not found in manifest"))
    val dependenciesSection = ini.getOrElse("dependencies", Map())
    val exportsSection = ini.getOrElse("exports", Map())

    if(dependenciesSection.exists(_._2.length != 1)) throw TemplateException("Dependency declared twice")

    Package(packageSection.getOrElse("name", throw TemplateException("No package name")).head,
            Version.parse(packageSection.getOrElse("version", throw TemplateException("No package version")).head),
            packageSection.getOrElse("gameId", Seq()).toSet,
            path,
            dependenciesSection.map(x => Dependency(x._1, DepVersion.parse(x._2.head))).toSeq,
            exportsSection)
  }
  private def loadPackageFromZip(path: Path) = TemplateException.context(s"loading package from zip $path") {
    val fs = FileSystems.newFileSystem(path, getClass.getClassLoader)
    loadPackageFromPath(fs.getPath("/"))
  }
  private def loadPackageFromDirectory(path: Path) = TemplateException.context(s"loading package from $path") {
    loadPackageFromPath(path)
  }
  def loadPackage(path: Path) =
    if(Files.isDirectory(path)) loadPackageFromDirectory(path)
    else                        loadPackageFromZip      (path)
}

case class LoadedPackages(packages: Seq[Package]) {
  val filePaths = packages.map(_.rootPath)

  private val allExports: Map[String, Seq[Path]] = packages.flatMap(_.exports.keySet).toSet.map(
    (key: String) => key ->
      packages.flatMap(pkg => pkg.exports.getOrElse(key, Seq()).map(x =>
        IOUtils.paranoidResolve(pkg.rootPath, x) match {
          case Some(path) => path
          case None => throw TemplateException(s"No exported file '$x' in package '${pkg.name}'")
        }))
  ).toMap

  def getExportKeys = allExports.keySet
  def getExports(key: String) = allExports.getOrElse(key, Seq())
  def resolve(path: String) = filePaths.view.map(x => IOUtils.paranoidResolve(x, path)).find(_.isDefined).flatten
}
case class PackageList(packages: Map[String, Package]) {
  def getPackageListForGameId(gameId: String) =
    packages.values.filter(_.gameIds.contains(gameId)).toSeq
  def getDependency(dep: Dependency) =
    packages.get(dep.name).filter(_.version >= dep.version)

  private def findPackages(packageList: Seq[String]) = {
    val requiredDependencies = new mutable.Queue[Dependency]
    val loadedPackageNames   = new mutable.HashMap[String, Version]
    val loadedPackages       = new mutable.ArrayBuffer[Package]
    requiredDependencies ++= packageList.map(x => Dependency(x, None))

    while(requiredDependencies.nonEmpty) {
      val depDef = requiredDependencies.dequeue()
      val dep = getDependency(depDef)
      dep match {
        case None =>
          throw TemplateException(s"Could not resolve dependency $depDef."+
            (if(packages.contains(depDef.name)) s" (Package ${packages(depDef.name).version} is installed)" else ""))
        case Some(pkg) =>
          loadedPackageNames.put(pkg.name, pkg.version)
          loadedPackages.append(pkg)
          for(dep <- pkg.dependencies) loadedPackageNames.get(dep.name) match {
            case None => requiredDependencies.enqueue(dep)
            case Some(version) =>
              if(!(version >= dep.version)) throw TemplateException(s"Could not resolve dependency $depDef. "+
                                                                    s"(Package ${dep.name} v$version is installed)")
          }
      }
    }

    loadedPackages.toList
  }
  private def resolveLoadOrder(toLoad: Seq[Package]) = {
    var unresolvedPackages = toLoad
    val loadedDependencies = new mutable.HashSet[String]
    val loadOrder          = new mutable.ArrayBuffer[Package]

    while(unresolvedPackages.nonEmpty) {
      val (resolved, unresolved) =
        unresolvedPackages.partition(_.dependencies.forall(x => loadedDependencies.contains(x.name)))
      if(resolved.isEmpty)
        throw TemplateException(s"Dependency cycle involved in packages: [${unresolved.map(_.name).mkString(", ")}]")
      unresolvedPackages = unresolved
      loadOrder.append(resolved : _*)
      for(pkg <- resolved) loadedDependencies.add(pkg.name)
    }

    loadOrder.reverse.toList
  }

  def loadPackages(packageList: Seq[String]) = LoadedPackages(resolveLoadOrder(findPackages(packageList)))
  def loadGameId(gameId: String) = loadPackages(packages.values.filter(_.gameIds.contains(gameId)).map(_.name).toSeq)

  lazy val gameIDs: Map[String, GameId] = TemplateException.context("loading GameIDs"){
    val loaded = loadGameId(PackageList.GameId_HasGameDef)
    val gameIdPaths = loaded.getExports(PackageList.Export_GameDef)
    val gameIds = gameIdPaths.map(x => GameId.loadGameId(loaded, x))
    gameIds.map(x => x.gameName -> x).toMap
  }
}
object PackageList {
  val GameId_HasGameDef = "princess/hasgamedef"
  val Export_GameDef    = "princess/gamedef"

  def apply(packages: Seq[Package]): PackageList = {
    val map = new mutable.HashMap[String, Package]
    for(pkg <- packages) {
      if(map.contains(pkg.name))
        throw TemplateException(s"Duplicate package ${pkg.name} (In ${pkg.rootPath} and ${map(pkg.name).rootPath})")
      map.put(pkg.name, pkg)
    }
    PackageList(map.toMap)
  }
  def loadPackageDirectory(packages: Path, extraDirs: Path*) =
    PackageList((for(x <- Files.list(packages).iterator().asScala ++ extraDirs) yield Package.loadPackage(x)).toSeq)

  lazy val defaultPath = loadPackageDirectory(Paths.get("packages"), Paths.get("core.pkg"))
}