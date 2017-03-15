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

package moe.lymia.princess.editor.controls

import java.awt.{GridBagConstraints, GridBagLayout}
import javax.swing._

import moe.lymia.princess.editor.data._
import moe.lymia.princess.lua.Lua
import rx._

case class LabelNode(label: String) extends ControlNode {
  override def createComponent(implicit ctx: NodeContext, owner: Ctx.Owner): JComponent = new JLabel(label)
}

private class VisibilityPane(ctx: NodeContext, isVisible: Rx[Any], contents: JComponent)
                            (implicit owner: Ctx.Owner) extends JPanel {
  add(contents)
  val obs = isVisible.foreach { b => SwingUtilities.invokeLater { () => setVisible(Lua.isBoolean(b)) } }
}
case class VisibilityNode(isVisible: FieldNode, contents: ControlNode) extends ControlNode {
  override def createComponent(implicit ctx: NodeContext, owner: Ctx.Owner): JComponent =
    new VisibilityPane(ctx, ctx.activateNode(isVisible), contents.createComponent)
}

case class GridBagComponent(component: ControlNode, contraints: GridBagConstraints)
case class GridNode(components: Seq[GridBagComponent]) extends ControlNode {
  override def createComponent(implicit ctx: NodeContext, owner: Ctx.Owner): JComponent = {
    val panel = new JPanel()
    panel.setLayout(new GridBagLayout)
    for(GridBagComponent(c, constraints) <- components) panel.add(c.createComponent, constraints)
    panel
  }
}