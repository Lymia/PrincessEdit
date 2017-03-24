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

package moe.lymia.princess.editor.utils

import moe.lymia.nebula.compositetable._

import org.eclipse.swt.SWT
import org.eclipse.swt.graphics.Point
import org.eclipse.swt.widgets._

import scala.reflect.ClassTag

// WARNING: Here be dragons

private trait TablePlatform {
  def initNativeHeader(header: Composite) { }
  def getSize(p: Point) = p
}
private object TablePlatform {
  lazy val instance = SWT.getPlatform match {
    case "gtk" => GTKTablePlatform
    case _ => DefaultTablePlatform
  }
}

private trait HeaderHeightHack extends TablePlatform {
  private def checkStack(stack: Array[StackTraceElement]): Boolean = {
    for(elem <- stack.tail) if(!elem.getMethodName.startsWith("getSize"))
      return elem.getClassName  == "org.eclipse.nebula.widgets.compositetable.InternalCompositeTable" &&
             elem.getMethodName == "updateVisibleRows"
    false
  }

  override def getSize(p: Point) = {
    // InternalCompositeTable adds 3 to the height of a table header for no apparent reason when laying out
    // composite tables. This hack makes it stop doing that, so they stop being ugly.
    if(checkStack(Thread.currentThread().getStackTrace)) p.y -= 3
    p
  }
}

private object DefaultTablePlatform extends TablePlatform
private object GTKTablePlatform extends TablePlatform with HeaderHeightHack {
  private val headerField = classOf[AbstractNativeHeader].getDeclaredField("headerTable")
  headerField.setAccessible(true)

  override def initNativeHeader(header: Composite) = {
    // For some reason, resizing table header fields causes scrollbars to appear. This stops that from happening.

    println("Setting SWT.NO_SCROLL on table")

    val old = headerField.get(header).asInstanceOf[Table]
    old.dispose()
    val newTable = new Table(header, SWT.NO_SCROLL)
    newTable.setHeaderVisible(true)
    headerField.set(header, newTable)
  }
}

trait HeaderBase extends Composite {
  private val plaf = TablePlatform.instance
  protected def removeHeaderPadding() = true
  protected def initNativeHeader() = plaf.initNativeHeader(this)
  override def getSize: Point = if(removeHeaderPadding()) plaf.getSize(super.getSize) else super.getSize
}

trait ElementBase {
  val id: Any
}
trait ExtraBase

trait CompositeTableElement[Element <: ElementBase, Extra <: ExtraBase] {
  private[utils] var isInit: Boolean = false
  protected[utils] var table: ScalaCompositeTable[Element, Extra, _, _] = _
  protected[utils] var extra: Extra = _

  protected[utils] var hasElem: Boolean = false
  protected[utils] var elem: Element = _
  protected def getCurrentID = elem.id

  private[utils] def updateInternal(i: Int, elem: Element): Unit = {
    hasElem = true
    this.elem = elem
  }

  def init() { }
  def update(i: Int, elem: Element) { }

  def canLeaveRow(i: Int) = true
  def onLeaveRow(i: Int) = { }
  def onEnterRow(i: Int) = { }
}

abstract class NativeHeader[Element <: ElementBase, Extra <: ExtraBase](parent: Composite, style: Int)
  extends AbstractNativeHeader(parent, style) with CompositeTableElement[Element, Extra] with HeaderBase {

  initNativeHeader()

  protected def toggleSortDirectionThreeWay() = {
    val sortDirection = this.getSortDirection match {
      case SWT.NONE => SWT.DOWN
      case SWT.DOWN => SWT.UP
      case SWT.UP   => SWT.NONE
    }
    this.setSortDirection(sortDirection)
    sortDirection
  }
}
abstract class SortableHeader[Element <: ElementBase, Extra <: ExtraBase](parent: Composite, style: Int)
  extends AbstractSortableHeader(parent, style) with CompositeTableElement[Element, Extra] with HeaderBase
abstract class SelectableRow[Element <: ElementBase, Extra <: ExtraBase](parent: Composite, style: Int)
  extends AbstractSelectableRow(parent, style) with CompositeTableElement[Element, Extra]
abstract class EditorSelectableRow[Element <: ElementBase, Extra <: ExtraBase](parent: Composite, style: Int)
  extends AbstractEditorSelectableRow(parent, style) with CompositeTableElement[Element, Extra] {

  override def init(): Unit = setEditorMode(isEditorModeActive)
  override def update(i: Int, elem: Element): Unit = setEditorMode(isEditorModeActive)

  override protected def canEnableEditorMode: Boolean = hasElem
}

private final case class SortInfo[Element, SortBy : math.Ordering](sortFn: Element => SortBy) {
  def sort(list: IndexedSeq[Element]) = list.zipWithIndex.sortBy(x => sortFn(x._1))
}
abstract class ScalaCompositeTable[Element <: ElementBase, Extra <: ExtraBase,
                                   Header  <: Control with CompositeTableElement[Element, Extra] : ClassTag,
                                   Row     <: Control with CompositeTableElement[Element, Extra] : ClassTag]
  (parent: Composite, style: Int) extends CompositeTable(parent, style) {

  type TableElement = CompositeTableElement[Element, Extra]

  private val extra = newState()
  protected def newState(): Extra

  protected def getElements: IndexedSeq[Element]

  private var sorter: Option[SortInfo[Element, _]] = None
  private var sorted: IndexedSeq[(Element, Int)] = _

  protected def initComponent(element: TableElement) = {
    if(!element.isInit) {
      element.table = this
      element.extra = extra
      element.isInit = true
      element.init()
    }
  }
  def refreshElements() = {
    val elements = getElements
    sorted = sorter.fold(elements.zipWithIndex)(_.sort(getElements))
    setNumRowsInCollection(sorted.length)
  }
  def setSortOrder[T : math.Ordering](fn: Element => T): Unit = {
    sorter = Some(SortInfo(fn))
    refreshElements()
  }

  addRowConstructionListener(new RowConstructionListener {
    override def rowConstructed(control: Control) = initComponent(control.asInstanceOf[TableElement])
    override def headerConstructed(control: Control) = initComponent(control.asInstanceOf[TableElement])
  })
  addRowContentProvider { (compositeTable: CompositeTable, i: Int, control: Control) =>
    val row = control.asInstanceOf[TableElement]
    val elem = sorted(i)._1
    row.updateInternal(i, elem)
    row.update(i, elem)
  }
  addRowFocusListener(new IRowFocusListener {
    override def requestRowChange(compositeTable: CompositeTable, i: Int, control: Control) =
      control.asInstanceOf[TableElement].canLeaveRow(i)
    override def arrive(compositeTable: CompositeTable, i: Int, control: Control) =
      control.asInstanceOf[TableElement].onEnterRow(i)
    override def depart(compositeTable: CompositeTable, i: Int, control: Control) =
      control.asInstanceOf[TableElement].onLeaveRow(i)
  })

  private def initClass[T <: TableElement : ClassTag]() = {
    val clazz = implicitly[ClassTag[T]].runtimeClass.asInstanceOf[Class[TableElement]]
    val constructor = clazz.getConstructor(classOf[Composite], classOf[Int])
    val obj = constructor.newInstance(this, SWT.NONE : Integer)
    initComponent(obj)
  }
  initClass[Header]()
  initClass[Row   ]()
  this.setRunTime(true)
}