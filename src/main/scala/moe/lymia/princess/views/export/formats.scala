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

package moe.lymia.princess.views.`export`

import com.coconut_palm_software.xscalawt.XScalaWT._
import moe.lymia.princess.core.state.GuiContext
import moe.lymia.princess.gui.utils.UIUtils
import moe.lymia.princess.svg.SVGData
import moe.lymia.princess.views.mainframe.MainFrameState
import org.eclipse.jface.layout.GridDataFactory
import org.eclipse.jface.window.IShellProvider
import org.eclipse.swt.SWT
import org.eclipse.swt.graphics.ImageLoader
import org.eclipse.swt.layout.GridData
import org.eclipse.swt.widgets._

import java.nio.file.{Files, Path}

sealed trait ExportControl[Options] {
  def getResult: Option[Options]
}
sealed trait ExportFormat[Options, InitData] {
  val displayName: String
  val extension: Seq[String]

  def initRender(state: MainFrameState) : InitData

  val hasControls: Boolean = true
  def nullOptions: Options = sys.error("no default control")
  def makeControl(parentShell: IShellProvider, parent: Composite, style: Int,
                  main: MainFrameState): ExportControl[Options]
  def export(svg: SVGData, options: Options, init: InitData, out: Path): Unit

  def addExtension(str: String): String = {
    val split = str.split("\\.")
    if(split.length > 1 && extension.contains(split.last)) str
    else s"$str.${extension.head}"
  }
}

sealed case class SimpleRasterInit(ctx: GuiContext, image: ImageLoader)
final case class SimpleRasterOptions(dpi: Double, quality: Int)
sealed abstract class SimpleRasterExport private[export] (val displayName: String, val extension: Seq[String])
  extends ExportFormat[SimpleRasterOptions, SimpleRasterInit] {

  override def initRender(state: MainFrameState): SimpleRasterInit =
    SimpleRasterInit(state.ctx, new ImageLoader)

  protected val useQualityControl = false
  protected val defaultQuality = 3

  override def makeControl(parentShell: IShellProvider, parent: Composite, style: Int,
                           state: MainFrameState): ExportControl[SimpleRasterOptions] = {
    var dpiField: Text = null

    parent.contains(
      label(
        state.i18n.system("_princess.export.dpi"),
        _.layoutData = new GridData(SWT.BEGINNING, SWT.CENTER, false, false)
      ),
      *[Text](SWT.SINGLE | SWT.BORDER)(
        dpiField = _,
        _.text = "150",
        _.layoutData =
          GridDataFactory.createFrom(new GridData(SWT.FILL, SWT.CENTER, true, false)).span(2, 1).create()
      )
    )

    var qualityField: Option[Text] = None
    if(useQualityControl) parent.contains(
      label(
        state.i18n.system("_princess.export.quality"),
        _.layoutData = new GridData(SWT.BEGINNING, SWT.CENTER, false, false)
      ),
      *[Text](SWT.SINGLE | SWT.BORDER)(
        x => qualityField = Some(x),
        _.text = defaultQuality.toString,
        _.layoutData =
          GridDataFactory.createFrom(new GridData(SWT.FILL, SWT.CENTER, true, false)).span(2, 1).create()
      )
    )

    new ExportControl[SimpleRasterOptions] {
      override def getResult: Option[SimpleRasterOptions] = {
        val dpi = try {
          val dpi = dpiField.getText.toDouble
          if(dpi <= 0) throw new NumberFormatException
          dpi
        } catch {
          case _: NumberFormatException =>
            UIUtils.openMessage(parentShell, SWT.ICON_ERROR | SWT.OK, state.i18n, "_princess.export.dpiNotNumber")
            return None
        }

        val quality = try {
          val quality = qualityField.map(_.getText.toInt).getOrElse(defaultQuality)
          if(quality < 1 || quality > 100) throw new NumberFormatException
          quality
        } catch {
          case _: NumberFormatException =>
            UIUtils.openMessage(parentShell, SWT.ICON_ERROR | SWT.OK, state.i18n, "_princess.export.qualityNotNumber")
            return None
        }

        Some(SimpleRasterOptions(dpi, quality))
      }
    }
  }

  protected def compressionFromQuality(i: Int): Int = i
  protected def format: Int
  override def export(svg: SVGData, options: SimpleRasterOptions, state: SimpleRasterInit, outPath: Path): Unit = {
    val (x, y) = svg.bestSizeForDPI(options.dpi)
    val image = state.ctx.syncRender(svg.asSvg(), x, y)

    val imageWriter = new ImageLoader()
    imageWriter.data = Array(image)
    imageWriter.compression = compressionFromQuality(options.quality)

    val out = Files.newOutputStream(outPath)
    try {
      imageWriter.save(out, format)
    } finally {
      out.close()
    }
  }
}

object ExportFormat {
  case object PNG  extends SimpleRasterExport("_princess.export.png", Seq("png")) {
    protected override def compressionFromQuality(i: Int): Int = 2
    protected def format: Int = SWT.IMAGE_PNG
  }
  case object JPEG extends SimpleRasterExport("_princess.export.jpeg", Seq("jpg", "jpeg", "jpe")) {
    override val useQualityControl = true
    override val defaultQuality = 90
    protected def format: Int = SWT.IMAGE_JPEG
  }
  case object SVG  extends ExportFormat[Unit, Unit] {
    override val displayName: String = "_princess.export.svg"
    override val extension: Seq[String] = Seq("svg")

    override def initRender(state: MainFrameState): Unit = ()

    override val hasControls: Boolean = false
    override def nullOptions: Unit = ()
    override def makeControl(parentShell: IShellProvider, parent: Composite, style: Int, main: MainFrameState): ExportControl[Unit] =
      new ExportControl[Unit] {
        override def getResult: Option[Unit] = Some(())
      }

    override def export(svg: SVGData, options: Unit, init: Unit, out: Path): Unit = {
      val writer = Files.newBufferedWriter(out)
      try {
        svg.write(writer)
      } finally {
        writer.close()
      }
    }
  }

  val formats: Seq[ExportFormat[_, _]] = Seq(PNG, JPEG, SVG)
}