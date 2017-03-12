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

package moe.lymia.princess.renderer

import java.io.Writer

import moe.lymia.princess.lua.LuaTable
import moe.lymia.princess.rasterizer.SVGRasterizer
import org.jfree.graphics2d.svg.{SVGGraphics2D, SVGHints}

import scala.collection.mutable
import scala.xml.dtd.{DocType, PublicID}
import scala.xml.{XML => _, _}

final class SVGGraphicsRenderer(settings: RenderSettings) {
  val gfx = new SVGGraphics2D(0, 0)
  gfx.setRenderingHint(SVGHints.KEY_DRAW_STRING_TYPE, SVGHints.VALUE_DRAW_STRING_TYPE_VECTOR)
  def renderXML() = XML.loadString(gfx.getSVGDocument)
}

final case class SVGDefinitionReference(name: String, bounds: Bounds, extraLayout: Option[LuaTable],
                                        parent: SVGBuilder) {
  private def incrementRefcount() = parent.addUsage(name)
  def setNoInline() = parent.noInline(name)

  private def rawInclude(x: Double, y: Double) = {
    incrementRefcount()
    <use x={x.toString} y={y.toString} xlink:href={s"#$name"}
         princess:reference={name}/>.copy(scope = SVGBuilder.princessOnlyScope)
  }
  def include(x: Double, y: Double): Elem = rawInclude(x + bounds.minX, y + bounds.minY)
  def includeInRect(x: Double, y: Double, width: Double, height: Double): Elem = {
    val boundSize = bounds.size
    if(boundSize == Size(width, height)) rawInclude(x, y)
    else (rawInclude(x, y) % Attribute(null : String, "transform",
                                       s"translate(${-x} ${-y}) "+
                                       s"scale(${width/boundSize.width} ${height/boundSize.height}) "+
                                       s"translate($x $y)", Null)
                           % Attribute("princess", "newX", width.toString , Null)
                           % Attribute("princess", "newY", height.toString, Null))
  }
  def includeInBounds(minX: Double, minY: Double, maxX: Double, maxY: Double): Elem =
    includeInRect(minX, minY, maxX - minX, maxY - minY)
}
final class SVGBuilder(val settings: RenderSettings) {
  private val id = GenID.makeId()
  private var defId = 0
  private val definitions = new mutable.ArrayBuffer[(String, Elem)]
  private val definitionMap = new mutable.HashMap[String, Elem]
  private val noInlineList = new mutable.HashSet[String]
  private val useCount = new mutable.HashMap[String, Int]

  private def attribute(key: String, value: String) = Attribute(None, key, Text(value), Null)

  private[renderer] def addUsage(id: String) = useCount.put(id, useCount.getOrElse(id, 0) + 1)
  private[renderer] def noInline(id: String) = noInlineList.add(id)

  private def getUseCount(id: String) = useCount.getOrElse(id, 0)
  private def isUsed(id: String) = getUseCount(id) > 0
  private def doInline(id: String) = !noInlineList.contains(id) && getUseCount(id) == 1

  private def inlineReferencesIterContinue(elem: Elem): Node =
    elem.copy(child = elem.child.map {
      case e: Elem => inlineReferencesIter(e)
      case e => e
    })
  private def inlineReferencesIter(elem: Elem): Node =
    if(elem.label == "use") elem.attribute(XMLNS.princess, "reference") match {
      case Some(x) if doInline(x.text) =>
        val newX = elem.attribute(XMLNS.princess, "newX")
        val newY = elem.attribute(XMLNS.princess, "newY")
        var node = definitionMap(x.text)

        if(newX.isDefined && newY.isDefined) node = (
          node % attribute("width" , newX.get.text)
               % attribute("height", newY.get.text)
        )
        for(attr <-elem.attributes if !SVGBuilder.useExcludeSet.contains(attr.key) &&
                                      !attr.prefixedKey.startsWith("princess:"))
          node = node % attr.copy(Null)

        inlineReferencesIter(node)
      case _ => inlineReferencesIterContinue(elem)
    } else inlineReferencesIterContinue(elem)

