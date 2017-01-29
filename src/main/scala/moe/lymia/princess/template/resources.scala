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

package moe.lymia.princess.template

import java.io.ByteArrayOutputStream
import java.nio.file.{Files, Path}
import javax.imageio.ImageIO
import javax.xml.bind.DatatypeConverter

import moe.lymia.princess.util.IOUtils

import scala.collection.mutable
import scala.xml.XML

trait ResourceLoader {
  def loadRaster   (builder: SVGBuilder, name: String, reencode: Option[String], expectedMime: String,
                    path: Path, expectedSize: Size): SVGDefinitionReference
  def loadVector   (builder: SVGBuilder, name: String,
                    path: Path, expectedSize: Size): SVGDefinitionReference
  def loadComponent(builder: SVGBuilder, name: String, path: Path): String
}

trait IncludeComponentLoader extends ResourceLoader {
  def loadComponent(builder: SVGBuilder, name: String, path: Path) =
    builder.createDefinition(name, XML.load(Files.newInputStream(path)))
}
trait IncludeVectorLoader extends ResourceLoader {
  def loadVector(builder: SVGBuilder, name: String, path: Path, expectedSize: Size) =
    builder.createDefinitionFromContainer(name, expectedSize, XML.load(Files.newInputStream(path)))
}

trait ReferenceRasterLoader extends ResourceLoader {
  def loadRaster(builder: SVGBuilder, name: String, reencode: Option[String], expectedMime: String,
                 path: Path, expectedSize: Size) =
    builder.createDefinitionFromContainer(name, expectedSize, <image xlink:href={path.toUri.toASCIIString}/>)
}
trait IncludeRasterLoader extends ResourceLoader {
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
    builder.createDefinitionFromContainer(name, expectedSize, <image xlink:href={uri}/>)
  }
}

object RasterizeResourceLoader extends IncludeComponentLoader with IncludeVectorLoader with IncludeRasterLoader
object ExportResourceLoader    extends IncludeComponentLoader with IncludeVectorLoader with ReferenceRasterLoader

private sealed trait ImageFormatType
private object ResourceFormatType {
  case class Raster(mime: String, reencode: Option[String] = None) extends ImageFormatType
  case object Vector extends ImageFormatType
}

private case class ImageFormat(extensions: Seq[String], formatType: ImageFormatType)

class ResourceCache {
  val imageResourceCache = new mutable.HashMap[String, Option[SVGDefinitionReference]]
  val componentCache = new mutable.HashMap[(String, String), Option[String]]
}
class ResourceManager(builder: SVGBuilder, loader: ResourceLoader, resourcePaths: Seq[Path],
                      cache: ResourceCache = new ResourceCache) {
  private def tryLoadImageInFile(path: Path, fullName: String, format: ImageFormat, size: Size) =
    IOUtils.paranoidResolve(path, fullName).map(fullPath =>
      format.formatType match {
        case ResourceFormatType.Raster(mime, reencode) =>
          loader.loadRaster(builder, fullName, reencode, mime, path, size)
        case ResourceFormatType.Vector =>
          loader.loadVector(builder, fullName, path, size)
      }
    )
  private def tryFindImageResourceInPath(path: Path, name: String, size: Size) =
    ResourceManager.formatSearchList.view.map { case (extension, format) =>
      tryLoadImageInFile(path, s"$name.$extension", format, size)
    }.find(_.isDefined).flatten
  private def tryFindImageResource(name: String, size: Size) =
    resourcePaths.view.map(x => tryFindImageResourceInPath(x, name, size)).find(_.isDefined).flatten

  def loadImageResource(name: String, size: Size) =
    cache.imageResourceCache.getOrElseUpdate(name, tryFindImageResource(name, size))
                            .getOrElse(throw TemplateException(s"Image resource $name not found."))

  private def tryLoadComponentInPath(path: Path, name: String, resourceType: String) =
    IOUtils.paranoidResolve(path, s"$name.$resourceType.xml").map(path =>
      loader.loadComponent(builder, name, path)
    )
  private def tryFindComponent(name: String, resourceType: String) =
    resourcePaths.view.map(x => tryLoadComponentInPath(x, name, resourceType)).find(_.isDefined).flatten

  def loadComponent(name: String, resourceType: String) =
    cache.componentCache.getOrElseUpdate((name, resourceType), tryFindComponent(name, resourceType))
                        .getOrElse(throw TemplateException(s"Component $name not found."))
}
object ResourceManager {
  private val imageFormats = Seq(
    ImageFormat(Seq("svg"), ResourceFormatType.Vector),
    ImageFormat(Seq("png"), ResourceFormatType.Raster("image/png")),
    ImageFormat(Seq("bmp"), ResourceFormatType.Raster("image/png", Some("png"))), // bmp is lossless but big
    ImageFormat(Seq("jpg", "jpeg"), ResourceFormatType.Raster("image/jpeg"))
  )
  private val formatSearchList = imageFormats.flatMap(x => x.extensions.map(y => (y, x)))
}