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

import scala.language.implicitConversions

/*

  Generated from the following code:
  ==================================

  def makeImplicit(count: Int) = {
    val n = (1 to count).toSeq

    val comma          = if(count == 0) "" else ", "
    val typeParameters = n.map(x => s"T$x : FromLua").mkString(", ")
    val fullTypeParams = if(typeParameters.nonEmpty) s"[$typeParameters]" else ""
    val fnTpParameters = n.map(x => s"T$x").mkString(", ")
    val passParameters = n.map(x => s"""implicitly[FromLua[T$x]].fromLua(L, L.value($x), LuaGeneratedImplicits.badArgFn$x())""").mkString(", ")
    s"""
      |  // Functions for $count parameters
      |  implicit def nullSingleFunction${count}2luaClosure[$typeParameters${comma}ReturnT: ToLua](fn: ($fnTpParameters) => ReturnT): ScalaLuaClosure =
      |    ScalaLuaClosure(L => { L.push(implicitly[ToLua[ReturnT]].toLua(fn($passParameters)).toLua(new LuaState(L))); 1 })
      |  implicit def nullFunction${count}2luaClosure$fullTypeParams(fn: ($fnTpParameters) => Seq[LuaObject]): ScalaLuaClosure =
      |    ScalaLuaClosure(L => { LuaGeneratedImplicits.pushSeqRet(L, fn($passParameters)) })
      |  implicit def nullUnitFunction${count}2luaClosure$fullTypeParams(fn: ($fnTpParameters) => Unit): ScalaLuaClosure =
      |    ScalaLuaClosure(L => { fn($passParameters); 0 })
      |  implicit def function${count}2luaClosure[$typeParameters${comma}ReturnT: ToLua](fn: (LuaState$comma$fnTpParameters) => ReturnT): ScalaLuaClosure =
      |    ScalaLuaClosure(L => { L.push(implicitly[ToLua[ReturnT]].toLua(fn(new LuaState(L)$comma$passParameters)).toLua(new LuaState(L))); 1 })
      |  implicit def function${count}2luaClosure$fullTypeParams(fn: (LuaState$comma$fnTpParameters) => Seq[LuaObject]): ScalaLuaClosure =
      |    ScalaLuaClosure(L => { LuaGeneratedImplicits.pushSeqRet(L, fn(new LuaState(L)$comma$passParameters)) })
      |  implicit def unitFunction${count}2luaClosure$fullTypeParams(fn: (LuaState$comma$fnTpParameters) => Unit): ScalaLuaClosure =
      |    ScalaLuaClosure(L => { fn(new LuaState(L)$comma$passParameters); 0 })
    """.stripMargin.trim()
  }
  val maxFunction = 10
  println(s"""
    |object LuaGeneratedImplicits {
    |  @inline private def pushSeqRet(L: Lua, ret: LuaRet) = {
    |    for(v <- ret) L.push(v.toLua(new LuaState(L)))
    |    ret.length
    |  }
    |  ${(1 to maxFunction).map(n => s"""private val badArg$n = Some("bad argument $n")""").mkString("\n  ")}
    |  ${(1 to maxFunction).map(n => s"private val badArgFn$n = () => badArg$n").mkString("\n  ")}
    |}
    |trait LuaGeneratedImplicits {
    |  ${(0 to maxFunction).map(makeImplicit).mkString("\n\n  ")}
    |}
  """.stripMargin)

*/

object LuaGeneratedImplicits {
  @inline private def pushSeqRet(L: Lua, ret: LuaRet) = {
    for(v <- ret) L.push(v.toLua(new LuaState(L)))
    ret.length
  }
  private val badArg1 = Some("bad argument 1")
  private val badArg2 = Some("bad argument 2")
  private val badArg3 = Some("bad argument 3")
  private val badArg4 = Some("bad argument 4")
  private val badArg5 = Some("bad argument 5")
  private val badArg6 = Some("bad argument 6")
  private val badArg7 = Some("bad argument 7")
  private val badArg8 = Some("bad argument 8")
  private val badArg9 = Some("bad argument 9")
  private val badArg10 = Some("bad argument 10")
  private val badArgFn1 = () => badArg1
  private val badArgFn2 = () => badArg2
  private val badArgFn3 = () => badArg3
  private val badArgFn4 = () => badArg4
  private val badArgFn5 = () => badArg5
  private val badArgFn6 = () => badArg6
  private val badArgFn7 = () => badArg7
  private val badArgFn8 = () => badArg8
  private val badArgFn9 = () => badArg9
  private val badArgFn10 = () => badArg10
}
trait LuaGeneratedImplicits {
  // Functions for 0 parameters
  implicit def nullSingleFunction02luaClosure[ReturnT: ToLua](fn: () => ReturnT): ScalaLuaClosure =
    ScalaLuaClosure(L => { L.push(implicitly[ToLua[ReturnT]].toLua(fn()).toLua(new LuaState(L))); 1 })
  implicit def nullFunction02luaClosure(fn: () => Seq[LuaObject]): ScalaLuaClosure =
    ScalaLuaClosure(L => { LuaGeneratedImplicits.pushSeqRet(L, fn()) })
  implicit def nullUnitFunction02luaClosure(fn: () => Unit): ScalaLuaClosure =
    ScalaLuaClosure(L => { fn(); 0 })
  implicit def function02luaClosure[ReturnT: ToLua](fn: (LuaState) => ReturnT): ScalaLuaClosure =
    ScalaLuaClosure(L => { L.push(implicitly[ToLua[ReturnT]].toLua(fn(new LuaState(L))).toLua(new LuaState(L))); 1 })
  implicit def function02luaClosure(fn: (LuaState) => Seq[LuaObject]): ScalaLuaClosure =
    ScalaLuaClosure(L => { LuaGeneratedImplicits.pushSeqRet(L, fn(new LuaState(L))) })
  implicit def unitFunction02luaClosure(fn: (LuaState) => Unit): ScalaLuaClosure =
    ScalaLuaClosure(L => { fn(new LuaState(L)); 0 })

  // Functions for 1 parameters
  implicit def nullSingleFunction12luaClosure[T1 : FromLua, ReturnT: ToLua](fn: (T1) => ReturnT): ScalaLuaClosure =
    ScalaLuaClosure(L => { L.push(implicitly[ToLua[ReturnT]].toLua(fn(implicitly[FromLua[T1]].fromLua(L, L.value(1), LuaGeneratedImplicits.badArgFn1()))).toLua(new LuaState(L))); 1 })
  implicit def nullFunction12luaClosure[T1 : FromLua](fn: (T1) => Seq[LuaObject]): ScalaLuaClosure =
    ScalaLuaClosure(L => { LuaGeneratedImplicits.pushSeqRet(L, fn(implicitly[FromLua[T1]].fromLua(L, L.value(1), LuaGeneratedImplicits.badArgFn1()))) })
  implicit def nullUnitFunction12luaClosure[T1 : FromLua](fn: (T1) => Unit): ScalaLuaClosure =
    ScalaLuaClosure(L => { fn(implicitly[FromLua[T1]].fromLua(L, L.value(1), LuaGeneratedImplicits.badArgFn1())); 0 })
  implicit def function12luaClosure[T1 : FromLua, ReturnT: ToLua](fn: (LuaState, T1) => ReturnT): ScalaLuaClosure =
    ScalaLuaClosure(L => { L.push(implicitly[ToLua[ReturnT]].toLua(fn(new LuaState(L), implicitly[FromLua[T1]].fromLua(L, L.value(1), LuaGeneratedImplicits.badArgFn1()))).toLua(new LuaState(L))); 1 })
  implicit def function12luaClosure[T1 : FromLua](fn: (LuaState, T1) => Seq[LuaObject]): ScalaLuaClosure =
    ScalaLuaClosure(L => { LuaGeneratedImplicits.pushSeqRet(L, fn(new LuaState(L), implicitly[FromLua[T1]].fromLua(L, L.value(1), LuaGeneratedImplicits.badArgFn1()))) })
  implicit def unitFunction12luaClosure[T1 : FromLua](fn: (LuaState, T1) => Unit): ScalaLuaClosure =
    ScalaLuaClosure(L => { fn(new LuaState(L), implicitly[FromLua[T1]].fromLua(L, L.value(1), LuaGeneratedImplicits.badArgFn1())); 0 })

