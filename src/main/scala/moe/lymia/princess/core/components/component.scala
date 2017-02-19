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
import moe.lymia.princess.core.svg._
import moe.lymia.princess.lua._
import org.apache.batik.svggen.SVGGraphics2D

import scala.collection.mutable
import scala.xml.NodeSeq

abstract class Component(private var noViewport: Boolean = false) extends LuaLookup {
  def renderComponent(manager: ComponentRenderManager): (NodeSeq, Size)
  def getDefinitionReference(ref: ComponentReference, manager: ComponentRenderManager): SVGDefinitionReference = {
    val (nodes, size) = renderComponent(manager)
    manager.builder.createDefinitionFromFragment(ref.name, size, nodes, getNoViewport)
  }

  final def getNoViewport = noViewport

  property("noViewport", _ => noViewport, (L, v: Boolean) => noViewport = v)

  def ref: ComponentReference = DirectComponentReference(this)
}

abstract class LowLevelComponent extends Component {
  final override def renderComponent(manager: ComponentRenderManager): (NodeSeq, Size) = ???
  def renderReference(ref: ComponentReference, manager: ComponentRenderManager): SVGDefinitionReference
  override def getDefinitionReference(ref: ComponentReference, manager: ComponentRenderManager) =
    renderReference(ref, manager)
}

abstract class GraphicsComponent(noViewportParam: Boolean = false) extends Component(noViewportParam) {
  def renderComponent(manager: ComponentRenderManager, graphics: SVGGraphics2D): Size
  override def renderComponent(manager: ComponentRenderManager): (NodeSeq, Size) = {
    val ref = manager.builder.createDefinitionFromGraphics("graphicsRender")(g => renderComponent(manager, g))
    (ref.include(0, 0), ref.expectedSize)
  }
}

trait SizedBase extends Component {
  protected def sizeParam: Size
  protected var size: Size = sizeParam
  property("size", _ => size, (L, v: Size) => size = v)
}
trait SizedComponent extends SizedBase {
  protected def sizedRender(manager: ComponentRenderManager): NodeSeq
  def renderComponent(manager: ComponentRenderManager): (NodeSeq, Size) = (sizedRender(manager), size)
}
trait SizedGraphicsComponent extends SizedBase {
  protected def sizedRender(manager: ComponentRenderManager, graphics: SVGGraphics2D): NodeSeq
  def renderComponent(manager: ComponentRenderManager, graphics: SVGGraphics2D): Size = {
    sizedRender(manager, graphics)
    size
  }
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