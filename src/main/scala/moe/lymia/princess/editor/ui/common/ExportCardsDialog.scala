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

package moe.lymia.princess.editor.ui.common

import moe.lymia.princess.editor.core._
import moe.lymia.princess.editor.ui.mainframe.MainFrameState
import moe.lymia.princess.editor.utils.DialogBase
import moe.lymia.princess.renderer.SVGData

import org.eclipse.jface.window.IShellProvider
import org.eclipse.swt.layout._
import org.eclipse.swt.widgets._

import rx._

final class ExportCardsDialog(parent: IShellProvider, state: MainFrameState,
                              exportTargets: Seq[CardData]) extends DialogBase(parent, state.ctx) {
  private val renderedImages = Var[Option[Seq[SVGData]]](None)

  override protected def frameContents(frame: Composite): Unit = {

  }

  override def open(): Int = {

    super.open()
  }
}
