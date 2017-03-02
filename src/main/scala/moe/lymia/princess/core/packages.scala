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

import java.nio.file.{FileSystems, Files, Path}

import moe.lymia.princess.util.{CountedCache, _}

import scala.collection.mutable

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

case class Dependency(name: String, version: Option[DepVersion]) {
  override def toString = s"$name${version.fold("")(x => s" v$x")}"
}

case class Export(path: String, types: Seq[String], metadata: Map[String, Seq[String]])

case class Package(name: String, version: Version, gameIds: Set[String], rootPath: Path,
                   dependencies: Seq[Dependency], exports: Map[String, Seq[Export]], isSystem: Boolean = false)
object Package {
  private def loadPackageFromPath(path: Path) = {
    val manifestPath = path.resolve("package.ini")
    if(!Files.exists(manifestPath) || !Files.isRegularFile(manifestPath))
      throw TemplateException(s"No manifest found in package")
    val manifest = INI.load(manifestPath)

    val exportMap = new mutable.HashMap[String, mutable.ArrayBuffer[Export]]
    def loadExports(exports: INI) = for((path, metadata) <- exports) {
      val types = metadata.getMulti("type")
      val export = Export(path, types, metadata.underlying)
      for(t <- types) exportMap.getOrElseUpdate(t, new mutable.ArrayBuffer[Export]).append(export)
    }

    val exportsPath = path.resolve("exports.ini")
    if(Files.exists(exportsPath) && Files.isRegularFile(exportsPath)) loadExports(INI.load(exportsPath))

    val exportsDirPath = path.resolve("exports")
    if(Files.exists(exportsDirPath) && Files.isDirectory(exportsDirPath))
      for(file <- IOUtils.list(exportsDirPath))
        loadExports(INI.load(file))

    val packageSection = manifest.getSection("package")
    val dependenciesSection = manifest.getSectionOptional("dependencies").underlying

    if(dependenciesSection.exists(_._2.length != 1)) throw TemplateException("Dependency declared twice")

    Package(packageSection.getSingle("name"), Version.parse(packageSection.getSingle("version")),
            packageSection.getMultiOptional("gameId").toSet,
            path,
            dependenciesSection.map(x => Dependency(x._1, DepVersion.parse(x._2.head))).toSeq,
            exportMap.mapValues(_.toSeq).toMap)
  }

  // TODO: Consider allowing loading multiple packages from one .zip file
  private def loadPackageFromZip(path: Path) = TemplateException.context(s"loading package from zip $path") {
    val fs = FileSystems.newFileSystem(path, getClass.getClassLoader)
    val root = fs.getPath("/")
    val fileList = IOUtils.list(root)
    loadPackageFromPath(if(!Files.exists(root.resolve("package.ini")) &&
                           fileList.length == 1 && Files.isDirectory(fileList.head)) fileList.head
                        else root)
  }
  private def loadPackageFromDirectory(path: Path) = TemplateException.context(s"loading package from $path") {
    loadPackageFromPath(path)
  }
  def loadPackage(path: Path) =
    if(Files.isDirectory(path)) loadPackageFromDirectory(path)
    else                        loadPackageFromZip      (path)
}

case class PackageList(gameId: String, packages: Seq[Package]) {
  val filePaths = packages.map(_.rootPath)

  private val exportMap = packages.flatMap(_.exports.keySet).toSet.map( (key: String) => {
    val existingExports = new mutable.HashSet[String]
    key -> packages.flatMap(pkg => {
      val exports = pkg.exports.getOrElse(key, Seq())
      exports.foreach(ex => {
        if(existingExports.contains(ex.path)) throw TemplateException(s"Duplicate export '${ex.path}'")
        existingExports.add(ex.path)
      })
      exports.map(pkg -> _)
    })
  }).toMap
  private val allExports = exportMap.values.flatten.map(_._2).toSet.toSeq

  def getExportKeys = exportMap.keySet
  def getSystemExports(key: String) = exportMap.getOrElse(key, Seq()).filter(_._1.isSystem).map(_._2)
  def getExports(key: String) =
    if(key == "*") allExports else exportMap.getOrElse(key, Seq()).map(_._2)

  private val (systemPackages, userPackages) = packages.partition(_.isSystem)
  private val resolveCache = CountedCache[String, Option[(Package, Path)]](4096)
  private def resolveInPath(packages: Seq[Package], path: String) =
    packages.view.map(x => IOUtils.paranoidResolve(x.rootPath, path).map(y => (x, y))).find(_.isDefined).flatten
  private def internalResolve(path: String) =
    resolveCache.cached(path, resolveInPath(systemPackages, path).orElse(resolveInPath(userPackages, path)))

  def resolve(path: String) = internalResolve(path).map(_._2)
  def forceResolve(path: String) = resolve(path).getOrElse(throw TemplateException(s"File '$path' not found."))
}
case class PackageResolver(packages: Map[String, Package]) {
  def getPackageListForGameId(gameId: String) =
    packages.values.filter(_.gameIds.contains(gameId)).toSeq
  def getDependency(dep: Dependency) =
    packages.get(dep.name).filter(_.version >= dep.version)

  private val packageVersions = packages.mapValues(_.version)
  private def findPackages(packageList: Seq[String]) = {
    val requiredDependencies = new mutable.Queue[String]
    val handledDependencies  = new mutable.HashSet[String]
    val loadedPackages       = new mutable.ArrayBuffer[Package]

    requiredDependencies ++= packageList
    handledDependencies ++= packageList

    while(requiredDependencies.nonEmpty) {
      val depName = requiredDependencies.dequeue()
      packages.get(depName) match {
        case None => throw TemplateException(s"Could not resolve dependency $depName.")
        case Some(pkg) =>
          loadedPackages.append(pkg)
          for(dep <- pkg.dependencies) {
            if(!handledDependencies.contains(dep.name)) {
              requiredDependencies.enqueue(dep.name)
              handledDependencies.add(dep.name)
            }
            packageVersions.get(dep.name) match {
              case None => throw TemplateException(s"Could not resolve dependency $depName.")
              case Some(version) => if(!(version >= dep.version))
                throw TemplateException(s"$depName requires package $dep, but version $version is installed.")
            }
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

  def loadPackages(gameId: String, packageList: Seq[String]) =
    PackageList(gameId, resolveLoadOrder(findPackages(packageList)))
  def loadGameId(gameId: String) =
    loadPackages(gameId, packages.values.filter(x => gameId == "*" ||
                                                     (x.gameIds.contains(StaticGameIDs.System) && x.isSystem) ||
                                                     x.gameIds.contains(gameId)).map(_.name).toSeq)
}
object PackageResolver {
  def apply(packages: Seq[Package]): PackageResolver = {
    val map = new mutable.HashMap[String, Package]
    for(pkg <- packages) {
      if(map.contains(pkg.name))
        throw TemplateException(s"Duplicate package ${pkg.name} (In ${pkg.rootPath} and ${map(pkg.name).rootPath})")
      map.put(pkg.name, pkg)
    }
    PackageResolver(map.toMap)
  }
  def loadPackageDirectory(packages: Path, systemPackages: Path*) =
    PackageResolver(
      (for(x <- IOUtils.list(packages) if x.getFileName.toString != ".gitignore") yield Package.loadPackage(x)) ++
      (for(x <- systemPackages) yield Package.loadPackage(x).copy(isSystem = true)))
}