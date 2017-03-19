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

import org.eclipse.nebula.widgets.compositetable.AbstractNativeHeader
import org.eclipse.swt.SWT
import org.eclipse.swt.graphics.Point
import org.eclipse.swt.widgets.{Composite, Table}

// WARNING: Here be dragons

private trait TablePlatform {
  def init(header: AbstractNativeHeader) { }
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
    // composite tables. This hack makes it stop doing that, to make them stop being ugly.
    if(checkStack(Thread.currentThread().getStackTrace)) p.y -= 3
    p
  }
}

private object DefaultTablePlatform extends TablePlatform
private object GTKTablePlatform extends TablePlatform with HeaderHeightHack {
  private val headerField = classOf[AbstractNativeHeader].getDeclaredField("headerTable")
  headerField.setAccessible(true)

  override def init(header: AbstractNativeHeader) = {
    // For some reason, resizing table header fields causes scrollbars to appear. This stops that from happening.
    val old = headerField.get(header).asInstanceOf[Table]
    old.dispose()
    val newTable = new Table(header, SWT.NO_SCROLL)
    newTable.setHeaderVisible(true)
    headerField.set(header, newTable)
  }
}

abstract class NativeHeader(parent: Composite, style: Int) extends AbstractNativeHeader(parent, style) {
  private val i = TablePlatform.instance
  i.init(this)
  override def getSize: Point = i.getSize(super.getSize)
}
