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

package moe.lymia.princess.gui.nodes

import moe.lymia.lua.Lua
import moe.lymia.princess.core.EditorException
import org.eclipse.swt.SWT
import org.eclipse.swt.layout._
import org.eclipse.swt.widgets._
import rx._

import scala.collection.mutable

case class LabelNode(text: String) extends ControlNode {
  override def setupNode(implicit ctx: NodeContext, owner: Ctx.Owner) = SetupData.none
  override def createControl(parent: Composite)(implicit ctx: NodeContext, uiCtx: UIContext, owner: Ctx.Owner) = {
    val label = new Label(parent, SWT.NONE)
    label.setText(ctx.i18n.user(text))
    label
  }
}

case object SpacerNode extends ControlNode {
  override def setupNode(implicit ctx: NodeContext, owner: Ctx.Owner) = SetupData.none
  override def createControl(parent: Composite)(implicit ctx: NodeContext, uiCtx: UIContext, owner: Ctx.Owner) =
    new Label(parent, SWT.NONE)
}

case class VisibilityNode(isVisible: FieldNode, contents: ControlNode) extends ControlNode {
  override def setupNode(implicit ctx: NodeContext, owner: Ctx.Owner): SetupData = {
    ctx.setupNode(isVisible)
    ctx.setupNode(contents)
    SetupData.none
  }

  override def createControl(parent: Composite)(implicit ctx: NodeContext, uiCtx: UIContext, owner: Ctx.Owner) = {
    val container = new Composite(parent, SWT.NONE)
    container.setLayout(new GridLayout)

    val widget = contents.createControl(container)
    val data = new GridData(SWT.FILL, SWT.FILL, true, true)
    widget.setLayoutData(data)

    val isVisibleRx = ctx.activateNode(isVisible).map(Lua.toBoolean)
    isVisibleRx.foreach { b => ctx.controlCtx.asyncUiExec {
      data.exclude = !b
      if(!widget.isDisposed) widget.setVisible(b)
      if(!container.isDisposed) container.layout(true)

      container.setTabList(if(b) Array(widget) else Array())
    }}

    container
  }
}

case class GridComponent(component: ControlNode, x: Int, y: Int, tabOrder: Int, newConstraints: () => GridData)
private case class ComputedGridComponent(component: ControlNode, tabOrder: Int, newConstraints: () => GridData)
class GridNode private (data: Seq[ComputedGridComponent], newLayout: () => GridLayout) extends ControlNode {
  override def setupNode(implicit ctx: NodeContext, owner: Ctx.Owner) = {
    data.foreach(x => ctx.setupNode(x.component))
    SetupData.none
  }
  override def createControl(parent: Composite)(implicit ctx: NodeContext, uiCtx: UIContext, owner: Ctx.Owner) = {
    val pane = new Composite(parent, SWT.NONE)
    pane.setLayout(newLayout())
    val tabData = (for(component <- data) yield {
      val control = component.component.createControl(pane)
      control.setLayoutData(component.newConstraints())
      (component.tabOrder, control)
    }).filter(_._1 != -1).sortBy(_._1)
    pane.setTabList(tabData.map(x => x._2).toArray)
    pane.layout()
    pane
  }
}
object GridNode {
  def apply(components: Seq[GridComponent], newLayout: () => GridLayout): GridNode = {
    val constraints = components.map(c => c -> c.newConstraints()).toMap

    val numColumns = components.view.map(c => c.x + constraints(c).horizontalSpan).max
    val numRows    = components.view.map(c => c.y + constraints(c).verticalSpan  ).max

    val used = new Array[Boolean](numColumns * numRows)
    def use(x: Int, y: Int) = used(x * numRows + y) = true
    def isUsed(x: Int, y: Int) = used(x * numRows + y)

    val tabOrder = components.sortBy(_.tabOrder).zipWithIndex.toMap
    val componentMap = components.map(c => (c.x, c.y) -> c).toMap
    val componentList = new mutable.ArrayBuffer[ComputedGridComponent]
    for(y <- 0 until numRows; x <- 0 until numColumns) componentMap.get((x, y)) match {
      case Some(c) =>
        for(x <- c.x until c.x + constraints(c).horizontalSpan;
            y <- c.y until c.y + constraints(c).verticalSpan  ) {
          if(isUsed(x, y)) throw EditorException("Overlapping components in Grid!")
          use(x, y)
        }
        componentList += ComputedGridComponent(c.component, tabOrder(c), c.newConstraints)
      case None =>
        if(!isUsed(x, y)) {
          componentList += ComputedGridComponent(SpacerNode, -1, () => new GridData())
          use(x, y)
        }
    }

    new GridNode(componentList.toSeq, () => {
      val layout = newLayout()
      layout.numColumns = numColumns
      layout
    })
  }
}