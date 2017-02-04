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

package moe.lymia.princess.core

import moe.lymia.princess.lua._
import moe.lymia.princess.util.IOUtils

import scala.collection.mutable
import scala.collection.JavaConverters._

final class LuaContext(packages: LoadedPackages) {
  val L = LuaState.makeSafeContext(packages.filePaths : _*)
  components.ComponentLib(packages).open(L)

  private def loadLuaPredef(path: String) = TemplateException.context(s"loading Lua predef $path") {
    val fullPath = packages.forceResolve(path)

    val chunk = L.loadString(IOUtils.readFileAsString(fullPath), s"@$path") match {
      case Left (c) => c
      case Right(e) => throw TemplateException(e)
    }
    L.pcall(chunk, 0) match {
      case Left (r) =>
      case Right(e) => throw TemplateException(e)
    }
  }
  private def loadPredefs(exportType: String) =
    for(e <- packages.getExports(exportType).sortBy(_.metadata.get("priority").map(_.head.toInt).getOrElse(0)))
      loadLuaPredef(e.path)
  loadPredefs("princess/predefs/global")
  loadPredefs(s"princess/predefs/${packages.gameId}")

  private val globalsCopy = new LuaRegistryEntry[LuaTable]
  private def copyTable(L: LuaState, tbl: LuaTable, ignore: String*): LuaTable = {
    val n = L.newTable()
    for(k <- tbl.keys().asScala) {
      k match {
        case s: String if !ignore.contains(s) =>
          L.push(L.rawGet(tbl, n))
          L.rawSet(n, s, L.`type`(L.getTop) match {
            case Lua.TTABLE => copyTable(L, L.popTop().as[LuaTable])
            case _          => L.popTop()
          })
        case _ => // ignore remaining fields
      }
    }

    val wrapper = L.newTable()
    val mt      = L.newTable()
    L.rawSet  (mt , "__metatable", "copied table metatable")
    L.rawSet  (mt , "__index"    , n)
    L.register(mt , "__newindex" , (L: LuaState) => { L.error("table is read only"); () })
    L.setMetatable(wrapper, mt)
    wrapper
  }
  private def loadLuaExport(path: String) = TemplateException.context(s"loading Lua export $path") {
    val fullPath = packages.forceResolve(path)

    val globals = L.getRegistry(globalsCopy, copyTable(L, L.getGlobals, "_G"))

    val overwrittenKeys = new mutable.WeakHashMap[Any, Unit]
    val env = L.newTable()

    val wrapper = L.newTable()
    val mt      = L.newTable()
    L.setMetatable(wrapper, mt)
    L.register(mt , "__index"    , (L: LuaState, tbl: Any, k: Any) => {
      if(overwrittenKeys.contains(k)) LuaRet(L.rawGet(env, k))
      else if(k == "_G")              LuaRet(wrapper)
      else                            LuaRet(L.rawGet(globals, k))
    })
    L.register(mt , "__newindex" , (L: LuaState, tbl: Any, k: Any, v: Any) => {
      overwrittenKeys.put(k, ())
      L.rawSet(env, k, v)
      ()
    })
    L.register(mt , "__tostring" , () => s"environment for $path")
    L.rawSet  (mt , "__metatable", s"environment metatable for $path")

    val chunk = L.loadString(IOUtils.readFileAsString(fullPath), s"@$path") match {
      case Left (c) => c
      case Right(e) => throw TemplateException(e)
    }
    L.setFenv(chunk, wrapper)
    L.pcall(chunk, 0) match {
      case Left (r) =>
      case Right(e) => throw TemplateException(e)
    }

    env
  }
  private val exportCache = new mutable.HashMap[String, LuaTable]
  def getLuaExport(path: String) = exportCache.getOrElseUpdate(path, loadLuaExport(path))
}