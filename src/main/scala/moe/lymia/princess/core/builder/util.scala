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

package moe.lymia.princess.core.builder

import java.awt.Font

import moe.lymia.princess.core.Size

import scala.collection.mutable
import scala.xml._

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
  def mapAttributes(m: MetaData)(fn: MetaData => MetaData) = {
    var newMetadata: MetaData = Null
    for(md <- m) newMetadata = fn(md).copy(newMetadata)
    newMetadata
  }
  def getAttribute(e: Elem, ns: String, key: String) =
    e.attributes.find {
      case PrefixedAttribute(`ns`, `key`, _, _) => true
      case _ => false
    }.map(_.value)
}

// TODO: Take care of some of the code repetition here.
case class MinifyXML(dropNamespaces: Set[String], dropTags: Set[String]) {
  private def iter(e: Elem, currentScope: mutable.HashMap[String, String],
                            currentScopeReverse: mutable.HashMap[String, String],
                            remapScope: mutable.HashMap[String, String]): Seq[Elem] =
    if(dropNamespaces.contains(currentScope.getOrElse(e.prefix, null)) ||
       dropTags      .contains(e.label)) Seq() else {
      val newRemapScope = remapScope.clone()
      val newCurrentScopeReverse = currentScopeReverse.clone()
      val newCurrentScope = currentScope.clone()
      var curNewScope: NamespaceBinding = TopScope
      var curScope = e.scope
      while(curScope != TopScope) {
        if(!dropNamespaces.contains(curScope.uri) && !currentScope.get(curScope.prefix).contains(curScope.uri)) {
          newCurrentScopeReverse.get(curScope.uri) match {
            case Some(newPrefix) => newRemapScope.put(curScope.prefix, newPrefix)
            case None =>
              newCurrentScopeReverse.put(curScope.uri, curScope.prefix)
              curNewScope = curScope.copy(parent = curNewScope)
          }
        }
        newCurrentScope.put(curScope.prefix, curScope.uri)
        curScope = curScope.parent
      }

      val newAttr = XMLUtils.mapAttributes(XMLUtils.filterAttributes(e.attributes) {
        case x: Attribute => !dropNamespaces.contains(newCurrentScope.getOrElse(x.pre, null))
        case x => true
      }) {
        case x: Attribute => newRemapScope.get(x.pre) match {
          case Some(remap) => Attribute(remap, x.key, x.value, Null)
          case None => x
        }
        case x => x
      }

      Seq(e.copy(prefix = newRemapScope.getOrElse(e.prefix, e.prefix),
                 scope = curNewScope, attributes = newAttr, child = e.child flatMap {
        case ce: Elem => iter(ce, newCurrentScope, newCurrentScopeReverse, newRemapScope)
        case n => Seq(n)
      }))
    }

  def apply(n: Elem, containerScope: NamespaceBinding = TopScope): Elem = {
    val scope = new mutable.HashMap[String, String]
    val scopeRev = new mutable.HashMap[String, String]
    val remap = new mutable.HashMap[String, String]
    var curScope = containerScope
    while(curScope != TopScope) {
      scope.put(curScope.prefix, curScope.uri)
      scopeRev.get(curScope.uri) match {
        case Some(newPrefix) => remap.put(curScope.prefix, newPrefix)
        case None =>
          scopeRev.put(curScope.uri, curScope.prefix)
          curScope = curScope.parent
      }
    }
    iter(n, scope, scopeRev, remap).headOption.getOrElse(<ELEM/>.copy(label = n.label))
  }
}
object MinifyXML {
  import XMLNS._
  val SVG = MinifyXML(Set(dc, cc, rdf, sodipodi, inkscape, jfreesvg, princess), Set())
}

object XMLNS {
  val dc       = "http://purl.org/dc/elements/1.1/"
  val cc       = "http://creativecommons.org/ns#"
  val rdf      = "http://www.w3.org/1999/02/22-rdf-syntax-ns#"
  val svg      = "http://www.w3.org/2000/svg"
  val sodipodi = "http://sodipodi.sourceforge.net/DTD/sodipodi-0.dtd"
  val inkscape = "http://www.inkscape.org/namespaces/inkscape"
  val xlink    = "http://www.w3.org/1999/xlink"
  val jfreesvg = "http://www.jfree.org/jfreesvg/svg"
  val princess = "http://lymia.moe/xmlns/princess"
}