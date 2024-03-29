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

package moe.lymia.princess.core.cardmodel

import moe.lymia.lua._
import moe.lymia.princess.core.gamedata.{GameData, I18N}
import moe.lymia.princess.core.state.GuiContext
import moe.lymia.princess.editor.nodes._
import moe.lymia.princess.editor.scripting._
import moe.lymia.princess.svg.RenderManager
import moe.lymia.princess.util.swt.RxOwner
import org.eclipse.swt.widgets._
import rx._

final class UIData(parent: Composite, node: RootNode, registerControlCallbacks: Control => Unit)
                  (implicit ctx: NodeContext, owner: Ctx.Owner) {
  private val rootRxValue = Rx { () }
  implicit val rootRx = new Ctx.Owner(rootRxValue)
  val control = node.createControl(parent)(ctx, ctx.newUIContext(registerControlCallbacks), rootRx)

  def kill() = rootRxValue.kill()
}

final class DataRoot(L: LuaState, data: DataStore, controlCtx: GuiContext, i18n: I18N, node: RootNode)
  extends RxOwner {

  private implicit val ctx = new NodeContext(L.newThread(), data, controlCtx, i18n)

  val luaData = node.createRx
  def createUI(parent: Composite, registerControlCallbacks: Control => Unit = _ => ()) =
    new UIData(parent, node, registerControlCallbacks)
}

sealed abstract class RootSource(L: LuaState, controlCtx: GuiContext, i18n: I18N) {
  val exists: Boolean = false
  def createRootNode(data: DataStore, args: Seq[Rx[LuaObject]]): RootNode
  def createRoot(data: DataStore, args: Seq[Rx[LuaObject]]) =
    new DataRoot(L.newThread(), data, controlCtx, i18n, createRootNode(data, args))
}

final class NullRootSource(L: LuaState, controlCtx: GuiContext, i18n: I18N)
  extends RootSource(L, controlCtx, i18n) {

  private val node = RootNode(None, Seq(), LuaClosure { () => })
  override def createRootNode(data: DataStore, args: Seq[Rx[LuaObject]]): RootNode = node
}
final class LuaRootSource(L: LuaState, controlCtx: GuiContext, i18n: I18N, fn: LuaClosure)
  extends RootSource(L, controlCtx, i18n) {

  override val exists = true
  private lazy val emptyRoot = RootNode(None, Seq(), fn)

  def createRootNode(data: DataStore, args: Seq[Rx[LuaObject]]) =
    if(args.isEmpty) emptyRoot else RootNode(None, args.map(RxFieldNode), fn)
}
object RootSource {
  private def create(game: GameData, controlCtx: GuiContext, i18n: I18N, export: LuaObject, method: String) = {
    val fn = game.lua.L.newThread().getTable(export, method).as[LuaClosure]
    new LuaRootSource(game.lua.L, controlCtx, i18n, fn)
  }
  def optional(game: GameData, controlCtx: GuiContext, i18n: I18N, export: String, method: String) = {
    game.getEntryPoint(export) match {
      case Some(exportObj) => create(game, controlCtx, i18n, exportObj, method)
      case None => new NullRootSource(game.lua.L, controlCtx, i18n)
    }
  }
  def apply(game: GameData, controlCtx: GuiContext, i18n: I18N, export: String, method: String) =
    create(game, controlCtx, i18n, game.getRequiredEntryPoint(export), method)
}

final class TableColumnData(val title: String, val width: Int, val isDefault: Boolean,
                            L: LuaState, fn: LuaClosure, orderFn: Option[LuaClosure]) {
  def computeColumnData(v: Any) = L.newThread().call(fn, 1, v).head.as[String]
  def computeOrdering(a: Any, aColumn: String, b: Any, bColumn: String) = orderFn match {
    case Some(order) =>
      L.newThread().call(order, 1, a, b).head.as[Int]
    case None => aColumn.compare(bColumn)
  }
}
final case class LuaColumnData(columns: Seq[TableColumnData])
object LuaColumnData {
  def apply(game: GameData): LuaColumnData = {
    val L = game.lua.L.newThread()
    val fn = L.getTable(game.getRequiredEntryPoint("card-columns"), "cardColumns").as[LuaClosure]
    LuaColumnData(L.call(fn, 1).head.as[Seq[TableColumnData]])
  }
}

final case class LuaExportData(defaultNameFormat: String, helpText: Option[String])
object LuaExportData {
  def apply(game: GameData): LuaExportData = {
    val L = game.lua.L.newThread()
    val ep = game.getRequiredEntryPoint("export-data")
    val name = L.getTable(ep, "defaultNameFormat").as[String]
    val help = L.getTable(ep, "helpText").as[Option[String]]
    LuaExportData(name, help)
  }
}

final class LuaViewData(L: LuaState, viewTypeName: String, setNameFn: LuaClosure) {
  def computeName(v: Any) = L.newThread().call(setNameFn, 1, v).head.as[String]
}
object LuaViewData {
  def apply(game: GameData): LuaViewData = {
    val L = game.lua.L.newThread()
    val ep = game.getRequiredEntryPoint("view-data")
    val viewTypeName = L.getTable(ep, "viewTypeName").as[String]
    val setNameFn = L.getTable(ep, "viewName").as[LuaClosure]
    new LuaViewData(L, viewTypeName, setNameFn)
  }
}

final class GameIDData(game: GameData, controlCtx: GuiContext, i18n: I18N) {
  val internal_L = game.lua.L.newThread()

  val card     = RootSource(game, controlCtx, i18n, "card-form", "cardForm")
  val viewRoot = RootSource(game, controlCtx, i18n, "view-data", "viewForm")
  val viewData = LuaViewData(game)
  val columns  = LuaColumnData(game)
  val renderer = new RenderManager(game, controlCtx.cache)
  val export   = LuaExportData(game)
}