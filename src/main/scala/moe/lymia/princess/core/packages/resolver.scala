/*
 * Copyright (c) 2017-2022 Lymia Alusyia <lymia@lymiahugs.com>
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

package moe.lymia.princess.core.packages

import moe.lymia.princess.core
import moe.lymia.princess.core.EditorException
import moe.lymia.princess.util._
import scalaz.std.AllInstances._
import scalaz.syntax.foldable._

import java.nio.file.{Files, Path}
import scala.collection.mutable

// TODO: Add framework for reporting multiple errors in one run.

case class LoadedPackage(manifest: PackageManifest, rootPath: Path, isSystem: Boolean) {
  @inline def name: String = manifest.name
  @inline def version: Version = manifest.version
  @inline def gameIds: Set[String] = manifest.gameIds
}

private object LoadedPackage {
  private def loadPackageFromPath(path: Path, isSystem: Boolean): LoadedPackage = {
    val fileList = PackageManifest.buildFileList(path)

    val manifestPath = path.resolve("package.toml")
    if (!Files.exists(manifestPath) || !Files.isRegularFile(manifestPath))
      throw EditorException(s"No manifest found in package")
    var manifest = EditorException.context("parsing package.toml")(PackageManifest.parse(fileList, manifestPath))

    val exportsPath = path.resolve("exports.toml")
    if (Files.exists(exportsPath) && Files.isRegularFile(exportsPath))
      manifest = manifest.addExports(fileList, exportsPath)

    def iterExportPath(path: Path): Unit =
      for (newFile <- IOUtils.list(path))
        if (Files.isDirectory(newFile)) iterExportPath(newFile)
        else if (Files.isReadable(newFile)) {
          if (newFile.getFileName.toString.endsWith(".toml"))
            manifest = EditorException.context(f"parsing ${newFile.getFileName}")(manifest.addExports(fileList, newFile))
        }

    val exportsDirPath = path.resolve("exports")
    if (Files.exists(exportsDirPath) && Files.isDirectory(exportsDirPath))
      iterExportPath(exportsDirPath)

    if (!isSystem)
      for (gameId <- manifest.gameIds)
        if (gameId.startsWith("_")) throw EditorException(s"Game ID cannot start with '_' in '$gameId'")

    LoadedPackage(manifest, path, isSystem)
  }

  // TODO: Allow loading multiple packages from one .zip file
  private def loadPackageFromZip(path: Path, isSystem: Boolean): Seq[LoadedPackage] =
    EditorException.context(s"loading package from zip $path") {
      val fs = IOUtils.openZip(path)
      val root = fs.getPath("/")
      val fileList = IOUtils.list(root)

      if (Files.exists(root.resolve("package.toml")))
        Seq(loadPackageFromPath(root, isSystem))
      else {
        val packages = fileList.view
          .filter(_.getFileName.toString.endsWith(".pedit-pkg"))
          .filter(x => Files.isRegularFile(x.resolve("package.toml")))
        packages.map(dir => loadPackageFromPath(dir, isSystem)).toSeq
      }
    }

  private def loadPackageFromDirectory(path: Path, isSystem: Boolean): Seq[LoadedPackage] =
    EditorException.context(s"loading package from $path") {
      Seq(loadPackageFromPath(path, isSystem))
    }

  def loadPackage(path: Path, isSystem: Boolean): Seq[LoadedPackage] = {
    logger.info(s"Loading ${ if (isSystem) "system " else "" }packages from $path")
    if (Files.isDirectory(path)) loadPackageFromDirectory(path, isSystem)
    else loadPackageFromZip(path, isSystem)
  }
}

case class PackageList(gameId: String, packages: Seq[LoadedPackage]) {
  private val systemPackageNames = packages.view.filter(_.isSystem).map(_.manifest.name).toSet

  private val exportMap = packages.view.map(_.manifest.exports).toList.suml
  for ((name, values) <- exportMap;
       (path, list) <- values.groupBy(_.path) if list.length > 1)
    throw EditorException(s"Duplicate export '$path' for type '$name'")
  private val allExports = exportMap.values.flatten.toSet.toSeq

  def getExportKeys: Set[String] = exportMap.keySet

  def getSystemExports(key: String): Seq[Export] =
    exportMap.getOrElse(key, Seq()).filter(x => systemPackageNames.contains(x.source)).toSeq

  def getExports(key: String): Seq[Export] =
    if (key == "*") allExports else exportMap.getOrElse(key, Seq())

  private def getPaths(exportId: String) =
    getSystemExports(exportId).flatMap(x =>
      if (!x.path.endsWith("/")) Seq(x.path, x.path + "/") else Seq(x.path))

  private val protectedPaths = getPaths(StaticExportIDs.ProtectedPath)
  private val ignoredPaths = getPaths(StaticExportIDs.IgnoredPath)

  private val (systemPackages, userPackages) = packages.partition(_.isSystem)
  private val resolveCache = new CountedCache[String, Option[(LoadedPackage, Path)]](4096)

  private def resolveInPath(packages: Seq[LoadedPackage], path: String) =
    packages.view.map(x => IOUtils.paranoidResolve(x.rootPath, path).map(y => (x, y))).find(_.isDefined).flatten

  private def internalResolve(path: String) =
    resolveCache.cached(path, {
      val isPathProtected = protectedPaths.exists(x => path.startsWith(x))
      val isPathIgnored = ignoredPaths.exists(x => path.startsWith(x))
      val tmp = resolveInPath(systemPackages, path).orElse(resolveInPath(userPackages, path))
      tmp.filter(x => !isPathIgnored && (!isPathProtected || x._1.isSystem))
    })

  def resolve(path: String): Option[Path] = internalResolve(path).map(_._2)

  def forceResolve(path: String): Path = resolve(path).getOrElse(throw EditorException(s"File '$path' not found."))
}

case class PackageResolver(packages: Map[String, LoadedPackage]) {
  def getPackageListForGameId(gameId: String): Seq[LoadedPackage] =
    packages.values.filter(_.gameIds.contains(gameId)).toSeq

  def getDependency(dep: Dependency): Option[LoadedPackage] =
    packages.get(dep.name).filter(_.version >= dep.version)

  private val packageVersions = packages.view.mapValues(_.version).toMap

  private def findPackages(packageList: Seq[String]) = {
    val requiredDependencies = new mutable.Queue[String]
    val handledDependencies = new mutable.HashSet[String]
    val loadedPackages = new mutable.ArrayBuffer[LoadedPackage]

    requiredDependencies ++= packageList
    handledDependencies ++= packageList

    while (requiredDependencies.nonEmpty) {
      val depName = requiredDependencies.dequeue()
      packages.get(depName) match {
        case None => throw EditorException(s"Could not resolve dependency $depName.")
        case Some(pkg) =>
          loadedPackages.append(pkg)
          for (dep <- pkg.manifest.dependencies) {
            if (!handledDependencies.contains(dep.name)) {
              requiredDependencies.enqueue(dep.name)
              handledDependencies.add(dep.name)
            }
            packageVersions.get(dep.name) match {
              case None => throw EditorException(s"Could not resolve dependency $depName.")
              case Some(version) => if (!(version >= dep.version))
                throw EditorException(s"$depName requires package $dep, but version $version is installed.")
            }
          }
      }
    }

    loadedPackages.toList
  }

  private def resolveLoadOrder(toLoad: Seq[LoadedPackage]) = {
    var unresolvedPackages = toLoad
    val loadedDependencies = new mutable.HashSet[String]
    val loadOrder = new mutable.ArrayBuffer[LoadedPackage]

    while (unresolvedPackages.nonEmpty) {
      val (resolved, unresolved) =
        unresolvedPackages.partition(_.manifest.dependencies.forall(x => loadedDependencies.contains(x.name)))
      if (resolved.isEmpty)
        throw EditorException(s"Dependency cycle found: [${unresolved.map(_.name).mkString(", ")}]")
      unresolvedPackages = unresolved
      loadOrder.appendAll(resolved)
      for (pkg <- resolved) loadedDependencies.add(pkg.name)
    }

    loadOrder.reverse.toList
  }

  def loadPackages(gameId: String, packageList: Seq[String]): PackageList =
    PackageList(gameId, resolveLoadOrder(findPackages(packageList)))

  def loadGameId(gameId: String): PackageList =
    loadPackages(gameId, packages.values.filter(x => gameId == "*" ||
      (x.gameIds.contains(StaticGameIDs.System) && x.isSystem) ||
      x.gameIds.contains(gameId)).map(_.name).toSeq)
}

object PackageResolver {
  def apply(packages: Seq[LoadedPackage]): PackageResolver = {
    val map = new mutable.HashMap[String, LoadedPackage]
    for (pkg <- packages) {
      val name = pkg.name
      if (map.contains(name))
        throw EditorException(s"Duplicate package ${name} (In ${pkg.rootPath} and ${map(name).rootPath})")
      map.put(name, pkg)
    }
    PackageResolver(map.toMap)
  }

  def loadPackageDirectory(packages: Path, systemPackages: Path*): PackageResolver = {
    val loadedPackages = for (x <- IOUtils.list(packages) if x.getFileName.toString.endsWith(".pedit-pkg"))
        yield LoadedPackage.loadPackage(x, isSystem = false)
    val loadedSystemPackages = for (x <- systemPackages) yield LoadedPackage.loadPackage(x, isSystem = true)
    core.packages.PackageResolver((loadedPackages ++ loadedSystemPackages).flatten)
  }
}