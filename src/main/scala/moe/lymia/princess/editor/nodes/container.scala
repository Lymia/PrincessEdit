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

package moe.lymia.princess.editor.nodes

import moe.lymia.princess.core.EditorException
import moe.lymia.princess.lua.Lua
import org.eclipse.swt.SWT
import org.eclipse.swt.layout._
import org.eclipse.swt.widgets._
import rx._

import scala.collection.mutable

case class LabelNode(text: String) extends ControlNode {
  override def createControl(parent: Composite)(implicit ctx: NodeContext, uiCtx: UIContext, owner: Ctx.Owner) = {
    val label = new Label(parent, SWT.NONE)
    label.setText(text)
    label
  }
}

case object SpacerNode extends ControlNode {
  override def createControl(parent: Composite)(implicit ctx: NodeContext, uiCtx: UIContext, owner: Ctx.Owner) =
    new Label(parent, SWT.NONE)
}

case class VisibilityNode(isVisible: FieldNode, contents: ControlNode) extends ControlNode {
  override def createControl(parent: Composite)(implicit ctx: NodeContext, uiCtx: UIContext, owner: Ctx.Owner) = {
    val container = new Composite(parent, SWT.NONE)
    container.setLayout(new GridLayout)

    val widget = contents.createControl(container)
    val data = new GridData(SWT.FILL, SWT.FILL, true, true)
    widget.setLayoutData(data)

    val isVisibleRx = ctx.activateNode(isVisible)
    isVisibleRx.map(Lua.toBoolean).foreach { b => ctx.controlCtx.asyncUiExec {
      data.exclude = !b
      widget.setVisible(b)
      container.layout(true)
    }}

    container
  }
}

case class GridComponent(component: ControlNode, x: Int, y: Int, newConstraints: () => GridData)
private case class ComputedGridComponent(component: ControlNode, newConstraints: () => GridData)
class GridNode private (data: Seq[ComputedGridComponent], newLayout: () => GridLayout) extends ControlNode {
  override def createControl(parent: Composite)(implicit ctx: NodeContext, uiCtx: UIContext, owner: Ctx.Owner) = {
    val pane = new Composite(parent, SWT.NONE)
    pane.setLayout(newLayout())
    for(component <- data) component.component.createControl(pane).setLayoutData(component.newConstraints())
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

    val componentMap = components.map(c => (c.x, c.y) -> c).toMap
    val componentList = new mutable.ArrayBuffer[ComputedGridComponent]
    for(y <- 0 until numRows; x <- 0 until numColumns) componentMap.get((x, y)) match {
      case Some(c) =>
        for(x <- c.x until c.x + constraints(c).horizontalSpan;
            y <- c.y until c.y + constraints(c).verticalSpan  ) {
          if(isUsed(x, y)) throw EditorException("Overlapping components in Grid!")
          use(x, y)
        }
        componentList += ComputedGridComponent(c.component, c.newConstraints)
      case None =>
        if(!isUsed(x, y)) {
          componentList += ComputedGridComponent(SpacerNode, () => new GridData())
          use(x, y)
        }
    }

    new GridNode(componentList, () => {
      val layout = newLayout()
      layout.numColumns = numColumns
      layout
    })
  }
}