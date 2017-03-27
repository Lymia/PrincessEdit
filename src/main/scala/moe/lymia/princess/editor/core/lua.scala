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

import moe.lymia.princess.core._
import moe.lymia.princess.editor.nodes._
import moe.lymia.lua._

import org.eclipse.swt.widgets._

import rx._

final class UIData(parent: Composite, node: RootNode, registerControlCallbacks: Control => Unit)
                  (implicit ctx: NodeContext, owner: Ctx.Owner) {
  private val rootRxValue = Rx { () }
  implicit val rootRx = new Ctx.Owner(rootRxValue)
  val control = node.createControl(parent)(ctx, ctx.newUIContext(registerControlCallbacks), rootRx)

  def kill() = rootRxValue.kill()
}

final class DataRoot(L: LuaState, data: DataStore, controlCtx: ControlContext, node: RootNode) {
  private implicit val ctx = new NodeContext(L.newThread(), data, controlCtx)

  private val dummyRx = Rx.unsafe { () }
  private implicit val owner = new Ctx.Owner(dummyRx)

  val luaData = node.createRx
  def createUI(parent: Composite, registerControlCallbacks: Control => Unit = _ => ()) =
    new UIData(parent, node, registerControlCallbacks)
  def kill() = dummyRx.kill()
}

sealed abstract class RootSource(L: LuaState, controlCtx: ControlContext) {
  val exists: Boolean = false
  def createRootNode(data: DataStore, args: Seq[Rx[LuaObject]]): RootNode
  def createRoot(data: DataStore, args: Seq[Rx[LuaObject]]) =
    new DataRoot(L.newThread(), data, controlCtx, createRootNode(data, args))
}

final class NullRootSource(L: LuaState, controlCtx: ControlContext) extends RootSource(L, controlCtx) {
  private val node = RootNode(None, Seq(), LuaClosure { () => })
  override def createRootNode(data: DataStore, args: Seq[Rx[LuaObject]]): RootNode = node
}
final class LuaRootSource(L: LuaState, controlCtx: ControlContext, fn: LuaClosure)
  extends RootSource(L, controlCtx) {

  override val exists = true
  private lazy val emptyRoot = RootNode(None, Seq(), fn)

  def createRootNode(data: DataStore, args: Seq[Rx[LuaObject]]) =
    if(args.isEmpty) emptyRoot else RootNode(None, args.map(RxFieldNode), fn)
}
object RootSource {
  private def create(game: GameManager, controlCtx: ControlContext, export: LuaObject, method: String) = {
    val fn = game.lua.L.newThread().getTable(export, method).as[LuaClosure]
    new LuaRootSource(game.lua.L, controlCtx, fn)
  }
  def optional(game: GameManager, controlCtx: ControlContext, export: String, method: String) = {
    val fullExport = StaticExportIDs.EntryPoint(game.gameId, export)
    game.getEntryPoint(fullExport) match {
      case Some(exportObj) => create(game, controlCtx, exportObj, method)
      case None => new NullRootSource(game.lua.L, controlCtx)
    }
  }
  def apply(game: GameManager, controlCtx: ControlContext, export: String, method: String) = {
    val fullExport = StaticExportIDs.EntryPoint(game.gameId, export)
    create(game, controlCtx, game.getRequiredEntryPoint(fullExport), method)
  }
}

final case class TableColumnData(L: LuaState, fn: LuaClosure)
final case class LuaColumnData(columns: Map[String, TableColumnData], defaultColumnOrder: Seq[String])
object LuaColumnData {
  def apply(game: GameManager): LuaColumnData = {
    val export = StaticExportIDs.EntryPoint(game.gameId, "card-columns")
    val L = game.lua.L.newThread()
    val fn = L.getTable(game.getRequiredEntryPoint(export), "cardColumns").as[LuaClosure]
    val Seq(columns, defaultColumnOrder) = L.call(fn, 2)
    LuaColumnData(columns.as[Map[String, LuaClosure]].map { t => t._1 -> TableColumnData(game.lua.L, t._2) },
                  defaultColumnOrder.as[Seq[String]])
  }
}

final class GameIDData(game: GameManager, controlCtx: ControlContext) {
  val card    = RootSource         (game, controlCtx, "card-form", "cardForm")
  val set     = RootSource.optional(game, controlCtx, "set-form" , "setForm" )
  val columns = LuaColumnData(game)
}