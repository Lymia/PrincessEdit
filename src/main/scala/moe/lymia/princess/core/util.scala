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

import java.awt.geom.Rectangle2D
import java.nio.file.Path
import java.security.SecureRandom
import java.util.Map.Entry
import java.util.concurrent.atomic.AtomicInteger
import javax.xml.parsers.SAXParserFactory

import moe.lymia.princess.lua.LuaErrorMarker
import moe.lymia.princess.util.IOUtils
import org.ini4j.Ini

import scala.collection.JavaConverters._
import scala.xml.factory.XMLLoader
import scala.xml.{Elem, SAXParser}

final case class Size(width: Double, height: Double)
final case class Bounds(minX: Double, minY: Double, maxX: Double, maxY: Double) {
  def width  = maxX - minX
  def height = maxY - minY

  def size = Size(width, height)
  def translate(x: Double, y: Double) = Bounds(minX + x, minY + y, maxX + x, maxY + y)

  def toRectangle2D = new Rectangle2D.Double(minX, minY, width, height)
}
object Bounds {
  def apply(width: Double, height: Double) = new Bounds(0, 0, width, height)
  def apply(size: Size) = new Bounds(0, 0, size.width, size.height)
  def apply(rectangle: Rectangle2D) = new Bounds(rectangle.getMinX, rectangle.getMinY,
                                                 rectangle.getMaxX, rectangle.getMaxY)
}

final case class TemplateException(message: String, ex: Throwable = null, context: Seq[String] = Seq(),
                                   suppressTrace: Boolean = false, noCause: Boolean = false)
  extends RuntimeException((context :+ message).mkString(": "), if(noCause) null else ex, suppressTrace, true)
  with    LuaErrorMarker {

  if(ex != null && suppressTrace) setStackTrace(ex.getStackTrace)
}
object TemplateException {
  def context[T](contextString: String)(f: => T) = try {
    f
  } catch {
    case ex @ TemplateException(msg, _, context, _, _) =>
      throw TemplateException(msg, ex, s"While $contextString" +: context, suppressTrace = true, noCause = true)
  }
}

private[core] object CacheHashMap {
  def apply[K, V](maxSize: Int) = new java.util.LinkedHashMap[K, V] {
    override def removeEldestEntry(eldest: Entry[K, V]): Boolean = size() > maxSize
  }.asScala
}

private[core] class INISection(section: String, val underlying: Map[String, Seq[String]]) {
  def getMultiOptional(key: String) = underlying.getOrElse(key, Seq())
  def getMulti(key: String) = underlying.get(key) match {
    case None => throw TemplateException(s"No value '$key' found in section '$section'")
    case Some(v) => v
  }
  def getSingleOption(key: String) = underlying.get(key) match {
    case None => None
    case Some(Seq(v)) => Some(v)
    case _ => throw TemplateException(s"More than one value '$key' in section '$section'")
  }
  def getSingle(key: String) = getMulti(key) match {
    case Seq(v) => v
    case _ => throw TemplateException(s"More than one value '$key' in section '$section'")
  }
}
private[core] class INI(sections: Map[String, INISection]) extends Iterable[(String, INISection)] {
  def getSection(name: String) = sections.getOrElse(name, throw TemplateException(s"No section '$name' found"))
  def getSectionOptional(name: String) = sections.getOrElse(name, new INISection(name, Map()))
  override def iterator: Iterator[(String, INISection)] = sections.iterator
}
private[core] object INI {
  def loadRaw(path: Path) = try {
    val ini = new Ini
    ini.load(IOUtils.getFileReader(path))
    ini.asScala.mapValues(x =>
      x.keySet.asScala.toSeq.map(key =>
        key -> x.getAll(key, classOf[Array[String]]).toSeq).toMap).filter(_._2.nonEmpty).toMap
  } catch {
    case e: Exception =>
      throw TemplateException(s"Failed to parse .ini: ${e.getClass.getClass}: ${e.getMessage}")
  }

  def load(path: Path) = new INI(loadRaw(path).map(x => x._1 -> new INISection(x._1, x._2)))
}

private[core] object GenID {
  private var globalId = new AtomicInteger(0)
  private def makeGlobalId() = globalId.incrementAndGet()

  private val      chars = "abcdefghijklmnopqrstuvwxyz0123456789"
  private lazy val rng   = new SecureRandom()
  def makeId() =
    makeGlobalId()+"_"+new String((for(i <- 0 until 16) yield
      chars.charAt(math.abs(rng.nextInt() % chars.length))).toArray)
}

private[core] object XML extends XMLLoader[Elem] {
  override def parser: SAXParser = {
    val factory = SAXParserFactory.newInstance()
    factory.setNamespaceAware(false)
    factory.setValidating(false)
    for(feature <- Seq(// DTD loading for SVG is way too slow, unfortunely
                       "http://apache.org/xml/features/nonvalidating/load-dtd-grammar",
                       "http://apache.org/xml/features/nonvalidating/load-external-dtd",
                       // No thousands smiles
                       "http://xml.org/sax/features/external-general-entities",
                       "http://xml.org/sax/features/external-parameter-entities")) try {
      factory.setFeature(feature, false)
    } catch {
      case _: Exception =>
    }
    factory.newSAXParser()
  }
}