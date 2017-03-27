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

package moe.lymia.princess.renderer.components

import moe.lymia.princess.core._
import moe.lymia.princess.renderer._
import moe.lymia.princess.renderer.lua._
import moe.lymia.lua._

class ComponentWrapper(underlying: ComponentReference) extends Component {
  override def getDefinitionReference(ref: ComponentReference, manager: ComponentRenderManager) =
    manager.renderComponent(underlying)
}

class ResourceComponent(protected var boundsParam: Bounds, private var resource: String)
  extends Component with BoundedBase {

  override def getDefinitionReference(ref: ComponentReference, manager: ComponentRenderManager) =
    manager.resources.loadImageResource(resource, bounds)
  property("resource", _ => resource, (L, v : String) => resource = v)
}

class LayoutComponent(private var L_main: LuaState, private var allowOverflow: Boolean = false) extends Component {
  private var prerenderHandler = LuaClosure((L: LuaState) => L.newTable())
  private var layoutHandler = LuaClosure((L: LuaState) => { L.error("no layout function registered"); () })

  private def renderCore(manager: ComponentRenderManager) = {
    val L = L_main.newThread()

    val componentsToSize = L.pcall(prerenderHandler, 1) match {
      case Left (x) => x.head.as[Seq[ComponentReference]]
      case Right(e) => throw EditorException(e)
    }
    val prerenderData = componentsToSize.map(x => x -> {
      val table = L.newTable()
      val ref = manager.renderComponent(x)
      L.rawSet(table, "bounds", ref.bounds)
      L.rawSet(table, "size"  , ref.bounds.size)
      L.rawSet(table, "layout", ref.extraLayout)
      table
    }).toMap

    val retTable = L.pcall(layoutHandler, 1, prerenderData) match {
      case Left (x) => x.head.as[LuaTable]
      case Right(e) => throw EditorException(e)
    }

    val components = L.rawGet(retTable, "components").as[LuaTable]
    val bounds = L.rawGet(retTable, "bounds").as[Option[Bounds]].getOrElse(L.rawGet(retTable, "size").as[Bounds])
    val outputLayout = L.rawGet(retTable, "layout").as[Option[LuaTable]]

    (for(i <- 1 to L.objLen(components)) yield {
      val entry = L.getTable(components, i)
      val component = L.getTable(entry, "component").as[Option[ComponentReference]]
                       .getOrElse(L.getTable(entry, 1).as[ComponentReference])
      val bounds    = L.getTable(entry, "bounds"   ).as[Option[Bounds]]
      bounds match {
        case Some(b) => manager.renderComponent(component).includeInBounds(b.minX, b.minY, b.maxX, b.maxY)
        case None =>
          val x    = L.getTable(entry, "x"   ).as[Double]
          val y    = L.getTable(entry, "y"   ).as[Double]
          val size = L.getTable(entry, "size").as[Option[Size]]
          size match {
            case Some(s) => manager.renderComponent(component).includeInRect(x, y, s.width, s.height)
            case None    => manager.renderComponent(component).include(x, y)
          }
      }
    }, bounds, outputLayout)
  }

  def getDefinitionReference(ref: ComponentReference, manager: ComponentRenderManager): SVGDefinitionReference = {
    val (nodes, size, layout) = renderCore(manager)
    manager.builder.createDefinitionFromFragment(ref.name, size, nodes,
                                                 extraLayout = layout, allowOverflow = allowOverflow)
  }

  property("allowOverflow"   , _ => allowOverflow   , (L, v: Boolean   ) => { L_main = L; allowOverflow    = v })
  property("prerenderHandler", _ => prerenderHandler, (L, v: LuaClosure) => { L_main = L; prerenderHandler = v })
  property("layoutHandler"   , _ => layoutHandler   , (L, v: LuaClosure) => { L_main = L; layoutHandler    = v })
}

class BoundedLayoutComponent(private var L_main: LuaState, protected val boundsParam: Bounds,
                             private var allowOverflow: Boolean = false)
  extends LayoutComponent(L_main, allowOverflow) with BoundedBase