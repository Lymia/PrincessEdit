/*
 * Copyright (c) 2017-2022 Lymia Alusyia <lymia@lymiahugs.com>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package moe.lymia.princess.views.mainframe

import moe.lymia.princess.core.state.SettingsKey
import moe.lymia.princess.util.Service
import moe.lymia.princess.util.swt.RxWidget
import org.eclipse.jface.action.MenuManager
import org.eclipse.swt.SWT
import org.eclipse.swt.custom.{CTabFolder, CTabFolder2Listener, CTabFolderEvent, CTabItem}
import org.eclipse.swt.events.{SelectionEvent, SelectionListener}
import org.eclipse.swt.layout.FillLayout
import org.eclipse.swt.widgets.{Composite, Control}
import play.api.libs.json._
import rx._

import java.util.UUID
import scala.collection.mutable

object TabDefs {
  type TabBase = Control with PrincessEditTab
}
import moe.lymia.princess.views.mainframe.TabDefs._

// TODO: Should we really use an UUID here?
abstract case class TabID[Data : Writes : Reads, TabClass <: TabBase, TabAPI](id: UUID) {
  def serialize(v: Data) = Json.toJson(v)
  def deserialize(js: JsValue): Data = js.as[Data]
  def extractData(tab: TabClass): TabAPI
}

trait PrincessEditTab { this: Control =>
  def addMenuItems(m: MenuManager)

  val isClosable = true

  val tabName: Rx[String]
  addDisposeListener(_ => tabName.kill())
}

trait TabType[Data, TabClass <: TabBase, TabAPI] {
  def createTab(parent: Composite, data: Data, state: MainFrameState): TabClass
}
trait TabProvider {
  private val ids = new mutable.HashMap[TabID[_, _, _], TabType[_, _, _]]
  protected def tabId[Data, TabClass <: TabBase, TabAPI](id: TabID[Data, TabClass, TabAPI])
                                                        (fn: (Composite, Data, MainFrameState) => TabClass) = {
    if(ids.contains(id)) sys.error(s"Duplicate TabID $id")
    ids.put(id, ((parent, data, state) => fn(parent, data, state)) : TabType[Data, TabClass, TabAPI])
  }
  private[mainframe] def getTabTypes: Map[TabID[_, _, _], TabType[_, _, _]] = ids.toMap
}

private[mainframe] case class TabData[Data, TabClass <: TabBase, TabAPI](tabID: TabID[Data, TabClass, TabAPI],
                                                                         data: Data, control: TabClass) {
  def serialize = Json.obj(
    "tabType" -> tabID.id,
    "parameters" -> tabID.serialize(data)
  )
}
private object TabData {
  def deserialize(parent: Composite,
                  js: JsValue, state: MainFrameState): TabData[_, _ <: TabBase, _] = {
    val uuid = (js \ "tabType").as[UUID]
    val id =
      MainTabFolder.getTabIDByUUID(uuid).getOrElse(sys.error(s"Unknown uuid $uuid"))
        .asInstanceOf[TabID[Any, TabBase, Any]]
    val t = MainTabFolder.getTabType(id).getOrElse(sys.error(s"unexpected error: inconsistent state"))
    val data = id.deserialize((js \ "parameters").as[JsValue])
    TabData(id, data, t.createTab(parent, data, state))
  }
}

private object MainTabFolder {
  private val tabDataId = "dd51f4c8-343c-11e7-80b1-3afa38669cf4"
  private val tabData = new SettingsKey[JsValue]("princess.openTabs")

  private lazy val (forUUID, forTabID) = {
    val forUUID = new mutable.HashMap[UUID, TabID[_, _, _]]
    val forTabID = new mutable.HashMap[TabID[_, _, _], TabType[_, _, _]]
    for(provider <- Service.get[TabProvider]; (id, t) <- provider.getTabTypes) {
      if(forUUID.contains(id.id)) sys.error(s"Duplicate UUID ${id.id}")
      if(forTabID.contains(id)) sys.error(s"Duplicate TabID $id")

      forUUID.put(id.id, id)
      forTabID.put(id, t)
    }
    (forUUID.toMap, forTabID.toMap)
  }

  def getTabIDByUUID(id: UUID) = forUUID.get(id)
  def getTabType[Data, TabClass <: TabBase, TabAPI](id: TabID[Data, TabClass, TabAPI]) =
    forTabID.get(id).asInstanceOf[Option[TabType[Data, TabClass, TabAPI]]]
}
private[mainframe] final class MainTabFolder(parent: Composite, state: MainFrameState)
  extends Composite(parent, SWT.NONE) with RxWidget {

  setLayout(new FillLayout())
  private val tabFolder = new CTabFolder(this, SWT.TOP | SWT.BORDER | SWT.CLOSE)
  tabFolder.addCTabFolder2Listener(new CTabFolder2Listener {
    override def minimize(cTabFolderEvent: CTabFolderEvent): Unit = { }
    override def restore(cTabFolderEvent: CTabFolderEvent): Unit = { }
    override def maximize(cTabFolderEvent: CTabFolderEvent): Unit = { }
    override def showList(cTabFolderEvent: CTabFolderEvent): Unit = { }
    override def close(cTabFolderEvent: CTabFolderEvent): Unit = {
      cTabFolderEvent.item.getData(MainTabFolder.tabDataId).asInstanceOf[TabData[_, _ <: TabBase, _]].control.dispose()
    }
  })

  private val tabs = new mutable.ArrayBuffer[TabData[_, _ <: TabBase, _]]

  val currentTab: Rx[Option[TabBase]] = Rx {
    val selection = tabFolder.getSelection
    if(selection == null) None
    else Some(selection.getData(MainTabFolder.tabDataId).asInstanceOf[TabData[_, TabBase, _]].control)
  }
  tabFolder.addSelectionListener(new SelectionListener {
    override def widgetSelected(selectionEvent: SelectionEvent): Unit = currentTab.recalc()
    override def widgetDefaultSelected(selectionEvent: SelectionEvent): Unit = { }
  })

  private val openTabs = new mutable.HashSet[(TabID[_, _, _], _)]
  def openTab[Data, TabClass <: TabBase, TabAPI](id: TabID[Data, TabClass, TabAPI], data: Data): TabAPI = {
    val idTuple = (id, data)
    if(!openTabs.contains(idTuple)) {
      openTabs.add(idTuple)
      val tab = TabData(id, data, MainTabFolder.getTabType(id).getOrElse(sys.error(s"Tab ID $id not registered"))
        .createTab(tabFolder, data, state))
      tab.control.addDisposeListener(_ => openTabs.remove(idTuple))
      tabs.append(tab)
      updateTabItems()
      saveSettings()
    }

    val tab = tabFolder.getItems.find { x =>
      val tabData = x.getData(MainTabFolder.tabDataId).asInstanceOf[TabData[_, _, _]]
      tabData.tabID == id && tabData.data == data
    }.get
    tabFolder.setSelection(tab)
    id.extractData(tab.getControl.asInstanceOf[TabClass])
  }

  private def clearTabs() = {
    for(item <- tabFolder.getItems) item.dispose()
    for(tab <- tabs) tab.control.dispose()
    tabs.clear()
  }
  private def updateTabItems() = {
    tabFolder.setRedraw(false)
    for(item <- tabFolder.getItems) item.dispose()
    for(tab <- tabs) {
      val item = new CTabItem(tabFolder, SWT.NONE)
      item.setData(MainTabFolder.tabDataId, tab)
      val obs = tab.control.tabName.foreach(x => state.ctx.asyncUiExec { if(!item.isDisposed) item.setText(x) })
      item.addDisposeListener(_ => obs.kill())
      item.setControl(tab.control)
    }
    tabFolder.setRedraw(true)
  }

  private def serialize = Json.toJson(tabs.map(_.serialize))
  private def deserialize(js: JsValue) = {
    clearTabs()
    tabs ++= js.as[Seq[JsValue]].map(x => TabData.deserialize(tabFolder, x, state))
    updateTabItems()
  }

  def loadSettings() = state.settings.getSetting(MainTabFolder.tabData, JsNull) match {
    case JsNull => false
    case v => try {
      deserialize(v)
      true
    } catch {
      case _: Throwable => false
    }
  }
  def saveSettings() = state.settings.setSetting(MainTabFolder.tabData, serialize)
}