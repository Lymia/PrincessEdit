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
  private val soName = sys.props("os.name") match {
    case null => None
    case x if x.startsWith("Windows ") => Some(???)
    case x if x.startsWith("Mac ") => Some(???)
    case "Linux" => Some(("native/x86_64-linux/libprincessedit_rendering.so", ".so"))
    case _ => None
  }
  private val resourceHash = DigestUtils.sha256Hex(Resources.getInputStream(soName.get._1).readAllBytes())
  @volatile private var loaded = false

  def loadInDirectory(cacheDir: Path): Unit = this synchronized {
    if (!loaded) {
      val targetLib = cacheDir.resolve(resourceHash + soName.get._2)
      if (!Files.exists(targetLib)) Files.write(targetLib, Resources.getInputStream(soName.get._1).readAllBytes())
      System.load(targetLib.toFile.toString)
    }
    loaded = true
  }
}
