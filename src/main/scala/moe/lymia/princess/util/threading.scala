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

package moe.lymia.princess.util

import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}

object ThreadId {
  private val threadId = new AtomicInteger(0)
  def make(): Int = threadId.incrementAndGet()
}

final class Condition(private val lock: Object = new Object) extends AnyVal {
  def done(): Unit = lock synchronized { lock.notify() }
  def waitFor(length: Int = 10): Unit = lock synchronized { lock.wait(length) }
}

final class AtomicMap[K, V](condition: Condition = new Condition()) {
  private val underlying = new AtomicReference(Map.empty[K, V])

  def nonEmpty: Boolean = underlying.get().nonEmpty

  def put(key: K, v: V): Unit = {
    while(!{
      val current = underlying.get()
      underlying.compareAndSet(current, current + ((key, v)))
    }) { }
    condition.done()
  }

  def pullAll(): Map[K, V] = underlying.getAndSet(Map.empty)
  def pullOne(): Option[(K, V)] = {
    var taken: Option[(K, V)] = null
    while(!{
      val current = underlying.get()
      taken = current.headOption
      taken match {
        case Some((k, _)) => underlying.compareAndSet(current, current - k)
        case None => true
      }
    }) { }
    taken
  }

  def waitFor(length: Int = 10): Unit = condition.waitFor(length)
}

final class AtomicList[V](condition: Condition = new Condition()) {
  private val underlying = new AtomicReference(Seq.empty[V])

  def nonEmpty: Boolean = underlying.get().nonEmpty

  def add(v: V): Unit = {
    while(!{
      val current = underlying.get()
      underlying.compareAndSet(current, current :+ v)
    }) { }
    condition.done()
  }

  def pullAll(): Seq[V] = underlying.getAndSet(Seq.empty)
  def pullOne(): Option[V] = {
    var head: Option[V] = null
    while(!{
      val current = underlying.get()
      head = current.headOption
      underlying.compareAndSet(current, current.drop(1))
    }) { }
    head
  }

  def waitFor(length: Int = 10): Unit = condition.waitFor(length)
}

final class RequestBuffer[K, V](condition: Condition = new Condition()) {
  private val supersedableRequests = new AtomicMap[K, V]
  private val requests             = new AtomicList[V]

  def nonEmpty: Boolean = requests.nonEmpty || supersedableRequests.nonEmpty

  def add(request: K, v: V): Unit = supersedableRequests.put(request, v)
  def add(v: V): Unit = requests.add(v)

  def pullAll(): Seq[V] = requests.pullAll() ++ supersedableRequests.pullAll().values
  def pullOne(): Option[V] = requests.pullOne().orElse(supersedableRequests.pullOne().map(_._2))

  def waitFor(length: Int = 10): Unit = condition.waitFor(length)
}