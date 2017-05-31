/*
 * Copyright (c) 2017 Lymia Alusyia <lymia@lymiahugs.com>
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

package moe.lymia.princess.editor.ui.mainframe

import java.util.UUID

import moe.lymia.princess.editor.SettingsKey
import moe.lymia.princess.editor.utils.RxWidget
import moe.lymia.princess.util.Service
import org.eclipse.jface.action.MenuManager
import org.eclipse.swt.SWT
import org.eclipse.swt.custom.{CTabFolder, CTabFolder2Listener, CTabFolderEvent, CTabItem}
import org.eclipse.swt.events.{SelectionEvent, SelectionListener}
import org.eclipse.swt.layout.FillLayout
import org.eclipse.swt.widgets.{Composite, Control}
import play.api.libs.json._
import rx._

import scala.collection.mutable

case class TabID[T : Writes : Reads](id: UUID) {
  def serialize(v: T) = Json.toJson(v)
  def deserialize(js: JsValue): T = js.as[T]
}

trait PrincessEditTab { this: Control =>
  def addMenuItems(m: MenuManager)

  val isClosable = true

  val tabName: Rx[String]
  addDisposeListener(_ => tabName.kill())
}

trait TabType[T] {
  def createTab(parent: Composite, data: T, state: MainFrameState): Control with PrincessEditTab
}
trait TabProvider {
  private val ids = new mutable.HashMap[TabID[_], TabType[_]]
  protected def tabId[T](id: TabID[T])(fn: (Composite, T, MainFrameState) => Control with PrincessEditTab) = {
    if(ids.contains(id)) sys.error(s"Duplicate TabID $id")
    ids.put(id, ((parent, data, state) => fn(parent, data, state)) : TabType[T])
  }
  private[mainframe] def getTabTypes: Map[TabID[_], TabType[_]] = ids.toMap
}

private[mainframe] case class TabData[T](tabID: TabID[T], data: T, control: Control with PrincessEditTab) {
  def serialize = Json.obj(
    "id" -> tabID.id,
    "data" -> tabID.serialize(data)
  )
}
private object TabData {
  def deserialize(parent: Composite, js: JsValue, state: MainFrameState): TabData[_] = {
    val uuid = (js \ "id").as[UUID]
    val id = MainTabFolder.getTabIDByUUID(uuid).getOrElse(sys.error(s"Unknown uuid $uuid")).asInstanceOf[TabID[Any]]
    val t = MainTabFolder.getTabType(id).getOrElse(sys.error(s"unexpected error: inconsistent state"))
    val data = id.deserialize((js \ "data").as[JsValue])
    TabData(id, data, t.createTab(parent, data, state))
  }
}

private object MainTabFolder {
  private val dataUUID = "dd51f4c8-343c-11e7-80b1-3afa38669cf4"
  private val tabData = new SettingsKey[JsValue](UUID.fromString("973b7496-3437-11e7-bc04-3afa38669cf4"))

  private lazy val (forUUID, forTabID) = {
    val forUUID = new mutable.HashMap[UUID, TabID[_]]
    val forTabID = new mutable.HashMap[TabID[_], TabType[_]]
    for(provider <- Service.get[TabProvider]; (id, t) <- provider.getTabTypes) {
      if(forUUID.contains(id.id)) sys.error(s"Duplicate UUID ${id.id}")
      if(forTabID.contains(id)) sys.error(s"Duplicate TabID $id")

      forUUID.put(id.id, id)
      forTabID.put(id, t)
    }
    (forUUID.toMap, forTabID.toMap)
  }

  def getTabIDByUUID(id: UUID) = forUUID.get(id)
  def getTabType[T](id: TabID[T]) = forTabID.get(id).asInstanceOf[Option[TabType[T]]]
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
      cTabFolderEvent.item.getData(MainTabFolder.dataUUID).asInstanceOf[TabData[_]].control.dispose()
    }
  })

  private val tabs = new mutable.ArrayBuffer[TabData[_]]

  val currentTab = Rx {
    val selection = tabFolder.getSelection
    if(selection == null) None
    else Some(selection.getData(MainTabFolder.dataUUID).asInstanceOf[TabData[_]].control)
  }
  tabFolder.addSelectionListener(new SelectionListener {
    override def widgetSelected(selectionEvent: SelectionEvent): Unit = currentTab.recalc()
    override def widgetDefaultSelected(selectionEvent: SelectionEvent): Unit = { }
  })

  private val openTabs = new mutable.HashSet[(TabID[_], _)]
  def openTab[T](id: TabID[T], data: T) = {
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
    tabFolder.setSelection(tabFolder.getItems.find { x =>
      val tabData = x.getData(MainTabFolder.dataUUID).asInstanceOf[TabData[_]]
      tabData.tabID == id && tabData.data == data
    }.get)
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
      item.setData(MainTabFolder.dataUUID, tab)
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
      case _ => false
    }
  }
  def saveSettings() = state.settings.setSetting(MainTabFolder.tabData, serialize)
}