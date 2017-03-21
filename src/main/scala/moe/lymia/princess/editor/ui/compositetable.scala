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

package moe.lymia.princess.editor.ui

import org.eclipse.nebula.widgets.compositetable
import org.eclipse.nebula.widgets.compositetable.{CompositeTable => _, _}
import org.eclipse.swt.SWT
import org.eclipse.swt.graphics.Point
import org.eclipse.swt.widgets.{Composite, Control, Table}

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
    for(elem <- stack.tail)
      if(!elem.getClassName.startsWith("moe.lymia.princess.editor.ui.") ||
         !elem.getMethodName.startsWith("getSize"))
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

trait CompositeTableElement[Extra] {
  private[ui] var isInit: Boolean = false
  protected[ui] var table: CompositeTable[Extra, _, _] = _
  protected[ui] var extra: Extra = _

  def update(i: Int) { }

  def canLeaveRow(i: Int) = true
  def onLeaveRow(i: Int) = { }
  def onEnterRow(i: Int) = { }
}

abstract class NativeHeader[Extra](parent: Composite, style: Int)
  extends AbstractNativeHeader(parent, style) with CompositeTableElement[Extra] with HeaderBase {

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
abstract class SortableHeader[Extra](parent: Composite, style: Int)
  extends AbstractSortableHeader(parent, style) with CompositeTableElement[Extra] with HeaderBase
abstract class SelectableRow[Extra](parent: Composite, style: Int)
  extends AbstractSelectableRow(parent, style) with CompositeTableElement[Extra]

abstract class CompositeTable[Extra,
                              Header <: Control with CompositeTableElement[Extra] : ClassTag,
                              Row    <: Control with CompositeTableElement[Extra] : ClassTag]
  (parent: Composite, style: Int) extends compositetable.CompositeTable(parent, style) {

  protected def getExtra: Extra

  protected def initComponent(element: CompositeTableElement[Extra]) =
    if(!element.isInit) {
      element.table = this
      element.extra = getExtra
      element.isInit = true
    }

  private def initClass[T <: CompositeTableElement[Extra] : ClassTag]() = {
    val clazz = implicitly[ClassTag[T]].runtimeClass.asInstanceOf[Class[CompositeTableElement[Extra]]]
    val constructor = clazz.getConstructor(classOf[Composite], classOf[Int])
    val obj = constructor.newInstance(this, SWT.NONE : Integer)
    initComponent(obj)
  }
  initClass[Header]()
  initClass[Row   ]()

  addRowConstructionListener(new RowConstructionListener {
    override def rowConstructed(control: Control) =
      initComponent(control.asInstanceOf[CompositeTableElement[Extra]])
    override def headerConstructed(control: Control) = rowConstructed(control)
  })
  addRowContentProvider((compositeTable: compositetable.CompositeTable, i: Int, control: Control) =>
    control.asInstanceOf[CompositeTableElement[Extra]].update(i))
  addRowFocusListener(new IRowFocusListener {
    override def requestRowChange(compositeTable: compositetable.CompositeTable, i: Int, control: Control) =
      control.asInstanceOf[CompositeTableElement[Extra]].canLeaveRow(i)
    override def arrive(compositeTable: compositetable.CompositeTable, i: Int, control: Control) =
      control.asInstanceOf[CompositeTableElement[Extra]].onEnterRow(i)
    override def depart(compositeTable: compositetable.CompositeTable, i: Int, control: Control) =
      control.asInstanceOf[CompositeTableElement[Extra]].onLeaveRow(i)
  })
}