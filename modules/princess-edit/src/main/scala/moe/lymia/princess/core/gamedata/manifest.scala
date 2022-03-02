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

package moe.lymia.princess.core.gamedata

import moe.lymia.princess.core.EditorException
import moe.lymia.princess.util.IOUtils
import scalaz.std.AllInstances._
import scalaz.syntax.foldable._
import scalaz.syntax.monoid._
import toml.Codecs._
import toml.{Toml, Value}

import _root_.java.nio.file.{Files, Path}
import scala.collection.mutable.ArrayBuffer

case class PackageManifest(name: String, version: Version, gameIds: Set[String],
                           dependencies: Seq[Dependency], exports: Map[String, List[Export]]) {
  private[gamedata] def addExports(fileList: Seq[String], path: Path) = {
    case class RawExportsContainer(exports: List[Value])

    val raw = Toml.parseAs[RawExportsContainer](IOUtils.readFileAsString(path)).checkErr

    this.copy(exports = exports |+| Export.parseList(name, fileList, raw.exports))
  }
}
private[gamedata] object PackageManifest {
  def buildFileList(path: Path): Seq[String] = {
    val buffer = new ArrayBuffer[String]()
    def pathIter(name: String, path: Path): Unit =
      if (Files.isDirectory(path))
        for (newPath <- IOUtils.list(path)) {
          val newName = if (name.isEmpty) newPath.getFileName.toString else s"$name/${newPath.getFileName}"
          pathIter(newName, newPath)
        }
      else if (Files.isRegularFile(path))
        buffer.append(name)
    pathIter("", path)
    buffer.toSeq
  }

  def parse(fileList: Seq[String], path: Path): PackageManifest = {
    case class RawPackageManifest(name: String, version: String, gameIds: List[String])
    case class RawPackageManifestContainer(`package`: RawPackageManifest,
                                           dependencies: Option[Map[String, String]],
                                           exports: Option[List[Value]])

    val raw = Toml.parseAs[RawPackageManifestContainer](IOUtils.readFileAsString(path)).checkErr

    PackageManifest(
      raw.`package`.name, Version.parse(raw.`package`.version), raw.`package`.gameIds.toSet,
      raw.dependencies.getOrElse(Map()).map(t => Dependency(t._1, VersionRequirement.parse(t._2))).toSeq,
      Export.parseList(raw.`package`.name, fileList, raw.exports.getOrElse(List())),
    )
  }
}

case class Version(major: Int, minor: Int, patch: Int) {
  def >=(ver: VersionRequirement): Boolean = major >= ver.minMajor && major <= ver.maxMajor &&
                                     (major != ver.minMajor || minor >= ver.minor)
  def >=(ver: Option[VersionRequirement]): Boolean = ver.fold(true)(this >= _)
  override def toString = s"$major.$minor.$patch"
}
private object Version {
  def parse(s: String) = try {
    val Array(major, minor, patch) = s.split("\\.")
    Version(major.toInt, minor.toInt, patch.toInt)
  } catch {
    case e: Exception => throw EditorException(s"Invalid version number '$s'", e)
  }
}

case class Dependency(name: String, version: Option[VersionRequirement]) {
  override def toString = s"$name${version.fold("")(x => s" v$x")}"
}
case class VersionRequirement(minMajor: Int, maxMajor: Int, minor: Int) {
  override def toString =
    if(minMajor == maxMajor) { if(minor == 0) s"$minMajor.x" else s"$minMajor.$minor+" }
    else                     { if(minor == 0) s"$minMajor.x-$maxMajor.x" else s"$minMajor.$minor-$maxMajor.x"}
}
private object VersionRequirement {
  def parse(s: String) = try {
    if(s == "*" || s == "any") None
    else s.split("-") match {
      case Array(v) =>
        val Array(major, minor) = v.split("\\.")
        Some(VersionRequirement(major.toInt, major.toInt, if(minor == "x") 0 else minor.toInt))
      case Array(min, max) =>
        val Array(minMajor, minor) = min.split("\\.")
        val Array(maxMajor, minorB) = max.split("\\.")
        if(minorB != "x") sys.error("max version ending not 'x'")
        Some(VersionRequirement(minMajor.toInt, maxMajor.toInt, if(minor == "x") 0 else minor.toInt))
      case _ => sys.error("too many '-'")
    }
  } catch {
    case e: Exception => throw EditorException(s"Invalid dependency version '$s'", e)
  }
}

case class Export(source: String, path: String, types: Seq[String], metadata: Map[String, Seq[String]])
private object Export {
  private def parse(source: String, fileList: Seq[String], s: Value): Seq[Export] = s match {
    case s: Value.Tbl =>
      case class ExportFixed(path: Option[String], pattern: Option[String], types: Option[List[String]])

      val metadata = s.values.filter {
        case ("path" | "pattern" | "types", _) => false
        case _ => true
      }.view.mapValues {
        case arr : Value.Arr => Toml.parseAsValue[List[String]](arr).checkErr
        case Value.Str(s) => Seq(s)
        case Value.Bool(true) => Seq("true")
        case Value.Bool(false) => Seq("false")
        case _ => throw new EditorException(s"Export metadata must be strings.")
      }.toMap

      val hasPath = s.values.contains("path")
      val hasPattern = s.values.contains("pattern")
      val hasTypes = s.values.contains("types")

      if (!hasTypes) throw new EditorException("'types' must be defined.")
      val types = Toml.parseAsValue[List[String]](s.values.get("types").get).checkErr

      if (!hasPath && !hasPattern)
        throw new EditorException("'path' or 'pattern' must be defined.")
      else if (hasPath && hasPattern)
        throw new EditorException("Only one of 'path' or 'pattern' may be defined.")
      else if (hasPath) {
        val path = Toml.parseAsValue[String](s.values.get("path").get).checkErr
        Seq(Export(source, path, types, metadata))
      } else {
        val regexp = Toml.parseAsValue[String](s.values.get("pattern").get).checkErr.r
        fileList.view
          .filter(x => regexp.matches(x))
          .map(x => Export(source, x, types, metadata))
          .toSeq
      }
    case _ => throw new EditorException(s"Exports must be array of tables.")
  }

  def parseList(source: String, fileList: Seq[String], s: Seq[Value]): Map[String, List[Export]] =
    s.view
      .flatMap(x => parse(source, fileList, x))
      .flatMap(x => x.types.map(y => Map(y -> List(x))))
      .toList.suml
}
