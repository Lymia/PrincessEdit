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
import moe.lymia.princess.lua._

import scala.collection.mutable
import scala.xml.NodeSeq

trait Component {
  def getSize: Size
  def renderComponent(manager: ComponentRenderManager): NodeSeq

  def setField(L: LuaState, k: String, v: Any): Unit = L.error(s"no such field '$k'")
  def getField(L: LuaState, k: String): LuaObject = LuaNil

  def ref: ComponentReference = DirectComponentReference(this)
}

sealed trait ComponentReference {
  def name: String
  def component: Component
  def deref: ComponentReference = DirectComponentReference(component)
}
final case class DirectComponentReference(component: Component) extends ComponentReference {
  def name = s"${component.getClass.getName}@0x${"%08x" format System.identityHashCode(component)}"
}
final case class IndirectComponentReference(manager: ComponentManager, name: String) extends ComponentReference {
  def component =
    manager.getComponent(name).getOrElse(throw TemplateException(s"No component $name in component manager $manager"))
}

final class ComponentRenderManager(val builder: SVGBuilder, val resources: ResourceManager,
                                   val components: ComponentManager) {
  private val currentlyRendering = new mutable.HashMap[Component, String]
  private val renderCache = new mutable.HashMap[Component, SVGDefinitionReference]
  def renderComponent(ref: ComponentReference) = TemplateException.context(s"rendering ${ref.name}") {
    val component = ref.component
    if(currentlyRendering.contains(component))
      throw TemplateException(s"Attempted to render component ${ref.name} while it is already rendering. "+
                              s"Components involved: [${currentlyRendering.values.mkString(", ")}]")
    renderCache.getOrElseUpdate(component, try {
      currentlyRendering.put(component, ref.name)
      builder.createDefinitionFromFragment(ref.name, component.getSize, component.renderComponent(this))
    } finally {
      currentlyRendering.remove(component)
    })
  }
}

final class ComponentManager(settings: RenderSettings) {
  private val componentMap  = new mutable.HashMap[String, Component]

  def setComponent(name: String, component: Component) = componentMap.put(name, component)
  def getComponent(name: String) = componentMap.get(name)

  def getComponentReference(name: String): ComponentReference =
    if(componentMap.contains(name)) IndirectComponentReference(this, name)
    else throw TemplateException(s"No component $name in component manager $this")
}