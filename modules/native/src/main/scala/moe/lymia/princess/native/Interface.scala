package moe.lymia.princess.native

import com.github.sbt.jni.nativeLoader

import java.nio.ByteBuffer

@nativeLoader("princessedit_rendering")
private[native] class Interface private () {
  // resvg
  @native def resvgRender(input: String, resourcePath: String, fontDb: Int, w: Int, h: Int): Array[Byte]

  // font database
  @native def fontDatabaseNew(): Int
  @native def fontDatabaseDelete(id: Int): Unit
}
object Interface extends Interface
