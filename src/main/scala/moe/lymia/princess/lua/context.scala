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

package moe.lymia.princess.lua
import java.io.{InputStream, Reader}
import java.nio.file.Path
import java.util

import scala.collection.JavaConverters._

case class LuaRegistryEntry[T]()
final case class LuaState(L: Lua) extends AnyVal {
  def getRegistry[T](entry: LuaRegistryEntry[T], default: => T) = {
    val reg = L.getRegistry
    if(!reg.contains(entry)) reg.putlua(L, reg, default)
    reg.getlua(entry).asInstanceOf[T]
  }

  def isMain: Boolean = L.isMain
  def status: Int = L.status()

  // stack manipulation functions
  def getTop: Int = L.getTop
  def setTop(n: Int): Unit = L.setTop(n)

  def pop(n: Int): Unit = L.pop(n)
  def popTop() = {
    val top = L.value(L.getTop).returnWrapper
    L.pop(1)
    top
  }
  def peekTop() = L.value(L.getTop()).returnWrapper
  def push[T : ToLua](o: T): Unit = L.push(o.toLua(this))
  def pushClosure(o: ScalaLuaClosure): Unit = L.push(o.toLua(this))
  def pushValue(idx: Int): Unit = L.pushValue(idx)
  def insert[T : ToLua](o: T, idx: Int): Unit = L.insert(o.toLua(this), idx)

  def xmove(to: LuaState, n: Int): Unit = L.xmove(to.L, n)

  def value(idx: Int) = L.value(idx).returnWrapper

  // conversion functions
  def isNoneOrNil(narg: Int): Boolean = L.isNoneOrNil(narg)
  def toBoolean[O : ToLua](o: O): Boolean = L.toBoolean(o.toLua(this))
  def toInteger[O : ToLua](o: O): Int = L.toInteger(o.toLua(this))
  def toNumber[O : ToLua](o: O): Double = L.toNumber(o.toLua(this))
  def toString[O : ToLua](o: O): String = L.toString(o.toLua(this))
  def toThread[O : ToLua](o: O): Lua = L.toThread(o.toLua(this))
  def toUserdata[O : ToLua](o: O): LuaUserdata = L.toUserdata(o.toLua(this))

  // field manipulation functions
  def getGlobals: LuaTable = L.getGlobals
  def getRegistry: LuaTable = L.getRegistry

  def getGlobal(name: String) = L.getGlobal(name).returnWrapper
  def setGlobal[V : ToLua](name: String, value: V): Unit = L.setGlobal(name, value.toLua(this))

  def getMetafield[O : ToLua](o: scala.Any, event: String) = L.getMetafield(o.toLua(this), event).returnWrapper

  def getTable[T : ToLua, K : ToLua](t: T, k: K) = L.getTable(t.toLua(this), k.toLua(this)).returnWrapper
  def setTable[T : ToLua, K : ToLua, V : ToLua](t: T, k: K, v: V) =
    L.setTable(t.toLua(this), k.toLua(this), v.toLua(this))
  def setField[T : ToLua, V : ToLua](t: T, name: String, v: V): Unit = L.setField(t.toLua(this), name, v.toLua(this))

  def rawGet[T : ToLua, K : ToLua](t: T, k: K) = Lua.rawGet(t.toLua(this), k.toLua(this)).returnWrapper
  def rawSet[T : ToLua, K : ToLua, V : ToLua](t: T, k: K, v: V) = L.rawSet(t.toLua(this), k.toLua(this), v.toLua(this))

  def registerGlobal(k: String, v: ScalaLuaClosure) = L.setGlobal(k, v.toLua(this))
  def register[T : ToLua, K : ToLua](t: T, k: K, v: ScalaLuaClosure) =
    L.rawSet(t.toLua(this), k.toLua(this), v.toLua(this))

  // chunk loading
  private def popLoad(status: Int) = {
    val ret = if(status == 0) Left (L.value(L.getTop).fromLua[LuaClosure](this))
              else            Right(L.value(L.getTop).fromLua[String    ](this))
    L.pop(1)
    ret
  }
  def load(in: InputStream, chunkname: String) = popLoad(L.load(in, chunkname))
  def load(in: Reader, chunkname: String) = popLoad(L.load(in, chunkname))
  def loadFile(filename: String) = popLoad(L.loadFile(filename))
  def loadString(s: String, chunkname: String) = popLoad(L.loadString(s, chunkname))
  def doString(s: String) = {
    val status = L.doString(s)
    if(status != 0) sys.error(s"Lua error: ${peekTop().as[String](this)}")
    L.pop(1)
  }

  // call functions
  def call(nargs: Int, nresults: Int) = L.call(nargs, nresults)
  def pcall(nargs: Int, nresults: Int, ef: LuaClosure): Int = L.pcall(nargs, nresults, ef.toLua(this))
  def callMeta(obj: Int, event: String): Boolean = L.callMeta(obj, event)

  // api functions
  def error(message: String) = {
    L.error(message.toLua(this))
    sys.error("L.error returned unexpectedly!")
  }
  def gc(what: Int, data: Int): Int = L.gc(what, data)

  def where(level: Int): String = L.where(level)
  def setHook(mask: Int, count: Int)(fn: (LuaState, Debug) => Unit) =
    L.setHook(new Hook {
      override def luaHook(L: Lua, ar: Debug) = {
        fn(new LuaState(L), ar)
        0
      }
    }, mask, count)

  def getFenv[T : ToLua](o: T): LuaTable = L.getFenv(o.toLua(this))
  def setFenv[O : ToLua, T : ToLua](o: O, table: T): Boolean = L.setFenv(o.toLua(this), table.toLua(this))

  def getMetatable[T : ToLua](o: T): LuaTable = L.getMetatable(o.toLua(this))
  def setMetatable[O : ToLua, M : ToLua](o: O, mt: M): Unit = L.setMetatable(o.toLua(this), mt.toLua(this))

  // operator functions
  def concat(n: Int): Unit = L.concat(n)
  def equal[A : ToLua, B: ToLua](o1: A, o2: B): Boolean = L.equal(o1.toLua(this), o2.toLua(this))
  def lessThan[A : ToLua, B : ToLua](o1: A, o2: B): Boolean = L.lessThan(o1.toLua(this), o2.toLua(this))
  def tableKeys[T : ToLua](t: T) = L.tableKeys(t.toLua(this)).asScala
  def `type`(idx: Int): Int = L.`type`(idx)
  def typeNameOfIndex(idx: Int): String = L.typeNameOfIndex(idx)
}
object LuaState {
  def makeBasicContext() = {
    val L = new Lua()

    BaseLib.open(L)
    MathLib.open(L)
    OSLib.open(L)
    StringLib.open(L)
    TableLib.open(L)

    new LuaState(L)
  }
  def makeSafeContext(paths: Path*) = {
    val L = makeBasicContext()
    new SafePackageLib(paths).open(L)
    L.setGlobal("loadfile", LuaNil)
    L
  }
}