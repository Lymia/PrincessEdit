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

package moe.lymia.princess.svg

import moe.lymia.princess.core._
import moe.lymia.princess.core.gamedata.GameData
import moe.lymia.princess.util._
import org.eclipse.swt.SWT
import org.eclipse.swt.graphics.ImageLoader

import java.awt.Font
import java.io.ByteArrayOutputStream
import java.nio.file.{Files, Path}
import java.util.Base64
import scala.collection.mutable
import scala.xml.{XML => _, _}

// TODO: Add caching for these resources between renders

trait ResourceLoader {
  def loadRaster    (cache: SizedCache, reencode: Option[String], expectedMime: String, path: Path): Elem
  def loadVector    (cache: SizedCache, path: Path): Elem
  def loadDefinition(cache: SizedCache, path: Path): Elem
}
private object ResourceLoader {
  val dataURLLoader: DataURLRasterLoader = new DataURLRasterLoader {}
  val normalSchemes = Set("http", "https", "ftp", "file")

  val loadXMLCache   = new CacheSection[Path, Elem]
  val loadImageCache = new CacheSection[Path, String]

  def cachedLoadXML(cache: SizedCache, path: Path): Elem = cache.cached(loadXMLCache)(path, {
    val size = Files.size(path)
    (XML.load(Files.newInputStream(path)), size)
  })
}

trait IncludeDefinitionLoader {
  def loadDefinition(cache: SizedCache, path: Path): Elem = ResourceLoader.cachedLoadXML(cache, path)
}
trait IncludeVectorLoader {
  def loadVector(cache: SizedCache, path: Path): Elem =
    ResourceLoader.cachedLoadXML(cache, path) % Attribute(null, "overflow", "hidden", Null)
}

trait LinkRasterLoader {
  def loadRaster(cache: SizedCache, reencode: Option[String], expectedMime: String, path: Path): Elem = {
    val uri = path.toUri
    if(ResourceLoader.normalSchemes.contains(uri.getScheme))
      <image xlink:href={path.toUri.toASCIIString}/>
    else ResourceLoader.dataURLLoader.loadRaster(cache, reencode, expectedMime, path)
  }
}
trait DataURLRasterLoader {
  def loadRaster(cache: SizedCache, reencode: Option[String], expectedMime: String, path: Path): Elem =
    <image xlink:href={cache.cached(ResourceLoader.loadImageCache)(path, {
      val data = reencode match {
        case None => Files.readAllBytes(path)
        case Some(reencodeTo) =>
          val imageReader = new ImageLoader()
          imageReader.load(Files.newInputStream(path))
          val byteOut = new ByteArrayOutputStream()
          imageReader.save(byteOut, reencodeTo.toLowerCase() match {
            case "image/png" => SWT.IMAGE_PNG
            case "image/jpeg" => SWT.IMAGE_JPEG
          })
          byteOut.toByteArray
      }
      val uri = s"data:$expectedMime;base64,${Base64.getEncoder.encodeToString(data)}"
      (uri, uri.length)
    })}/>
}

object RasterizeResourceLoader
  extends ResourceLoader with IncludeDefinitionLoader with IncludeVectorLoader with LinkRasterLoader
object ExportResourceLoader
  extends ResourceLoader with IncludeDefinitionLoader with IncludeVectorLoader with DataURLRasterLoader

private sealed trait ImageFormatType
private object ResourceFormatType {
  case class Raster(mime: String, reencode: Option[String] = None) extends ImageFormatType
  case object Vector extends ImageFormatType
}

private case class ImageFormat(extensions: Seq[String], formatType: ImageFormatType)

final class ResourceManager(builder: SVGBuilder, settings: RenderSettings, cache: SizedCache,
                            loader: ResourceLoader, packages: GameData) {
  lazy val systemFont: Font = {
    val tryResolve = packages.getSystemExports("princess/system_font").headOption.flatMap(x =>
      packages.resolve(x.path).map(path => Font.createFont(Font.TRUETYPE_FONT, Files.newInputStream(path))))
    tryResolve.getOrElse(new Font(Font.SANS_SERIF, Font.PLAIN, 1))
  }

  private def stripExtension(name: String) = {
    val split      = name.split("/")
    val components = split.last.split("\\.")
    (split.init :+ (if(components.length == 1) components.head else components.init.mkString("."))).mkString("/")
  }

  private def tryFindImageResource(name: String, bounds: Bounds) =
    ResourceManager.formatSearchList.view.map { case (extension, format) =>
      packages.resolve(s"$name.$extension").map(fullPath =>
        format.formatType match {
          case ResourceFormatType.Raster(mime, reencode) =>
            builder.createDefinitionFromContainer(name, bounds, loader.loadRaster(cache, reencode, mime, fullPath))
          case ResourceFormatType.Vector =>
            builder.createDefinitionFromContainer(name, bounds, loader.loadVector(cache, fullPath))
        }
      )
    }.find(_.isDefined).flatten
  val imageResourceCache = new mutable.HashMap[String, Option[SVGDefinitionReference]]
  def loadImageResource(name: String, bounds: Bounds): SVGDefinitionReference =
    imageResourceCache.getOrElseUpdate(stripExtension(name), tryFindImageResource(stripExtension(name), bounds))
                      .getOrElse(throw EditorException(s"image '$name' not found"))

  private def tryFindDefinition(name: String) =
    packages.resolve(name).map(path =>
      builder.createDefinition(name, loader.loadDefinition(cache, path), isDef = true))
  val definitionCache = new mutable.HashMap[String, Option[String]]
  def loadDefinition(name: String): String =
    definitionCache.getOrElseUpdate(name, tryFindDefinition(name))
                   .getOrElse(throw EditorException(s"definition '$name' not found"))
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