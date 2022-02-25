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

package moe.lymia.princess.core.state

import moe.lymia.princess.svg.rasterizer.SVGRasterizer
import moe.lymia.princess.util.ThreadId

import java.util.concurrent.{ConcurrentHashMap, LinkedBlockingQueue}
import java.util.concurrent.atomic.AtomicInteger

object ThreadId {
  private val threadId = new AtomicInteger(0)
  def make(): Int = threadId.incrementAndGet()
}

trait ExecutorBase[Request, Message, Return] {
  protected def nameBase: String
  protected def handleRequest(r: Request, m: Message, callback: Return => Unit): Unit

  @volatile private var isRunning = true
  def shutdown(): Unit = {
    isRunning = false
  }

  private val requestData = new ConcurrentHashMap[Request, (Message, Return => Unit)]
  private val requestBuffer = new LinkedBlockingQueue[Request]

  def pushRequest(req: Request, msg: Message, callback: Return => Unit): Unit = {
    requestData.put(req, (msg, callback))
    requestBuffer.add(req)
  }

  private val thread = new Thread() {
    setName(s"PrincessEdit $nameBase thread #${ThreadId.make()}")
    override def run(): Unit = {
      while (isRunning) {
        val req = requestBuffer.poll()
        requestData.remove(req) match {
          case null => // no op
          case (msg, callback) => handleRequest(req, msg, callback)
        }
      }
    }
  }
  def start(): Unit = thread.start()
}
