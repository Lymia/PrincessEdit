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
import moe.lymia.princess.util._
import org.eclipse.jface.resource.{JFaceResources, LocalResourceManager}
import org.eclipse.jface.window._
import org.eclipse.swt.SWT
import org.eclipse.swt.dnd.Clipboard
import org.eclipse.swt.graphics._
import org.eclipse.swt.widgets._
import rx._

import java.io.ByteArrayInputStream
import scala.annotation.elidable
import scala.util.Try

// TODO: Improve error handling

class GuiContext(val display: Display, loop: GuiLoop, uiThread: Thread) {
  private val svgExecutor = new SvgRasterizer
  private val luaExecutor = new LuaExecutor

  svgExecutor.start()
  luaExecutor.start()

  @volatile var isRunning = true
  def shutdown(): Unit = {
    isRunning = false
    svgExecutor.shutdown()
    luaExecutor.shutdown()
  }

  val clipboard = new Clipboard(display)
  val cache: SizedCache = SizedCache(1024 * 1024 * 64 /* TODO 64 MB cache, make an option in the future */)

  val wm: WindowManager = loop.wm

  @elidable(elidable.ASSERTION)
  def assertUiThread(): Unit = assert(Thread.currentThread() eq uiThread)

  @elidable(elidable.ASSERTION)
  def assertLuaThread(): Unit = luaExecutor.assertActiveThread()

  private val jfaceResources = JFaceResources.getResources(display)
  val resources = new ExtendedResourceManager(jfaceResources, this)
  def newResourceManager() = new ExtendedResourceManager(new LocalResourceManager(jfaceResources), this)

  def newShell(style: Int = SWT.SHELL_TRIM) = new Shell(display, style)

  private class Syncer[T] {
    private val lock = new Condition()
    @volatile private var isDone = false
    @volatile private var ret = null.asInstanceOf[Try[T]]
    def doTry(t: => T): Unit = {
      ret = Try(t)
      isDone = true
      lock.done()
    }
    def sync(): T = {
      while (isRunning && !isDone) lock.waitFor(1)
      ret.get
    }
  }

  def queueUpdate[A, B <: A](rxVar: Var[A], newValue: B): Unit = luaExecutor.updateVar(rxVar, newValue)

  private def loadImage(arr: Array[Byte]): ImageData = {
    val loader = new ImageLoader()
    loader.load(new ByteArrayInputStream(arr))
    loader.data.head
  }

  def asyncRender(key: Any, svg: String, x: Int, y: Int)(callback: ImageData => Unit): Unit =
    svgExecutor.render(() => (svg, x, y), key, x => callback(loadImage(x)))
  def asyncRender(key: Any, getData: => (String, Int, Int))(callback: ImageData => Unit): Unit =
    svgExecutor.render(() => getData, key, x => callback(loadImage(x)))
  def syncRender(svg: String, x: Int, y: Int): ImageData =
    loadImage(svgExecutor.renderSync(svg, x, y))

  def asyncLuaExec(f: => Unit): Unit = luaExecutor.executeLua(() => f)
  def asyncLuaExec(key: Any, f: => Unit): Unit = luaExecutor.executeLua(() => f, key)
  def syncLuaExec[T](f: => T): T = if (luaExecutor.isActiveThread) f else {
    val sync = new Syncer[T]
    asyncLuaExec {
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
    (if (Thread.currentThread() == uiThread) ui else syncUiExec(ui), sync.sync())
  }
}

class GuiLoop {
  val wm = new WindowManager()

  def mainLoop(init: GuiContext => Unit): Unit = {
    val display = new Display()
    val ctx = new GuiContext(display, this, Thread.currentThread())

    try {
      display.addListener(SWT.Dispose, _ => ctx.shutdown())
      init(ctx)
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