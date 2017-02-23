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

import java.awt.font.{FontRenderContext, LineBreakMeasurer, TextLayout}
import java.text.AttributedString

import moe.lymia.princess.core.{Bounds, TemplateException}
import moe.lymia.princess.core.lua._
import moe.lymia.princess.lua._
import org.jfree.graphics2d.svg.SVGGraphics2D

import scala.collection.mutable

private sealed trait TextLayoutResult
private object TextLayoutResult {
  case object Failure extends TextLayoutResult
  case class Success(data: Seq[(Double, Double, TextLayout)], problems: Int) extends TextLayoutResult
}

// TODO: Eventually add support for RTL languages and maybe even vertical text
// TODO: Support for horizontal centering of text

private class TextLayoutArea(parent: TextLayoutComponent, protected val boundsParam: Bounds)
  extends LuaLookup with BoundedBase {

  private var text: FormattedString = _

  private val exclusions = new mutable.ArrayBuffer[Bounds]
  private def aabbCollision(a: Bounds, b: Bounds) =
    a.minX < b.maxX && a.maxX > b.minX &&
    a.minY < b.maxY && a.maxY > b.minY
  private def hitTest(bound: Bounds) =
    exclusions.foldLeft(bound)((x, y) => if(aabbCollision(x, y)) x.copy(maxX = y.maxX) else x)

  private def emSize(manager: ComponentRenderManager, fontSize: Double) =
    (manager.settings.coordUnitsPerIn / 72) * fontSize

  private def doTextLayout(manager: ComponentRenderManager, frc: FontRenderContext, fontSize: Double,
                           startYOffset: Double): Option[(Double, Double, Int, Seq[(Double, Double, TextLayout)])] = {
    val em = emSize(manager, fontSize)

    var problems: Int = 0

    val data = new mutable.ArrayBuffer[(Double, Double, TextLayout)]
    var lineStart = true
    var currentBaseline: Double = -1
    var currentXPosition: Double = bounds.minX
    var currentLineEnd: Double = bounds.maxX
    var currentBulletXPosition: Double = bounds.minX
    var bottomBounds: Double = startYOffset
    var lastLineWords: Int = 0

    def finishLine() = {
      if(lastLineWords == 1) problems = problems + 1 // isolate line
      currentLineEnd = bounds.maxX
      lastLineWords = 0
      lineStart = true
    }
    def newLine(): Unit = {
      currentXPosition = currentBulletXPosition
      currentBaseline = currentBaseline + parent.emLineBreakSize * em
      finishLine()
    }
    def newParagraph(): Unit = {
      currentXPosition = bounds.minX
      currentBulletXPosition = bounds.minX
      currentBaseline = currentBaseline + parent.emParagraphBreakSize * em
      finishLine()
    }

    text.execute(manager, fontSize) {
      case FormatInstruction.RenderString(str) =>
        val iter = str.getIterator
        val len = iter.getEndIndex
        val measurer = new LineBreakMeasurer(iter, frc)

        var restorePosition: Int = 0
        var nextLayout: TextLayout = null
        var firstSection: Boolean = true
        while(measurer.getPosition != len && {
          def tryRemaining(requireWord: Boolean) = {
            val remainingSpace = currentLineEnd - currentXPosition
            nextLayout = measurer.nextLayout(remainingSpace.toFloat, len, requireWord)
          }

          restorePosition = measurer.getPosition
          if(!firstSection) newLine()
          tryRemaining(true)
          if(nextLayout == null) {
            if(!lineStart && firstSection) newLine()
            tryRemaining(false)
          }
          firstSection = false
          nextLayout != null
        }) {
          lineStart = false

          var nextBounds = Bounds(nextLayout.getBounds).translate(currentXPosition, currentBaseline)
          val actualBounds = hitTest(nextBounds)
          if(actualBounds != nextBounds) {
            measurer.setPosition(restorePosition)
            currentLineEnd = actualBounds.maxX
          } else {
            nextBounds = nextBounds.translate(-currentXPosition, -currentBaseline)

            if(currentBaseline == -1) currentBaseline = startYOffset - nextBounds.minY

            bottomBounds = math.max(bottomBounds, currentBaseline + nextBounds.maxY)
            lastLineWords = lastLineWords + nextLayout.toString.count(_ == ' ') + 1

            if(bottomBounds > bounds.maxY) return None

            data.append((currentXPosition, currentBaseline, nextLayout))
            currentXPosition = currentXPosition + nextBounds.size.width
          }
        }
      case FormatInstruction.BulletStop =>
        currentXPosition = currentXPosition + parent.emBulletStopOffset * em
        currentBulletXPosition = currentXPosition
      case FormatInstruction.NewLine => newLine()
      case FormatInstruction.NewParagraph => newParagraph()
      case FormatInstruction.NoStartLineHint => if(lineStart) problems = problems + 1
    }

    finishLine() // for isolate tracking

    Some((startYOffset, bottomBounds, problems, data))
  }

  def layout(manager: ComponentRenderManager, frc: FontRenderContext, fontSize: Double): TextLayoutResult = {
    var verticalOffset: Double = 0
    var result: TextLayoutResult = TextLayoutResult.Failure
    for(i <- 0 until (if(parent.centerVertical) parent.centerVerticalCycles else 1))
      doTextLayout(manager, frc, fontSize, bounds.minY + verticalOffset) match {
        case Some((minY, maxY, problems, data)) =>
          verticalOffset = (bounds.size.height - (maxY - minY)) / 2
          result = TextLayoutResult.Success(data, problems)
        case None => return result
      }
    result
  }

  method("addExclusion") { (bounds: Bounds) => exclusions.append(bounds) }
  property("text", _ => text, (_, n: FormattedString) => text = n)

  lock()
}