  // Functions for 2 parameters
  implicit def nullSingleFunction22luaClosure[T1 : FromLua, T2 : FromLua, ReturnT: ToLua](fn: (T1, T2) => ReturnT): ScalaLuaClosure =
    ScalaLuaClosure(L => { L.push(implicitly[ToLua[ReturnT]].toLua(fn(implicitly[FromLua[T1]].fromLua(L, L.value(1), LuaGeneratedImplicits.badArgFn1()), implicitly[FromLua[T2]].fromLua(L, L.value(2), LuaGeneratedImplicits.badArgFn2()))).toLua(new LuaState(L))); 1 })
  implicit def nullFunction22luaClosure[T1 : FromLua, T2 : FromLua](fn: (T1, T2) => Seq[LuaObject]): ScalaLuaClosure =
    ScalaLuaClosure(L => { LuaGeneratedImplicits.pushSeqRet(L, fn(implicitly[FromLua[T1]].fromLua(L, L.value(1), LuaGeneratedImplicits.badArgFn1()), implicitly[FromLua[T2]].fromLua(L, L.value(2), LuaGeneratedImplicits.badArgFn2()))) })
  implicit def nullUnitFunction22luaClosure[T1 : FromLua, T2 : FromLua](fn: (T1, T2) => Unit): ScalaLuaClosure =
    ScalaLuaClosure(L => { fn(implicitly[FromLua[T1]].fromLua(L, L.value(1), LuaGeneratedImplicits.badArgFn1()), implicitly[FromLua[T2]].fromLua(L, L.value(2), LuaGeneratedImplicits.badArgFn2())); 0 })
  implicit def function22luaClosure[T1 : FromLua, T2 : FromLua, ReturnT: ToLua](fn: (LuaState, T1, T2) => ReturnT): ScalaLuaClosure =
    ScalaLuaClosure(L => { L.push(implicitly[ToLua[ReturnT]].toLua(fn(new LuaState(L), implicitly[FromLua[T1]].fromLua(L, L.value(1), LuaGeneratedImplicits.badArgFn1()), implicitly[FromLua[T2]].fromLua(L, L.value(2), LuaGeneratedImplicits.badArgFn2()))).toLua(new LuaState(L))); 1 })
  implicit def function22luaClosure[T1 : FromLua, T2 : FromLua](fn: (LuaState, T1, T2) => Seq[LuaObject]): ScalaLuaClosure =
    ScalaLuaClosure(L => { LuaGeneratedImplicits.pushSeqRet(L, fn(new LuaState(L), implicitly[FromLua[T1]].fromLua(L, L.value(1), LuaGeneratedImplicits.badArgFn1()), implicitly[FromLua[T2]].fromLua(L, L.value(2), LuaGeneratedImplicits.badArgFn2()))) })
  implicit def unitFunction22luaClosure[T1 : FromLua, T2 : FromLua](fn: (LuaState, T1, T2) => Unit): ScalaLuaClosure =
    ScalaLuaClosure(L => { fn(new LuaState(L), implicitly[FromLua[T1]].fromLua(L, L.value(1), LuaGeneratedImplicits.badArgFn1()), implicitly[FromLua[T2]].fromLua(L, L.value(2), LuaGeneratedImplicits.badArgFn2())); 0 })

  // Functions for 3 parameters
  implicit def nullSingleFunction32luaClosure[T1 : FromLua, T2 : FromLua, T3 : FromLua, ReturnT: ToLua](fn: (T1, T2, T3) => ReturnT): ScalaLuaClosure =
    ScalaLuaClosure(L => { L.push(implicitly[ToLua[ReturnT]].toLua(fn(implicitly[FromLua[T1]].fromLua(L, L.value(1), LuaGeneratedImplicits.badArgFn1()), implicitly[FromLua[T2]].fromLua(L, L.value(2), LuaGeneratedImplicits.badArgFn2()), implicitly[FromLua[T3]].fromLua(L, L.value(3), LuaGeneratedImplicits.badArgFn3()))).toLua(new LuaState(L))); 1 })
  implicit def nullFunction32luaClosure[T1 : FromLua, T2 : FromLua, T3 : FromLua](fn: (T1, T2, T3) => Seq[LuaObject]): ScalaLuaClosure =
    ScalaLuaClosure(L => { LuaGeneratedImplicits.pushSeqRet(L, fn(implicitly[FromLua[T1]].fromLua(L, L.value(1), LuaGeneratedImplicits.badArgFn1()), implicitly[FromLua[T2]].fromLua(L, L.value(2), LuaGeneratedImplicits.badArgFn2()), implicitly[FromLua[T3]].fromLua(L, L.value(3), LuaGeneratedImplicits.badArgFn3()))) })
  implicit def nullUnitFunction32luaClosure[T1 : FromLua, T2 : FromLua, T3 : FromLua](fn: (T1, T2, T3) => Unit): ScalaLuaClosure =
    ScalaLuaClosure(L => { fn(implicitly[FromLua[T1]].fromLua(L, L.value(1), LuaGeneratedImplicits.badArgFn1()), implicitly[FromLua[T2]].fromLua(L, L.value(2), LuaGeneratedImplicits.badArgFn2()), implicitly[FromLua[T3]].fromLua(L, L.value(3), LuaGeneratedImplicits.badArgFn3())); 0 })
  implicit def function32luaClosure[T1 : FromLua, T2 : FromLua, T3 : FromLua, ReturnT: ToLua](fn: (LuaState, T1, T2, T3) => ReturnT): ScalaLuaClosure =
    ScalaLuaClosure(L => { L.push(implicitly[ToLua[ReturnT]].toLua(fn(new LuaState(L), implicitly[FromLua[T1]].fromLua(L, L.value(1), LuaGeneratedImplicits.badArgFn1()), implicitly[FromLua[T2]].fromLua(L, L.value(2), LuaGeneratedImplicits.badArgFn2()), implicitly[FromLua[T3]].fromLua(L, L.value(3), LuaGeneratedImplicits.badArgFn3()))).toLua(new LuaState(L))); 1 })
  implicit def function32luaClosure[T1 : FromLua, T2 : FromLua, T3 : FromLua](fn: (LuaState, T1, T2, T3) => Seq[LuaObject]): ScalaLuaClosure =
    ScalaLuaClosure(L => { LuaGeneratedImplicits.pushSeqRet(L, fn(new LuaState(L), implicitly[FromLua[T1]].fromLua(L, L.value(1), LuaGeneratedImplicits.badArgFn1()), implicitly[FromLua[T2]].fromLua(L, L.value(2), LuaGeneratedImplicits.badArgFn2()), implicitly[FromLua[T3]].fromLua(L, L.value(3), LuaGeneratedImplicits.badArgFn3()))) })
  implicit def unitFunction32luaClosure[T1 : FromLua, T2 : FromLua, T3 : FromLua](fn: (LuaState, T1, T2, T3) => Unit): ScalaLuaClosure =
    ScalaLuaClosure(L => { fn(new LuaState(L), implicitly[FromLua[T1]].fromLua(L, L.value(1), LuaGeneratedImplicits.badArgFn1()), implicitly[FromLua[T2]].fromLua(L, L.value(2), LuaGeneratedImplicits.badArgFn2()), implicitly[FromLua[T3]].fromLua(L, L.value(3), LuaGeneratedImplicits.badArgFn3())); 0 })

