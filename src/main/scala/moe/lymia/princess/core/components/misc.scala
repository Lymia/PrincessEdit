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

package moe.lymia.princess.core.components

import java.awt.font.TextLayout

import moe.lymia.princess.core.TemplateException
import moe.lymia.princess.core.svg.Size
import moe.lymia.princess.lua._
import org.apache.batik.svggen.SVGGraphics2D

import scala.xml.NodeSeq

class ResourceComponent(protected var sizeParam: Size, private var resource: String)
  extends Component with SizedComponent {

  override def sizedRender(manager: ComponentRenderManager) =
    manager.resources.loadImageResource(resource, size).include(0, 0, size.width, size.height)
  property("resource")(_ => resource, (L, v : String) => resource = v)
}

class LayoutComponent(private var L_main: LuaState) extends Component {
  private var prerenderHandler = LuaClosure((L: LuaState) => L.newTable())
  private var layoutHandler = LuaClosure((L: LuaState) => { L.error("no layout function registered"); () })

  override def renderComponent(manager: ComponentRenderManager): (NodeSeq, Size) = {
    val L = L_main.newThread()

    val componentsToSize = L.pcall(prerenderHandler, 1) match {
      case Left (x) => x.head.as[Seq[ComponentReference]]
      case Right(e) => throw TemplateException(e)
    }
    val sizeMap = componentsToSize.map(x => x -> manager.renderComponent(x).expectedSize).toMap

    val Seq(a, b) = L.pcall(layoutHandler, 2, sizeMap) match {
      case Left (x) => x
      case Right(e) => throw TemplateException(e)
    }
    val (components, size) = (a.as[LuaTable], b.as[Size])
    (for(i <- 1 to L.objLen(components)) yield {
      val entry = L.getTable(components, i)
      val component = L.getTable(entry, "component").as[ComponentReference]
      val x         = L.getTable(entry, "x"        ).as[Double]
      val y         = L.getTable(entry, "y"        ).as[Double]
      val size      = L.getTable(entry, "size"     ).as[Size]
      manager.renderComponent(component).include(x, y, size.width, size.height)
    }, size)
  }

  property("prerenderHandler")(_ => prerenderHandler, (L, v: LuaClosure) => { L_main = L; prerenderHandler = v })
  property("layoutHandler"   )(_ => layoutHandler   , (L, v: LuaClosure) => { L_main = L; layoutHandler    = v })
}

class SimpleTextComponent(private var text: FormattedString) extends GraphicsComponent {
  override def renderComponent(manager: ComponentRenderManager, graphics: SVGGraphics2D): Size = {
    val attributed = text.toAttributedStrings(manager.resources).head
    val layout = new TextLayout(attributed.getIterator, graphics.getFontRenderContext)
    val bounds = layout.getBounds
    layout.draw(graphics, (0 - bounds.getMinX).toFloat, (0f - bounds.getMinY).toFloat)
    Size(bounds.getWidth, bounds.getHeight)
  }
}