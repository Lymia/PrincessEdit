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

package moe.lymia.princess.ui

import java.util
import java.util.Map.Entry

import scala.collection.JavaConverters._

case class CountedCache[K, V](var maxSize: Int) {
  private val underlying = new java.util.LinkedHashMap[K, V] {
    override def removeEldestEntry(eldest: Entry[K, V]): Boolean = size() > maxSize
  }.asScala

  def cached(key: K, value: => V) = underlying.getOrElseUpdate(key, value)
}

case class CacheSection[K, V]()
case class SizedCache(var maxSize: Int) {
  private var currentSize = 0
  private val underlying = new util.LinkedHashMap[(CacheSection[_, _], Any), (Any, Int)] {
    override def removeEldestEntry(eldest: Entry[(CacheSection[_, _], Any), (Any, Int)]): Boolean = {
      if(currentSize > maxSize && !this.isEmpty) {
        val iter = this.entrySet().iterator()
        do {
          val entry = iter.next()
          currentSize = currentSize - entry.getValue._2
          iter.remove()
        } while(currentSize > maxSize && iter.hasNext)
      }
      false
    }
  }.asScala

  def cached[K, V](section: CacheSection[K, V])(key: K, value: => (V, Int)): V = {
    val ulKey: (CacheSection[_, _], Any) = (section, key)
    underlying.getOrElseUpdate(ulKey, {
      val t = value
      currentSize = currentSize + t._2
      t
    })._1.asInstanceOf[V]
  }
}