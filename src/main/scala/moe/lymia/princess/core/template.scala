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
import moe.lymia.princess.core.renderer._
import moe.lymia.princess.lua._

trait Template {
  protected def renderSettings: RenderSettings
  protected def doRender(builder: SVGBuilder, cardData: LuaTable, isImageRender: Boolean): SVGDefinitionReference

  private def doRender(cardData: LuaTable, isImageRender: Boolean): (SVGBuilder, SVGDefinitionReference) = {
    val builder = new SVGBuilder(renderSettings)
    (builder, doRender(builder, cardData, isImageRender))
  }

  def renderSVGTag(cardData: LuaTable) = {
    val (builder, definition) = doRender(cardData, isImageRender = false)
    builder.renderSVGTag(definition)
  }
  def write(w: Writer, cardData: LuaTable, encoding: String = "utf-8") = {
    val (builder, definition) = doRender(cardData, isImageRender = false)
    builder.write(w, definition, encoding)
  }
  def renderImage(x: Int, y: Int, cardData: LuaTable) = {
    val (builder, definition) = doRender(cardData, isImageRender = true)
    builder.renderImage(x, y, definition)
  }
}

class LuaTemplate(name: String, packages: PackageList, context: LuaContext, table: LuaTable) extends Template {
  override protected def renderSettings = TemplateException.context(s"rendering template $name") {
    val L = context.L.newThread()
    RenderSettings(L.getTable(table, "size"      ).as[PhysicalSize  ],
                   L.getTable(table, "coordScale").as[Option[Double]].getOrElse(1))
  }
  override protected def doRender(builder: SVGBuilder, cardData: LuaTable, isImageRender: Boolean) =
    TemplateException.context(s"rendering template $name") {
      val L = context.L.newThread()
      val layoutFn = L.getTable(table, "layoutComponents").as[LuaClosure]

      val reference = L.pcall(layoutFn, 1, cardData) match {
        case Left(Seq(x)) => x.as[ComponentReference]
        case Right(e) => throw TemplateException(e)
      }

      val resources = new ResourceManager(builder, if(isImageRender) RasterizeResourceLoader else ExportResourceLoader,
                                          packages)
      val renderManager = new ComponentRenderManager(builder, resources)
      renderManager.renderComponent(reference)
    }
}
