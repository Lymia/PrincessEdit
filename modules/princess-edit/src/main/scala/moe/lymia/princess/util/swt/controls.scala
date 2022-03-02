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

package moe.lymia.princess.util.swt

import org.eclipse.swt.SWT
import org.eclipse.swt.events.{FocusEvent, FocusListener, SelectionEvent, SelectionListener}
import org.eclipse.swt.graphics.Point
import org.eclipse.swt.layout.FillLayout
import org.eclipse.swt.widgets._

final class HelpButton(parent: Composite, style: Int) extends Composite(parent, style) {
  setLayout(new FillLayout())

  private val button = new Button(this, SWT.PUSH)
  button.setText("?")

  private val balloon = new ToolTip(this.getShell, SWT.BALLOON | SWT.ICON_INFORMATION)
  balloon.setVisible(false)
  button.addSelectionListener(new SelectionListener {
    override def widgetSelected(selectionEvent: SelectionEvent): Unit = {
      balloon.setLocation(Display.getCurrent.getCursorLocation)
      balloon.setVisible(true)
    }
    override def widgetDefaultSelected(selectionEvent: SelectionEvent): Unit = { }
  })
  button.addFocusListener(new FocusListener {
    override def focusGained(focusEvent: FocusEvent): Unit = { }
    override def focusLost(focusEvent: FocusEvent): Unit = balloon.setVisible(false)
  })

  def setBalloonText(s: String): Unit = balloon.setText(s)
  def setBalloonMessage(s: String): Unit = balloon.setMessage(s)

  private def makeSquare(p: Point) = new Point(p.y, p.y)
  override def computeSize(wHint: Int, hHint: Int): Point =
    makeSquare(super.computeSize(wHint, hHint))
  override def computeSize(wHint: Int, hHint: Int, changed: Boolean): Point =
    makeSquare(super.computeSize(wHint, hHint, changed))
}