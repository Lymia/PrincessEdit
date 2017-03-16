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
import moe.lymia.princess.editor.data._
import moe.lymia.princess.editor.lua._
import moe.lymia.princess.rasterizer._
import moe.lymia.princess.renderer._
import moe.lymia.princess.renderer.lua._
import moe.lymia.princess.util._
import rx._

private case class RasterizerRequest(svg: SVGData, x: Int, y: Int, callback: BufferedImage => Unit)
private class VolatileState {
  private val rasterizerRenderRequest = new AtomicReference[Option[RasterizerRequest]](None)
  private val rasterizerRequestSync = new Object
  def waitRequest() = rasterizerRequestSync synchronized { rasterizerRequestSync.wait(10) }
  def pullRequest() = rasterizerRenderRequest.getAndSet(None)
  def requestRender(svg: SVGData, x: Int, y: Int)(callback: BufferedImage => Unit) = {
    rasterizerRenderRequest.set(Some(RasterizerRequest(svg, x, y, callback)))
    rasterizerRequestSync synchronized { rasterizerRequestSync.notify() }
  }

  private val luaActionQueue = new AtomicReference(Seq.empty[() => Unit])
  private val luaUpdateSync = new Object
  private val varUpdateQueue = new AtomicReference(Map.empty[Var[_], Any])
  def waitLua() = luaUpdateSync synchronized { luaUpdateSync.wait(10) }
  def pullLuaActionQueue() = luaActionQueue.getAndSet(Seq.empty)
  def pullUpdateQueue() =
    varUpdateQueue.getAndSet(Map.empty).map(x => VarTuple(x._1.asInstanceOf[Var[Any]], x._2)).toSeq
  def queueLuaAction(a: => Unit) = {
    while(!{
      val current = luaActionQueue.get()
      luaActionQueue.compareAndSet(current, current :+ (() => a))
    }) { }
    luaUpdateSync synchronized { luaUpdateSync.notify() }
  }
  def queueUpdate[T](rxVar: Var[T], newValue: T) = {
    while(!{
      val current = varUpdateQueue.get()
      varUpdateQueue.compareAndSet(current, current + ((rxVar, newValue)))
    }) { }
    luaUpdateSync synchronized { luaUpdateSync.notify() }
  }

  @volatile var isRunning = true
  def shutdown() = {
    isRunning = false
    rasterizerRequestSync.notify()
    luaUpdateSync synchronized { luaUpdateSync.notify() }
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
      case Some(req) => req.callback(req.svg.rasterize(rasterizer, req.x, req.y))
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

private class UIControlContext(state: VolatileState) extends ControlContext {
  override def needsSaving() = { }
  override def queueUpdate[T](rxVar: Var[T], newValue: T): Unit = state.queueUpdate(rxVar, newValue)
  override def queueLua(f: => Unit) = state.queueLuaAction(f)
  override def renderRequest(svg: SVGData, x: Int, y: Int)(callback: BufferedImage => Unit) =
    state.requestRender(svg, x, y)(callback)
}

class UIManager(game: GameManager, factory: SVGRasterizerFactory) {
  game.lua.loadModule(RenderModule)
  game.lua.loadModule(EditorModule)

  private val state = new VolatileState

  private val cache = SizedCache(1024 * 1024 * 64 /* 64 MB cache, make an option in the future */)
  val render = new RenderManager(game, cache)

  private val luaThread = new LuaThread(state)
  private val rasterizeThread = new RasterizeThread(state, factory.createRasterizer())

  luaThread.start()
  rasterizeThread.start()

  val ctx: ControlContext = new UIControlContext(state)
  def shutdown() = state.shutdown()
}