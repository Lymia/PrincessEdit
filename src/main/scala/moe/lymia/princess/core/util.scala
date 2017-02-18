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

import java.nio.file.Path
import java.security.SecureRandom
import java.util.Map.Entry
import javax.xml.parsers.SAXParserFactory

import moe.lymia.princess.lua.LuaErrorMarker
import moe.lymia.princess.util.IOUtils

import scala.collection.JavaConverters._
import scala.xml.factory.XMLLoader
import scala.xml.{Elem, SAXParser}

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

private[core] object INI {
  def load(path: Path) = try {
    IOUtils.loadIni(path)
  } catch {
    case e: Exception =>
      throw TemplateException(s"Failed to parse .ini: ${e.getClass.getClass}: ${e.getMessage}")
  }
}

private[core] object GenID {
  private var globalId = 0
  private val globalIdLock = new Object
  private def makeGlobalId() = globalIdLock synchronized {
    val id = globalId
    globalId = globalId + 1
    id
  }

  private val      chars = "abcdefghijklmnopqrstuvwxyz0123456789"
  private lazy val rng   = new SecureRandom()
  def makeId() =
    makeGlobalId()+"_"+new String((for(i <- 0 until 16) yield chars.charAt(rng.nextInt(chars.length))).toArray)
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