  // Functions for 4 parameters
  implicit def nullSingleFunction42luaClosure[T1 : FromLua, T2 : FromLua, T3 : FromLua, T4 : FromLua, ReturnT: ToLua](fn: (T1, T2, T3, T4) => ReturnT): ScalaLuaClosure =
    ScalaLuaClosure(L => { L.push(implicitly[ToLua[ReturnT]].toLua(fn(implicitly[FromLua[T1]].fromLua(L, L.value(1), LuaGeneratedImplicits.badArgFn1()), implicitly[FromLua[T2]].fromLua(L, L.value(2), LuaGeneratedImplicits.badArgFn2()), implicitly[FromLua[T3]].fromLua(L, L.value(3), LuaGeneratedImplicits.badArgFn3()), implicitly[FromLua[T4]].fromLua(L, L.value(4), LuaGeneratedImplicits.badArgFn4()))).toLua(new LuaState(L))); 1 })
  implicit def nullFunction42luaClosure[T1 : FromLua, T2 : FromLua, T3 : FromLua, T4 : FromLua](fn: (T1, T2, T3, T4) => Seq[LuaObject]): ScalaLuaClosure =
    ScalaLuaClosure(L => { LuaGeneratedImplicits.pushSeqRet(L, fn(implicitly[FromLua[T1]].fromLua(L, L.value(1), LuaGeneratedImplicits.badArgFn1()), implicitly[FromLua[T2]].fromLua(L, L.value(2), LuaGeneratedImplicits.badArgFn2()), implicitly[FromLua[T3]].fromLua(L, L.value(3), LuaGeneratedImplicits.badArgFn3()), implicitly[FromLua[T4]].fromLua(L, L.value(4), LuaGeneratedImplicits.badArgFn4()))) })
  implicit def nullUnitFunction42luaClosure[T1 : FromLua, T2 : FromLua, T3 : FromLua, T4 : FromLua](fn: (T1, T2, T3, T4) => Unit): ScalaLuaClosure =
    ScalaLuaClosure(L => { fn(implicitly[FromLua[T1]].fromLua(L, L.value(1), LuaGeneratedImplicits.badArgFn1()), implicitly[FromLua[T2]].fromLua(L, L.value(2), LuaGeneratedImplicits.badArgFn2()), implicitly[FromLua[T3]].fromLua(L, L.value(3), LuaGeneratedImplicits.badArgFn3()), implicitly[FromLua[T4]].fromLua(L, L.value(4), LuaGeneratedImplicits.badArgFn4())); 0 })
  implicit def function42luaClosure[T1 : FromLua, T2 : FromLua, T3 : FromLua, T4 : FromLua, ReturnT: ToLua](fn: (LuaState, T1, T2, T3, T4) => ReturnT): ScalaLuaClosure =
    ScalaLuaClosure(L => { L.push(implicitly[ToLua[ReturnT]].toLua(fn(new LuaState(L), implicitly[FromLua[T1]].fromLua(L, L.value(1), LuaGeneratedImplicits.badArgFn1()), implicitly[FromLua[T2]].fromLua(L, L.value(2), LuaGeneratedImplicits.badArgFn2()), implicitly[FromLua[T3]].fromLua(L, L.value(3), LuaGeneratedImplicits.badArgFn3()), implicitly[FromLua[T4]].fromLua(L, L.value(4), LuaGeneratedImplicits.badArgFn4()))).toLua(new LuaState(L))); 1 })
  implicit def function42luaClosure[T1 : FromLua, T2 : FromLua, T3 : FromLua, T4 : FromLua](fn: (LuaState, T1, T2, T3, T4) => Seq[LuaObject]): ScalaLuaClosure =
    ScalaLuaClosure(L => { LuaGeneratedImplicits.pushSeqRet(L, fn(new LuaState(L), implicitly[FromLua[T1]].fromLua(L, L.value(1), LuaGeneratedImplicits.badArgFn1()), implicitly[FromLua[T2]].fromLua(L, L.value(2), LuaGeneratedImplicits.badArgFn2()), implicitly[FromLua[T3]].fromLua(L, L.value(3), LuaGeneratedImplicits.badArgFn3()), implicitly[FromLua[T4]].fromLua(L, L.value(4), LuaGeneratedImplicits.badArgFn4()))) })
  implicit def unitFunction42luaClosure[T1 : FromLua, T2 : FromLua, T3 : FromLua, T4 : FromLua](fn: (LuaState, T1, T2, T3, T4) => Unit): ScalaLuaClosure =
    ScalaLuaClosure(L => { fn(new LuaState(L), implicitly[FromLua[T1]].fromLua(L, L.value(1), LuaGeneratedImplicits.badArgFn1()), implicitly[FromLua[T2]].fromLua(L, L.value(2), LuaGeneratedImplicits.badArgFn2()), implicitly[FromLua[T3]].fromLua(L, L.value(3), LuaGeneratedImplicits.badArgFn3()), implicitly[FromLua[T4]].fromLua(L, L.value(4), LuaGeneratedImplicits.badArgFn4())); 0 })

  // Functions for 5 parameters
  implicit def nullSingleFunction52luaClosure[T1 : FromLua, T2 : FromLua, T3 : FromLua, T4 : FromLua, T5 : FromLua, ReturnT: ToLua](fn: (T1, T2, T3, T4, T5) => ReturnT): ScalaLuaClosure =
    ScalaLuaClosure(L => { L.push(implicitly[ToLua[ReturnT]].toLua(fn(implicitly[FromLua[T1]].fromLua(L, L.value(1), LuaGeneratedImplicits.badArgFn1()), implicitly[FromLua[T2]].fromLua(L, L.value(2), LuaGeneratedImplicits.badArgFn2()), implicitly[FromLua[T3]].fromLua(L, L.value(3), LuaGeneratedImplicits.badArgFn3()), implicitly[FromLua[T4]].fromLua(L, L.value(4), LuaGeneratedImplicits.badArgFn4()), implicitly[FromLua[T5]].fromLua(L, L.value(5), LuaGeneratedImplicits.badArgFn5()))).toLua(new LuaState(L))); 1 })
  implicit def nullFunction52luaClosure[T1 : FromLua, T2 : FromLua, T3 : FromLua, T4 : FromLua, T5 : FromLua](fn: (T1, T2, T3, T4, T5) => Seq[LuaObject]): ScalaLuaClosure =
    ScalaLuaClosure(L => { LuaGeneratedImplicits.pushSeqRet(L, fn(implicitly[FromLua[T1]].fromLua(L, L.value(1), LuaGeneratedImplicits.badArgFn1()), implicitly[FromLua[T2]].fromLua(L, L.value(2), LuaGeneratedImplicits.badArgFn2()), implicitly[FromLua[T3]].fromLua(L, L.value(3), LuaGeneratedImplicits.badArgFn3()), implicitly[FromLua[T4]].fromLua(L, L.value(4), LuaGeneratedImplicits.badArgFn4()), implicitly[FromLua[T5]].fromLua(L, L.value(5), LuaGeneratedImplicits.badArgFn5()))) })
  implicit def nullUnitFunction52luaClosure[T1 : FromLua, T2 : FromLua, T3 : FromLua, T4 : FromLua, T5 : FromLua](fn: (T1, T2, T3, T4, T5) => Unit): ScalaLuaClosure =
    ScalaLuaClosure(L => { fn(implicitly[FromLua[T1]].fromLua(L, L.value(1), LuaGeneratedImplicits.badArgFn1()), implicitly[FromLua[T2]].fromLua(L, L.value(2), LuaGeneratedImplicits.badArgFn2()), implicitly[FromLua[T3]].fromLua(L, L.value(3), LuaGeneratedImplicits.badArgFn3()), implicitly[FromLua[T4]].fromLua(L, L.value(4), LuaGeneratedImplicits.badArgFn4()), implicitly[FromLua[T5]].fromLua(L, L.value(5), LuaGeneratedImplicits.badArgFn5())); 0 })
  implicit def function52luaClosure[T1 : FromLua, T2 : FromLua, T3 : FromLua, T4 : FromLua, T5 : FromLua, ReturnT: ToLua](fn: (LuaState, T1, T2, T3, T4, T5) => ReturnT): ScalaLuaClosure =
    ScalaLuaClosure(L => { L.push(implicitly[ToLua[ReturnT]].toLua(fn(new LuaState(L), implicitly[FromLua[T1]].fromLua(L, L.value(1), LuaGeneratedImplicits.badArgFn1()), implicitly[FromLua[T2]].fromLua(L, L.value(2), LuaGeneratedImplicits.badArgFn2()), implicitly[FromLua[T3]].fromLua(L, L.value(3), LuaGeneratedImplicits.badArgFn3()), implicitly[FromLua[T4]].fromLua(L, L.value(4), LuaGeneratedImplicits.badArgFn4()), implicitly[FromLua[T5]].fromLua(L, L.value(5), LuaGeneratedImplicits.badArgFn5()))).toLua(new LuaState(L))); 1 })
  implicit def function52luaClosure[T1 : FromLua, T2 : FromLua, T3 : FromLua, T4 : FromLua, T5 : FromLua](fn: (LuaState, T1, T2, T3, T4, T5) => Seq[LuaObject]): ScalaLuaClosure =
    ScalaLuaClosure(L => { LuaGeneratedImplicits.pushSeqRet(L, fn(new LuaState(L), implicitly[FromLua[T1]].fromLua(L, L.value(1), LuaGeneratedImplicits.badArgFn1()), implicitly[FromLua[T2]].fromLua(L, L.value(2), LuaGeneratedImplicits.badArgFn2()), implicitly[FromLua[T3]].fromLua(L, L.value(3), LuaGeneratedImplicits.badArgFn3()), implicitly[FromLua[T4]].fromLua(L, L.value(4), LuaGeneratedImplicits.badArgFn4()), implicitly[FromLua[T5]].fromLua(L, L.value(5), LuaGeneratedImplicits.badArgFn5()))) })
  implicit def unitFunction52luaClosure[T1 : FromLua, T2 : FromLua, T3 : FromLua, T4 : FromLua, T5 : FromLua](fn: (LuaState, T1, T2, T3, T4, T5) => Unit): ScalaLuaClosure =
    ScalaLuaClosure(L => { fn(new LuaState(L), implicitly[FromLua[T1]].fromLua(L, L.value(1), LuaGeneratedImplicits.badArgFn1()), implicitly[FromLua[T2]].fromLua(L, L.value(2), LuaGeneratedImplicits.badArgFn2()), implicitly[FromLua[T3]].fromLua(L, L.value(3), LuaGeneratedImplicits.badArgFn3()), implicitly[FromLua[T4]].fromLua(L, L.value(4), LuaGeneratedImplicits.badArgFn4()), implicitly[FromLua[T5]].fromLua(L, L.value(5), LuaGeneratedImplicits.badArgFn5())); 0 })

