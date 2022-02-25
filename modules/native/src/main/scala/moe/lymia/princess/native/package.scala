package moe.lymia.princess

import java.nio.file.Path

package object native {
  def loadNatives(cachePath: Path) = Interface.loadInDirectory(cachePath)
}