  def createDefinition(name: String, elem: Elem, isDef: Boolean = false) = {
    val resourceName = s"princess_def_${id}_${defId}_${name.replace(" ", "_").replaceAll("[^a-zA-Z0-9_]", "")}"
    defId = defId + 1
    definitions.append((resourceName, elem % attribute("id", resourceName)))
    definitionMap.put(resourceName, elem)
    if(isDef) {
      useCount.put(resourceName, 1)
      noInlineList.add(resourceName)
    }
    resourceName
  }
  private def setSize(elem: Elem, bounds: Bounds) =
    (elem % attribute("width"              , bounds.width.toString)
          % attribute("height"             , bounds.height.toString)
          % attribute("preserveAspectRatio", "none"))
  def createDefinitionFromContainer(name: String, bounds: Bounds, elems: Elem,
                                    extraLayout: Option[LuaTable] = None) = {
    SVGDefinitionReference(createDefinition(name, setSize(elems, bounds)), bounds, extraLayout, this)
  }
  def createDefinitionFromSVG(name: String, bounds: Bounds, elem: Elem,
                              extraLayout: Option[LuaTable] = None, allowOverflow: Boolean = false) = {
    val withViewbox =
      elem % Attribute(null, "viewBox", s"${bounds.minX} ${bounds.minY} ${bounds.width} ${bounds.height}", Null)
    createDefinitionFromContainer(name, bounds,
      if(!allowOverflow) withViewbox else withViewbox % Attribute(null, "overflow", "visible", Null),
      extraLayout = extraLayout)
  }
  def createDefinitionFromFragment(name: String, bounds: Bounds, elems: NodeSeq,
                                   extraLayout: Option[LuaTable] = None, allowOverflow: Boolean = false) =
    createDefinitionFromSVG(name, bounds, <svg>{elems}</svg>, extraLayout = extraLayout, allowOverflow = allowOverflow)

  def createRenderer() = new SVGGraphicsRenderer(settings)

  private val stylesheetDefs = new mutable.ArrayBuffer[String]
  def addStylesheetDefinition(str: String) = stylesheetDefs.append(str)

  def renderSVGTag(root: SVGDefinitionReference, pretty: Boolean = false) = {
    def inlineRefs(elem: Elem) = if(pretty) inlineReferencesIter(elem) else elem
    def doMinify(elem: Elem) = if(pretty) MinifyXML.SVG(elem, SVGBuilder.princessScope) else elem
    val rootTag = inlineRefs(root.includeInRect(0, 0, settings.viewport.width, settings.viewport.height))
    doMinify(<svg version="1.1" preserveAspectRatio="none" overflow="hidden"
          width={settings.size.widthString} height={settings.size.heightString}
          viewBox={s"0 0 ${settings.viewport.width} ${settings.viewport.height}"}>
      {stylesheetDefs.map(x => <style>{x}</style>)}
      <defs> {
        definitions.filter(x => (!pretty || !doInline(x._1)) && isUsed(x._1)).map(x => inlineRefs(x._2).head)
      } </defs>
      {rootTag}
    </svg>).copy(scope = if(pretty) SVGBuilder.scope else SVGBuilder.princessScope)
  }

  def write(w: Writer, root: SVGDefinitionReference, encoding: String= "utf-8", pretty: Boolean = true) = {
    def prettyPrint(e: Elem) = if(pretty) SVGBuilder.prettyPrinter.format(e) else e.toString()
    w.write(s"<?xml version='1.0' encoding='$encoding'?>\n")
    w.write(s"${SVGBuilder.SVG11Doctype.toString}\n")
    w.write( "<!-- SVG generated by PrincessEdit -->\n")
    w.write(s"${prettyPrint(renderSVGTag(root, pretty = pretty))}\n")
    w.close()
  }
  def rasterize(rasterizer: SVGRasterizer, x: Int, y: Int, root: SVGDefinitionReference) =
    rasterizer.rasterizeSVG(x, y, renderSVGTag(root))
}
private[renderer] object SVGBuilder {
  val SVG11Doctype = DocType(
    "svg",
    PublicID("-//W3C//DTD SVG 1.1//EN",
    "http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd"),
    Nil
  )
  val scope = NamespaceBinding(null, XMLNS.svg,
              NamespaceBinding("xlink", XMLNS.xlink,  TopScope))
  val princessScope = NamespaceBinding("princess", XMLNS.princess, scope)
  val princessOnlyScope = princessScope.copy(parent = TopScope)
  val prettyPrinter = new PrettyPrinter(Int.MaxValue, 2)
  val useExcludeSet = Set("transform", "href")
}