  // Functions for 6 parameters
  implicit def nullSingleFunction62luaClosure[T1 : FromLua, T2 : FromLua, T3 : FromLua, T4 : FromLua, T5 : FromLua, T6 : FromLua, ReturnT: ToLua](fn: (T1, T2, T3, T4, T5, T6) => ReturnT): ScalaLuaClosure =
    ScalaLuaClosure(L => { L.push(implicitly[ToLua[ReturnT]].toLua(fn(implicitly[FromLua[T1]].fromLua(L, L.value(1), LuaGeneratedImplicits.badArgFn1()), implicitly[FromLua[T2]].fromLua(L, L.value(2), LuaGeneratedImplicits.badArgFn2()), implicitly[FromLua[T3]].fromLua(L, L.value(3), LuaGeneratedImplicits.badArgFn3()), implicitly[FromLua[T4]].fromLua(L, L.value(4), LuaGeneratedImplicits.badArgFn4()), implicitly[FromLua[T5]].fromLua(L, L.value(5), LuaGeneratedImplicits.badArgFn5()), implicitly[FromLua[T6]].fromLua(L, L.value(6), LuaGeneratedImplicits.badArgFn6()))).toLua(new LuaState(L))); 1 })
  implicit def nullFunction62luaClosure[T1 : FromLua, T2 : FromLua, T3 : FromLua, T4 : FromLua, T5 : FromLua, T6 : FromLua](fn: (T1, T2, T3, T4, T5, T6) => Seq[LuaObject]): ScalaLuaClosure =
    ScalaLuaClosure(L => { LuaGeneratedImplicits.pushSeqRet(L, fn(implicitly[FromLua[T1]].fromLua(L, L.value(1), LuaGeneratedImplicits.badArgFn1()), implicitly[FromLua[T2]].fromLua(L, L.value(2), LuaGeneratedImplicits.badArgFn2()), implicitly[FromLua[T3]].fromLua(L, L.value(3), LuaGeneratedImplicits.badArgFn3()), implicitly[FromLua[T4]].fromLua(L, L.value(4), LuaGeneratedImplicits.badArgFn4()), implicitly[FromLua[T5]].fromLua(L, L.value(5), LuaGeneratedImplicits.badArgFn5()), implicitly[FromLua[T6]].fromLua(L, L.value(6), LuaGeneratedImplicits.badArgFn6()))) })
  implicit def nullUnitFunction62luaClosure[T1 : FromLua, T2 : FromLua, T3 : FromLua, T4 : FromLua, T5 : FromLua, T6 : FromLua](fn: (T1, T2, T3, T4, T5, T6) => Unit): ScalaLuaClosure =
    ScalaLuaClosure(L => { fn(implicitly[FromLua[T1]].fromLua(L, L.value(1), LuaGeneratedImplicits.badArgFn1()), implicitly[FromLua[T2]].fromLua(L, L.value(2), LuaGeneratedImplicits.badArgFn2()), implicitly[FromLua[T3]].fromLua(L, L.value(3), LuaGeneratedImplicits.badArgFn3()), implicitly[FromLua[T4]].fromLua(L, L.value(4), LuaGeneratedImplicits.badArgFn4()), implicitly[FromLua[T5]].fromLua(L, L.value(5), LuaGeneratedImplicits.badArgFn5()), implicitly[FromLua[T6]].fromLua(L, L.value(6), LuaGeneratedImplicits.badArgFn6())); 0 })
  implicit def function62luaClosure[T1 : FromLua, T2 : FromLua, T3 : FromLua, T4 : FromLua, T5 : FromLua, T6 : FromLua, ReturnT: ToLua](fn: (LuaState, T1, T2, T3, T4, T5, T6) => ReturnT): ScalaLuaClosure =
    ScalaLuaClosure(L => { L.push(implicitly[ToLua[ReturnT]].toLua(fn(new LuaState(L), implicitly[FromLua[T1]].fromLua(L, L.value(1), LuaGeneratedImplicits.badArgFn1()), implicitly[FromLua[T2]].fromLua(L, L.value(2), LuaGeneratedImplicits.badArgFn2()), implicitly[FromLua[T3]].fromLua(L, L.value(3), LuaGeneratedImplicits.badArgFn3()), implicitly[FromLua[T4]].fromLua(L, L.value(4), LuaGeneratedImplicits.badArgFn4()), implicitly[FromLua[T5]].fromLua(L, L.value(5), LuaGeneratedImplicits.badArgFn5()), implicitly[FromLua[T6]].fromLua(L, L.value(6), LuaGeneratedImplicits.badArgFn6()))).toLua(new LuaState(L))); 1 })
  implicit def function62luaClosure[T1 : FromLua, T2 : FromLua, T3 : FromLua, T4 : FromLua, T5 : FromLua, T6 : FromLua](fn: (LuaState, T1, T2, T3, T4, T5, T6) => Seq[LuaObject]): ScalaLuaClosure =
    ScalaLuaClosure(L => { LuaGeneratedImplicits.pushSeqRet(L, fn(new LuaState(L), implicitly[FromLua[T1]].fromLua(L, L.value(1), LuaGeneratedImplicits.badArgFn1()), implicitly[FromLua[T2]].fromLua(L, L.value(2), LuaGeneratedImplicits.badArgFn2()), implicitly[FromLua[T3]].fromLua(L, L.value(3), LuaGeneratedImplicits.badArgFn3()), implicitly[FromLua[T4]].fromLua(L, L.value(4), LuaGeneratedImplicits.badArgFn4()), implicitly[FromLua[T5]].fromLua(L, L.value(5), LuaGeneratedImplicits.badArgFn5()), implicitly[FromLua[T6]].fromLua(L, L.value(6), LuaGeneratedImplicits.badArgFn6()))) })
  implicit def unitFunction62luaClosure[T1 : FromLua, T2 : FromLua, T3 : FromLua, T4 : FromLua, T5 : FromLua, T6 : FromLua](fn: (LuaState, T1, T2, T3, T4, T5, T6) => Unit): ScalaLuaClosure =
    ScalaLuaClosure(L => { fn(new LuaState(L), implicitly[FromLua[T1]].fromLua(L, L.value(1), LuaGeneratedImplicits.badArgFn1()), implicitly[FromLua[T2]].fromLua(L, L.value(2), LuaGeneratedImplicits.badArgFn2()), implicitly[FromLua[T3]].fromLua(L, L.value(3), LuaGeneratedImplicits.badArgFn3()), implicitly[FromLua[T4]].fromLua(L, L.value(4), LuaGeneratedImplicits.badArgFn4()), implicitly[FromLua[T5]].fromLua(L, L.value(5), LuaGeneratedImplicits.badArgFn5()), implicitly[FromLua[T6]].fromLua(L, L.value(6), LuaGeneratedImplicits.badArgFn6())); 0 })

