package moe.lymia.princess.native.fonts

import moe.lymia.princess.native.Disposable

import java.nio.file.{Files, Path}

class FontDatabase extends Disposable {
  @native private def newNative(): Int
  @native private def deleteNative(id: Int): Unit

  @native private def addFontByDataNative(id: Int, data: Array[Byte]): Unit
  @native private def addFontByPathNative(id: Int, path: String): Unit
  @native private def loadSystemFontsNative(id: Int): Unit
  @native private def setDefaultNative(id: Int, family: Int, name: String): Unit

  private[native] val id = newNative()
  override protected def rawDispose(): Unit = deleteNative(id)

  def addFont(data: Array[Byte]): Unit = addFontByDataNative(id, data)
  def addFont(path: Path): Unit = {
    if (path.toUri.getScheme == "file") addFontByPathNative(id, path.toFile.toString)
    else addFontByDataNative(id, Files.readAllBytes(path))
  }

  def setDefaultFamily(family: StaticFontFamily, name: String): Unit = family match {
    case FontFamily.Serif => setDefaultNative(id, 0, name)
    case FontFamily.SansSerif => setDefaultNative(id, 1, name)
    case FontFamily.Cursive => setDefaultNative(id, 2, name)
    case FontFamily.Fantasy => setDefaultNative(id, 3, name)
    case FontFamily.Monospace => setDefaultNative(id, 4, name)
  }

  def serifFamily_=(name: String): Unit = setDefaultNative(id, 0, name)
  def sansSerifFamily_=(name: String): Unit = setDefaultNative(id, 1, name)
  def cursiveFamily_=(name: String): Unit = setDefaultNative(id, 2, name)
  def fantasyFamily_=(name: String): Unit = setDefaultNative(id, 3, name)
  def monospaceFamily_=(name: String): Unit = setDefaultNative(id, 4, name)
}
object FontDatabase {
  val System: FontDatabase = {
    val db = new FontDatabase
    db.loadSystemFontsNative(db.id)
    db
  }
}

sealed trait FontFamily
sealed trait StaticFontFamily extends FontFamily
object FontFamily {
  case class Name(name: String) extends FontFamily
  case object Serif extends StaticFontFamily
  case object SansSerif extends StaticFontFamily
  case object Cursive extends StaticFontFamily
  case object Fantasy extends StaticFontFamily
  case object Monospace extends StaticFontFamily
}

case class FontWeight(weight: Int)
object FontWeight {
  val Tiny: FontWeight = FontWeight(100)
  val ExtraLight: FontWeight = FontWeight(200)
  val Light: FontWeight = FontWeight(300)
  val Normal: FontWeight = FontWeight(400)
  val Medium: FontWeight = FontWeight(500)
  val SemiBold: FontWeight = FontWeight(600)
  val Bold: FontWeight = FontWeight(700)
  val ExtraBold: FontWeight = FontWeight(800)
  val Black: FontWeight = FontWeight(900)
}

sealed trait FontStyle
object FontStyle {
  case object Normal extends FontStyle
  case object Italic extends FontStyle
  case object Oblique extends FontStyle
}

class FontDatabaseQuery extends Disposable {
  @native private def newNative(): Int
  @native private def deleteNative(id: Int): Unit

  @native private def addFamilyNative(id: Int, name: String): Unit
  @native private def addStaticFamilyNative(id: Int, family: Int): Unit
  @native private def setWeightNative(id: Int, weight: Int): Unit
  @native private def setStyleNative(id: Int, style: Int): Unit

  private[native] val id = newNative()
  override protected def rawDispose(): Unit = deleteNative(id)

  def addFamily(name: String): Unit = addFamilyNative(id, name)
  def addFamily(family: FontFamily): Unit = family match {
    case FontFamily.Name(name) => addFamilyNative(id, name)
    case FontFamily.Serif => addStaticFamilyNative(id, 0)
    case FontFamily.SansSerif => addStaticFamilyNative(id, 1)
    case FontFamily.Cursive => addStaticFamilyNative(id, 2)
    case FontFamily.Fantasy => addStaticFamilyNative(id, 3)
    case FontFamily.Monospace => addStaticFamilyNative(id, 4)
  }

  def weight_=(weight: FontWeight): Unit = setWeightNative(id, weight.weight)
  def weight_=(weight: Int): Unit = setWeightNative(id, weight)

  def style_=(style: FontStyle): Unit = style match {
    case FontStyle.Normal => setStyleNative(id, 0)
    case FontStyle.Italic => setStyleNative(id, 1)
    case FontStyle.Oblique => setStyleNative(id, 2)
  }
}
