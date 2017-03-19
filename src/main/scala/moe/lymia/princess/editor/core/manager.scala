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

import moe.lymia.princess.core._
import moe.lymia.princess.editor.nodes._
import moe.lymia.princess.editor.lua._
import moe.lymia.princess.rasterizer._
import moe.lymia.princess.renderer._
import moe.lymia.princess.renderer.lua._
import moe.lymia.princess.util._

import org.eclipse.swt.graphics._
import org.eclipse.swt.widgets._

import rx._

private class Condition(val lock: Object = new Object) extends AnyVal {
  def done() = lock synchronized { lock.notify() }
  def waitFor(length: Int = 10) = lock synchronized { lock.wait(length) }
}

private case class RasterizerRequest(svg: SVGData, x: Int, y: Int,
                                     callback: Either[BufferedImage => Unit, ImageData => Unit])
private class VolatileState {
  private val rasterizerRenderRequest = new AtomicReference[Option[RasterizerRequest]](None)
  private val rasterizerRequestSync = new Condition
  def waitRequest() = rasterizerRequestSync.waitFor()
  def pullRequest() = rasterizerRenderRequest.getAndSet(None)

  def requestRenderAwt(svg: SVGData, x: Int, y: Int)(callback: BufferedImage => Unit) = {
    rasterizerRenderRequest.set(Some(RasterizerRequest(svg, x, y, Left(callback))))
    rasterizerRequestSync.done()
  }
  def requestRenderSwt(svg: SVGData, x: Int, y: Int)(callback: ImageData => Unit) = {
    rasterizerRenderRequest.set(Some(RasterizerRequest(svg, x, y, Right(callback))))
    rasterizerRequestSync.done()
  }

  private val luaActionQueue = new AtomicReference(Seq.empty[() => Unit])
  private val luaUpdateSync = new Condition
  private val varUpdateQueue = new AtomicReference(Map.empty[Var[_], Any])
  def waitLua() = luaUpdateSync.waitFor()
  def pullLuaActionQueue() = luaActionQueue.getAndSet(Seq.empty)
  def pullUpdateQueue() =
    varUpdateQueue.getAndSet(Map.empty).map(x => VarTuple(x._1.asInstanceOf[Var[Any]], x._2)).toSeq
  def queueLuaAction(a: => Unit) = {
    while(!{
      val current = luaActionQueue.get()
      luaActionQueue.compareAndSet(current, current :+ (() => a))
    }) { }
    luaUpdateSync.done()
  }
  def queueUpdate[T](rxVar: Var[T], newValue: T) = {
    while(!{
      val current = varUpdateQueue.get()
      varUpdateQueue.compareAndSet(current, current + ((rxVar, newValue)))
    }) { }
    luaUpdateSync.done()
  }

  @volatile var isRunning = true
  def shutdown() = {
    isRunning = false
    rasterizerRequestSync.done()
    luaUpdateSync.done()
  }
}

private object ThreadId {
  private var threadId = new AtomicInteger(0)
  def make() = threadId.incrementAndGet()
}

private class RasterizeThread(state: VolatileState, rasterizer: SVGRasterizer) extends Thread {
  setName(s"PrincessEdit rasterizer thread #${ThreadId.make()}")
  override def run(): Unit =
    while(state.isRunning) state.pullRequest() match {
      case Some(req) => req.callback match {
        case Left (fn) => fn(req.svg.rasterizeAwt(rasterizer, req.x, req.y))
        case Right(fn) => fn(req.svg.rasterizeSwt(rasterizer, req.x, req.y))
      }
      case None => state.waitRequest()
    }
}

private class LuaThread(state: VolatileState) extends Thread {
  setName(s"PrincessEdit Lua thread #${ThreadId.make()}")
  override def run(): Unit =
    while(state.isRunning) {
      val varUpdates = state.pullUpdateQueue()
      val actions = state.pullLuaActionQueue()
      if(varUpdates.nonEmpty || actions.nonEmpty) {
        Var.set(varUpdates : _*)
        for(action <- actions) action()
      } else state.waitLua()
    }
}

private class UIControlContext(display: Display, state: VolatileState) extends ControlContext {
  private class Syncer[T] {
    private val lock = new Condition()
    @volatile private var isDone = false
    @volatile private var ret = null.asInstanceOf[T]
    def done(t: T) = {
      isDone = true
      ret = t
      lock.done()
    }
    def sync() = {
      while(state.isRunning && !isDone) lock.waitFor(1)
      ret
    }
  }

  override def needsSaving() = { }
  override def queueUpdate[T](rxVar: Var[T], newValue: T): Unit = state.queueUpdate(rxVar, newValue)

  override def asyncRenderAwt(svg: SVGData, x: Int, y: Int)(callback: BufferedImage => Unit) =
    state.requestRenderAwt(svg, x, y)(callback)
  override def asyncRenderSwt(svg: SVGData, x: Int, y: Int)(callback: ImageData => Unit) =
    state.requestRenderSwt(svg, x, y)(callback)

  override def syncRenderAwt(svg: SVGData, x: Int, y: Int): BufferedImage = {
    val sync = new Syncer[BufferedImage]
    asyncRenderAwt(svg, x, y)(sync.done)
    sync.sync()
  }
  override def syncRenderSwt(svg: SVGData, x: Int, y: Int): ImageData = {
    val sync = new Syncer[ImageData]
    asyncRenderSwt(svg, x, y)(sync.done)
    sync.sync()
  }

  override def asyncLuaExec(f: => Unit) = state.queueLuaAction(f)
  override def syncLuaExec[T](f: => T): T = {
    val sync = new Syncer[T]
    state.queueLuaAction {
      sync.done(f)
    }
    sync.sync()
  }

  override def asyncUiExec(f: => Unit): Unit = display.asyncExec(() => f)
  override def syncUiExec[T](f: => T): T = {
    @volatile var ret: T = null.asInstanceOf[T]
    display.syncExec(() =>
      ret = f
    )
    ret
  }
}

class UIManager(game: GameManager, factory: SVGRasterizerFactory) {
  game.lua.loadModule(RenderModule)
  game.lua.loadModule(EditorModule)

  private val state = new VolatileState

  private val cache = SizedCache(1024 * 1024 * 64 /* TODO 64 MB cache, make an option in the future */)
  val render = new RenderManager(game, cache)

  private val luaThread = new LuaThread(state)
  private val rasterizeThread = new RasterizeThread(state, factory.createRasterizer())

  luaThread.start()
  rasterizeThread.start()

  def shutdown() = state.shutdown()

  def mainLoop(init: (Display, Shell, ControlContext) => Unit) = {
    val display = new Display()
    try {
      val shell = new Shell(display)
      val ctx = new UIControlContext(display, state)
      init(display, shell, ctx)
      shell.layout(true)
      shell.open()

      // run the event loop as long as the window is open
      while(!shell.isDisposed) if(!display.readAndDispatch()) display.sleep()
    } finally {
      display.dispose()
    }
    state.shutdown()
  }
}