  // Functions for 7 parameters
  implicit def nullSingleFunction72luaClosure[T1 : FromLua, T2 : FromLua, T3 : FromLua, T4 : FromLua, T5 : FromLua, T6 : FromLua, T7 : FromLua, ReturnT: ToLua](fn: (T1, T2, T3, T4, T5, T6, T7) => ReturnT): ScalaLuaClosure =
    ScalaLuaClosure(L => { L.push(implicitly[ToLua[ReturnT]].toLua(fn(implicitly[FromLua[T1]].fromLua(L, L.value(1), LuaGeneratedImplicits.badArgFn1()), implicitly[FromLua[T2]].fromLua(L, L.value(2), LuaGeneratedImplicits.badArgFn2()), implicitly[FromLua[T3]].fromLua(L, L.value(3), LuaGeneratedImplicits.badArgFn3()), implicitly[FromLua[T4]].fromLua(L, L.value(4), LuaGeneratedImplicits.badArgFn4()), implicitly[FromLua[T5]].fromLua(L, L.value(5), LuaGeneratedImplicits.badArgFn5()), implicitly[FromLua[T6]].fromLua(L, L.value(6), LuaGeneratedImplicits.badArgFn6()), implicitly[FromLua[T7]].fromLua(L, L.value(7), LuaGeneratedImplicits.badArgFn7()))).toLua(new LuaState(L))); 1 })
  implicit def nullFunction72luaClosure[T1 : FromLua, T2 : FromLua, T3 : FromLua, T4 : FromLua, T5 : FromLua, T6 : FromLua, T7 : FromLua](fn: (T1, T2, T3, T4, T5, T6, T7) => Seq[LuaObject]): ScalaLuaClosure =
    ScalaLuaClosure(L => { LuaGeneratedImplicits.pushSeqRet(L, fn(implicitly[FromLua[T1]].fromLua(L, L.value(1), LuaGeneratedImplicits.badArgFn1()), implicitly[FromLua[T2]].fromLua(L, L.value(2), LuaGeneratedImplicits.badArgFn2()), implicitly[FromLua[T3]].fromLua(L, L.value(3), LuaGeneratedImplicits.badArgFn3()), implicitly[FromLua[T4]].fromLua(L, L.value(4), LuaGeneratedImplicits.badArgFn4()), implicitly[FromLua[T5]].fromLua(L, L.value(5), LuaGeneratedImplicits.badArgFn5()), implicitly[FromLua[T6]].fromLua(L, L.value(6), LuaGeneratedImplicits.badArgFn6()), implicitly[FromLua[T7]].fromLua(L, L.value(7), LuaGeneratedImplicits.badArgFn7()))) })
  implicit def nullUnitFunction72luaClosure[T1 : FromLua, T2 : FromLua, T3 : FromLua, T4 : FromLua, T5 : FromLua, T6 : FromLua, T7 : FromLua](fn: (T1, T2, T3, T4, T5, T6, T7) => Unit): ScalaLuaClosure =
    ScalaLuaClosure(L => { fn(implicitly[FromLua[T1]].fromLua(L, L.value(1), LuaGeneratedImplicits.badArgFn1()), implicitly[FromLua[T2]].fromLua(L, L.value(2), LuaGeneratedImplicits.badArgFn2()), implicitly[FromLua[T3]].fromLua(L, L.value(3), LuaGeneratedImplicits.badArgFn3()), implicitly[FromLua[T4]].fromLua(L, L.value(4), LuaGeneratedImplicits.badArgFn4()), implicitly[FromLua[T5]].fromLua(L, L.value(5), LuaGeneratedImplicits.badArgFn5()), implicitly[FromLua[T6]].fromLua(L, L.value(6), LuaGeneratedImplicits.badArgFn6()), implicitly[FromLua[T7]].fromLua(L, L.value(7), LuaGeneratedImplicits.badArgFn7())); 0 })
  implicit def function72luaClosure[T1 : FromLua, T2 : FromLua, T3 : FromLua, T4 : FromLua, T5 : FromLua, T6 : FromLua, T7 : FromLua, ReturnT: ToLua](fn: (LuaState, T1, T2, T3, T4, T5, T6, T7) => ReturnT): ScalaLuaClosure =
    ScalaLuaClosure(L => { L.push(implicitly[ToLua[ReturnT]].toLua(fn(new LuaState(L), implicitly[FromLua[T1]].fromLua(L, L.value(1), LuaGeneratedImplicits.badArgFn1()), implicitly[FromLua[T2]].fromLua(L, L.value(2), LuaGeneratedImplicits.badArgFn2()), implicitly[FromLua[T3]].fromLua(L, L.value(3), LuaGeneratedImplicits.badArgFn3()), implicitly[FromLua[T4]].fromLua(L, L.value(4), LuaGeneratedImplicits.badArgFn4()), implicitly[FromLua[T5]].fromLua(L, L.value(5), LuaGeneratedImplicits.badArgFn5()), implicitly[FromLua[T6]].fromLua(L, L.value(6), LuaGeneratedImplicits.badArgFn6()), implicitly[FromLua[T7]].fromLua(L, L.value(7), LuaGeneratedImplicits.badArgFn7()))).toLua(new LuaState(L))); 1 })
  implicit def function72luaClosure[T1 : FromLua, T2 : FromLua, T3 : FromLua, T4 : FromLua, T5 : FromLua, T6 : FromLua, T7 : FromLua](fn: (LuaState, T1, T2, T3, T4, T5, T6, T7) => Seq[LuaObject]): ScalaLuaClosure =
    ScalaLuaClosure(L => { LuaGeneratedImplicits.pushSeqRet(L, fn(new LuaState(L), implicitly[FromLua[T1]].fromLua(L, L.value(1), LuaGeneratedImplicits.badArgFn1()), implicitly[FromLua[T2]].fromLua(L, L.value(2), LuaGeneratedImplicits.badArgFn2()), implicitly[FromLua[T3]].fromLua(L, L.value(3), LuaGeneratedImplicits.badArgFn3()), implicitly[FromLua[T4]].fromLua(L, L.value(4), LuaGeneratedImplicits.badArgFn4()), implicitly[FromLua[T5]].fromLua(L, L.value(5), LuaGeneratedImplicits.badArgFn5()), implicitly[FromLua[T6]].fromLua(L, L.value(6), LuaGeneratedImplicits.badArgFn6()), implicitly[FromLua[T7]].fromLua(L, L.value(7), LuaGeneratedImplicits.badArgFn7()))) })
  implicit def unitFunction72luaClosure[T1 : FromLua, T2 : FromLua, T3 : FromLua, T4 : FromLua, T5 : FromLua, T6 : FromLua, T7 : FromLua](fn: (LuaState, T1, T2, T3, T4, T5, T6, T7) => Unit): ScalaLuaClosure =
    ScalaLuaClosure(L => { fn(new LuaState(L), implicitly[FromLua[T1]].fromLua(L, L.value(1), LuaGeneratedImplicits.badArgFn1()), implicitly[FromLua[T2]].fromLua(L, L.value(2), LuaGeneratedImplicits.badArgFn2()), implicitly[FromLua[T3]].fromLua(L, L.value(3), LuaGeneratedImplicits.badArgFn3()), implicitly[FromLua[T4]].fromLua(L, L.value(4), LuaGeneratedImplicits.badArgFn4()), implicitly[FromLua[T5]].fromLua(L, L.value(5), LuaGeneratedImplicits.badArgFn5()), implicitly[FromLua[T6]].fromLua(L, L.value(6), LuaGeneratedImplicits.badArgFn6()), implicitly[FromLua[T7]].fromLua(L, L.value(7), LuaGeneratedImplicits.badArgFn7())); 0 })

