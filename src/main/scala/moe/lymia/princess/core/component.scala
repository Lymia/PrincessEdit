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

import moe.lymia.princess.lua._

import scala.collection.mutable
import scala.xml.NodeSeq

final case class ComponentReference(name: String, isTransient: Boolean)
trait Component {
  def getSize: Size
  def methodList: Seq[String]
  def callMethod(key: String, args: LuaObject*): Seq[LuaObject]
  def renderComponent(manager: ComponentRenderManager): NodeSeq
}

final class ComponentRenderManager(val builder: SVGBuilder, val components: ComponentManager) {
  private val currentlyRendering = new mutable.HashSet[String]
  private val renderCache = new mutable.HashMap[String, SVGDefinitionReference]
  def renderComponent(name: ComponentReference) = {
    if(currentlyRendering.contains(name.name))
      throw TemplateException(s"Attempted to render component ${name.name} while it is already rendering. "+
                              s"Components involved: [${currentlyRendering.mkString(", ")}]")
    renderCache.getOrElseUpdate(name.name, try {
      currentlyRendering.add(name.name)
      components.getComponent(name.name) match {
        case None => throw TemplateException(s"Component ${name.name} does not exist.")
        case Some(component) =>
          builder.createDefinitionFromFragment(name.name, component.getSize, component.renderComponent(this))
      }
    } finally {
      currentlyRendering.remove(name.name)
    })
  }
}

private sealed trait ComponentType
private case class StaticComponentType(component: Component)
private case class LazyComponentType(component: Component)
final class ComponentManager {
  private var transientId = 0
  private val componentMap  = new mutable.HashMap[String, Component]

  def setComponent(name: String, component: Component, isTransient: Boolean = false) = {
    val finalName = if(isTransient) s"transient_{${
      transientId = transientId + 1
      transientId
    }_$name" else name
    componentMap.put(finalName, component)
    ComponentReference(finalName, isTransient)
  }
  def getComponent(name: String) = componentMap.get(name)
  def getComponent(reference: ComponentReference) = componentMap.get(reference.name)
}