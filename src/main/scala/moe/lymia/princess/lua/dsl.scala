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

import scala.collection.mutable
import scala.collection.JavaConverters._
import scala.reflect.ClassTag

import scala.language.implicitConversions

class LuaUserdataType[T : ClassTag] {
  private val metatableInitializers = new mutable.ArrayBuffer[(LuaState, LuaTable) => Unit]
  protected def registerInit(fn: (LuaState, LuaTable) => Unit) = metatableInitializers.append(fn)
  def tag = implicitly[ClassTag[T]]
  def getMetatable(L: LuaState) = {
    if(metatableInitializers.isEmpty) None
    else {
      val table = new LuaTable()
      metatableInitializers.foreach(_(L, table))
      Some(table)
    }
  }
}

case class LuaThreadWrapper(thread: Lua)
case class LuaExecWrapper(fn: LuaState => Any)
class LuaOutObject(val wrapped: Any) extends AnyVal {
  def toLua(L: LuaState) = wrapped match {
    case LuaExecWrapper(fn) => fn(L)
    case any => any
  }
}
sealed trait ToLua[T] {
  def toLua(t: T): LuaOutObject
}
private[lua] trait ToLuaEscape[T] extends ToLua[T]
sealed trait FromLua[T] {
  def fromLua(L: Lua, v: Any, source: String): T
}
sealed trait LuaParameter[T] extends ToLua[T] with FromLua[T]

case class LuaClosure(fn: Any)
case class ScalaLuaClosure(fn: LuaState => Seq[LuaOutObject]) extends AnyVal

class LuaReturnWrapper(L: LuaState, val wrapped: Any) {
  def as[T : FromLua]: T = wrapped.fromLua[T](L)
}

case object LuaNil

trait LuaImplicits extends LuaGeneratedImplicits {
  type LuaRet = Seq[LuaOutObject]
  def LuaRet(v: LuaOutObject*) = Seq(v : _*)

  private def typerror[T](L: Lua, source: String, got: String, expected: String): T = {
    L.error(s"bad argument $source ($expected expected, got $got)")
    sys.error("L.error returned unexpectedly!")
  }
  private def typerror[T](L: Lua, source: String, got: Any, expected: Int): T =
    typerror(L, source, Lua.typeName(Lua.`type`(got)), Lua.typeName(expected))

  // Function wrapper
  implicit def unitClosure2luaClosure(fn: LuaState => Unit): ScalaLuaClosure = ScalaLuaClosure(L => { fn(L); Seq() })
  implicit def closure2luaClosure(fn: LuaState => Seq[LuaOutObject]): ScalaLuaClosure = ScalaLuaClosure(fn)
  implicit def nullUnitClosure2luaClosure(fn: () => Unit): ScalaLuaClosure = ScalaLuaClosure(L => { fn(); Seq() })
  implicit def nullClosure2luaClosure(fn: () => Seq[LuaOutObject]): ScalaLuaClosure = ScalaLuaClosure(L => { fn() })

  private class LuaClosureWrapper(fn: ScalaLuaClosure) extends LuaJavaCallback {
    override def luaFunction(Lr: Lua) = {
      val L = new LuaState(Lr)
      val rets = fn.fn(L)
      for(ret <- rets) Lr.push(ret.toLua(L))
      rets.length
    }
  }
  implicit object ToLuaScalaLuaClosure extends ToLua[ScalaLuaClosure] {
    override def toLua(t: ScalaLuaClosure): LuaOutObject = new LuaOutObject(new LuaClosureWrapper(t))
  }

  // Lua Object wrappers
  implicit def toLua2luaObject[T : ToLua](obj: T): LuaOutObject = implicitly[ToLua[T]].toLua(obj)
  implicit class FromLuaAnyExtension(obj: Any) {
    def fromLua[T : FromLua](L: LuaState, source: String = "<java function>") =
      implicitly[FromLua[T]].fromLua(L.L, obj, source)
    private[lua] def returnWrapper(L: LuaState) = new LuaReturnWrapper(L, obj)
  }

  implicit object LuaParameterAny extends LuaParameter[Any] {
    override def toLua(t: Any): LuaOutObject = new LuaOutObject(t)
    override def fromLua(L: Lua, v: Any, source: String) = v
  }
  implicit object ToLuaReturnWrapper extends ToLua[LuaReturnWrapper] {
    override def toLua(t: LuaReturnWrapper): LuaOutObject = new LuaOutObject(t.wrapped)
  }
  implicit object LuaParameterNil extends ToLua[LuaNil.type] {
    override def toLua(t: LuaNil.type): LuaOutObject = new LuaOutObject(Lua.NIL)
  }

  private class LuaParameterNumeric[N : Numeric](toN: Double => N) extends LuaParameter[N] {
    override def toLua(n: N) = new LuaOutObject(implicitly[Numeric[N]].toDouble(n))
    override def fromLua(L: Lua, v: Any, source: String) = v match {
      case n: java.lang.Double => toN(n)
      case _ => typerror(L, source, v, Lua.TNUMBER)
    }
  }
  implicit val LuaParameterByte   : LuaParameter[Byte  ] = new LuaParameterNumeric[Byte  ](_.toByte)
  implicit val LuaParameterShort  : LuaParameter[Short ] = new LuaParameterNumeric[Short ](_.toShort)
  implicit val LuaParameterInt    : LuaParameter[Int   ] = new LuaParameterNumeric[Int   ](_.toInt)
  implicit val LuaParameterLong   : LuaParameter[Long  ] = new LuaParameterNumeric[Long  ](_.toLong)
  implicit val LuaParameterFloat  : LuaParameter[Float ] = new LuaParameterNumeric[Float ](_.toFloat)
  implicit val LuaParameterDouble : LuaParameter[Double] = new LuaParameterNumeric[Double](identity)

