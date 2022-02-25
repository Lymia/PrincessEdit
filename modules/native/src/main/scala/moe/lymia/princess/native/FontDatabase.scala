package moe.lymia.princess.native

class FontDatabase extends Disposable {
  private[native] val id = Interface.fontDatabaseNew()
  override protected def rawDispose(): Unit = Interface.fontDatabaseDelete(id)
}
