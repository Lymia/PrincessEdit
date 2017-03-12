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

package moe.lymia.princess.renderer

import java.io.Writer

import moe.lymia.princess.core._
import moe.lymia.princess.lua._
import moe.lymia.princess.renderer.components._
import moe.lymia.princess.renderer.lua._
import moe.lymia.princess.rasterizer.SVGRasterizer
import moe.lymia.princess.util.SizedCache

trait Renderer {
  protected def doRender(cardData: LuaObject, res: ResourceLoader): (SVGBuilder, SVGDefinitionReference)

  def renderSVGTag(cardData: LuaObject, res: ResourceLoader = ExportResourceLoader) = {
    val (builder, definition) = doRender(cardData, res)
    builder.renderSVGTag(definition)
  }
  def write(w: Writer, cardData: LuaObject, encoding: String = "utf-8", pretty: Boolean = true,
            res: ResourceLoader = ExportResourceLoader) = {
    val (builder, definition) = doRender(cardData, res)
    builder.write(w, definition, encoding, pretty = pretty)
  }
  def rasterize(rasterize: SVGRasterizer, x: Int, y: Int, cardData: LuaObject,
                res: ResourceLoader = RasterizeResourceLoader) = {
    val (builder, definition) = doRender(cardData, res)
    builder.rasterize(rasterize, x, y, definition)
  }
}

final class RenderManager(game: GameManager, cache: SizedCache) extends Renderer {
  if(!game.lua.isModuleLoaded(lua.RenderModule)) game.lua.loadModule(lua.RenderModule)

  private lazy val export = game.getEntryPoint(StaticExportIDs.EntryPoint(game.gameId, "render"))
  override protected def doRender(cardData: LuaObject, res: ResourceLoader) =
    EditorException.context(s"rendering card") {
      val L = game.lua.L.newThread()
      val layoutFn    = L.getTable(export, "render").as[LuaClosure]

      val table = L.pcall(layoutFn, 1, cardData) match {
        case Left(Seq(x)) => x.as[LuaTable]
        case Right(e) => throw EditorException(e)
      }

      val reference = L.getTable(table, "component").as[ComponentReference]
      val scale     = L.getTable(table, "scale").as[PhysicalScale]
      val size      = L.getTable(table, "size").as[Size]

      val renderSettings = RenderSettings(size, scale.unPerViewport, scale.unit)

      val builder = new SVGBuilder(renderSettings)
      val resources = new ResourceManager(builder, renderSettings, cache, res, game)
      val renderManager = new ComponentRenderManager(builder, resources)
      (builder, renderManager.renderComponent(reference))
    }
}
