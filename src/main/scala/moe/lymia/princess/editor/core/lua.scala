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

import org.eclipse.swt.widgets.Composite

import rx._

final class UIData(parent: Composite, node: RootNode)(implicit ctx: NodeContext, owner: Ctx.Owner) {
  private val dummyRx = Rx { () }
  val control = node.createControl(parent)(ctx, ctx.newUIContext(), new Ctx.Owner(dummyRx))

  def kill() = dummyRx.kill()
}

final class DataRoot(L: LuaState, data: DataStore, controlCtx: ControlContext, node: RootNode) {
  private implicit val ctx = new NodeContext(L.newThread(), data, controlCtx)

  private val dummyRx = Rx.unsafe { () }
  private implicit val owner = new Ctx.Owner(dummyRx)

  val luaData = node.createRx
  def createUI(parent: Composite) = new UIData(parent, node)
  def kill() = dummyRx.kill()
}

sealed abstract class RootSource(L: LuaState, controlCtx: ControlContext) {
  def createRootNode(data: DataStore, args: Seq[Rx[LuaObject]]): RootNode
  def createRoot(data: DataStore, args: Seq[Rx[LuaObject]]) =
    new DataRoot(L.newThread(), data, controlCtx, createRootNode(data, args))
}

final class NullRootSource(L: LuaState, controlCtx: ControlContext) extends RootSource(L, controlCtx) {
  private val node = RootNode(None, Seq(), LuaClosure { () => })
  override def createRootNode(data: DataStore, args: Seq[Rx[LuaObject]]): RootNode = node
}
final class LuaRootSource(L: LuaState, controlCtx: ControlContext, fn: LuaClosure) extends RootSource(L, controlCtx) {
  private lazy val emptyRoot = RootNode(None, Seq(), fn)

  def createRootNode(data: DataStore, args: Seq[Rx[LuaObject]]) =
    if(args.isEmpty) emptyRoot else RootNode(None, args.map(RxFieldNode), fn)
}
object RootSource {
  private def create(L: LuaState, controlCtx: ControlContext, game: GameManager, export: LuaObject, method: String) = {
    val fn = game.lua.L.newThread().getTable(export, method).as[LuaClosure]
    new LuaRootSource(L, controlCtx, fn)
  }
  def optional(L: LuaState, controlCtx: ControlContext, game: GameManager, export: String, method: String) = {
    val export = StaticExportIDs.EntryPoint(game.gameId, export)
    game.getEntryPoint(export) match {
      case Some(exportObj) => create(L, controlCtx, game, exportObj, method)
      case None => new NullRootSource(L, controlCtx)
    }
  }
  def apply(L: LuaState, controlCtx: ControlContext, game: GameManager, export: String, method: String) = {
    val export = StaticExportIDs.EntryPoint(game.gameId, export)
    create(L, controlCtx, game, game.getRequiredEntryPoint(export), method)
  }
}

final class GameIDData(game: GameManager, controlCtx: ControlContext) {
  lazy val card = RootSource         (game.lua.L, controlCtx, game, "card-form", "cardForm")
  lazy val set  = RootSource.optional(game.lua.L, controlCtx, game, "set-form" , "setForm" )
}