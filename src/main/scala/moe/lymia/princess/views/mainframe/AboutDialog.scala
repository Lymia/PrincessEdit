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

package moe.lymia.princess.views.mainframe

import com.coconut_palm_software.xscalawt.XScalaWT._
import moe.lymia.princess.VersionInfo
import moe.lymia.princess.util.swt.{DialogBase, IconData, UIUtils, WithResources}
import org.eclipse.jface.window.IShellProvider
import org.eclipse.swt.SWT
import org.eclipse.swt.layout.GridData
import org.eclipse.swt.widgets.Composite

import java.text.DateFormat

class AboutDialog(parent: IShellProvider, state: MainFrameState)
  extends DialogBase(parent, state.ctx) with WithResources {

  private val dateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM, state.i18n.locale)
  override protected def frameContents(frame: Composite): Unit = frame.contains (
    gridLayout(2)(),
    label(
      resourceManager.createImage(IconData.AppIcon.imageData(48)),
      _.layoutData = new GridData(SWT.CENTER, SWT.CENTER, false, false)
    ),
    composite(
      gridLayout()(),
      label(
        state.i18n.system("_princess.main.about.header_1", VersionInfo.versionString),
        UIUtils.fontStyle(SWT.BOLD),
      _.layoutData = new GridData(SWT.FILL, SWT.CENTER, false, true)
      ),
      label(
        state.i18n.system("_princess.main.about.header_2",
          VersionInfo.commit.substring(0, 8),
          if(VersionInfo.isDirty) state.i18n.system("_princess.main.about.isDirty") else "",
          dateFormat.format(VersionInfo.buildDate),
          VersionInfo.buildUser),
      _.layoutData = new GridData(SWT.FILL, SWT.CENTER, false, true)
      ),
      _.layoutData = new GridData(SWT.FILL, SWT.CENTER, false, true)
    )
  )
}
