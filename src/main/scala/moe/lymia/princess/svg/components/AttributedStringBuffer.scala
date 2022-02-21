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

// Based on code under the following license:
/*
   Licensed to the Apache Software Foundation (ASF) under one or more
   contributor license agreements.  See the NOTICE file distributed with
   this work for additional information regarding copyright ownership.
   The ASF licenses this file to You under the Apache License, Version 2.0
   (the "License"); you may not use this file except in compliance with
   the License.  You may obtain a copy of the License at
       http://www.apache.org/licenses/LICENSE-2.0
   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/

package moe.lymia.princess.svg.components

import java.text.{AttributedCharacterIterator, AttributedString}
import java.util
import scala.collection.JavaConverters._
import scala.collection.mutable

/**
  * This class is used to build an AttributedString.
  */
final class AttributedStringBuffer {
  private val strings = new mutable.ArrayBuffer[String]
  private val attributes = new mutable.ArrayBuffer[util.Map[AttributedCharacterIterator.Attribute, AnyRef]]

  private var count = 0
  private var length0 = 0

  def length = length0
  def isEmpty = count > 0

  def append(s: String, m: util.Map[AttributedCharacterIterator.Attribute, AnyRef]): Unit = {
    if (s.length == 0) return
    count += 1
    length0 += s.length
    strings.append(s)
    attributes.append(m)
  }
  def append(s: String, m: Map[AttributedCharacterIterator.Attribute, AnyRef]): Unit = append(s, m.asJava)

  def toAttributedString: AttributedString =
    if(count == 0) null
    else if(count == 1) {
      new AttributedString(strings.head, attributes.head)
    } else {
      val result = new AttributedString(strings.mkString(""))

      // Set the attributes
      var idx = 0
      for((s, m) <- strings.zip(attributes)) {
        val nidx = idx + s.length
        val kit = m.keySet.iterator
        val vit = m.values.iterator
        while (kit.hasNext) {
          val attr = kit.next
          val value = vit.next
          result.addAttribute(attr, value, idx, nidx)
        }
        idx = nidx
      }
      result
    }

  override def toString: String =
    if(count == 0) ""
    else if(count == 1) strings.head
    else strings.mkString("")
}