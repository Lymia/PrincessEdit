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

package moe.lymia.princess.util

import java.security.MessageDigest
import java.util.Base64

object Crypto {
  def digest(algorithm: String, data: Array[Byte]) = {
    val md = MessageDigest.getInstance(algorithm)
    val hash = md.digest(data)
    hash
  }
  def hexdigest(algorithm: String, data: Array[Byte]) =
    digest(algorithm, data).map(x => "%02x".format(x)).reduce(_ + _)
  def base64digest(algorithm: String, data: Array[Byte]) =
    Base64.getUrlEncoder.encodeToString(digest(algorithm, data)).replace("=", "")

  @inline private def encodeInt(a: Array[Byte], i: Int, v: Int) = {
    a(i + 0) = v.toByte
    a(i + 1) = (v >>> 8).toByte
    a(i + 2) = (v >>> 16).toByte
    a(i + 3) = (v >>> 24).toByte
    Array(v & 0xFF, (v >>> 8) & 0xFF, (v >>> 16) & 0xFF, (v >>> 24) & 0xFF)
  }
  def combine(data: Array[Byte]*) = {
    val combined = new Array[Byte](4 + data.length * 4 + data.map(_.length).sum)
    encodeInt(combined, 0, data.length)
    for((v, i) <- data.zipWithIndex) encodeInt(combined, 4 + i * 4, v.length)

    var start = 4 + data.length * 4
    for(v <- data) {
      System.arraycopy(v, 0, combined, start, v.length)
      start = start + v.length
    }

    combined
  }

  def md5_hex   (data: Array[Byte]) = hexdigest("MD5"    , data)
  def sha1_hex  (data: Array[Byte]) = hexdigest("SHA-1"  , data)
  def sha256_hex(data: Array[Byte]) = hexdigest("SHA-256", data)
  def sha512_hex(data: Array[Byte]) = hexdigest("SHA-512", data)

  def md5_b64   (data: Array[Byte]) = base64digest("MD5"    , data)
  def sha1_b64  (data: Array[Byte]) = base64digest("SHA-1"  , data)
  def sha256_b64(data: Array[Byte]) = base64digest("SHA-256", data)
  def sha512_b64(data: Array[Byte]) = base64digest("SHA-512", data)

  def md5   (data: Array[Byte]) = digest("MD5"    , data)
  def sha1  (data: Array[Byte]) = digest("SHA-1"  , data)
  def sha256(data: Array[Byte]) = digest("SHA-256", data)
  def sha512(data: Array[Byte]) = digest("SHA-512", data)
}
