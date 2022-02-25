package moe.lymia.princess.native

object Resvg {
  def render(svg: String, resourcePath: Option[String], fontDb: FontDatabase, w: Int, h: Int): Array[Byte] =
    Interface.resvgRender(svg, resourcePath.orNull, fontDb.id, w, h)
}