package moe.lymia.princess.native.svg

import moe.lymia.princess.native.fonts.FontDatabase

private class Resvg {
  @native def renderNative(input: String, resourcePath: String, fontDb: Int, w: Int, h: Int): Array[Byte]
}
object Resvg {
  private val instance = new Resvg
  def render(svg: String, resourcePath: Option[String], fontDb: FontDatabase, w: Int, h: Int): Array[Byte] =
    instance.renderNative(svg, resourcePath.orNull, fontDb.id, w, h)
}