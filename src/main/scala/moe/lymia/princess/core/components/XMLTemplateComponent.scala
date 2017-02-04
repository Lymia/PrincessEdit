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

import java.nio.file.Files

import moe.lymia.princess.core._
import moe.lymia.princess.lua._

import scala.collection.mutable
import scala.xml.{XML => _, _}

sealed trait ExpectedType
object ExpectedType {
  case object Component extends ExpectedType
  case object String    extends ExpectedType
  case object Integer   extends ExpectedType
  case object Number    extends ExpectedType
  case object GenSym    extends ExpectedType
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
      case x => throw TemplateException(s"unknown parameter type '$x'")
    })
  def loadTemplate(data: NodeSeq): XMLTemplateData =
    XMLTemplateData((data \ "param").map(loadParam).toMap, (data \ "elems").flatMap(_.child))
  def loadTemplate(packages: LoadedPackages, name: String): XMLTemplateData =
    TemplateException.context(s"while loading xml template '$name'") {
      loadTemplate(XML.load(Files.newInputStream(packages.forceResolve(name))))
    }
}

class XMLTemplateComponent(private var size: Size, data: XMLTemplateData) extends Component {
  private val componentMap = new mutable.HashMap[String, ComponentReference]
  private val stringMap    = new mutable.HashMap[String, String]
  private val extTable     = new LuaTable()

  for(par <- data.parameters.filter(_._2 == ExpectedType.GenSym).keySet)
    stringMap.put(par, s"princess_gensym_${GenID.makeId()}")

  override def getSize: Size = size

  private def templateString(str: String) =
    XMLTemplateComponent.varRegex.replaceAllIn(str, x => stringMap.get(x.group(1)) match {
      case Some(s) => s
      case None    => throw TemplateException(s"field '${x.group(1)}' not set")
    })
  private def processMetadata(m: MetaData): MetaData =
    if(m == null) null else new UnprefixedAttribute(m.key, templateString(m.value.text), processMetadata(m.next))
  private def processNode(manager: ComponentRenderManager, n: Node): Node = n match {
    case Text(s) => Text(templateString(s))
    case e: Elem if e.label == "component" =>
      val componentRef = manager.renderComponent(componentMap.get((e \ "@id").text) match {
        case Some(c) => c
        case None    => throw TemplateException(s"field '${(e \ "@id").text}' not set")
      })

      val x = (e \ "@x").text.toDouble
      val y = (e \ "@y").text.toDouble
      val widthElem  = e \ "@width"
      val heightElem = e \ "@height"
      val otherAttrs = e.attributes.filter(x => XMLTemplateComponent.ignoreAttrs.contains(x.key))

      var elem = if(widthElem.nonEmpty && heightElem.nonEmpty)
        componentRef.include(x, y, widthElem.text.toDouble, heightElem.text.toDouble)
      else componentRef.include(x, y)
      for(attr <- otherAttrs) elem = elem % Attribute(None, attr.key, Text(attr.value.text), Null)
      elem
    case e: Elem => e.copy(attributes = processMetadata(e.attributes),
                           child = n.child.map(x => processNode(manager, x)))
    case x => x
  }
  override def renderComponent(manager: ComponentRenderManager) = data.elems.map(x => processNode(manager, x))

  override def setField(L: LuaState, k: String, v: Any) = k match {
    case "extensions" | "ext" => sys.error(s"field '$k' is immutable")
    case "size"               => size = v.fromLua[Size](L)
    case _ => data.parameters.get(k) match {
      case Some(ExpectedType.Component) => componentMap.put(k, v.fromLua[ComponentReference](L))
      case Some(ExpectedType.Integer  ) => stringMap   .put(k, v.fromLua[Int](L).toString)
      case Some(ExpectedType.Number   ) => stringMap   .put(k, v.fromLua[Double](L).toString)
      case Some(ExpectedType.String   ) |
           Some(ExpectedType.GenSym   ) => stringMap   .put(k, v.fromLua[String](L))
      case None => super.setField(L, k, v)
    }
  }
  override def getField(L: LuaState, k: String): LuaObject = k match {
    case "extensions" | "ext" => extTable
    case "size"               => size
    case _ => data.parameters.get(k) match {
      case Some(ExpectedType.Component) => componentMap.get(k)
      case Some(ExpectedType.Integer  ) => stringMap   .get(k).map(_.toInt)
      case Some(ExpectedType.Number   ) => stringMap   .get(k).map(_.toDouble)
      case Some(ExpectedType.String   ) |
           Some(ExpectedType.GenSym   ) => stringMap   .get(k)
      case None => L.getTable(extTable, k)
    }
  }
}
object XMLTemplateComponent {
  private val varRegex = "\\$\\{([a-zA-Z_][a-zA-Z_0-9]+)\\}".r
  private val ignoreAttrs = Set("x", "y", "width", "height", "id")
}