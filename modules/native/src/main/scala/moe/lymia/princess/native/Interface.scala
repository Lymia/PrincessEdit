package moe.lymia.princess.native

import org.apache.commons.codec.Resources
import org.apache.commons.codec.digest.DigestUtils

import java.nio.file.{Files, Path}

private[native] class Interface private () {
  // resvg
  @native def resvgRender(input: String, resourcePath: String, fontDb: Int, w: Int, h: Int): Array[Byte]

  // font database
  @native def fontDatabaseNew(): Int
  @native def fontDatabaseDelete(id: Int): Unit
}
private[native] object Interface extends Interface {
  private val (soName, soExt) = sys.props("os.name") match {
    case null => sys.error("Cannot detect operating system")
    case x if x.startsWith("Windows ") => ("princessedit_native.x86_64", ".dll")
    case x if x.startsWith("Mac ") => ("libprincessedit_native.x86_64", ".dylib")
    case "Linux" => ("libprincessedit_native.x86_64", ".so")
    case _ => sys.error("Incompatible operating system")
  }

  private val soPath = s"moe/lymia/princess/native/$soName$soExt"
  private val resourceHash = DigestUtils.sha256Hex(Resources.getInputStream(soPath).readAllBytes())
  private val versionedName = s"$soName.${resourceHash.substring(0, 10)}$soExt"

  @volatile private var loaded = false

  def loadInDirectory(cacheDir: Path): Unit = this synchronized {
    if (!loaded) {
      val targetLib = cacheDir.resolve(versionedName)
      if (!Files.exists(targetLib)) Files.write(targetLib, Resources.getInputStream(soPath).readAllBytes())
      System.load(targetLib.toFile.toString)
    }
    loaded = true
  }
}
