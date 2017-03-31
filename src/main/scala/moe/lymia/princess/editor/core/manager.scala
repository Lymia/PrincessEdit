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

package moe.lymia.princess.editor.core

import java.awt.image.BufferedImage
import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}

import moe.lymia.princess.rasterizer._
import moe.lymia.princess.renderer._
import moe.lymia.princess.util._
import org.eclipse.swt.SWT
import org.eclipse.swt.dnd.Clipboard
import org.eclipse.swt.graphics._
import org.eclipse.swt.widgets._
import org.eclipse.jface.window._
import rx._

import scala.util.Try

private case class RasterizerRequest(getData: () => (SVGData, Int, Int),
                                     callback: Either[BufferedImage => Unit, ImageData => Unit])
private class VolatileState {
  val rasterizerCondition = new Condition()
  val rasterizerRequests = new RequestBuffer[Any, RasterizerRequest](rasterizerCondition)

  val luaCondition = new Condition()
  val varUpdates = new AtomicMap[Var[_], Any]
  val luaRequests = new RequestBuffer[Any, () => Unit]

  @volatile var isRunning = true
  def shutdown() = {
    isRunning = false
    rasterizerCondition.done()
    luaCondition.done()
  }
}

private class RasterizeThread(state: VolatileState, rasterizer: SVGRasterizer) extends Thread {
  setName(s"PrincessEdit rasterizer thread #${ThreadId.make()}")
  override def run(): Unit =
    while(state.isRunning) state.rasterizerRequests.pullOne() match {
      case Some(req) =>
        val (svg, x, y) = req.getData()
        req.callback match {
          case Left (fn) => fn(svg.rasterizeAwt(rasterizer, x, y))
          case Right(fn) => fn(svg.rasterizeSwt(rasterizer, x, y))
        }
      case None => state.rasterizerCondition.waitFor()
    }
}

private class LuaThread(state: VolatileState) extends Thread {
  setName(s"PrincessEdit Lua thread #${ThreadId.make()}")
  override def run(): Unit =
    while(state.isRunning) {
      val varUpdates = state.varUpdates.pullAll()
      val actions = state.luaRequests.pullOne()
      if(varUpdates.nonEmpty || actions.nonEmpty) {
        Var.set(varUpdates.map(x => VarTuple(x._1.asInstanceOf[Var[Any]], x._2)).toSeq : _*)
        for(action <- actions) action()
      } else state.luaCondition.waitFor()
    }
}

// TODO: Catch errors during asynchronous execution
class ControlContext(val display: Display, state: VolatileState, factory: SVGRasterizerFactory,
                     luaThread: LuaThread, uiThread: Thread) extends SVGRasterizerFactory {
  val wm = new WindowManager()
  val clipboard = new Clipboard(display)
  val cache = SizedCache(1024 * 1024 * 64 /* TODO 64 MB cache, make an option in the future */)

  def createRasterizer() = factory.createRasterizer()

  def newShell(style: Int = SWT.SHELL_TRIM) = new Shell(display, style)

  private class Syncer[T] {
    private val lock = new Condition()
    @volatile private var isDone = false
    @volatile private var ret = null.asInstanceOf[Try[T]]
    def doTry(t: => T) = {
      ret = Try(t)
      isDone = true
      lock.done()
    }
    def sync() = {
      while(state.isRunning && !isDone) lock.waitFor(1)
      ret.get
    }
  }

  def needsSaving() = { } // TODO
  def queueUpdate[A, B <: A](rxVar: Var[A], newValue: B): Unit = state.varUpdates.put(rxVar, newValue)

  def asyncRenderAwt(key: Any, svg: SVGData, x: Int, y: Int)(callback: BufferedImage => Unit) =
    state.rasterizerRequests.add(key, RasterizerRequest(() => (svg, x, y), Left (callback)))
  def asyncRenderSwt(key: Any, svg: SVGData, x: Int, y: Int)(callback: ImageData => Unit) =
    state.rasterizerRequests.add(key, RasterizerRequest(() => (svg, x, y), Right(callback)))

  def asyncRenderAwt(key: Any, getData: => (SVGData, Int, Int))(callback: BufferedImage => Unit) =
    state.rasterizerRequests.add(key, RasterizerRequest(() => getData, Left (callback)))
  def asyncRenderSwt(key: Any, getData: => (SVGData, Int, Int))(callback: ImageData => Unit) =
    state.rasterizerRequests.add(key, RasterizerRequest(() => getData, Right(callback)))

  def syncRenderAwt(svg: SVGData, x: Int, y: Int): BufferedImage = {
    val sync = new Syncer[BufferedImage]
    asyncRenderAwt(new Object, svg, x, y)(x => sync.doTry(x))
    sync.sync()
  }
  def syncRenderSwt(svg: SVGData, x: Int, y: Int): ImageData = {
    val sync = new Syncer[ImageData]
    asyncRenderSwt(new Object, svg, x, y)(x => sync.doTry(x))
    sync.sync()
  }

  def asyncLuaExec(f: => Unit) = state.luaRequests.add(() => f)
  def asyncLuaExec(key: Any, f: => Unit) = state.luaRequests.add(key, () => f)
  def syncLuaExec[T](f: => T): T = if(Thread.currentThread() == luaThread) f else {
    val sync = new Syncer[T]
    state.luaRequests.add { () =>
      sync.doTry(f)
    }
    sync.sync()
  }

  def asyncUiExec(f: => Unit): Unit = display.asyncExec(() => f)
  def syncUiExec[T](f: => T): T = {
    @volatile var ret: T = null.asInstanceOf[T]
    display.syncExec(() =>
      ret = f
    )
    ret
  }

  def syncUiLuaExec[A, B](ui: => A, lua: => B): (A, B) = {
    val sync = new Syncer[B]
    asyncLuaExec {
      sync.doTry(lua)
    }
    (if(Thread.currentThread() == uiThread) ui else syncUiExec(ui), sync.sync())
  }
}

class UIManager(factory: SVGRasterizerFactory) {
  private val state = new VolatileState

  private val luaThread = new LuaThread(state)
  private val rasterizeThread = new RasterizeThread(state, factory.createRasterizer())

  luaThread.start()
  rasterizeThread.start()

  def mainLoop(init: ControlContext => Unit) = {
    val display = new Display()
    try {
      val ctx = new ControlContext(display, state, factory, luaThread, Thread.currentThread())
      init(ctx)
      while(!display.isDisposed && ctx.wm.getWindowCount > 0 && state.isRunning)
        if(!display.readAndDispatch()) display.sleep()
    } catch {
      case e: Exception => e.printStackTrace()
    } finally {
      if(!display.isDisposed) display.dispose()
      if(state.isRunning) state.shutdown()
    }
  }
}