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
import moe.lymia.princess.core.lua._
import moe.lymia.princess.core.builder._
import moe.lymia.princess.lua._

import org.jfree.graphics2d.svg.SVGGraphics2D

import scala.collection.mutable
import scala.xml._

trait Component extends LuaLookup {
  def getDefinitionReference(ref: ComponentReference, manager: ComponentRenderManager): SVGDefinitionReference
  def ref: ComponentReference = DirectComponentReference(this)
}

abstract class SimpleComponent(protected var allowOverflow: Boolean = false) extends Component {
  def renderComponent(manager: ComponentRenderManager): (NodeSeq, Bounds)
  def getDefinitionReference(ref: ComponentReference, manager: ComponentRenderManager): SVGDefinitionReference = {
    val (nodes, bounds) = renderComponent(manager)
    manager.builder.createDefinitionFromFragment(ref.name, bounds, nodes, allowOverflow = allowOverflow)
  }
}

abstract class GraphicsComponent(protected var allowOverflow: Boolean = false) extends Component {
  def renderComponent(manager: ComponentRenderManager, graphics: SVGGraphics2D, table: LuaTable): Bounds
  override def getDefinitionReference(ref: ComponentReference, manager: ComponentRenderManager) = {
    val renderer = manager.builder.createRenderer()
    val table = new LuaTable()
    val bounds = renderComponent(manager, renderer.gfx, table)
    val rendered = renderer.renderXML()
    manager.builder.createDefinitionFromSVG(ref.name, bounds, rendered,
                                            extraLayout = Some(table), allowOverflow = allowOverflow)
  }
}

trait BoundedBase extends LuaLookup {
  protected def boundsParam: Bounds
  protected var bounds: Bounds = boundsParam
  property("size", L => bounds.size, (_, v: Size) => {bounds = Bounds(v)})
  property("bounds", _ => bounds, (_, v: Bounds) => bounds = v)
}

sealed trait ComponentReference {
  def name: String
  def component: Component
  def deref: ComponentReference = DirectComponentReference(component)
}
final case class DirectComponentReference(component: Component) extends ComponentReference {
  def name = s"${component.getClass.getName}: 0x${"%08x" format System.identityHashCode(component)}"
}
final case class IndirectComponentReference(manager: ComponentManager, name: String) extends ComponentReference {
  def component =
    manager.getComponent(name).getOrElse(throw TemplateException(s"No component $name in component manager $manager"))
}

final class ComponentRenderManager(val builder: SVGBuilder, val resources: ResourceManager) {
  val settings = builder.settings

  private val currentlyRendering = new mutable.HashMap[Component, String]
  private val renderCache = new mutable.HashMap[Component, SVGDefinitionReference]
  def renderComponent(ref: ComponentReference) = TemplateException.context(s"rendering ${ref.name}") {
    val component = ref.component
    if(currentlyRendering.contains(component))
      throw TemplateException(s"Attempted to render component ${ref.name} while it is already rendering. "+
                              s"Components involved: [${currentlyRendering.values.mkString(", ")}]")
    renderCache.getOrElseUpdate(component, try {
      currentlyRendering.put(component, ref.name)
      component.getDefinitionReference(ref, this)
    } finally {
      currentlyRendering.remove(component)
    })
  }
}

final class ComponentManager {
  private val componentMap  = new mutable.HashMap[String, Component]

  def setComponent(name: String, component: Component) = componentMap.put(name, component)
  def getComponent(name: String) = componentMap.get(name)

  def getComponentReference(name: String): ComponentReference =
    if(componentMap.contains(name)) IndirectComponentReference(this, name)
    else throw TemplateException(s"No component $name in component manager $this")
}