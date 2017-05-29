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

import moe.lymia.lua._
import moe.lymia.princess.util._

import scala.collection.JavaConverters._
import scala.collection.mutable

trait LuaLibrary {
  def open(L: LuaState, table: LuaTable)
}

trait LuaModule {
  val moduleName: String
  def getLibraries(ctx: LuaContext): Seq[LuaLibrary]
}

private object CoreLib {
  val defaultSort = LuaClosure((L: LuaState, a: Any, b: Any) => L.lessThan(a, b))
  implicit case object LuaLogLevel extends LuaUserdataType[LogLevel]
}
private final case class CoreLib(context: LuaContext) extends LuaLibrary {
  import CoreLib._

  def open(L: LuaState, table: LuaTable) = {
    L.rawSet(table, "gameId", context.packages.gameId)

    L.register(table, "hasExport", (s: String) => context.packages.resolve(s).isDefined)
    L.register(table, "loadLuaExport", (s: String) => context.getLuaExport(s))
    L.register(table, "loadINIExport", (s: String) => INI.loadRaw(context.packages.forceResolve(s)))
    L.register(table, "getExportList", () => context.packages.getExportKeys.toSeq)
    L.register(table, "getExports", (s: String, system: Boolean) =>
      (if(system) context.packages.getSystemExports(s) else context.packages.getExports(s)).map{ e =>
        val t = L.newTable()
        L.rawSet(t, "path", e.path)
        L.rawSet(t, "types", e.types)
        L.rawSet(t, "metadata", e.metadata)
        t
      })

    L.register(table, "where", (L: LuaState, i: Int) => {
      val where = L.where(i + 1)
      if(where.isEmpty) None else Some(where.replace(": ", ""))
    })

    L.register(table, "trimString", (s: String) => s.trim)
    L.register(table, "splitString", (s: String, on: String) => s.split(on).toSeq)

    L.register(table, "stableSort", (L: LuaState, t: Seq[Any], cmp: Option[LuaClosure]) => {
      // TODO: Optimize
      val sortFn = cmp.getOrElse(defaultSort)
      def lt(x: Any, y: Any) = L.call(sortFn, 1, x, y).head.as[Boolean]
      t.sorted((x : Any, y : Any) => if(lt(x, y)) -1 else if(lt(y, x)) 1 else 0)
    })

    L.register(table, "Object", () => new LuaLookup { } : HasLuaMethods)

    val levels = L.newTable()
    L.rawSet(levels, "TRACE", LogLevel.TRACE)
    L.rawSet(levels, "DEBUG", LogLevel.DEBUG)
    L.rawSet(levels, "INFO" , LogLevel.INFO )
    L.rawSet(levels, "WARN" , LogLevel.WARN )
    L.rawSet(levels, "ERROR", LogLevel.ERROR)
    L.rawSet(table, "LogLevel", levels)

    L.register(table, "log", (L: LuaState, level: LogLevel, source: Option[String], fn: LuaClosure) =>
      context.logger.log(level, source, L.call(fn, 1).head.as[String]))
  }
}

private case object CoreModule extends LuaModule {
  override val moduleName: String = "core"
  override def getLibraries(ctx: LuaContext) = Seq(CoreLib(ctx))
}

final class LuaContext(val packages: PackageList, val logger: Logger, modules: Seq[LuaModule]) {
  val L = LuaState.makeSafeContext()

  private val systemTable = L.newTable()
  private val loadedModules = new mutable.HashSet[String]
  private var isClean = true

  private def loadLuaPredef(path: String) = EditorException.context(s"loading Lua predef $path") {
    logger.trace(s" - Loading predef '$path'")

    val fullPath = packages.forceResolve(path)

    val chunk = L.loadString(IOUtils.readFileAsString(fullPath), s"@$path") match {
      case Left (c) => c
      case Right(e) => throw EditorException(e)
    }
    L.pcall(chunk, 0, systemTable).right.foreach(e => throw EditorException(e))
  }
  private def loadPredefs(exportType: String) =
    for(e <- packages.getSystemExports(exportType).sortBy(_.metadata.get("priority").map(_.head.toInt).getOrElse(0)))
      loadLuaPredef(e.path)
  def loadModule(mods: LuaModule*) = for(mod <- mods) {
    logger.trace(s"Loading module '${mod.moduleName}'")

    if(!isClean) throw EditorException("cannot load additional Lua modules after exports are loaded")
    if(isModuleLoaded(mod)) throw EditorException("module already loaded")

    val libs = mod.getLibraries(this)
    if(libs.nonEmpty) logger.trace(" - Loading native libraries")

    val table = L.newTable()
    for(lib <- libs) lib.open(L, table)
    L.rawSet(systemTable, mod.moduleName, table)

    loadPredefs(StaticExportIDs.Predef(mod.moduleName))
    loadedModules.add(mod.moduleName)
  }
  def isModuleLoaded(mod: LuaModule) = loadedModules.contains(mod.moduleName)
  loadModule(CoreModule)
  for(mod <- modules) loadModule(mod)

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

  private def loadLuaExport(path: String): LuaObject = EditorException.context(s"loading Lua export $path") {
    isClean = false

    logger.trace(s"Loading export '$path'")

    val fullPath = packages.forceResolve(path)

    val L = this.L.newThread()

    val globals = L.getRegistry(LuaContext.globalsWrapper, copyTable(L, Seq(), L.getGlobals, "_G", "package").toLua(L))

    val overwrittenKeys = new mutable.WeakHashMap[Any, Unit]
    val exports = L.newTable()

    val _G = L.newTable()
    val mt = L.newTable()
    L.setMetatable(_G, mt)

    L.register(mt , "__tostring" , (tbl: Any) => s"environment for $path")
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
      case Right(e) => throw EditorException(e)
    }
    L.setFenv(chunk, _G)
    L.pcall(chunk, 1).fold(identity, e => throw EditorException(e)).head.as[Option[Any]] match {
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