  // Functions for 8 parameters
  implicit def nullSingleFunction82luaClosure[T1 : FromLua, T2 : FromLua, T3 : FromLua, T4 : FromLua, T5 : FromLua, T6 : FromLua, T7 : FromLua, T8 : FromLua, ReturnT: ToLua](fn: (T1, T2, T3, T4, T5, T6, T7, T8) => ReturnT): ScalaLuaClosure =
    ScalaLuaClosure(L => { L.push(implicitly[ToLua[ReturnT]].toLua(fn(implicitly[FromLua[T1]].fromLua(L, L.value(1), LuaGeneratedImplicits.badArgFn1()), implicitly[FromLua[T2]].fromLua(L, L.value(2), LuaGeneratedImplicits.badArgFn2()), implicitly[FromLua[T3]].fromLua(L, L.value(3), LuaGeneratedImplicits.badArgFn3()), implicitly[FromLua[T4]].fromLua(L, L.value(4), LuaGeneratedImplicits.badArgFn4()), implicitly[FromLua[T5]].fromLua(L, L.value(5), LuaGeneratedImplicits.badArgFn5()), implicitly[FromLua[T6]].fromLua(L, L.value(6), LuaGeneratedImplicits.badArgFn6()), implicitly[FromLua[T7]].fromLua(L, L.value(7), LuaGeneratedImplicits.badArgFn7()), implicitly[FromLua[T8]].fromLua(L, L.value(8), LuaGeneratedImplicits.badArgFn8()))).toLua(new LuaState(L))); 1 })
  implicit def nullFunction82luaClosure[T1 : FromLua, T2 : FromLua, T3 : FromLua, T4 : FromLua, T5 : FromLua, T6 : FromLua, T7 : FromLua, T8 : FromLua](fn: (T1, T2, T3, T4, T5, T6, T7, T8) => Seq[LuaObject]): ScalaLuaClosure =
    ScalaLuaClosure(L => { LuaGeneratedImplicits.pushSeqRet(L, fn(implicitly[FromLua[T1]].fromLua(L, L.value(1), LuaGeneratedImplicits.badArgFn1()), implicitly[FromLua[T2]].fromLua(L, L.value(2), LuaGeneratedImplicits.badArgFn2()), implicitly[FromLua[T3]].fromLua(L, L.value(3), LuaGeneratedImplicits.badArgFn3()), implicitly[FromLua[T4]].fromLua(L, L.value(4), LuaGeneratedImplicits.badArgFn4()), implicitly[FromLua[T5]].fromLua(L, L.value(5), LuaGeneratedImplicits.badArgFn5()), implicitly[FromLua[T6]].fromLua(L, L.value(6), LuaGeneratedImplicits.badArgFn6()), implicitly[FromLua[T7]].fromLua(L, L.value(7), LuaGeneratedImplicits.badArgFn7()), implicitly[FromLua[T8]].fromLua(L, L.value(8), LuaGeneratedImplicits.badArgFn8()))) })
  implicit def nullUnitFunction82luaClosure[T1 : FromLua, T2 : FromLua, T3 : FromLua, T4 : FromLua, T5 : FromLua, T6 : FromLua, T7 : FromLua, T8 : FromLua](fn: (T1, T2, T3, T4, T5, T6, T7, T8) => Unit): ScalaLuaClosure =
    ScalaLuaClosure(L => { fn(implicitly[FromLua[T1]].fromLua(L, L.value(1), LuaGeneratedImplicits.badArgFn1()), implicitly[FromLua[T2]].fromLua(L, L.value(2), LuaGeneratedImplicits.badArgFn2()), implicitly[FromLua[T3]].fromLua(L, L.value(3), LuaGeneratedImplicits.badArgFn3()), implicitly[FromLua[T4]].fromLua(L, L.value(4), LuaGeneratedImplicits.badArgFn4()), implicitly[FromLua[T5]].fromLua(L, L.value(5), LuaGeneratedImplicits.badArgFn5()), implicitly[FromLua[T6]].fromLua(L, L.value(6), LuaGeneratedImplicits.badArgFn6()), implicitly[FromLua[T7]].fromLua(L, L.value(7), LuaGeneratedImplicits.badArgFn7()), implicitly[FromLua[T8]].fromLua(L, L.value(8), LuaGeneratedImplicits.badArgFn8())); 0 })
  implicit def function82luaClosure[T1 : FromLua, T2 : FromLua, T3 : FromLua, T4 : FromLua, T5 : FromLua, T6 : FromLua, T7 : FromLua, T8 : FromLua, ReturnT: ToLua](fn: (LuaState, T1, T2, T3, T4, T5, T6, T7, T8) => ReturnT): ScalaLuaClosure =
    ScalaLuaClosure(L => { L.push(implicitly[ToLua[ReturnT]].toLua(fn(new LuaState(L), implicitly[FromLua[T1]].fromLua(L, L.value(1), LuaGeneratedImplicits.badArgFn1()), implicitly[FromLua[T2]].fromLua(L, L.value(2), LuaGeneratedImplicits.badArgFn2()), implicitly[FromLua[T3]].fromLua(L, L.value(3), LuaGeneratedImplicits.badArgFn3()), implicitly[FromLua[T4]].fromLua(L, L.value(4), LuaGeneratedImplicits.badArgFn4()), implicitly[FromLua[T5]].fromLua(L, L.value(5), LuaGeneratedImplicits.badArgFn5()), implicitly[FromLua[T6]].fromLua(L, L.value(6), LuaGeneratedImplicits.badArgFn6()), implicitly[FromLua[T7]].fromLua(L, L.value(7), LuaGeneratedImplicits.badArgFn7()), implicitly[FromLua[T8]].fromLua(L, L.value(8), LuaGeneratedImplicits.badArgFn8()))).toLua(new LuaState(L))); 1 })
  implicit def function82luaClosure[T1 : FromLua, T2 : FromLua, T3 : FromLua, T4 : FromLua, T5 : FromLua, T6 : FromLua, T7 : FromLua, T8 : FromLua](fn: (LuaState, T1, T2, T3, T4, T5, T6, T7, T8) => Seq[LuaObject]): ScalaLuaClosure =
    ScalaLuaClosure(L => { LuaGeneratedImplicits.pushSeqRet(L, fn(new LuaState(L), implicitly[FromLua[T1]].fromLua(L, L.value(1), LuaGeneratedImplicits.badArgFn1()), implicitly[FromLua[T2]].fromLua(L, L.value(2), LuaGeneratedImplicits.badArgFn2()), implicitly[FromLua[T3]].fromLua(L, L.value(3), LuaGeneratedImplicits.badArgFn3()), implicitly[FromLua[T4]].fromLua(L, L.value(4), LuaGeneratedImplicits.badArgFn4()), implicitly[FromLua[T5]].fromLua(L, L.value(5), LuaGeneratedImplicits.badArgFn5()), implicitly[FromLua[T6]].fromLua(L, L.value(6), LuaGeneratedImplicits.badArgFn6()), implicitly[FromLua[T7]].fromLua(L, L.value(7), LuaGeneratedImplicits.badArgFn7()), implicitly[FromLua[T8]].fromLua(L, L.value(8), LuaGeneratedImplicits.badArgFn8()))) })
  implicit def unitFunction82luaClosure[T1 : FromLua, T2 : FromLua, T3 : FromLua, T4 : FromLua, T5 : FromLua, T6 : FromLua, T7 : FromLua, T8 : FromLua](fn: (LuaState, T1, T2, T3, T4, T5, T6, T7, T8) => Unit): ScalaLuaClosure =
    ScalaLuaClosure(L => { fn(new LuaState(L), implicitly[FromLua[T1]].fromLua(L, L.value(1), LuaGeneratedImplicits.badArgFn1()), implicitly[FromLua[T2]].fromLua(L, L.value(2), LuaGeneratedImplicits.badArgFn2()), implicitly[FromLua[T3]].fromLua(L, L.value(3), LuaGeneratedImplicits.badArgFn3()), implicitly[FromLua[T4]].fromLua(L, L.value(4), LuaGeneratedImplicits.badArgFn4()), implicitly[FromLua[T5]].fromLua(L, L.value(5), LuaGeneratedImplicits.badArgFn5()), implicitly[FromLua[T6]].fromLua(L, L.value(6), LuaGeneratedImplicits.badArgFn6()), implicitly[FromLua[T7]].fromLua(L, L.value(7), LuaGeneratedImplicits.badArgFn7()), implicitly[FromLua[T8]].fromLua(L, L.value(8), LuaGeneratedImplicits.badArgFn8())); 0 })

