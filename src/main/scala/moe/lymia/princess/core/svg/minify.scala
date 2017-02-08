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

package moe.lymia.princess.core.svg

import scala.xml._

case class MinifyDefinition(dropNamespaces: Set[String], dropTags: Set[String])
object MinifyDefinition {
  val SVG = MinifyDefinition(Set("dc", "cc", "rdf", "sodipodi", "inkscape"), Set("metadata"))
}

object MinifyXML {
  private def minifyXMLIter(definition: MinifyDefinition, node: Elem): Seq[Elem] =
    if(definition.dropNamespaces.contains(node.prefix) ||
       definition.dropTags      .contains(node.label)) Seq()
    else {
      var newAttr: MetaData = Null
      for(attribute <- node.attributes.map(_.asInstanceOf[Attribute]))
        if(!definition.dropNamespaces.contains(attribute.pre))
          newAttr = Attribute(attribute.pre, attribute.key, attribute.value, newAttr)

      var curScope = node.scope
      var curNewScope: NamespaceBinding = TopScope
      while(curScope != TopScope) {
        if(!definition.dropNamespaces.contains(curScope.prefix)) curNewScope = curScope.copy(parent = curNewScope)
        curScope = curScope.parent
      }

      Seq(node.copy(scope = curNewScope, attributes = newAttr, child = node.child flatMap {
        case x: Elem => minifyXMLIter(definition, x)
        case x => Seq(x)
      }))
    }
  def minifyXML(definition: MinifyDefinition, node: Elem) =
    minifyXMLIter(definition, node).headOption.getOrElse(<NODE/>.copy(label = node.label))
}
