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

package moe.lymia.princess.core

import moe.lymia.princess.DefaultLogger
import toml.{Codec, Parse, Value}

package object gamedata {
  private[gamedata] implicit val tomlValueCodec: Codec[Value] = Codec { (x, _, _) => Right(x) }

  private[gamedata] implicit class TomlError[T](data: Either[Parse.Error, T]) {
    def checkErr: T = data match {
      case Left(error) =>
        // TODO: Make this way prettier.
        throw new EditorException(error.toString())
      case Right(x) => x
    }
  }

  private[gamedata] val logger = DefaultLogger.bind("core.packages")

  object StaticGameIds {
    val System        = "_princess/system-package"
    val DefinesGameId = "defines-gameid"
  }

  object StaticExportIds {
    val GameId = "gameid"
    val ProtectedPath = "_princess/protected-path"
    val IgnoredPath = "_princess/ignored-path"
    def Predef(t: String) = s"_princess/predef/$t"
    def EntryPoint(t: String, ep: String) = s"$t/entry-point/$ep"
    def I18N(t: String, language: String, country: String) = s"$t/i18n/${language}_$country"
  }
}
