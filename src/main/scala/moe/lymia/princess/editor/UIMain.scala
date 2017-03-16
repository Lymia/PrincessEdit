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

package moe.lymia.princess.editor

import java.awt.{GridBagConstraints, GridBagLayout}
import javax.swing.{UIManager => _, _}

import moe.lymia.princess.core._
import moe.lymia.princess.editor.core._
import moe.lymia.princess.editor.data._
import moe.lymia.princess.rasterizer._
import moe.lymia.princess.renderer._

import play.api.libs.json.Json

import scala.collection.JavaConverters._

// TODO: This is only a temporary test UI
class UIMain {
  private var gameId: String = _
  trait ScoptArgs { this: scopt.OptionParser[Unit] =>
    def uiOptions() = {
      arg[String]("<gameId>").foreach(gameId = _).hidden()
    }
  }
  def main() = {
    val monitorThread = new Thread() {
      override def run(): Unit = {
        while(true) {
          Thread.sleep(1000)

          println()
          println("=====================================================================")
          println("=====================================================================")
          println("=====================================================================")
          println()
          for((thread, trace) <- Thread.getAllStackTraces.asScala) {
            val e = new Exception(s"Stack Trace for ${thread.getName}")
            e.setStackTrace(trace)
            e.printStackTrace()
          }
        }
      }
    }
    //monitorThread.start()

    val frame = new JFrame("PrincessEdit Editor Test")
    val game = PackageManager.default.loadGameId(gameId)
    val manager = new UIManager(game, new InkscapeConnectionFactory("inkscape"))
    val ep = new LuaNodeSource(game, "card-form", "cardForm")
    val data = new DataStore
    val root = ep.createRoot(game.lua.L, data, "card", manager.ctx, Seq())

    frame.setLayout(new GridBagLayout)
    val c = new GridBagConstraints()

    val label = new JLabel()
    c.fill = GridBagConstraints.NONE
    frame.add(label, c)

    c.gridx = 1
    c.weightx = 1
    c.fill = GridBagConstraints.BOTH
    c.anchor = GridBagConstraints.NORTH
    frame.add(root.component.now, c)

    import rx.Ctx.Owner.Unsafe._
    val obs = root.luaData.now.foreach { d =>
      println(Json.prettyPrint(data.serialize))
      manager.ctx.renderRequest(manager.render.render(d, RasterizeResourceLoader), 250, 350) { i =>
        SwingUtilities.invokeLater { () =>
          label.setIcon(new ImageIcon(i))
        }
      }
    }

    frame.pack()
    frame.setLocationRelativeTo(null)
    frame.setVisible(true)

    while(true) Thread.sleep(1)
  }
}
