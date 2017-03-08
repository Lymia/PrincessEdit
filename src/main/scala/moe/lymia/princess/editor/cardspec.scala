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

import moe.lymia.princess.lua._

import scala.annotation.tailrec
import scala.collection.mutable

import rx._

sealed trait CardSpecField {
  def deps: Set[CardSpecField] = Set()
  override val hashCode = super.hashCode
}
case class CardSpecInputField(dataStoreName: String) extends CardSpecField
case class CardSpecDerivedField(params: Seq[CardSpecField], L: LuaState, fn: LuaClosure) extends CardSpecField {
  override def deps = params.toSet
}

class CardSpec(val fields: Map[String, CardSpecField]) {
  val loadOrder = CardSpec.findLoadOrder(fields)
}
object CardSpec {
  private def findLoadOrder(fields: Map[String, CardSpecField]) = {
    @tailrec def findSpecFields(gen: Set[CardSpecField], found: Set[CardSpecField]): Set[CardSpecField] = {
      val allDeps  = gen.flatMap(_.deps)
      val combined = gen ++ found
      val newDeps  = allDeps -- combined
      if(newDeps.isEmpty) combined else findSpecFields(newDeps, combined)
    }
    @tailrec def resolveLoadOrder(unresolved: Set[CardSpecField], resolved: Seq[CardSpecField]): Seq[CardSpecField] = {
      val (newUnresolved, newResolved) = unresolved.partition(_.deps.forall(resolved.contains))
      if(newResolved.isEmpty) sys.error("cycle in cardspec")
      val combined = resolved ++ newResolved
      if(newUnresolved.isEmpty) combined else resolveLoadOrder(newUnresolved, combined)
    }

    resolveLoadOrder(findSpecFields(fields.values.toSet, Set()), Seq())
  }
}

class ActiveCardSpec private (t: (Seq[Rx[Any]], Map[String, Rx[Any]])) {
  def this(L: LuaState, data: CardData, spec: CardSpec) = this(ActiveCardSpec.loadSpec(L, data, spec))

  private val (rxs, fields) = t
  val objects = Rx { fields.mapValues(_()) }

  def kill() = {
    for(rx <- rxs) rx.kill()
    for((_, rx) <- fields) rx.kill()
    objects.kill()
  }
}
object ActiveCardSpec {
  private def loadSpec(L: LuaState, data: CardData, spec: CardSpec): (Seq[Rx[Any]], Map[String, Rx[Any]]) = {
    val rxLoaded = new mutable.HashMap[CardSpecField, Rx[Any]]
    spec.loadOrder.foreach(x => rxLoaded.put(x, x match {
      case CardSpecInputField(name) =>
        val field = data.cardData.getField(name)
        Rx.unsafe { field().toLua(L) }
      case CardSpecDerivedField(deps, l_in, fn) =>
        val L = l_in.newThread()
        val rxs = deps.map(rxLoaded)
        Rx.unsafe { L.call(fn, 1, rxs.map(x => new LuaObject(x())) : _*).head.as[Any] }
    }))
    (rxLoaded.values.toSeq, spec.fields.mapValues(rxLoaded))
  }
}