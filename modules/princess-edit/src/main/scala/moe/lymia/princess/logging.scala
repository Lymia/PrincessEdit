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

package moe.lymia.princess

import java.text.SimpleDateFormat
import java.util.{Date, TimeZone}

// TODO: Logging to file?
// TODO: Probably just use a library for this.

final case class LogLevel private (name: String, i: Int) {
  def > (o: LogLevel) = i >  o.i
  def < (o: LogLevel) = i <  o.i
  def >=(o: LogLevel) = i >= o.i
  def <=(o: LogLevel) = i <= o.i

  override def toString = name
}
object LogLevel {
  val TRACE = LogLevel("TRACE", 0)
  val DEBUG = LogLevel("DEBUG", 1)
  val INFO  = LogLevel("INFO" , 2)
  val WARN  = LogLevel("WARN" , 3)
  val ERROR = LogLevel("ERROR", 4)
}

trait Logger {
  def log(level: LogLevel, source: Option[String], message: => String)
  def log(level: LogLevel, message: => String): Unit = log(level, None, message)

  final def trace(message: => String) = log(LogLevel.TRACE, message)
  final def debug(message: => String) = log(LogLevel.DEBUG, message)
  final def info (message: => String) = log(LogLevel.INFO , message)
  final def warn (message: => String) = log(LogLevel.WARN , message)
  final def error(message: => String) = log(LogLevel.ERROR, message)

  def bind(name: String): Logger = new BoundLogger(this, name)
}
private class BoundLogger(parent: Logger, name: String) extends Logger {
  override def log(level: LogLevel, source: Option[String], message: => String): Unit =
    parent.log(level, Some(source.getOrElse(name)), message)
  override def bind(name: String): Logger = new BoundLogger(parent, name)
}

case object DefaultLogger extends Logger {
  private val dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
  dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"))
  override def log(level: LogLevel, source: Option[String], message: => String): Unit =
    println("%s [%-5s - %s] %s".format(
      dateFormat.format(new Date()), level.toString, source.getOrElse("<unknown>"), message))
}