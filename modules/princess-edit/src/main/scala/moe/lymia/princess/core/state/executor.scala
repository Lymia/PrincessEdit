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

import moe.lymia.princess.native.fonts.FontDatabase
import moe.lymia.princess.native.svg.Resvg
import rx.Var

import java.util.concurrent.atomic.{AtomicInteger, AtomicLong}
import java.util.concurrent.{ConcurrentHashMap, LinkedBlockingQueue}
import scala.collection.mutable

private object ThreadId {
  private val threadId = new AtomicInteger(0)
  def make(): Int = threadId.incrementAndGet()
}

private trait ExecutorBase[Request, Message] {
  protected def nameBase: String
  protected def handleRequest(r: Request, m: Message): Unit

  private val randId = new AtomicLong(0)
  protected def newRandId(): Long = randId.incrementAndGet()

  @volatile private var isRunning = true
  def shutdown(): Unit = {
    isRunning = false
  }

  private val requestData = new ConcurrentHashMap[Request, Message]
  private val requestBuffer = new LinkedBlockingQueue[Request]

  protected def pushRequest(req: Request, msg: Message): Unit = {
    requestData.put(req, msg)
    requestBuffer.add(req)
  }

  private val thread = new Thread() {
    setName(s"PrincessEdit $nameBase thread #${ThreadId.make()}")
    override def run(): Unit = {
      while (isRunning) {
        val req = requestBuffer.poll()
        if (req != null) requestData.remove(req) match {
          case null => // no op
          case msg => handleRequest(req, msg)
        }
      }
    }
  }

  def isActiveThread: Boolean = Thread.currentThread() eq thread
  def assertActiveThread(): Unit = assert(isActiveThread)
  def start(): Unit = thread.start()
}

private case class SvgRenderRequest(data: () => (String, Int, Int), callback: Array[Byte] => Unit)
private[state] class SvgRasterizer extends ExecutorBase[Any, SvgRenderRequest] {
  private val fontDb = new FontDatabase()

  override protected def nameBase: String = "SVG rasterizer"
  override protected def handleRequest(r: Any, m: SvgRenderRequest): Unit = {
    val (data, w, h) = m.data()
    m.callback(renderSync(data, w, h))
  }

  def renderSync(data: String, w: Int, h: Int): Array[Byte] =
    Resvg.render(data, None, fontDb, w, h)
  def render(func: () => (String, Int, Int), key: Any, callback: Array[Byte] => Unit): Unit =
    pushRequest(key, SvgRenderRequest(func, callback))
}

private sealed trait LuaRequest
private object LuaRequest {
  case object UpdateVars extends LuaRequest
  case class Execute(v: () => Unit) extends LuaRequest
}
private[state] class LuaExecutor extends ExecutorBase[Any, LuaRequest] {
  private val varUpdatesMarker = new Object
  private val varUpdatesLock = new Object
  @volatile private var varUpdates = new mutable.HashMap[Var[_], Any]

  override protected def nameBase: String = "Lua executor"
  override protected def handleRequest(r: Any, m: LuaRequest): Unit = {
    m match {
      case LuaRequest.UpdateVars =>
        val oldMap = varUpdatesLock synchronized {
          val oldMap = varUpdates
          varUpdates = new mutable.HashMap()
          oldMap
        }
        Var.set(oldMap.map(x => Var.Assignment(x._1.asInstanceOf[Var[Any]], x._2)).toSeq : _*)
      case LuaRequest.Execute(func) => func()
    }
  }

  def updateVar[T](v: Var[T], value: T): Unit = {
    varUpdatesLock synchronized { varUpdates.put(v, value) }
    pushRequest(varUpdatesMarker, LuaRequest.UpdateVars)
  }
  def executeLua(func: () => Unit): Unit = pushRequest(newRandId(), LuaRequest.Execute(func))
  def executeLua(func: () => Unit, key: Any): Unit = pushRequest(key, LuaRequest.Execute(func))
}