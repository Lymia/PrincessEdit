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

package moe.lymia.princess.svg.components

import moe.lymia.lua._
import moe.lymia.princess.core._
import moe.lymia.princess.core.packages.PackageList
import moe.lymia.princess.svg._
import moe.lymia.princess.svg.lua._

import java.nio.file.Files
import scala.collection.mutable
import scala.xml.{XML => _, _}

sealed trait ExpectedType
object ExpectedType {
  case object Component  extends ExpectedType
  case object String     extends ExpectedType
  case object Integer    extends ExpectedType
  case object Number     extends ExpectedType
  case object GenSym     extends ExpectedType
  case object Definition extends ExpectedType
}

case class XMLTemplateData(parameters: Map[String, ExpectedType], elems: NodeSeq)
object XMLTemplateData {
  private def loadParam(data: Node) =
    (data \ "@name").text -> ((data \ "@type").text.toLowerCase match {
      case "component"                         => ExpectedType.Component
      case "str" | "string"                    => ExpectedType.String
      case "int" | "integer"                   => ExpectedType.Integer
      case "number" | "float" | "double"       => ExpectedType.Number
      case "gensym" | "unique-id" | "uniqueid" => ExpectedType.GenSym
      case "definition"                        => ExpectedType.Definition
      case x => throw EditorException(s"unknown parameter type '$x'")
    })
  def loadTemplate(data: NodeSeq): XMLTemplateData =
    XMLTemplateData((data \ "param").map(loadParam).toMap, (data \ "elems").flatMap(_.child))
  def loadTemplate(packages: PackageList, name: String): XMLTemplateData =
    EditorException.context(s"while loading xml template '$name'") {
      loadTemplate(XML.load(Files.newInputStream(packages.forceResolve(name))))
    }
}

class XMLTemplateComponent(protected val boundsParam: Bounds, data: XMLTemplateData)
  extends SimpleComponent with BoundedBase {

  private val componentMap = new mutable.HashMap[String, ComponentReference]
  private val stringMap    = new mutable.HashMap[String, String]

  for(par <- data.parameters.filter(_._2 == ExpectedType.GenSym).keySet)
    stringMap.put(par, s"princess_gensym_${GenID.makeId()}")

  private def templateString(manager: ComponentRenderManager, str: String) =
    XMLTemplateComponent.varRegex.replaceAllIn(str, x => x.group(1) match {
      case "minX" => bounds.minX.toString
      case "maxX" => bounds.maxX.toString
      case "width" | "maxX" => bounds.maxX.toString
      case "height" | "maxY" => bounds.maxY.toString
      case field => stringMap.get(field) match {
        case Some(s) => data.parameters.get(field) match {
          case Some(ExpectedType.Definition) =>
            manager.resources.loadDefinition(s)
          case _ => s
        }
        case None    => throw EditorException(s"field '${x.group(1)}' not set")
      }
    })
  private def processMetadata(manager: ComponentRenderManager, m: MetaData): MetaData =
    if(m == Null) Null
    else Attribute(if(m.isPrefixed) Some(m.asInstanceOf[Attribute].pre) else None,
                   m.key, if(m.value == null) Seq() else m.value.map(x => processNode(manager, x)),
                   processMetadata(manager, m.next))
  private def processNode(manager: ComponentRenderManager, n: Node): Node = n match {
    case Text(s) => Text(templateString(manager, s))
    case e: Elem if e.label == "component" =>
      val componentRef = manager.renderComponent(componentMap.get((e \ "@id").text) match {
        case Some(c) => c
        case None    => throw EditorException(s"field '${(e \ "@id").text}' not set")
      })

      val fill = (e \ "@fillComponent").nonEmpty
      val x = e \ "@x"
      val y = e \ "@y"
      val widthElem  = e \ "@width"
      val heightElem = e \ "@height"
      val otherAttrs = e.attributes.filter(x => !XMLTemplateComponent.ignoreAttrs.contains(x.key))

      val xd = if(x.nonEmpty) x.text.toDouble else 0d
      val yd = if(x.nonEmpty) y.text.toDouble else 0d

      var elem =
        if(fill)
          componentRef.includeInBounds(bounds.minX, bounds.minY, bounds.maxX, bounds.maxY)
        else if(widthElem.nonEmpty && heightElem.nonEmpty)
          componentRef.includeInRect(xd, yd, widthElem.text.toDouble, heightElem.text.toDouble)
        else componentRef.include(xd, yd)
      for(attr <- otherAttrs) elem = elem % Attribute(None, attr.key, Text(attr.value.text), Null)
      processNode(manager, elem)
    case e: Elem => e.copy(attributes = processMetadata(manager, e.attributes),
                           child = n.child.map(x => processNode(manager, x)))
    case x => x
  }

  override def renderComponent(manager: ComponentRenderManager) =
    (data.elems.map(x => processNode(manager, x)), bounds)

  for((k, expectedType) <- data.parameters) expectedType match {
    case ExpectedType.Component =>
      property(k, _                          => componentMap.get(k),
                  (L, v: ComponentReference) => componentMap.put(k, v))
    case ExpectedType.Integer =>
      property(k, _                          => stringMap.get(k).map(_.toInt),
                  (L, v: Int               ) => stringMap.put(k, v.toString))
    case ExpectedType.Number =>
      property(k, _                          => stringMap.get(k).map(_.toDouble),
                  (L, v: Double            ) => stringMap.put(k, v.toString))
    case ExpectedType.String | ExpectedType.GenSym | ExpectedType.Definition =>
      property(k, _                          => stringMap.get(k),
                  (L, v: String            ) => stringMap.put(k, v))
  }

  property("allowOverflow", _ => allowOverflow, (_, b: Boolean) => allowOverflow = b)
}
object XMLTemplateComponent {
  private val varRegex = "\\$\\{([a-zA-Z_][a-zA-Z_0-9]+)\\}".r
  private val ignoreAttrs = Set("fillComponent", "x", "y", "width", "height", "id")
}