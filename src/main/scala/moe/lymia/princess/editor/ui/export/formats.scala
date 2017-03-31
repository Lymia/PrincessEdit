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

package moe.lymia.princess.editor.ui.export

import java.awt.Color
import java.awt.image.BufferedImage
import java.nio.file.{Files, Path}
import javax.imageio._
import javax.imageio.metadata.IIOMetadata
import javax.imageio.plugins.jpeg.JPEGImageWriteParam

import moe.lymia.princess.editor.ui.mainframe.MainFrameState
import moe.lymia.princess.editor.utils.Message
import moe.lymia.princess.rasterizer.SVGRasterizer
import moe.lymia.princess.renderer.SVGData
import moe.lymia.princess.util.VersionInfo
import org.eclipse.jface.window.IShellProvider
import org.eclipse.swt.SWT
import org.eclipse.swt.graphics.ImageLoader
import org.eclipse.swt.layout.{GridData, GridLayout}
import org.eclipse.swt.widgets._

import scala.collection.JavaConverters._

sealed trait ExportControl[Options] {
  def getResult: Option[Options]
}
sealed trait ExportFormat[Options, InitData] {
  val displayName: String
  val extension: Seq[String]

  def initRender(state: MainFrameState) : InitData
  def finalizeRender(init: InitData)

  val hasControls: Boolean = true
  def nullOptions: Options = sys.error("no default control")
  def makeControl(parentShell: IShellProvider, parent: Composite, style: Int,
                  main: MainFrameState): ExportControl[Options]
  def export(svg: SVGData, options: Options, init: InitData, out: Path)

  def addExtension(str: String) = {
    val split = str.split("\\.")
    if(split.length > 1 && extension.contains(split.last)) str
    else s"$str.${extension.head}"
  }
}

sealed case class SimpleRasterInit(rasterizer: SVGRasterizer, image: ImageLoader)
final case class SimpleRasterOptions(dpi: Double, quality: Int)
sealed abstract class SimpleRasterExport private[export] (val displayName: String, val extension: Seq[String])
  extends ExportFormat[SimpleRasterOptions, SimpleRasterInit] {

  override def initRender(state: MainFrameState): SimpleRasterInit =
    SimpleRasterInit(state.ctx.createRasterizer(), new ImageLoader)
  override def finalizeRender(state: SimpleRasterInit): Unit =
    state.rasterizer.dispose()

  protected val useQualityControl = false
  protected val defaultQuality = 3

  override def makeControl(parentShell: IShellProvider, parent: Composite, style: Int, state: MainFrameState) = {
    val dpiLabel = new Label(parent, SWT.NONE)
    dpiLabel.setText(state.i18n.system("_princess.export.dpi"))
    dpiLabel.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false))

    val dpiField = new Text(parent, SWT.SINGLE | SWT.BORDER)
    dpiField.setText("150")
    val dpiData = new GridData(SWT.FILL, SWT.CENTER, true, false)
    dpiData.horizontalSpan = 2
    dpiField.setLayoutData(dpiData)

    var qualityField: Option[Text] = None
    if(useQualityControl) {
      val qualityLabel = new Label(parent, SWT.NONE)
      qualityLabel.setText(state.i18n.system("_princess.export.quality"))
      qualityLabel.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false))

      val qualityField0 = new Text(parent, SWT.SINGLE | SWT.BORDER)
      qualityField0.setText(defaultQuality.toString)
      val qualityData = new GridData(SWT.FILL, SWT.CENTER, true, false)
      qualityData.horizontalSpan = 2
      qualityField0.setLayoutData(qualityData)

      qualityField = Some(qualityField0)
    }

    new ExportControl[SimpleRasterOptions] {
      override def getResult: Option[SimpleRasterOptions] = {
        val dpi = try {
          val dpi = dpiField.getText.toDouble
          if(dpi <= 0) throw new NumberFormatException
          dpi
        } catch {
          case e: NumberFormatException =>
            Message.open(parentShell, SWT.ICON_ERROR | SWT.OK, state.i18n, "_princess.export.dpiNotNumber")
            return None
        }

        val quality = try {
          val quality = qualityField.map(_.getText.toInt).getOrElse(defaultQuality)
          if(quality < 1 || quality > 100) throw new NumberFormatException
          quality
        } catch {
          case e: NumberFormatException =>
            Message.open(parentShell, SWT.ICON_ERROR | SWT.OK, state.i18n, "_princess.export.qualityNotNumber")
            return None
        }

        Some(SimpleRasterOptions(dpi, quality))
      }
    }
  }

  protected val preferredImplementation: Set[String] = Set()
  private def newImageWriter() = {
    val writers = ImageIO.getImageWritersBySuffix(extension.head).asScala.toSeq
    writers.find(x => preferredImplementation.contains(x.getClass.getName)).getOrElse(writers.head)
  }

  protected def scaleDPI(i: Double, implName: String) = (1 / i) * 25.4
  protected def newParams(writer: ImageWriter, options: SimpleRasterOptions) = writer.getDefaultWriteParam

  protected def makeFormatMetadata(metadata: IIOMetadata, options: SimpleRasterOptions) = { }
  private def makeMetadata(metadata: IIOMetadata, options: SimpleRasterOptions, implName: String) = {
    import javax.imageio.metadata.IIOMetadataNode
    val xDpi = new IIOMetadataNode("HorizontalPixelSize")
    xDpi.setAttribute("value", scaleDPI(options.dpi, implName).toString)

    val yDpi = new IIOMetadataNode("VerticalPixelSize")
    yDpi.setAttribute("value", scaleDPI(options.dpi, implName).toString)

    val dim = new IIOMetadataNode("Dimension")
    dim.appendChild(xDpi)
    dim.appendChild(yDpi)

    val textEntry = new IIOMetadataNode("TextEntry")
    textEntry.setAttribute("keyword", s"Software")
    textEntry.setAttribute("value", s"Rendered by PrincessEdit v${VersionInfo.versionString}")

    val text = new IIOMetadataNode("Text")
    text.appendChild(textEntry)

    val root = new IIOMetadataNode("javax_imageio_1.0")
    root.appendChild(dim)
    root.appendChild(text)

    metadata.mergeTree("javax_imageio_1.0", root)

    makeFormatMetadata(metadata, options)
  }

  protected def prepareImage(image: BufferedImage) = image
  override def export(svg: SVGData, options: SimpleRasterOptions, state: SimpleRasterInit, out: Path): Unit = {
    val (x, y) = svg.bestSizeForDPI(options.dpi)
    val image = prepareImage(svg.rasterizeAwt(state.rasterizer, x, y))

    val imageWriter = newImageWriter()
    val param = newParams(imageWriter, options)
    val metadata = imageWriter.getDefaultImageMetadata(ImageTypeSpecifier.createFromRenderedImage(image), param)
    makeMetadata(metadata, options, imageWriter.getClass.getName)

    imageWriter.setOutput(ImageIO.createImageOutputStream(Files.newOutputStream(out)))
    imageWriter.write(metadata, new IIOImage(image, null, metadata), param)
  }
}

