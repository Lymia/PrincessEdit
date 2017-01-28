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

package moe.lymia.princess.lua

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.util.regex.Pattern

import moe.lymia.princess.util.IOUtils

final class SafePackageLib(paths: Seq[Path]) {
  def open(L: LuaState): Unit = {
    PackageLib.open(L. L)

    L.rawSet(L.getGlobal("package"), "path", LuaNil)
    val loader = L.getTable(L.getGlobal("package"), "loaders").as[LuaTable]
    L.register(loader, loader.getn(), load _) // replace LOADER_LUA
  }

  private def load(L: LuaState, module: String): LuaRet = {
    val filename = s"${module.replace(".", "/")}.lua"
    val components = filename.split("/").map(_.trim)
    if(!SafePackageLib.validModuleRegex.matcher(module).matches ||
       components.exists(c => c.isEmpty || !SafePackageLib.validDirRegex.matcher(c).matches))
      LuaRet(s"\n\tinvalid module name '$module'")
    else {
      var errStr = ""
      for(path <- paths) IOUtils.paranoidResolve(path, filename) match {
        case None =>
          errStr = errStr + s"\n\tno file '$filename' in '${path.toString}'"
        case Some(x) =>
          val luaString = new String(Files.readAllBytes(x), StandardCharsets.UTF_8)
          L.loadString(luaString, s"@$filename") match {
            case Left (chunk) => return LuaRet(chunk)
            case Right(err)   => errStr += s"\n\tcould not load file '$filename' in '${path.toString}: $err"
          }
      }
      LuaRet(errStr)
    }
  }
}
object SafePackageLib {
  private val validModuleRegex = Pattern.compile("^[a-zA-Z0-9_.]+^$")
  private val validDirRegex    = Pattern.compile("^[a-zA-Z0-9_]+$")
}