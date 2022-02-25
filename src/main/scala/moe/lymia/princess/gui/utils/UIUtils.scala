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

package moe.lymia.princess.gui.utils

import com.coconut_palm_software.xscalawt.XScalaWTStyles._
import moe.lymia.princess.core.gamedata.I18N
import moe.lymia.princess.svg.SVGFile
import moe.lymia.princess.util.IOUtils
import org.eclipse.jface.dialogs.MessageDialog
import org.eclipse.jface.resource.FontDescriptor
import org.eclipse.jface.window.IShellProvider
import org.eclipse.swt.SWT
import org.eclipse.swt.graphics.Point
import org.eclipse.swt.widgets.{Control, MessageBox}

import java.nio.file.{Files, Path}
import scala.xml.XML

object UIUtils {
  def openMessage(shell: IShellProvider, style: Int, i18n: I18N, root: String, args: Any*) = {
    val messageBox = new MessageBox(if(shell ne null) shell.getShell else null, style)
    messageBox.setText(i18n.system(s"$root.title", args : _*))
    messageBox.setMessage(i18n.system(s"$root.message", args : _*))
    messageBox.open()
  }

  def openMessage(shell: IShellProvider, icon: Int, i18n: I18N, options: Seq[String], defaultOption: Int,
                  root: String, args: Any*) = {
    val messageDialog =
      new MessageDialog(if(shell ne null) shell.getShell else null,
                        i18n.system(s"$root.title", args : _*), null, i18n.system(s"$root.message", args : _*),
                        icon, options.map(x => i18n.system(x, args : _*)).toArray, defaultOption)
    messageDialog.setBlockOnOpen(true)
    messageDialog.open()
  }

  def computeSizeFromRatio(canvasSize: Point, width: Double, height: Double) = {
    val (cx, cy) = (if(canvasSize.x == 0) 1 else canvasSize.x, if(canvasSize.y == 0) 1 else canvasSize.y)
    val size = (cx, math.round((cx.toDouble / width) * height).toInt)
    if(size._2 > cy) (math.round((cy.toDouble / height) * width).toInt, cy)
    else size
  }

  def loadSVGFromResource(res: String) = SVGFile(XML.load(IOUtils.getResource(res)))
  def loadSVGFromPath(path: Path) = SVGFile(XML.load(Files.newInputStream(path)))

  // XScalaWT compatible
  def listBackground(c: Control) = c.setBackground(c.getDisplay.getSystemColor(SWT.COLOR_LIST_BACKGROUND))
  object listBackgroundStyle extends Stylesheet ($[Control](listBackground))

  def fontStyle(style: Int)(c: Control) =
    c.setFont(FontDescriptor.createFrom(c.getFont).setStyle(style).createFont(c.getDisplay))
}
