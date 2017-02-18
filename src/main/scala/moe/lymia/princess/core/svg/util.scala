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

import java.awt.Font

import scala.collection.mutable
import scala.xml._

final case class Size(width: Double, height: Double)

case class PhysicalUnit(svgName: String, unPerInch: Double)
object PhysicalUnit {
  val mm = PhysicalUnit("mm", 25.4)
  val in = PhysicalUnit("in", 1)
}

final case class PhysicalSize(width: Double, height: Double, unit: PhysicalUnit) {
  val widthString  = s"$width${unit.svgName}"
  val heightString = s"$height${unit.svgName}"
}

final case class RenderSettings(viewport: Size, unPerViewport: Double, physicalUnit: PhysicalUnit) {
  val size            = PhysicalSize(viewport.width * unPerViewport, viewport.height * unPerViewport, physicalUnit)
  val coordUnitsPerIn = size.unit.unPerInch / unPerViewport
  def scaleFont(font: Font, ptSize: Double) =
    font.deriveFont((ptSize * (coordUnitsPerIn / 72.0)).toFloat)
}

object XMLUtils {
  def filterAttributes(m: MetaData)(fn: MetaData => Boolean) = {
    var newMetadata: MetaData = Null
    for(md <- m) if(fn(md)) newMetadata = md.copy(newMetadata)
    newMetadata
  }
  def getAttribute(e: Elem, ns: String, key: String) =
    e.attributes.find {
      case PrefixedAttribute(`ns`, `key`, _, _) => true
      case _ => false
    }.map(_.value)
}

case class MinifyXML(dropNamespaces: Set[String], dropTags: Set[String]) {
  private def iter(e: Elem, currentScope: mutable.HashMap[String, String]): Seq[Elem] =
    if(dropNamespaces.contains(e.prefix) ||
       dropTags      .contains(e.label)) Seq() else {
      val newAttr = XMLUtils.filterAttributes(e.attributes) {
        case x: Attribute => !dropNamespaces.contains(x.pre)
        case x => true
      }

      var newCurrentScope = currentScope.clone()
      var curNewScope: NamespaceBinding = TopScope
      var curScope = e.scope
      while(curScope != TopScope) {
        if(!dropNamespaces.contains(curScope.prefix) && !currentScope.get(curScope.prefix).contains(curScope.uri)) {
          curNewScope = curScope.copy(parent = curNewScope)
          newCurrentScope.put(curScope.prefix, curScope.uri)
        }
        curScope = curScope.parent
      }

      Seq(e.copy(scope = curNewScope, attributes = newAttr, child = e.child flatMap {
        case ce: Elem => iter(ce, newCurrentScope)
        case n => Seq(n)
      }))
    }

  def apply(n: Elem, containerScope: NamespaceBinding = TopScope): Elem = {
    val scope = new mutable.HashMap[String, String]
    var curScope = containerScope
    while(curScope != TopScope) {
      scope.put(curScope.prefix, curScope.uri)
      curScope = curScope.parent
    }
    iter(n, scope).headOption.getOrElse(<ELEM/>.copy(label = n.label))
  }
}
object MinifyXML {
  val SVG = MinifyXML(Set("dc", "cc", "rdf", "sodipodi", "inkscape"), Set("metadata"))
  val SVGFinalize = MinifyXML(Set("princess"), Set())
}