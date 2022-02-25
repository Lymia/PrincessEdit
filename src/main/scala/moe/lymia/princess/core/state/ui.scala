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

import moe.lymia.princess.gui.utils.ExtendedResourceManager
import moe.lymia.princess.svg._
import moe.lymia.princess.svg.rasterizer.{SVGRasterizer, SVGRasterizerFactory}
import moe.lymia.princess.util._
import org.eclipse.jface.resource.{JFaceResources, LocalResourceManager}
import org.eclipse.jface.window._
import org.eclipse.swt.SWT
import org.eclipse.swt.dnd.Clipboard
import org.eclipse.swt.graphics._
import org.eclipse.swt.widgets._
import rx._

import java.awt.image.BufferedImage
import scala.annotation.elidable
import scala.util.Try

// TODO: Improve error handling

private case class RasterizerRequest(getData: () => (SVGRenderable, Int, Int),
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
        Var.set(varUpdates.map(x => Var.Assignment(x._1.asInstanceOf[Var[Any]], x._2)).toSeq : _*)
        for(action <- actions) action()
      } else state.luaCondition.waitFor()
    }
}

class ControlContext(val display: Display, state: VolatileState, loop: UILoop, factory: SVGRasterizerFactory,
                     luaThread: LuaThread, uiThread: Thread, rasterizeThread: Thread) {
  val clipboard = new Clipboard(display)
  val cache = SizedCache(1024 * 1024 * 64 /* TODO 64 MB cache, make an option in the future */)

  val wm = loop.wm

  @elidable(elidable.ASSERTION)
  def assertUIThread(): Unit = assert(Thread.currentThread() eq uiThread)

  @elidable(elidable.ASSERTION)
  def assertLuaThread(): Unit = assert(Thread.currentThread() eq luaThread)

  @elidable(elidable.ASSERTION)
  def assertRasterizeThread(): Unit = assert(Thread.currentThread() eq rasterizeThread)

  private val jfaceResources = JFaceResources.getResources(display)
  val resources = new ExtendedResourceManager(jfaceResources, this)
  def newResourceManager() = new ExtendedResourceManager(new LocalResourceManager(jfaceResources), this)

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

  def queueUpdate[A, B <: A](rxVar: Var[A], newValue: B): Unit = state.varUpdates.put(rxVar, newValue)

  def asyncRenderAwt(key: Any, svg: SVGRenderable, x: Int, y: Int)(callback: BufferedImage => Unit) =
    state.rasterizerRequests.add(key, RasterizerRequest(() => (svg, x, y), Left (callback)))
  def asyncRenderSwt(key: Any, svg: SVGRenderable, x: Int, y: Int)(callback: ImageData => Unit) =
    state.rasterizerRequests.add(key, RasterizerRequest(() => (svg, x, y), Right(callback)))

  def asyncRenderAwt(key: Any, getData: => (SVGRenderable, Int, Int))(callback: BufferedImage => Unit) =
    state.rasterizerRequests.add(key, RasterizerRequest(() => getData, Left (callback)))
  def asyncRenderSwt(key: Any, getData: => (SVGRenderable, Int, Int))(callback: ImageData => Unit) =
    state.rasterizerRequests.add(key, RasterizerRequest(() => getData, Right(callback)))

  def syncRenderAwt(svg: SVGRenderable, x: Int, y: Int): BufferedImage = {
    val sync = new Syncer[BufferedImage]
    asyncRenderAwt(new Object, svg, x, y)(x => sync.doTry(x))
    sync.sync()
  }
  def syncRenderSwt(svg: SVGRenderable, x: Int, y: Int): ImageData = {
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

class UILoop {
  val wm = new WindowManager()

  def mainLoop(init: Display => Unit) = {
    val display = new Display()
    try {
      init(display)
      while(!display.isDisposed && wm.getWindowCount > 0) if(!display.readAndDispatch()) display.sleep()
    } catch {
      case e: Exception =>
        e.printStackTrace()
      case t: Throwable =>
        t.printStackTrace()
        throw t
    } finally {
      if(!display.isDisposed) display.dispose()
    }
  }
}

class UIManager(loop: UILoop, factory: SVGRasterizerFactory) {
  private val state = new VolatileState

  private val luaThread = new LuaThread(state)
  private val rasterizeThread = new RasterizeThread(state, factory.createRasterizer())

  luaThread.start()
  rasterizeThread.start()

  def mainLoop(display: Display)(init: ControlContext => Unit) = {
    val ctx = new ControlContext(display, state, loop, factory, luaThread, Thread.currentThread(), rasterizeThread)
    init(ctx)
    display.addListener(SWT.Dispose, _ => state.shutdown())
  }
}