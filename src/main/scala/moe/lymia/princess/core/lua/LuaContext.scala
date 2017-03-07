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

package moe.lymia.princess.core.lua

import moe.lymia.princess.core._
import moe.lymia.princess.lua._
import moe.lymia.princess.util._

import scala.collection.mutable
import scala.collection.JavaConverters._

private final case class ExportLib(context: LuaContext, packages: PackageList) {
  def open(L: LuaState) = {
    val princess = L.newLib("_princess")

    L.rawSet(princess, "gameId", packages.gameId)

    L.register(princess, "hasExport", (s: String) => packages.resolve(s).isDefined)
    L.register(princess, "loadLuaExport", (s: String) => context.getLuaExport(s))
    L.register(princess, "loadINIExport", (s: String) => INI.loadRaw(packages.forceResolve(s)))
    L.register(princess, "getExportList", () => packages.getExportKeys.toSeq)
    L.register(princess, "getExports", (s: String, system: Boolean) =>
      (if(system) packages.getSystemExports(s) else packages.getExports(s)).map{ e =>
        val t = L.newTable()
        L.rawSet(t, "path", e.path)
        L.rawSet(t, "types", e.types)
        L.rawSet(t, "metadata", e.metadata)
        t
      })
  }
}

final class LuaContext(packages: PackageList, loggerP: Logger) {
  val L = LuaState.makeSafeContext()
  CoreLib(loggerP).open(L)
  ComponentLib(packages).open(L)
  ExportLib(this, packages).open(L)
  TemplateLib.open(L)
  TextLib(packages).open(L)

  private val logger = loggerP.bind("LuaContext")

  private def loadLuaPredef(path: String) = TemplateException.context(s"loading Lua predef $path") {
    logger.trace(s"Loading predef $path")

    val fullPath = packages.forceResolve(path)

    val chunk = L.loadString(IOUtils.readFileAsString(fullPath), s"@$path") match {
      case Left (c) => c
      case Right(e) => throw TemplateException(e)
    }
    L.pcall(chunk, 0).right.foreach(e => throw TemplateException(e))
  }
  private def loadPredefs(exportType: String) =
    for(e <- packages.getSystemExports(exportType).sortBy(_.metadata.get("priority").map(_.head.toInt).getOrElse(0)))
      loadLuaPredef(e.path)
  loadPredefs(StaticExportIDs.Predef)

  private case class TableWrapper(name: String, contents: Map[String, Any])
  private implicit object LuaTableWrapper extends LuaUserdataType[TableWrapper] {
    metatable { (L, mt) =>
      L.register(mt , "__tostring", (w: TableWrapper) => s"copy of ${w.name}")
      L.rawSet  (mt, "__metatable", "global table metatable")
      L.register(mt, "__index"    , (w: TableWrapper, k: String) => w.contents.getOrElse(k, Lua.NIL))
      L.register(mt, "__newindex" , (L: LuaState) => { L.error("table is read only"); () })
    }
  }

  private def copyTable(L: LuaState, path: Seq[String], tbl: LuaTable, ignore: String*): TableWrapper = {
    val map = new mutable.HashMap[String, Any]
    for(k <- tbl.keySet().asScala) {
      k match {
        case s: String if !ignore.contains(s) =>
          L.push(L.rawGet(tbl, s))
          val t = L.`type`(L.getTop)
          map.put(s, t match {
            case Lua.TTABLE => copyTable(L, path :+ s, L.popTop().as[LuaTable]).toLua(L)
            case _          => L.popTop().as[Any]
          })
        case _ => // ignore remaining fields
      }
    }
    TableWrapper(path.mkString("."), map.toMap)
  }

  private case class TableReturn(path: String, env: LuaTable)
  private implicit object LuaTableReturn extends LuaUserdataType[TableReturn] {
    metatable { (L, mt) =>
      L.register(mt , "__tostring" , (t: TableReturn) => s"exports for ${t.path}")
      L.rawSet  (mt , "__metatable", s"export metatable")
      L.register(mt, "__index"     , (L: LuaState, t: TableReturn, k: String) => L.rawGet(t.env, k))
      L.register(mt, "__newindex"  , (L: LuaState) => { L.error("table is read only"); () })
    }
  }

  private def loadLuaExport(path: String): LuaObject = TemplateException.context(s"loading Lua export $path") {
    logger.trace(s"Loading export $path")

    val fullPath = packages.forceResolve(path)

    val L = this.L.newThread()

    val globals = L.getRegistry(LuaContext.globalsWrapper, copyTable(L, Seq(), L.getGlobals, "_G", "package").toLua(L))

    val overwrittenKeys = new mutable.WeakHashMap[Any, Unit]
    val exports = L.newTable()

    val _G = L.newTable()
    val mt = L.newTable()
    L.setMetatable(_G, mt)

    L.register(mt , "__tostring" , (tbl: Any) => s"environment for ${path}")
    L.rawSet  (mt , "__metatable", s"export environment metatable")
    L.register(mt, "__index"     , (L: LuaState, tbl: Any, k: Any) => {
      if(overwrittenKeys.contains(k)) LuaRet(L.rawGet(exports, k))
      else if(k == "_G")              LuaRet(_G)
      else                            LuaRet(L.getTable(globals, k))
    })
    L.register(mt, "__newindex"  , (L: LuaState, tbl: Any, k: Any, v: Any) => {
      overwrittenKeys.put(k, ())
      L.rawSet(exports, k, v)
      ()
    })

    val chunk = L.loadString(IOUtils.readFileAsString(fullPath), s"@$path") match {
      case Left (c) => c
      case Right(e) => throw TemplateException(e)
    }
    L.setFenv(chunk, _G)
    L.pcall(chunk, 1).fold(identity, e => throw TemplateException(e)).head.as[Option[Any]] match {
      case Some(x) => x
      case None    => TableReturn(path, exports)
    }
  }

  private val exportCache = new mutable.HashMap[String, LuaObject]
  def getLuaExport(path: String) = exportCache.getOrElseUpdate(path, loadLuaExport(path))
}
object LuaContext {
  private val globalsWrapper = new LuaRegistryEntry[Any]
}
