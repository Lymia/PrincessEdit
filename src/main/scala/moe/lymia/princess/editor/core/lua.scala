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
import moe.lymia.princess.lua._

import org.eclipse.swt.widgets.Composite

import rx._

final class LuaUIRoot(L: LuaState, parent: Composite, data: DataStore, controlCtx: ControlContext, node: RootNode) {
  private implicit val ctx = new NodeContext(L, data, controlCtx)

  private val dummyRx = Rx.unsafe { () }
  private implicit val owner = new Ctx.Owner(dummyRx)

  val luaData = node.createRx
  val component = node.createComponent(parent)

  def kill() = dummyRx.kill()
}

final class LuaNodeSource(game: GameManager, entryExport: String, entryMethod: String) {
  private val export = StaticExportIDs.EntryPoint(game.gameId, entryExport)
  private val fn = game.lua.L.newThread().getTable(game.getEntryPoint(export), entryMethod).as[LuaClosure]

  def createRootNode(L: LuaState, subtableName: String, data: DataStore, args: Seq[Rx[LuaObject]]) =
    RootNode(subtableName, args.map(RxFieldNode), fn)
  def createRoot(L: LuaState, parent: Composite, data: DataStore, subtableName: String,
                 controlCtx: ControlContext, args: Seq[Rx[LuaObject]]) =
    new LuaUIRoot(L, parent, data, controlCtx, createRootNode(L, subtableName, data, args))
}