  implicit object LuaParameterBoolean extends LuaParameter[Boolean] {
    override def toLua(b: Boolean) = new LuaOutObject(b)
    override def fromLua(L: Lua, v: Any, source: String) = L.toBoolean(v)
  }
  implicit object LuaParameterTable extends LuaParameter[LuaTable] {
    def toLua(t: LuaTable) = new LuaOutObject(t)
    override def fromLua(L: Lua, v: Any, source: String) = v match {
      case n: LuaTable => n
      case _ => typerror(L, source, v, Lua.TTABLE)
    }
  }
  implicit object LuaParameterThread extends LuaParameter[Lua] {
    def toLua(t: Lua) = new LuaOutObject(t)
    override def fromLua(L: Lua, v: Any, source: String) = v match {
      case n: Lua => n
      case _ => typerror(L, source, v, Lua.TTHREAD)
    }
  }

  implicit object LuaParameterString extends LuaParameter[String] {
    override def toLua(t: String) = new LuaOutObject(t)
    override def fromLua(L: Lua, v: Any, source: String) = L.toString(v) match {
      case null => typerror(L, source, v, Lua.TSTRING)
      case s => s
    }
  }

  private val userdataMetatableCache = new mutable.WeakHashMap[LuaUserdataType[_], Any]
  private type MetatableCacheType = mutable.HashMap[LuaUserdataType[_], Option[LuaTable]]
  private val metatableCache = LuaRegistryEntry[MetatableCacheType]()
  implicit def luaParameterUnwrapUserdata[T : LuaUserdataType] = new LuaParameter[T] {
    private def metadata = implicitly[LuaUserdataType[T]]
    override def toLua(t: T) = new LuaOutObject(LuaExecWrapper { L =>
      val cache = L.getRegistry(metatableCache, new MetatableCacheType)
      val mt = cache.getOrElseUpdate(metadata, metadata.getMetatable(L))
      val ud = new LuaUserdata(t)
      mt.foreach(ud.setMetatable)
      ud
    })
    override def fromLua(L: Lua, v: Any, source: String) = v match {
      case v: LuaUserdata =>
        if(!metadata.tag.runtimeClass.isAssignableFrom(v.getClass))
          typerror(L, source, v.getClass.toString, metadata.tag.toString)
        v.asInstanceOf[T]
      case _ => typerror(L, source, v, Lua.TUSERDATA)
    }
  }

  implicit object LuaParameterLuaClosure extends LuaParameter[LuaClosure] {
    override def toLua(t: LuaClosure) = new LuaOutObject(t.fn)
    override def fromLua(L: Lua, v: Any, source: String) = v match {
      case v: LuaFunction     => LuaClosure(v)
      case v: LuaJavaCallback => LuaClosure(v)
      case _ => typerror(L, source, v, Lua.TFUNCTION)
    }
  }

  // Scala type wrappers
  implicit def toLuaSeq[V : ToLua] = new ToLua[Seq[V]] {
    override def toLua(s: Seq[V]) = new LuaOutObject(LuaExecWrapper { L =>
      val t = new LuaTable()
      for((v, k) <- s.zipWithIndex) t.putnum(k + 1, implicitly[ToLua[V]].toLua(v))
      t
    })
  }
  implicit def fromLuaSeq[V : FromLua] = new FromLua[Seq[V]] {
    override def fromLua(L: Lua, v: Any, source: String): Seq[V] = v match {
      case table: LuaTable =>
        for(k <- table.array.take(table.getn())) yield
          implicitly[FromLua[V]].fromLua(L, table.getlua(k), s"$source in table value")
      case _ => typerror(L, source, v, Lua.TTABLE)
    }
  }

  implicit def toLuaMap[K : ToLua, V : ToLua] = new ToLua[Map[K, V]] {
    override def toLua(m: Map[K, V]) = new LuaOutObject(LuaExecWrapper { L =>
      val t = new LuaTable()
      for((k, v) <- m) t.putlua(L.L, implicitly[ToLua[K]].toLua(k), implicitly[ToLua[V]].toLua(v))
      t
    })
  }
  implicit def fromLuaMap[K : FromLua, V : FromLua] = new FromLua[Map[K, V]] {
    override def fromLua(L: Lua, v: Any, source: String): Map[K, V] = v match {
      case table: LuaTable =>
        (for(k <- table.keySet().asScala) yield (
          implicitly[FromLua[K]].fromLua(L, k              , s"$source in table key"  ),
          implicitly[FromLua[V]].fromLua(L, table.getlua(k), s"$source in table value"))).toMap
      case _ => typerror(L, source, v, Lua.TTABLE)
    }
  }

  implicit def toLuaOptional[T : ToLua] = new ToLua[Option[T]] {
    override def toLua(t: Option[T]) = t.fold(new LuaOutObject(Lua.NIL))(implicitly[ToLua[T]].toLua)
  }
  implicit def fromLuaOptional[T : FromLua] = new FromLua[Option[T]] {
    override def fromLua(L: Lua, v: Any, source: String) = Lua.`type`(v) match {
      case Lua.TNIL | Lua.TNONE => None
      case _ => Some(implicitly[FromLua[T]].fromLua(L, v, source))
    }
  }
}