// TODO: Check if com.sun.imageio.plugins.jpeg.JPEGImageWriter and com.sun.imageio.plugins.png.PNGImageWriter are still broken

object ExportFormat {
  case object PNG  extends SimpleRasterExport("_princess.export.png", Seq("png")) {
    private val oracleImpl = "com.sun.imageio.plugins.png.PNGImageWriter"

    override protected val preferredImplementation: Set[String] = Set(oracleImpl)
    override protected def scaleDPI(i: Double, implName: String): Double = {
      val result = super.scaleDPI(i, implName)
      if(implName == oracleImpl) 1 / result else result
    }
  }
  case object JPEG extends SimpleRasterExport("_princess.export.jpeg", Seq("jpg", "jpeg", "jpe")) {
    private val oracleImpl = "com.sun.imageio.plugins.jpeg.JPEGImageWriter"

    override protected val preferredImplementation: Set[String] = Set(oracleImpl)
    override protected def scaleDPI(i: Double, implName: String): Double = {
      val result = super.scaleDPI(i, implName)
      if(implName == oracleImpl) result / 100 else result
    }

    override val useQualityControl = true
    override val defaultQuality = 90

    override protected def prepareImage(image: BufferedImage): BufferedImage = {
      val tx = new BufferedImage(image.getWidth, image.getHeight, BufferedImage.TYPE_3BYTE_BGR)
      val g = tx.getGraphics
      g.setColor(Color.WHITE)
      g.fillRect(0, 0, image.getWidth, image.getHeight)
      g.drawImage(image, 0, 0, null)
      tx
    }

    override protected def newParams(writer: ImageWriter, options: SimpleRasterOptions): ImageWriteParam = {
      val params = new JPEGImageWriteParam(null)
      params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT)
      params.setCompressionQuality(options.quality.toFloat / 100f)
      params
    }
  }
  case object SVG  extends ExportFormat[Unit, Unit] {
    override val displayName: String = "_princess.export.svg"
    override val extension: Seq[String] = Seq("svg")

    override def initRender(state: MainFrameState): Unit = ()
    override def finalizeRender(init: Unit): Unit = ()

    override val hasControls: Boolean = false
    override def nullOptions = ()
    override def makeControl(parentShell: IShellProvider, parent: Composite, style: Int, main: MainFrameState) =
      new ExportControl[Unit] {
        override def getResult: Option[Unit] = Some(())
      }

    override def export(svg: SVGData, options: Unit, init: Unit, out: Path): Unit =
      svg.write(Files.newBufferedWriter(out))
  }

  val formats: Seq[ExportFormat[_, _]] = Seq(PNG, JPEG, SVG)
}