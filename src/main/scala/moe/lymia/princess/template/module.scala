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

package moe.lymia.princess.template

case class Version(major: Int, minor: Int, patch: Int) {
  def sastifiedBy(v: Version) =
    v.major == major && v.minor > minor
  override def toString = s"$major.$minor.$patch"
}

case class RenderData()

trait ModuleAction {
  def applyModule(data: RenderData, render: RenderManager, modules: ModuleManager)
}
case class Module(moduleName: String, version: Version, dependencies: Map[String, Version],
                  actions: Seq[ModuleAction]) extends ModuleAction {
  def applyModule(data: RenderData, render: RenderManager, modules: ModuleManager) =
    actions.foreach(_.applyModule(data, render, modules))
}

trait ModuleLoader {
  def loadModule(name: String): Either[Module, String]
}
class ModuleManager(loader: ModuleLoader) {

}