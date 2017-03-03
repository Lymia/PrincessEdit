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

import java.io.Writer

import moe.lymia.princess.core.components._
import moe.lymia.princess.core.lua._
import moe.lymia.princess.core.builder._
import moe.lymia.princess.lua._
import moe.lymia.princess.core.svg.SVGRenderer
import moe.lymia.princess.util.SizedCache

trait Template {
  protected def renderSettings: RenderSettings
  protected def doRender(builder: SVGBuilder, cardData: LuaObject, res: ResourceLoader): SVGDefinitionReference

  private def doRender(cardData: LuaObject, res: ResourceLoader): (SVGBuilder, SVGDefinitionReference) = {
    val builder = new SVGBuilder(renderSettings)
    (builder, doRender(builder, cardData, res))
  }

  def renderSVGTag(cardData: LuaObject, res: ResourceLoader = ExportResourceLoader) = {
    val (builder, definition) = doRender(cardData, res)
    builder.renderSVGTag(definition)
  }
  def write(w: Writer, cardData: LuaObject, encoding: String = "utf-8", pretty: Boolean = true,
            res: ResourceLoader = ExportResourceLoader) = {
    val (builder, definition) = doRender(cardData, res)
    builder.write(w, definition, encoding, pretty = pretty)
  }
  def renderImage(renderer: SVGRenderer, x: Int, y: Int, cardData: LuaObject,
                  res: ResourceLoader = RasterizeResourceLoader) = {
    val (builder, definition) = doRender(cardData, res)
    builder.renderImage(renderer, x, y, definition)
  }
}

class LuaTemplate(name: String, packages: PackageList, context: LuaContext, export: LuaObject,
                  cache: SizedCache) extends Template {
  override protected def renderSettings = TemplateException.context(s"rendering template $name") {
    val L = context.L.newThread()
    val scale = L.getTable(export, "scale").as[PhysicalScale]
    RenderSettings(L.getTable(export, "size").as[Size], scale.unPerViewport, scale.unit)
  }
  override protected def doRender(builder: SVGBuilder, cardData: LuaObject, res: ResourceLoader) =
    TemplateException.context(s"rendering template $name") {
      val L = context.L.newThread()
      val prerenderFn = L.getTable(export, "prerender"       ).as[Option[LuaClosure]]
      val layoutFn    = L.getTable(export, "layoutComponents").as[LuaClosure]

      val prerenderData = prerenderFn.map(x => L.pcall(x, 1, cardData) match {
        case Left(Seq(ret)) => ret.as[Any]
        case Right(e) => throw TemplateException(e)
      })
      val reference = L.pcall(layoutFn, 1, cardData, prerenderData) match {
        case Left(Seq(x)) => x.as[ComponentReference]
        case Right(e) => throw TemplateException(e)
      }

      val resources = new ResourceManager(builder, renderSettings, cache, res, packages)
      val renderManager = new ComponentRenderManager(builder, resources)
      renderManager.renderComponent(reference)
    }
}
