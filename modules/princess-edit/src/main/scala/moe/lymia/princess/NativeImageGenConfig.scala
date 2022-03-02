package moe.lymia.princess

import moe.lymia.princess.core.state.GuiContext
import moe.lymia.princess.views.exportcards.{ExportCardsDialogMulti, ExportFormat}
import moe.lymia.princess.views.editor.{EditorTab, EditorTabData}
import moe.lymia.princess.views.frontend.{GameSelectorDialog, SplashScreen}
import moe.lymia.princess.views.mainframe.{AboutDialog, MainFrame}
import org.eclipse.jface.window.Window
import org.eclipse.swt.program.Program
import rx.Rx

class NativeImageGenConfig(ctx: GuiContext) {
  private val logger = DefaultLogger.bind("NativeImageGenConfig")

  private def executeForMs(duration: Long): Unit = {
    val end = System.currentTimeMillis() + duration
    while (System.currentTimeMillis() < end) {
      ctx.display.readAndDispatch
    }
  }
  private def openForPeriod(w: Window): Unit = {
    w.setBlockOnOpen(false)
    logger.info(s"Opening window of type '${w.getClass.getSimpleName}'")
    w.open()
    logger.info(s"(Done!)")
    executeForMs(750)
    logger.info(s"(Closing!)")
    w.close()
  }

  def execute(): Unit = {
    // frontend
    openForPeriod(new SplashScreen(ctx))
    openForPeriod(new GameSelectorDialog(null, ctx))

    // initial mainframe
    val (mainFrame, mainFrameState) = MainFrame.openForGenConfig(ctx)
    mainFrame.setBlockOnOpen(false)
    mainFrame.open()

    openForPeriod(new AboutDialog(null, mainFrameState))

    // test cards
    val (viewUuid, testCards) = ctx.syncLuaExec {
      val viewUuid = mainFrameState.project.views.create()._1
      val testCards = (0 until 10).map(_ => {
        val uuid = mainFrameState.project.cards.create()._1
        mainFrameState.project.allViews.now(viewUuid).addCard(uuid)
        uuid
      })
      logger.info(s"Created test cards: $testCards")
      (viewUuid, testCards)
    }
    mainFrame.tabFolder.clearTabs()
    val editorApi = mainFrameState.openTab(EditorTab, new EditorTabData(viewUuid))
    mainFrame.tabFolder.updateTabItems()
    executeForMs(500)
    editorApi.setSelectedCards(testCards : _*)
    executeForMs(500)

    // test export dialog
    val multiDialog = new ExportCardsDialogMulti(mainFrameState, ctx.syncLuaExec {
      Rx.unsafe {
        testCards.map(uuid => {
          val full = mainFrameState.project.views.get(viewUuid).get.getFullCard(uuid).get
          (uuid, full)
        })
      }.now
    })
    multiDialog.setBlockOnOpen(false)
    multiDialog.open()
    executeForMs(500)
    multiDialog.setFormat(ExportFormat.JPEG)
    executeForMs(500)
    multiDialog.close()

    // close mainframe
    mainFrame.close()
  }
}
