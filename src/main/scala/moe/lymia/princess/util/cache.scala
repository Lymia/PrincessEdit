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

package moe.lymia.princess.util

import java.util
import java.util.Map.Entry

import scala.collection.JavaConverters._

final case class CountedCache[K, V](var maxSize: Int) {
  private val underlying = new java.util.LinkedHashMap[K, V] {
    override def removeEldestEntry(eldest: Entry[K, V]): Boolean = size() > maxSize
  }.asScala

  def cached(key: K, value: => V) =
    underlying.get(key) match {
      case Some(x) =>
        // refresh the current entry, bumping it to the top of the list
        underlying.remove(key)
        underlying.put(key, x)

        x
      case None =>
        val res = value
        underlying.put(key, res)
        res
    }
}

final case class CacheSection[K, V]()
trait SizedCache {
  var maxSize: Long
  def cached[K, V](section: CacheSection[K, V])(key: K, value: => (V, Long)): V
}
object SizedCache {
  def apply(maxSize: Long): SizedCache = new SizedCacheImpl(maxSize)
}

object NullCache extends SizedCache {
  override def maxSize: Long = 0
  override def maxSize_=(l: Long) { }
  override def cached[K, V](section: CacheSection[K, V])(key: K, value: => (V, Long)): V = value._1
}

private final class SizedCacheImpl(var maxSize: Long) extends SizedCache {
  private val entryOverhead = 16

  private var currentSize = 0l
  private val underlying = new util.LinkedHashMap[(CacheSection[_, _], Any), (Any, Long)] {
    override def removeEldestEntry(eldest: Entry[(CacheSection[_, _], Any), (Any, Long)]): Boolean = {
      if(currentSize > maxSize && !this.isEmpty) {
        val iter = this.entrySet().iterator()
        do {
          val entry = iter.next()
          currentSize = Math.subtractExact(currentSize, entry.getValue._2)
          iter.remove()
        } while(currentSize > maxSize && iter.hasNext)
      }
      false
    }
  }.asScala

  def cached[K, V](section: CacheSection[K, V])(key: K, value: => (V, Long)): V = {
    val ulKey: (CacheSection[_, _], Any) = (section, key)
    underlying.get(ulKey) match {
      case Some(x) =>
        // refresh the current entry, bumping it to the top of the list
        underlying.remove(ulKey)
        underlying.put(ulKey, x)

        x._1.asInstanceOf[V]
      case None =>
        val t = {
          val rt = value
          if(rt._2 < 0) sys.error("Cache object size must be positive")
          rt.copy(_2 = rt._2 + entryOverhead)
        }
        if(t._2 < maxSize) {
          underlying.put(ulKey, t)
          currentSize = Math.addExact(currentSize, t._2)
        }
        t._1
    }
  }
}