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

import moe.lymia.princess.core._
import moe.lymia.princess.core.renderer.Size
import moe.lymia.princess.lua._

import scala.xml.NodeSeq

class ResourceComponent(sizeParam: Size, private var resource: String) extends Component(sizeParam) {
  override def renderComponent(manager: ComponentRenderManager): NodeSeq =
    manager.resources.loadImageResource(resource, size).include(0, 0, size.width, size.height)
  property("resource")(_ => resource, (L, v : String) => resource = v)
}

class LayoutComponent(private var L_main: LuaState, sizeParam: Size) extends Component(sizeParam) {
  private var handler = LuaClosure((L: LuaState) => { L.error("no layout function registered"); () })

  override def renderComponent(manager: ComponentRenderManager): NodeSeq = {
    val L = L_main.newThread()
    val table = L.call(handler, 1, size).head.as[LuaTable]
    for(i <- 1 to L.objLen(table)) yield {
      val entry = L.getTable(table, i)
      val component = L.getTable(entry, "component").as[ComponentReference]
      val x         = L.getTable(entry, "x"        ).as[Double]
      val y         = L.getTable(entry, "y"        ).as[Double]
      val size      = L.getTable(entry, "size"     ).as[Size]
      manager.renderComponent(component).include(x, y, size.width, size.height)
    }
  }

  property("handler")(_ => handler, (L, v: LuaClosure) => { L_main = L; handler = v })
}
