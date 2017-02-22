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

package moe.lymia.princess.core.builder

import java.io.ByteArrayOutputStream
import java.nio.file.{Files, Path}
import java.util.zip.GZIPInputStream
import javax.imageio.ImageIO
import javax.xml.bind.DatatypeConverter

import moe.lymia.princess.core._

import scala.collection.mutable
import scala.xml.{XML => _, _}

trait ResourceLoader {
  def loadRaster    (builder: SVGBuilder, name: String, reencode: Option[String], expectedMime: String,
                     path: Path, expectedSize: Size): SVGDefinitionReference
  def loadVector    (builder: SVGBuilder, name: String, compression: Boolean,
                     path: Path, expectedSize: Size): SVGDefinitionReference
  def loadDefinition(builder: SVGBuilder, name: String, path: Path): String
}

trait IncludeDefinitionLoader extends ResourceLoader {
  def loadDefinition(builder: SVGBuilder, name: String, path: Path) =
    builder.createDefinition(name, XML.load(Files.newInputStream(path)), isDef = true)
}

trait SimpleVectorLoader extends ResourceLoader {
  def minifyVectorElem(elem: Elem, scope: NamespaceBinding) = elem
  def loadVector(builder: SVGBuilder, name: String, compression: Boolean, path: Path, expectedSize: Size) =
    builder.createDefinitionFromContainer(name, Bounds(expectedSize), minifyVectorElem(
      XML.load(if(compression) new GZIPInputStream(Files.newInputStream(path)) else Files.newInputStream(path)),
      SVGBuilder.scope) % Attribute(null, "overflow", "hidden", Null))
}
trait MinifyVectorLoader extends SimpleVectorLoader {
  override def minifyVectorElem(elem: Elem, scope: NamespaceBinding) = MinifyXML.SVG(elem, scope)
}

trait LinkRasterLoader extends ResourceLoader {
  def loadRaster(builder: SVGBuilder, name: String, reencode: Option[String], expectedMime: String,
                 path: Path, expectedSize: Size) =
    builder.createDefinitionFromContainer(name, Bounds(expectedSize), <image xlink:href={path.toUri.toASCIIString}/>)
}
trait DataURLRasterLoader extends ResourceLoader {
  def loadRaster(builder: SVGBuilder, name: String, reencode: Option[String], expectedMime: String,
                 path: Path, expectedSize: Size) = {
    val data = reencode match {
      case None => Files.readAllBytes(path)
      case Some(reencodeTo) =>
        val image = ImageIO.read(Files.newInputStream(path))
        val byteOut = new ByteArrayOutputStream()
        ImageIO.write(image, reencodeTo, byteOut)
        byteOut.toByteArray
    }
    val uri = s"data:$expectedMime;base64,${DatatypeConverter.printBase64Binary(Files.readAllBytes(path))}"
    builder.createDefinitionFromContainer(name, Bounds(expectedSize), <image xlink:href={uri}/>)
  }
}

object RasterizeResourceLoader  extends IncludeDefinitionLoader with SimpleVectorLoader with LinkRasterLoader
object LinkExportResourceLoader extends IncludeDefinitionLoader with MinifyVectorLoader with LinkRasterLoader
object ExportResourceLoader     extends IncludeDefinitionLoader with MinifyVectorLoader with DataURLRasterLoader

private sealed trait ImageFormatType
private object ResourceFormatType {
  case class Raster(mime: String, reencode: Option[String] = None) extends ImageFormatType
  case class Vector(compression: Boolean) extends ImageFormatType
}

private case class ImageFormat(extensions: Seq[String], formatType: ImageFormatType)

final class ResourceManager(builder: SVGBuilder, settings: RenderSettings,
                            loader: ResourceLoader, packages: PackageList) {
  private def stripExtension(name: String) = {
    val split      = name.split("/")
    val components = split.last.split("\\.")
    (split.init :+ (if(components.length == 1) components.head else components.init.mkString("."))).mkString("/")
  }

  private def tryFindImageResource(name: String, size: Size) =
    ResourceManager.formatSearchList.view.map { case (extension, format) =>
      packages.resolve(s"$name.$extension").map(fullPath =>
        format.formatType match {
          case ResourceFormatType.Raster(mime, reencode) =>
            loader.loadRaster(builder, name, reencode, mime, fullPath, size)
          case ResourceFormatType.Vector(compression) =>
            loader.loadVector(builder, name, compression, fullPath, size)
        }
      )
    }.find(_.isDefined).flatten
  val imageResourceCache = new mutable.HashMap[String, Option[SVGDefinitionReference]]
  def loadImageResource(name: String, size: Size) =
    imageResourceCache.getOrElseUpdate(stripExtension(name), tryFindImageResource(stripExtension(name), size))
                      .getOrElse(throw TemplateException(s"image '$name' not found"))

  private def tryFindDefinition(name: String) =
    packages.resolve(name).map(path =>
      loader.loadDefinition(builder, name, path)
    )
  val definitionCache = new mutable.HashMap[String, Option[String]]
  def loadDefinition(name: String) =
    definitionCache.getOrElseUpdate(name, tryFindDefinition(name))
                   .getOrElse(throw TemplateException(s"definition '$name' not found"))
}
object ResourceManager {
  private val imageFormats = Seq(
    ImageFormat(Seq("svgz"), ResourceFormatType.Vector(compression = true)),
    ImageFormat(Seq("svg"), ResourceFormatType.Vector(compression = false)),
    ImageFormat(Seq("png"), ResourceFormatType.Raster("image/png")),
    ImageFormat(Seq("bmp"), ResourceFormatType.Raster("image/png", Some("png"))), // bmp is lossless but big
    ImageFormat(Seq("jpg", "jpeg"), ResourceFormatType.Raster("image/jpeg"))
  )
  private val formatSearchList = imageFormats.flatMap(x => x.extensions.map(y => (y, x)))
}