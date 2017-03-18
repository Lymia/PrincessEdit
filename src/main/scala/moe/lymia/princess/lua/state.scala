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
import java.nio.file.{Path, Paths}

import moe.lymia.princess.util.IOUtils

import scala.collection.JavaConverters._

case class LuaRegistryEntry[T]()
final case class LuaState(L: Lua) extends AnyVal {
  def getRegistry[T](entry: LuaRegistryEntry[T], default: => T) = {
    val reg = L.getRegistry
    if(!reg.contains(entry)) reg.putlua(L, entry, default)
    reg.getlua(entry).asInstanceOf[T]
  }

  def isMain: Boolean = L.isMain
  def status: Int = L.status()
  def newThread() = new LuaState(L.newThread())

  def unwrap(o: LuaObject) = o.wrapped

  // stack manipulation functions
  def getTop: Int = L.getTop
  def setTop(n: Int): Unit = L.setTop(n)

  def pop(n: Int): Unit = L.pop(n)
  def popTop() = {
    val top = L.value(L.getTop).returnWrapper(this)
    L.pop(1)
    top
  }
  def peekTop() = L.value(L.getTop).returnWrapper(this)
  def push(o: LuaObject): Unit = L.push(o.toLua(this))
  def pushClosure(o: ScalaLuaClosure, doCheck: Boolean = true): Unit = L.push(o.checkError(doCheck).toLua(this))
  def pushValue(idx: Int): Unit = L.pushValue(idx)
  def insert(o: LuaObject, idx: Int): Unit = L.insert(o.toLua(this), idx)

  def xmove(to: LuaState, n: Int): Unit = L.xmove(to.L, n)

  def value(idx: Int): LuaReturnWrapper = L.value(idx).returnWrapper(this)

  def valueRange(range: Range): Seq[LuaReturnWrapper] = range.map((x : Int) => value(x))
  def valueRange(min: Int, max: Int): Seq[LuaReturnWrapper] = valueRange(min to max)
  def valueRange(min: Int): Seq[LuaReturnWrapper] = valueRange(min, getTop)

  // conversion functions
  def isNoneOrNil(narg: Int): Boolean = L.isNoneOrNil(narg)
  def toBoolean(o: LuaObject): Boolean = Lua.toBoolean(o.toLua(this))
  def toInteger(o: LuaObject): Int = L.toInteger(o.toLua(this))
  def toNumber(o: LuaObject): Double = L.toNumber(o.toLua(this))
  def toString(o: LuaObject): String = L.toString(o.toLua(this))
  def toPrintString(o: LuaObject): String = L.toPrintString(o.toLua(this))
  def toThread(o: LuaObject): Lua = L.toThread(o.toLua(this))
  def toUserdata(o: LuaObject): LuaUserdata = L.toUserdata(o.toLua(this))

  // field manipulation functions
  def getGlobals: LuaTable = L.getGlobals
  def getRegistry: LuaTable = L.getRegistry

  def getGlobal(name: String) = L.getGlobal(name).returnWrapper(this, s"invalid global $name")
  def setGlobal(name: String, value: LuaObject): Unit = L.setGlobal(name, value.toLua(this))

  def getMetafield(o: LuaObject, event: String) =
    L.getMetafield(o.toLua(this), event).returnWrapper(this, s"invalid metafield $event of ${toPrintString(o)}")

  def getTable(t: LuaObject, k: LuaObject) =
    L.getTable(t.toLua(this), k.toLua(this))
      .returnWrapper(this, s"invalid table field ${toPrintString(k)} of ${toPrintString(t)}")
  def setTable(t: LuaObject, k: LuaObject, v: LuaObject) =
    L.setTable(t.toLua(this), k.toLua(this), v.toLua(this))
  def setField(t: LuaObject, name: String, v: LuaObject): Unit = L.setField(t.toLua(this), name, v.toLua(this))

  def rawGet(t: LuaTable, k: LuaObject) =
    Lua.rawGet(t, k.toLua(this))
      .returnWrapper(this, s"invalid table field ${toPrintString(k)} of ${toPrintString(t)}")
  def rawSet(t: LuaTable, k: LuaObject, v: LuaObject) = L.rawSet(t, k.toLua(this), v.toLua(this))

  def newLib(root: LuaTable, k: String*): LuaTable = {
    var t: LuaTable = root
    for(n <- k) {
      if(Lua.`type`(Lua.rawGet(t, n)) == Lua.TNIL) L.rawSet(t, n, L.newTable())
      t = Lua.rawGet(t, n).asInstanceOf[LuaTable]
    }
    t
  }

  def registerGlobal(k: String, v: ScalaLuaClosure, doCheck: Boolean = true) =
    L.setGlobal(k, v.checkError(doCheck).toLua(this))
  def register(t: LuaObject, k: LuaObject, v: ScalaLuaClosure, doCheck: Boolean = true) =
    L.rawSet(t.toLua(this), k.toLua(this), v.checkError(doCheck).toLua(this))

  // chunk loading
  private def popLoad(status: Int) = {
    val ret = if(status == 0) Left (L.value(L.getTop).fromLua[LuaClosure](this))
              else            Right(L.value(L.getTop).fromLua[String    ](this))
    L.pop(1)
    ret
  }
  def load(in: InputStream, chunkname: String) = popLoad(L.load(in, chunkname))
  def load(in: Reader, chunkname: String) = popLoad(L.load(in, chunkname))
  def loadFile(filename: String) = popLoad(L.loadString(IOUtils.readFileAsString(Paths.get(filename)), s"@$filename"))
  def loadResource(filename: String) = popLoad(L.loadFile(filename))
  def loadString(s: String, chunkname: String) = popLoad(L.loadString(s, chunkname))
  def doString(s: String) = {
    val status = L.doString(s)
    if(status != 0) L.error(peekTop().as[String])
    L.pop(1)
  }

  // call functions
  def call(nargs: Int, nresults: Int) = L.call(nargs, nresults)
  def pcall(nargs: Int, nresults: Int, ef: LuaClosure): Int = L.pcall(nargs, nresults, ef.toLua(this))
  def pcall(nargs: Int, nresults: Int): Int =
    L.pcall(nargs, nresults, ScalaLuaClosure(L => { L.pushValue(1); 1 }).toLua(this))
  def callMeta(obj: Int, event: String): Boolean = L.callMeta(obj, event)

  def callCapture(fn: LuaClosure, args: LuaObject*) = {
    val capture = getRegistry(LuaState.captureFunctionReturn, {
      val chunk = loadString(
        """local function capture(fn, ...)
          | return {fn(...)}
          |end
          |return capture
        """.stripMargin, "@captureFn")
      call(chunk.left.getOrElse(sys.error("Failed to load capture function.")), 1).head.as[LuaClosure]
    })
    L.push(capture)
    L.push(fn.toLua(this))
    for(arg <- args) L.push(arg.toLua(this))
    L.call(args.length, 1)
    popTop().as[LuaTable]
  }
  def call(fn: LuaClosure, nresults: Int, args: LuaObject*) = {
    L.push(fn.toLua(this))
    for(arg <- args) L.push(arg.toLua(this))
    L.call(args.length, nresults)
    (for(_ <- 0 until nresults) yield popTop()).reverse
  }
  def pcall(fn: LuaClosure, nresults: Int, args: LuaObject*): Either[Seq[LuaReturnWrapper], String] = {
    L.push(fn.toLua(this))
    for(arg <- args) L.push(arg.toLua(this))
    if(pcall(args.length, nresults) != 0) Right(popTop().as[String])
    else Left((for(_ <- 0 until nresults) yield popTop()).reverse)
  }

  // api functions
  def error(message: String) = {
    L.error(message.toLua(this))
    sys.error("L.error returned unexpectedly!")
  }
  def error(message: String, level: Int) = {
    L.error(message.toLua(this), level)
    sys.error("L.error returned unexpectedly!")
  }
  def gc(what: Int, data: Int): Int = L.gc(what, data)

  def where(level: Int): String = L.where(level)
  def setHook(mask: Int, count: Int)(fn: (LuaState, Debug) => Unit) =
    L.setHook((L: Lua, ar: Debug) => {
      fn(new LuaState(L), ar)
      0
    }, mask, count)

  def newTable() = L.newTable()

  def getFenv(o: LuaObject): LuaTable = L.getFenv(o.toLua(this))
  def setFenv(o: LuaObject, table: LuaTable): Boolean = L.setFenv(o.toLua(this), table)

  def getMetatable(o: LuaObject): LuaTable = L.getMetatable(o.toLua(this))
  def setMetatable(o: LuaObject, mt: LuaTable): Unit = L.setMetatable(o.toLua(this), mt)
  def setMetatable(o: LuaObject, mt: Option[LuaTable]): Unit = L.setMetatable(o.toLua(this), mt.toLua(this))

  // operator functions
  def objLen(o: LuaObject) = Lua.objLen(o.toLua(this))
  def concat(n: Int): Unit = L.concat(n)
  def equal(o1: LuaObject, o2: LuaObject): Boolean = L.equal(o1.toLua(this), o2.toLua(this))
  def lessThan(o1: LuaObject, o2: LuaObject): Boolean = L.lessThan(o1.toLua(this), o2.toLua(this))
  def tableKeys(t: LuaObject) = L.tableKeys(t.toLua(this)).asScala
  def `type`(idx: Int): Int = L.`type`(idx)
  def typeNameOfIndex(idx: Int): String = L.typeNameOfIndex(idx)
}
object LuaState {
  private val captureFunctionReturn = LuaRegistryEntry[LuaClosure]()

  def makeSafeContext() = {
    val L = new Lua()

    BaseLib.open(L)
    MathLib.open(L)
    OSLib.open(L)
    StringLib.open(L)
    TableLib.open(L)
    L.setGlobal("dofile", Lua.NIL)
    L.setGlobal("loadfile", Lua.NIL)

    new LuaState(L)
  }
}