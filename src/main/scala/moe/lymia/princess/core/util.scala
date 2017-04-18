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

import moe.lymia.lua.LuaErrorMarker
import moe.lymia.princess.util.IOUtils
import org.ini4j.Ini

import scala.collection.JavaConverters._

final case class EditorException(message: String, ex: Throwable = null, context: Seq[String] = Seq(),
                                 suppressTrace: Boolean = false, noCause: Boolean = false)
  extends RuntimeException((context :+ message).mkString(": "), if(noCause) null else ex, suppressTrace, true)
  with    LuaErrorMarker {

  if(ex != null && suppressTrace) setStackTrace(ex.getStackTrace)
}
object EditorException {
  def context[T](contextString: String)(f: => T) = try {
    f
  } catch {
    case ex @ EditorException(msg, _, context, _, _) =>
      throw EditorException(msg, ex, s"While $contextString" +: context, suppressTrace = true, noCause = true)
  }
}

class INISection(section: String, val underlying: Map[String, Seq[String]]) {
  def getMultiOptional(key: String) = underlying.getOrElse(key, Seq())
  def getMulti(key: String) = underlying.get(key) match {
    case None => throw EditorException(s"No value '$key' found in section '$section'")
    case Some(v) => v
  }
  def getSingleOption(key: String) = underlying.get(key) match {
    case None => None
    case Some(Seq(v)) => Some(v)
    case _ => throw EditorException(s"More than one value '$key' in section '$section'")
  }
  def getSingle(key: String) = getMulti(key) match {
    case Seq(v) => v
    case _ => throw EditorException(s"More than one value '$key' in section '$section'")
  }
}

class INI(sections: Map[String, INISection]) extends Iterable[(String, INISection)] {
  def getSection(name: String) = sections.getOrElse(name, throw EditorException(s"No section '$name' found"))
  def getSectionOptional(name: String) = sections.getOrElse(name, new INISection(name, Map()))
  override def iterator: Iterator[(String, INISection)] = sections.iterator
}

object INI {
  def loadRaw(path: Path) = try {
    val ini = new Ini
    ini.load(IOUtils.getFileReader(path))
    ini.asScala.mapValues(x =>
      x.keySet.asScala.toSeq.map(key =>
        key -> x.getAll(key, classOf[Array[String]]).toSeq).toMap).filter(_._2.nonEmpty).toMap
  } catch {
    case e: Exception =>
      throw EditorException(s"Failed to parse .ini: ${e.getClass.getClass}: ${e.getMessage}")
  }

  def load(path: Path) = new INI(loadRaw(path).map(x => x._1 -> new INISection(x._1, x._2)))
}