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

package moe.lymia.princess.svg

import moe.lymia.lua.{LuaTable, _}
import moe.lymia.princess.core._
import moe.lymia.princess.core.gamedata.GameData
import moe.lymia.princess.svg.components._
import moe.lymia.princess.svg.scripting._
import moe.lymia.princess.util.SizedCache

import java.io.Writer
import scala.xml.Elem

trait SVGRenderable {
  val size: SizeBase
  def asSvg(): String
}

// TODO: Error check this cleaner
final case class SVGFile(nodes: Elem) extends SVGRenderable {
  val (width , widthUnit ) = SVGFile.parseLength((nodes \ "@width" ).text)
  val (height, heightUnit) = SVGFile.parseLength((nodes \ "@height").text)

  if(widthUnit != heightUnit) sys.error("width unit != height unit")

  val size: SizeBase = Size(width, height)

  override def asSvg(): String = nodes.toString()
}
object SVGFile {
  private val LengthPattern = "([0-9]+(\\.[0-9])?)([a-z]*)".r
  private def parseLength(str: String) = {
    val LengthPattern(length, _, unit) = str
    (length.toDouble, unit)
  }
}

final case class SVGData(private val builder: SVGBuilder, private val definition: SVGDefinitionReference)
  extends SVGRenderable {

  val bounds: Bounds = definition.bounds
  val size: PhysicalSize = builder.settings.size

  private def norm(d: Double) = math.max(1, math.round(d)).toInt
  def bestSizeForDPI(dpi: Double): (Int, Int) =
    (norm(size.width  / size.unit.unPerInch * dpi),
     norm(size.height / size.unit.unPerInch * dpi))

  def renderSVGTag(): Elem =
    builder.renderSVGTag(definition)
  def write(w: Writer, encoding: String = "utf-8", pretty: Boolean = true): Unit =
    builder.write(w, definition, encoding, pretty = pretty)

  override def asSvg(): String = builder.renderSVGTag(definition).toString
}

final class RenderManager(game: GameData, cache: SizedCache) {
  if(!game.lua.isModuleLoaded(RenderModule)) game.lua.loadModule(RenderModule)

  private lazy val layoutFn =
    game.lua.L.newThread().getTable(game.getRequiredEntryPoint("render"), "render").as[LuaClosure]
  def render(cardData: Seq[LuaObject], res: ResourceLoader): SVGData =
    EditorException.context(s"rendering card") {
      val L = game.lua.L.newThread()

      val table = L.pcall(layoutFn, 1, cardData : _*) match {
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
      SVGData(builder, renderManager.renderComponent(reference))
    }
}
