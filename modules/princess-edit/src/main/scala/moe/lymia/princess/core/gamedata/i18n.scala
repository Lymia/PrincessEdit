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

import moe.lymia.princess.util.CountedCache

import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.text.MessageFormat
import java.util.{Locale, Properties}
import scala.collection.JavaConverters._
import scala.collection.mutable

trait I18NSource {
  val locale: Locale
  def apply(key: String, args: Any*): String
}

final case class MarkedI18NSource(parent: I18NSource) extends I18NSource {
  private val messageFormatCache = new CountedCache[String, MessageFormat](1024)

  override val locale: Locale = parent.locale
  private def applyLiteral(key: String, args: Seq[Any]) =
    messageFormatCache.cached(key, new MessageFormat(key, locale)).format(args.toArray)
  override def apply(key: String, args: Any*): String =
    if(key.startsWith("$$")) applyLiteral(key.substring(1), args)
    else if(key.startsWith("$")) parent.apply(key.substring(1), args : _*)
    else applyLiteral(key, args)
}

final case class StaticI18NSource(locale: Locale, map: Map[String, String]) extends I18NSource {
  private val messageFormatCache = new collection.mutable.HashMap[String, Option[MessageFormat]]
  def getFormat(key: String): Option[MessageFormat] =
    messageFormatCache.getOrElseUpdate(key, map.get(key).map(s => new MessageFormat(s, locale)))
  def apply(key: String, args: Any*): String = getFormat(key).map(format =>
    format.format(args.toArray)
  ).getOrElse("<"+key+">")
}

final case class I18N(userLua: I18NSource, system: I18NSource) {
  val user: MarkedI18NSource = MarkedI18NSource(userLua)

  assert(userLua.locale == system.locale)
  val locale: Locale = system.locale
}

final class I18NLoader(game: GameData) {
  private def loadExportData(id: String, language: String, country: String, system: Boolean = false) = {
    val exports = game.getExports(StaticExportIds.I18N(id, language, country), system)
    val sorted = exports.sortBy(_.metadata.get("priority").flatMap(_.headOption).map(_.toInt).getOrElse(0))

    val map = new mutable.HashMap[String, String]

    for(file <- sorted) {
      val prop = new Properties()
      val reader = new InputStreamReader(Files.newInputStream(game.forceResolve(file.path)), StandardCharsets.UTF_8)
      prop.load(reader)
      reader.close()

      for((k, v) <- prop.asScala) map.put(k, v)
    }

    StaticI18NSource(Locale.ENGLISH, map.toMap)
  }

  // TODO: Temporary loader
  val i18n: I18N = {
    val user   = loadExportData(game.gameId, "en", "generic")
    val system = loadExportData("_princess", "en", "generic", system = true)
    I18N(user, system)
  }
}