  // Functions for 9 parameters
  implicit def nullSingleFunction92luaClosure[T1 : FromLua, T2 : FromLua, T3 : FromLua, T4 : FromLua, T5 : FromLua, T6 : FromLua, T7 : FromLua, T8 : FromLua, T9 : FromLua, ReturnT: ToLua](fn: (T1, T2, T3, T4, T5, T6, T7, T8, T9) => ReturnT): ScalaLuaClosure =
    ScalaLuaClosure(L => { L.push(implicitly[ToLua[ReturnT]].toLua(fn(implicitly[FromLua[T1]].fromLua(L, L.value(1), LuaGeneratedImplicits.badArgFn1()), implicitly[FromLua[T2]].fromLua(L, L.value(2), LuaGeneratedImplicits.badArgFn2()), implicitly[FromLua[T3]].fromLua(L, L.value(3), LuaGeneratedImplicits.badArgFn3()), implicitly[FromLua[T4]].fromLua(L, L.value(4), LuaGeneratedImplicits.badArgFn4()), implicitly[FromLua[T5]].fromLua(L, L.value(5), LuaGeneratedImplicits.badArgFn5()), implicitly[FromLua[T6]].fromLua(L, L.value(6), LuaGeneratedImplicits.badArgFn6()), implicitly[FromLua[T7]].fromLua(L, L.value(7), LuaGeneratedImplicits.badArgFn7()), implicitly[FromLua[T8]].fromLua(L, L.value(8), LuaGeneratedImplicits.badArgFn8()), implicitly[FromLua[T9]].fromLua(L, L.value(9), LuaGeneratedImplicits.badArgFn9()))).toLua(new LuaState(L))); 1 })
  implicit def nullFunction92luaClosure[T1 : FromLua, T2 : FromLua, T3 : FromLua, T4 : FromLua, T5 : FromLua, T6 : FromLua, T7 : FromLua, T8 : FromLua, T9 : FromLua](fn: (T1, T2, T3, T4, T5, T6, T7, T8, T9) => Seq[LuaObject]): ScalaLuaClosure =
    ScalaLuaClosure(L => { LuaGeneratedImplicits.pushSeqRet(L, fn(implicitly[FromLua[T1]].fromLua(L, L.value(1), LuaGeneratedImplicits.badArgFn1()), implicitly[FromLua[T2]].fromLua(L, L.value(2), LuaGeneratedImplicits.badArgFn2()), implicitly[FromLua[T3]].fromLua(L, L.value(3), LuaGeneratedImplicits.badArgFn3()), implicitly[FromLua[T4]].fromLua(L, L.value(4), LuaGeneratedImplicits.badArgFn4()), implicitly[FromLua[T5]].fromLua(L, L.value(5), LuaGeneratedImplicits.badArgFn5()), implicitly[FromLua[T6]].fromLua(L, L.value(6), LuaGeneratedImplicits.badArgFn6()), implicitly[FromLua[T7]].fromLua(L, L.value(7), LuaGeneratedImplicits.badArgFn7()), implicitly[FromLua[T8]].fromLua(L, L.value(8), LuaGeneratedImplicits.badArgFn8()), implicitly[FromLua[T9]].fromLua(L, L.value(9), LuaGeneratedImplicits.badArgFn9()))) })
  implicit def nullUnitFunction92luaClosure[T1 : FromLua, T2 : FromLua, T3 : FromLua, T4 : FromLua, T5 : FromLua, T6 : FromLua, T7 : FromLua, T8 : FromLua, T9 : FromLua](fn: (T1, T2, T3, T4, T5, T6, T7, T8, T9) => Unit): ScalaLuaClosure =
    ScalaLuaClosure(L => { fn(implicitly[FromLua[T1]].fromLua(L, L.value(1), LuaGeneratedImplicits.badArgFn1()), implicitly[FromLua[T2]].fromLua(L, L.value(2), LuaGeneratedImplicits.badArgFn2()), implicitly[FromLua[T3]].fromLua(L, L.value(3), LuaGeneratedImplicits.badArgFn3()), implicitly[FromLua[T4]].fromLua(L, L.value(4), LuaGeneratedImplicits.badArgFn4()), implicitly[FromLua[T5]].fromLua(L, L.value(5), LuaGeneratedImplicits.badArgFn5()), implicitly[FromLua[T6]].fromLua(L, L.value(6), LuaGeneratedImplicits.badArgFn6()), implicitly[FromLua[T7]].fromLua(L, L.value(7), LuaGeneratedImplicits.badArgFn7()), implicitly[FromLua[T8]].fromLua(L, L.value(8), LuaGeneratedImplicits.badArgFn8()), implicitly[FromLua[T9]].fromLua(L, L.value(9), LuaGeneratedImplicits.badArgFn9())); 0 })
  implicit def function92luaClosure[T1 : FromLua, T2 : FromLua, T3 : FromLua, T4 : FromLua, T5 : FromLua, T6 : FromLua, T7 : FromLua, T8 : FromLua, T9 : FromLua, ReturnT: ToLua](fn: (LuaState, T1, T2, T3, T4, T5, T6, T7, T8, T9) => ReturnT): ScalaLuaClosure =
    ScalaLuaClosure(L => { L.push(implicitly[ToLua[ReturnT]].toLua(fn(new LuaState(L), implicitly[FromLua[T1]].fromLua(L, L.value(1), LuaGeneratedImplicits.badArgFn1()), implicitly[FromLua[T2]].fromLua(L, L.value(2), LuaGeneratedImplicits.badArgFn2()), implicitly[FromLua[T3]].fromLua(L, L.value(3), LuaGeneratedImplicits.badArgFn3()), implicitly[FromLua[T4]].fromLua(L, L.value(4), LuaGeneratedImplicits.badArgFn4()), implicitly[FromLua[T5]].fromLua(L, L.value(5), LuaGeneratedImplicits.badArgFn5()), implicitly[FromLua[T6]].fromLua(L, L.value(6), LuaGeneratedImplicits.badArgFn6()), implicitly[FromLua[T7]].fromLua(L, L.value(7), LuaGeneratedImplicits.badArgFn7()), implicitly[FromLua[T8]].fromLua(L, L.value(8), LuaGeneratedImplicits.badArgFn8()), implicitly[FromLua[T9]].fromLua(L, L.value(9), LuaGeneratedImplicits.badArgFn9()))).toLua(new LuaState(L))); 1 })
  implicit def function92luaClosure[T1 : FromLua, T2 : FromLua, T3 : FromLua, T4 : FromLua, T5 : FromLua, T6 : FromLua, T7 : FromLua, T8 : FromLua, T9 : FromLua](fn: (LuaState, T1, T2, T3, T4, T5, T6, T7, T8, T9) => Seq[LuaObject]): ScalaLuaClosure =
    ScalaLuaClosure(L => { LuaGeneratedImplicits.pushSeqRet(L, fn(new LuaState(L), implicitly[FromLua[T1]].fromLua(L, L.value(1), LuaGeneratedImplicits.badArgFn1()), implicitly[FromLua[T2]].fromLua(L, L.value(2), LuaGeneratedImplicits.badArgFn2()), implicitly[FromLua[T3]].fromLua(L, L.value(3), LuaGeneratedImplicits.badArgFn3()), implicitly[FromLua[T4]].fromLua(L, L.value(4), LuaGeneratedImplicits.badArgFn4()), implicitly[FromLua[T5]].fromLua(L, L.value(5), LuaGeneratedImplicits.badArgFn5()), implicitly[FromLua[T6]].fromLua(L, L.value(6), LuaGeneratedImplicits.badArgFn6()), implicitly[FromLua[T7]].fromLua(L, L.value(7), LuaGeneratedImplicits.badArgFn7()), implicitly[FromLua[T8]].fromLua(L, L.value(8), LuaGeneratedImplicits.badArgFn8()), implicitly[FromLua[T9]].fromLua(L, L.value(9), LuaGeneratedImplicits.badArgFn9()))) })
  implicit def unitFunction92luaClosure[T1 : FromLua, T2 : FromLua, T3 : FromLua, T4 : FromLua, T5 : FromLua, T6 : FromLua, T7 : FromLua, T8 : FromLua, T9 : FromLua](fn: (LuaState, T1, T2, T3, T4, T5, T6, T7, T8, T9) => Unit): ScalaLuaClosure =
    ScalaLuaClosure(L => { fn(new LuaState(L), implicitly[FromLua[T1]].fromLua(L, L.value(1), LuaGeneratedImplicits.badArgFn1()), implicitly[FromLua[T2]].fromLua(L, L.value(2), LuaGeneratedImplicits.badArgFn2()), implicitly[FromLua[T3]].fromLua(L, L.value(3), LuaGeneratedImplicits.badArgFn3()), implicitly[FromLua[T4]].fromLua(L, L.value(4), LuaGeneratedImplicits.badArgFn4()), implicitly[FromLua[T5]].fromLua(L, L.value(5), LuaGeneratedImplicits.badArgFn5()), implicitly[FromLua[T6]].fromLua(L, L.value(6), LuaGeneratedImplicits.badArgFn6()), implicitly[FromLua[T7]].fromLua(L, L.value(7), LuaGeneratedImplicits.badArgFn7()), implicitly[FromLua[T8]].fromLua(L, L.value(8), LuaGeneratedImplicits.badArgFn8()), implicitly[FromLua[T9]].fromLua(L, L.value(9), LuaGeneratedImplicits.badArgFn9())); 0 })

