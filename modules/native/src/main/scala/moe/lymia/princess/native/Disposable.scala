package moe.lymia.princess.native

import java.util.concurrent.atomic.AtomicBoolean

private[native] trait Disposable {
  protected def rawDispose(): Unit
  private val isDisposed = new AtomicBoolean(false)

  def dispose(): Unit = {
    if (isDisposed.getAndSet(true)) {
      throw new NativeException(s"Attempt to dispose '${getClass.getSimpleName}' twice!")
    }
    rawDispose()
  }

  override def finalize(): Unit = {
    super.finalize()
    if (!isDisposed.getAndSet(true)) rawDispose()
  }
}
