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

package moe.lymia.princess.editor

import moe.lymia.lua._
import moe.lymia.princess.core._
import moe.lymia.princess.editor.lua._
import moe.lymia.princess.editor.model.DataStore
import moe.lymia.princess.editor.nodes._
import moe.lymia.princess.editor.utils.RxOwner
import moe.lymia.princess.renderer.RenderManager
import org.eclipse.swt.widgets._
import rx._

final class UIData(parent: Composite, node: RootNode, registerControlCallbacks: Control => Unit)
                  (implicit ctx: NodeContext, owner: Ctx.Owner) {
  private val rootRxValue = Rx { () }
  implicit val rootRx = new Ctx.Owner(rootRxValue)
  val control = node.createControl(parent)(ctx, ctx.newUIContext(registerControlCallbacks), rootRx)

  def kill() = rootRxValue.kill()
}

final class DataRoot(L: LuaState, data: DataStore, controlCtx: ControlContext, i18n: I18N, node: RootNode)
  extends RxOwner {

  private implicit val ctx = new NodeContext(L.newThread(), data, controlCtx, i18n)

  val luaData = node.createRx
  def createUI(parent: Composite, registerControlCallbacks: Control => Unit = _ => ()) =
    new UIData(parent, node, registerControlCallbacks)
}

sealed abstract class RootSource(L: LuaState, controlCtx: ControlContext, i18n: I18N) {
  val exists: Boolean = false
  def createRootNode(data: DataStore, args: Seq[Rx[LuaObject]]): RootNode
  def createRoot(data: DataStore, args: Seq[Rx[LuaObject]]) =
    new DataRoot(L.newThread(), data, controlCtx, i18n, createRootNode(data, args))
}

final class NullRootSource(L: LuaState, controlCtx: ControlContext, i18n: I18N)
  extends RootSource(L, controlCtx, i18n) {

  private val node = RootNode(None, Seq(), LuaClosure { () => })
  override def createRootNode(data: DataStore, args: Seq[Rx[LuaObject]]): RootNode = node
}
final class LuaRootSource(L: LuaState, controlCtx: ControlContext, i18n: I18N, fn: LuaClosure)
  extends RootSource(L, controlCtx, i18n) {

  override val exists = true
  private lazy val emptyRoot = RootNode(None, Seq(), fn)

  def createRootNode(data: DataStore, args: Seq[Rx[LuaObject]]) =
    if(args.isEmpty) emptyRoot else RootNode(None, args.map(RxFieldNode), fn)
}
object RootSource {
  private def create(game: GameManager, controlCtx: ControlContext, i18n: I18N, export: LuaObject, method: String) = {
    val fn = game.lua.L.newThread().getTable(export, method).as[LuaClosure]
    new LuaRootSource(game.lua.L, controlCtx, i18n, fn)
  }
  def optional(game: GameManager, controlCtx: ControlContext, i18n: I18N, export: String, method: String) = {
    game.getEntryPoint(export) match {
      case Some(exportObj) => create(game, controlCtx, i18n, exportObj, method)
      case None => new NullRootSource(game.lua.L, controlCtx, i18n)
    }
  }
  def apply(game: GameManager, controlCtx: ControlContext, i18n: I18N, export: String, method: String) =
    create(game, controlCtx, i18n, game.getRequiredEntryPoint(export), method)
}

final class TableColumnData(val title: String, val width: Int, val isDefault: Boolean,
                            val L: LuaState, val fn: LuaClosure, val sortFn: Option[LuaClosure])
final case class LuaColumnData(columns: Seq[TableColumnData])
object LuaColumnData {
  def apply(game: GameManager): LuaColumnData = {
    val L = game.lua.L.newThread()
    val fn = L.getTable(game.getRequiredEntryPoint("card-columns"), "cardColumns").as[LuaClosure]
    LuaColumnData(L.call(fn, 1).head.as[Seq[TableColumnData]])
  }
}

final case class LuaExportData(defaultNameFormat: String, helpText: Option[String])
object LuaExportData {
  def apply(game: GameManager): LuaExportData = {
    val L = game.lua.L.newThread()
    val ep = game.getRequiredEntryPoint("export-data")
    val name = L.getTable(ep, "defaultNameFormat").as[String]
    val help = L.getTable(ep, "helpText").as[Option[String]]
    LuaExportData(name, help)
  }
}

final class GameIDData(game: GameManager, controlCtx: ControlContext, i18n: I18N) {
  val card     = RootSource         (game, controlCtx, i18n, "card-form", "cardForm")
  val set      = RootSource.optional(game, controlCtx, i18n, "set-form" , "setForm" )
  val columns  = LuaColumnData(game)
  val renderer = new RenderManager(game, controlCtx.cache)
  val export   = LuaExportData(game)
}