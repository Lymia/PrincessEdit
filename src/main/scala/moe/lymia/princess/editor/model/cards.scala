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

package moe.lymia.princess.editor.model

import moe.lymia.lua.{Lua, _}
import rx._

import java.util.UUID
import scala.collection.mutable

final class CardData(protected val project: Project)
  extends JsonPathSerializable with HasModifyTimeDataStore with RefCount {

  val root = project.idData.card.createRoot(fields, Seq())
}

private final class MergeLuaTable(tables: Any*) {
  private val cache = new mutable.HashMap[String, Any]
  private def findObject(L: LuaState, k: String): Any = {
    for(t <- tables) {
      val o = L.getTable(t, k).as[Any].asInstanceOf[AnyRef]
      if(o ne Lua.NIL) return o
    }
    Lua.NIL
  }
  def getValue(L: LuaState, k: String) = cache.getOrElseUpdate(k, findObject(L, k))
}
private object MergeLuaTable {
  implicit object LuaMergeLuaTable extends LuaUserdataType[MergeLuaTable] {
    metatable { (L, mt) =>
      L.register(mt, "__index", (L: LuaState, t: MergeLuaTable, k: String) => t.getValue(L, k))
    }
  }
}

final case class FullCardData(uuid: UUID, project: Project, cardData: CardData, sourceInfo: ViewInfo,
                              globalData: Rx[Option[Any]])
                             (implicit owner: Ctx.Owner){
  val luaData = Rx {
    val table: Seq[Any] = Seq(cardData.root.luaData(), sourceInfo.root.luaData())
    new MergeLuaTable(globalData().fold(table)(_ +: table) : _*).toLua(project.idData.internal_L)
  }
  val columnData = Rx {
    project.idData.columns.columns.map(f => f -> f.computeColumnData(luaData())).toMap
  }
}