private class TextLayoutAreaManager(parent: TextLayoutComponent) extends HasLuaMethods {
  val layoutAreas = new mutable.HashMap[String, TextLayoutArea]

  private val newFn: ScalaLuaClosure = (L: LuaState, name: String, bounds: Bounds) => {
    if(layoutAreas.contains(name)) L.error(s"layout area '$name' already exists")
    layoutAreas.put(name, new TextLayoutArea(parent, bounds))
    ()
  }
  override def getField(L: LuaState, name: String): LuaObject = name match {
    case "new" => newFn
    case _     => layoutAreas.get(name) : Option[HasLuaMethods]
  }
  override def setField(L: LuaState, name: String, obj: Any) = L.error(s"cannot set fields in 'area'")
}

class TextLayoutComponent(protected val boundsParam: Bounds) extends GraphicsComponent with BoundedBase {
  private val areaManager = new TextLayoutAreaManager(this)

  private[components] var emLineBreakSize: Double = 1
  private[components] var emParagraphBreakSize: Double = 2
  private[components] var emBulletStopOffset: Double = 0.5
  private[components] var centerVertical: Boolean = false
  private[components] var centerVerticalCycles: Int = 3

  // TODO: Find a font size scaling system that fits text better at very small sizes
  // TODO: Implement some kind of binary search to speed up searches
  private var startFontSize: Double = 12
  private var fontSizeDecrement: Double = 0.25
  private var minFontSize: Double = 1
  private var tryExtra: Int = 4

  def findTextSize(manager: ComponentRenderManager, frc: FontRenderContext) = {
    var fontSize = startFontSize

    var bestData: Seq[(Double, Double, TextLayout)] = null
    var bestProblems: Int = Int.MaxValue

    def layoutCurrentSize() =
      areaManager.layoutAreas.valuesIterator.map(_.layout(manager, frc, fontSize)).toSeq
    def isSolutionGood(result: Seq[TextLayoutResult]) = result.forall(_.isInstanceOf[TextLayoutResult.Success])
    def checkIsBest(result: Seq[TextLayoutResult]) = {
      val problems = result.map(_.asInstanceOf[TextLayoutResult.Success].problems).sum
      if(problems < bestProblems) {
        val data = result.map(_.asInstanceOf[TextLayoutResult.Success].data).reduce(_ ++ _)
        bestData = data
        bestProblems = problems
      }
    }

    while(fontSize >= minFontSize) {
      val results = layoutCurrentSize()
      if(isSolutionGood(results)) {
        checkIsBest(results)

        for(i <- 1 until tryExtra) if(fontSize >= minFontSize) {
          fontSize = fontSize - fontSizeDecrement
          val results = layoutCurrentSize()
          if(isSolutionGood(results)) checkIsBest(results)
        }
      }
      fontSize = fontSize - fontSizeDecrement
    }

    if(bestData != null) Some(bestData) else null
  }

  override def renderComponent(manager: ComponentRenderManager, graphics: SVGGraphics2D, table: LuaTable): Bounds = {
    // TODO: Proper error handling
    val layouts =
      findTextSize(manager, graphics.getFontRenderContext).getOrElse(throw TemplateException("Error laying out text"))
    for((x, y, layout) <- layouts) layout.draw(graphics, x.toFloat, y.toFloat)
    bounds
  }

  property("areas", _ => areaManager : HasLuaMethods)

  property("lineBreakSize"       , _ => emLineBreakSize     , (_, d: Double ) => emLineBreakSize      = d)
  property("paragraphBreakSize"  , _ => emParagraphBreakSize, (_, d: Double ) => emParagraphBreakSize = d)
  property("bulletStopOffset"    , _ => emBulletStopOffset  , (_, d: Double ) => emBulletStopOffset   = d)
  property("centerVertical"      , _ => centerVertical      , (_, b: Boolean) => centerVertical       = b)
  property("centerVerticalCycles", _ => centerVerticalCycles, (_, i: Int    ) => centerVerticalCycles = i)

  property("startFontSize"       , _ => startFontSize       , (_, d: Double) => startFontSize         = d)
  property("fontSizeDecrement"   , _ => fontSizeDecrement   , (_, d: Double) => fontSizeDecrement     = d)
  property("minFontSize"         , _ => minFontSize         , (_, d: Double) => minFontSize           = d)
  property("tryExtra"            , _ => tryExtra            , (_, i: Int   ) => tryExtra              = i)
}
