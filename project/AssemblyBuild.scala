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

import sbt._
import sbt.Keys._

import sbtassembly._
import AssemblyKeys._

object AssemblyBuild {
  object Keys {
    val shadeMappings     = SettingKey[Seq[(String, String)]]("assembly-wrapper-shade-mappings")
    val ignoreDuplicate   = SettingKey[Seq[String]]("assembly-wrapper-ignore-duplicate")
    val excludeFiles      = SettingKey[Set[String]]("assembly-wrapper-exclude-files")
    val excludePatterns   = SettingKey[Seq[String]]("assembly-wrapper-exclude-patterns")
  }
  import Keys._

  val settings = Seq(
    shadeMappings := Seq(),
    ignoreDuplicate := Seq(),
    excludeFiles := Set(),
    excludePatterns := Seq(),
    assemblyShadeRules in assembly := shadeMappings.value.map(x => ShadeRule.rename(x).inAll),
    assemblyMergeStrategy in assembly := {
      case x if excludeFiles.value.contains(x) || excludePatterns.value.exists(x.matches) =>
        MergeStrategy.discard
      case x if ignoreDuplicate.value.exists(x.matches) =>
        MergeStrategy.first
      case x =>
        val oldStrategy = (assemblyMergeStrategy in assembly).value
        oldStrategy(x)
    }
  )
}