  // Functions for 10 parameters
  implicit def nullSingleFunction102luaClosure[T1 : FromLua, T2 : FromLua, T3 : FromLua, T4 : FromLua, T5 : FromLua, T6 : FromLua, T7 : FromLua, T8 : FromLua, T9 : FromLua, T10 : FromLua, ReturnT: ToLua](fn: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10) => ReturnT): ScalaLuaClosure =
    ScalaLuaClosure(L => { L.push(implicitly[ToLua[ReturnT]].toLua(fn(implicitly[FromLua[T1]].fromLua(L, L.value(1), LuaGeneratedImplicits.badArgFn1()), implicitly[FromLua[T2]].fromLua(L, L.value(2), LuaGeneratedImplicits.badArgFn2()), implicitly[FromLua[T3]].fromLua(L, L.value(3), LuaGeneratedImplicits.badArgFn3()), implicitly[FromLua[T4]].fromLua(L, L.value(4), LuaGeneratedImplicits.badArgFn4()), implicitly[FromLua[T5]].fromLua(L, L.value(5), LuaGeneratedImplicits.badArgFn5()), implicitly[FromLua[T6]].fromLua(L, L.value(6), LuaGeneratedImplicits.badArgFn6()), implicitly[FromLua[T7]].fromLua(L, L.value(7), LuaGeneratedImplicits.badArgFn7()), implicitly[FromLua[T8]].fromLua(L, L.value(8), LuaGeneratedImplicits.badArgFn8()), implicitly[FromLua[T9]].fromLua(L, L.value(9), LuaGeneratedImplicits.badArgFn9()), implicitly[FromLua[T10]].fromLua(L, L.value(10), LuaGeneratedImplicits.badArgFn10()))).toLua(new LuaState(L))); 1 })
  implicit def nullFunction102luaClosure[T1 : FromLua, T2 : FromLua, T3 : FromLua, T4 : FromLua, T5 : FromLua, T6 : FromLua, T7 : FromLua, T8 : FromLua, T9 : FromLua, T10 : FromLua](fn: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10) => Seq[LuaObject]): ScalaLuaClosure =
    ScalaLuaClosure(L => { LuaGeneratedImplicits.pushSeqRet(L, fn(implicitly[FromLua[T1]].fromLua(L, L.value(1), LuaGeneratedImplicits.badArgFn1()), implicitly[FromLua[T2]].fromLua(L, L.value(2), LuaGeneratedImplicits.badArgFn2()), implicitly[FromLua[T3]].fromLua(L, L.value(3), LuaGeneratedImplicits.badArgFn3()), implicitly[FromLua[T4]].fromLua(L, L.value(4), LuaGeneratedImplicits.badArgFn4()), implicitly[FromLua[T5]].fromLua(L, L.value(5), LuaGeneratedImplicits.badArgFn5()), implicitly[FromLua[T6]].fromLua(L, L.value(6), LuaGeneratedImplicits.badArgFn6()), implicitly[FromLua[T7]].fromLua(L, L.value(7), LuaGeneratedImplicits.badArgFn7()), implicitly[FromLua[T8]].fromLua(L, L.value(8), LuaGeneratedImplicits.badArgFn8()), implicitly[FromLua[T9]].fromLua(L, L.value(9), LuaGeneratedImplicits.badArgFn9()), implicitly[FromLua[T10]].fromLua(L, L.value(10), LuaGeneratedImplicits.badArgFn10()))) })
  implicit def nullUnitFunction102luaClosure[T1 : FromLua, T2 : FromLua, T3 : FromLua, T4 : FromLua, T5 : FromLua, T6 : FromLua, T7 : FromLua, T8 : FromLua, T9 : FromLua, T10 : FromLua](fn: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10) => Unit): ScalaLuaClosure =
    ScalaLuaClosure(L => { fn(implicitly[FromLua[T1]].fromLua(L, L.value(1), LuaGeneratedImplicits.badArgFn1()), implicitly[FromLua[T2]].fromLua(L, L.value(2), LuaGeneratedImplicits.badArgFn2()), implicitly[FromLua[T3]].fromLua(L, L.value(3), LuaGeneratedImplicits.badArgFn3()), implicitly[FromLua[T4]].fromLua(L, L.value(4), LuaGeneratedImplicits.badArgFn4()), implicitly[FromLua[T5]].fromLua(L, L.value(5), LuaGeneratedImplicits.badArgFn5()), implicitly[FromLua[T6]].fromLua(L, L.value(6), LuaGeneratedImplicits.badArgFn6()), implicitly[FromLua[T7]].fromLua(L, L.value(7), LuaGeneratedImplicits.badArgFn7()), implicitly[FromLua[T8]].fromLua(L, L.value(8), LuaGeneratedImplicits.badArgFn8()), implicitly[FromLua[T9]].fromLua(L, L.value(9), LuaGeneratedImplicits.badArgFn9()), implicitly[FromLua[T10]].fromLua(L, L.value(10), LuaGeneratedImplicits.badArgFn10())); 0 })
  implicit def function102luaClosure[T1 : FromLua, T2 : FromLua, T3 : FromLua, T4 : FromLua, T5 : FromLua, T6 : FromLua, T7 : FromLua, T8 : FromLua, T9 : FromLua, T10 : FromLua, ReturnT: ToLua](fn: (LuaState, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10) => ReturnT): ScalaLuaClosure =
    ScalaLuaClosure(L => { L.push(implicitly[ToLua[ReturnT]].toLua(fn(new LuaState(L), implicitly[FromLua[T1]].fromLua(L, L.value(1), LuaGeneratedImplicits.badArgFn1()), implicitly[FromLua[T2]].fromLua(L, L.value(2), LuaGeneratedImplicits.badArgFn2()), implicitly[FromLua[T3]].fromLua(L, L.value(3), LuaGeneratedImplicits.badArgFn3()), implicitly[FromLua[T4]].fromLua(L, L.value(4), LuaGeneratedImplicits.badArgFn4()), implicitly[FromLua[T5]].fromLua(L, L.value(5), LuaGeneratedImplicits.badArgFn5()), implicitly[FromLua[T6]].fromLua(L, L.value(6), LuaGeneratedImplicits.badArgFn6()), implicitly[FromLua[T7]].fromLua(L, L.value(7), LuaGeneratedImplicits.badArgFn7()), implicitly[FromLua[T8]].fromLua(L, L.value(8), LuaGeneratedImplicits.badArgFn8()), implicitly[FromLua[T9]].fromLua(L, L.value(9), LuaGeneratedImplicits.badArgFn9()), implicitly[FromLua[T10]].fromLua(L, L.value(10), LuaGeneratedImplicits.badArgFn10()))).toLua(new LuaState(L))); 1 })
  implicit def function102luaClosure[T1 : FromLua, T2 : FromLua, T3 : FromLua, T4 : FromLua, T5 : FromLua, T6 : FromLua, T7 : FromLua, T8 : FromLua, T9 : FromLua, T10 : FromLua](fn: (LuaState, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10) => Seq[LuaObject]): ScalaLuaClosure =
    ScalaLuaClosure(L => { LuaGeneratedImplicits.pushSeqRet(L, fn(new LuaState(L), implicitly[FromLua[T1]].fromLua(L, L.value(1), LuaGeneratedImplicits.badArgFn1()), implicitly[FromLua[T2]].fromLua(L, L.value(2), LuaGeneratedImplicits.badArgFn2()), implicitly[FromLua[T3]].fromLua(L, L.value(3), LuaGeneratedImplicits.badArgFn3()), implicitly[FromLua[T4]].fromLua(L, L.value(4), LuaGeneratedImplicits.badArgFn4()), implicitly[FromLua[T5]].fromLua(L, L.value(5), LuaGeneratedImplicits.badArgFn5()), implicitly[FromLua[T6]].fromLua(L, L.value(6), LuaGeneratedImplicits.badArgFn6()), implicitly[FromLua[T7]].fromLua(L, L.value(7), LuaGeneratedImplicits.badArgFn7()), implicitly[FromLua[T8]].fromLua(L, L.value(8), LuaGeneratedImplicits.badArgFn8()), implicitly[FromLua[T9]].fromLua(L, L.value(9), LuaGeneratedImplicits.badArgFn9()), implicitly[FromLua[T10]].fromLua(L, L.value(10), LuaGeneratedImplicits.badArgFn10()))) })
  implicit def unitFunction102luaClosure[T1 : FromLua, T2 : FromLua, T3 : FromLua, T4 : FromLua, T5 : FromLua, T6 : FromLua, T7 : FromLua, T8 : FromLua, T9 : FromLua, T10 : FromLua](fn: (LuaState, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10) => Unit): ScalaLuaClosure =
    ScalaLuaClosure(L => { fn(new LuaState(L), implicitly[FromLua[T1]].fromLua(L, L.value(1), LuaGeneratedImplicits.badArgFn1()), implicitly[FromLua[T2]].fromLua(L, L.value(2), LuaGeneratedImplicits.badArgFn2()), implicitly[FromLua[T3]].fromLua(L, L.value(3), LuaGeneratedImplicits.badArgFn3()), implicitly[FromLua[T4]].fromLua(L, L.value(4), LuaGeneratedImplicits.badArgFn4()), implicitly[FromLua[T5]].fromLua(L, L.value(5), LuaGeneratedImplicits.badArgFn5()), implicitly[FromLua[T6]].fromLua(L, L.value(6), LuaGeneratedImplicits.badArgFn6()), implicitly[FromLua[T7]].fromLua(L, L.value(7), LuaGeneratedImplicits.badArgFn7()), implicitly[FromLua[T8]].fromLua(L, L.value(8), LuaGeneratedImplicits.badArgFn8()), implicitly[FromLua[T9]].fromLua(L, L.value(9), LuaGeneratedImplicits.badArgFn9()), implicitly[FromLua[T10]].fromLua(L, L.value(10), LuaGeneratedImplicits.badArgFn10())); 0 })
}
