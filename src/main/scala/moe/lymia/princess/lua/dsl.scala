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
  protected final def metatable(fn: (LuaState, LuaTable) => Unit) = metatableInitializers.append(fn)
  final def tag = implicitly[ClassTag[T]]
  final def getMetatable(L: LuaState) = {
    val table = new LuaTable()
    L.register(table, "__tostring" ,
               (L: Lua, o: Any) => s"${tag.toString()}@0x${"%08x" format System.identityHashCode(o)}")
    L.rawSet(table, "__metatable", s"metatable for ${tag.toString()}")
    metatableInitializers.foreach(_(L, table))
    table
  }
}

case class LuaThreadWrapper(thread: Lua)
case class LuaExecWrapper(fn: LuaState => Any)
class LuaObject(val wrapped: Any) extends AnyVal {
  def toLua(L: LuaState) = wrapped match {
    case LuaExecWrapper(fn) => fn(L)
    case any => any
  }
}
sealed trait ToLua[T] {
  def toLua(t: T): LuaObject
}
sealed trait FromLua[T] {
  def fromLua(L: Lua, v: Any, source: String): T
}
sealed trait LuaParameter[T] extends ToLua[T] with FromLua[T]

case class LuaClosure(fn: Any)
object LuaClosure {
  def apply(cl: ScalaLuaClosure) = cl
}

trait LuaErrorMarker
case class ScalaLuaClosure(fn: LuaJavaCallback) extends AnyVal {
  private[lua] def checkError(doCheck: Boolean = true) =
    if(doCheck) ScalaLuaClosure(L => try {
      fn.luaFunction(L)
    } catch {
      case e: LuaError => throw e
      case e: LuaErrorMarker => L.error(e.getMessage)
      case e: Exception => L.error(s"Java error - ${e.getClass}: ${e.getMessage}")
    }) else this
}

class LuaReturnWrapper(L: LuaState, val wrapped: Any) {
  def as[T : FromLua]: T = wrapped.fromLua[T](L)
}

case object LuaNil

trait LuaImplicits extends LuaGeneratedImplicits {
  type LuaRet = Seq[LuaObject]
  def LuaRet(v: LuaObject*) = Seq(v : _*)

  private def typerror[T](L: Lua, source: String, got: String, expected: String): T = {
    L.error(s"bad argument $source ($expected expected, got $got)")
    sys.error("L.error returned unexpectedly!")
  }
  private def typerror[T](L: Lua, source: String, got: Any, expected: String): T =
    typerror(L, source, Lua.typeName(Lua.`type`(got)), expected)
  private def typerror[T](L: Lua, source: String, got: Any, expected: Int): T =
    typerror(L, source, got, Lua.typeName(expected))

  implicit object ToLuaScalaLuaClosure extends ToLua[ScalaLuaClosure] {
    override def toLua(t: ScalaLuaClosure): LuaObject = new LuaObject(t.fn)
  }

  // Lua Object wrappers
  implicit def toLua2luaObject[T : ToLua](obj: T): LuaObject = implicitly[ToLua[T]].toLua(obj)
  implicit class FromLuaAnyExtension(obj: Any) {
    def fromLua[T : FromLua](L: LuaState, source: String = "<java function>") =
      implicitly[FromLua[T]].fromLua(L.L, obj, source)
    private[lua] def returnWrapper(L: LuaState) = new LuaReturnWrapper(L, obj)
  }

  implicit object LuaParameterAny extends LuaParameter[Any] {
    override def toLua(t: Any): LuaObject = new LuaObject(t)
    override def fromLua(L: Lua, v: Any, source: String) = v
  }
  implicit object ToLuaReturnWrapper extends ToLua[LuaReturnWrapper] {
    override def toLua(t: LuaReturnWrapper): LuaObject = new LuaObject(t.wrapped)
  }
  implicit object LuaParameterNil extends ToLua[LuaNil.type] {
    override def toLua(t: LuaNil.type): LuaObject = new LuaObject(Lua.NIL)
  }

  private class LuaParameterNumeric[N : Numeric](toN: Double => N, expected: String, checkRange: Double => Boolean)
    extends LuaParameter[N] {

    override def toLua(n: N) = new LuaObject(implicitly[Numeric[N]].toDouble(n))
    override def fromLua(L: Lua, v: Any, source: String) = v match {
      case n: java.lang.Double =>
        if(checkRange(n)) toN(n)
        else {
          L.error(s"bad argument $source (number out of $expected range)")
          sys.error("L.error returned unexpectedly")
        }
      case _ => typerror(L, source, v, Lua.TNUMBER)
    }
  }
  implicit val LuaParameterByte   : LuaParameter[Byte  ] =
    new LuaParameterNumeric[Byte  ](_.toByte , "byte"  , _.isValidByte)
  implicit val LuaParameterShort  : LuaParameter[Short ] =
    new LuaParameterNumeric[Short ](_.toShort, "short" , _.isValidShort)
  implicit val LuaParameterChar   : LuaParameter[Char  ] =
    new LuaParameterNumeric[Char  ](_.toChar , "char"  , _.isValidChar)
  implicit val LuaParameterInt    : LuaParameter[Int   ] =
    new LuaParameterNumeric[Int   ](_.toInt  , "int"   , _.isValidInt)
  implicit val LuaParameterLong   : LuaParameter[Long  ] =
    new LuaParameterNumeric[Long  ](_.toLong , "long"  , x => x.isWhole() && x >= Long.MinValue && x <= Long.MaxValue)
  implicit val LuaParameterFloat  : LuaParameter[Float ] =
    new LuaParameterNumeric[Float ](_.toFloat, "float" , _ => true)
  implicit val LuaParameterDouble : LuaParameter[Double] =
    new LuaParameterNumeric[Double](identity , "double", _ => true)

  implicit object LuaParameterBoolean extends LuaParameter[Boolean] {
    override def toLua(b: Boolean) = new LuaObject(b)
    override def fromLua(L: Lua, v: Any, source: String) = L.toBoolean(v)
  }
  implicit object LuaParameterTable extends LuaParameter[LuaTable] {
    def toLua(t: LuaTable) = new LuaObject(t)
    override def fromLua(L: Lua, v: Any, source: String) = v match {
      case n: LuaTable => n
      case _ => typerror(L, source, v, Lua.TTABLE)
    }
  }
  implicit object LuaParameterThread extends LuaParameter[Lua] {
    def toLua(t: Lua) = new LuaObject(t)
    override def fromLua(L: Lua, v: Any, source: String) = v match {
      case n: Lua => n
      case _ => typerror(L, source, v, Lua.TTHREAD)
    }
  }

  implicit object LuaParameterString extends LuaParameter[String] {
    override def toLua(t: String) = new LuaObject(t)
    override def fromLua(L: Lua, v: Any, source: String) = L.toString(v) match {
      case null => typerror(L, source, v, Lua.TSTRING)
      case s => s
    }
  }

  private val userdataMetatableCache = new mutable.WeakHashMap[LuaUserdataType[_], Any]
  private type MetatableCacheType = mutable.HashMap[LuaUserdataType[_], LuaTable]
  private val metatableCache = LuaRegistryEntry[MetatableCacheType]()
  implicit def luaParameterUnwrapUserdata[T : LuaUserdataType] = new LuaParameter[T] {
    private def metadata = implicitly[LuaUserdataType[T]]
    override def toLua(t: T) = new LuaObject(LuaExecWrapper { L =>
      val cache = L.getRegistry(metatableCache, new MetatableCacheType)
      val mt = cache.getOrElseUpdate(metadata, metadata.getMetatable(L))
      val ud = new LuaUserdata(t)
      ud.setMetatable(mt)
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
    override def toLua(t: LuaClosure) = new LuaObject(t.fn)
    override def fromLua(L: Lua, v: Any, source: String) = v match {
      case v: LuaFunction     => LuaClosure(v)
      case v: LuaJavaCallback => LuaClosure(v)
      case _ => typerror(L, source, v, Lua.TFUNCTION)
    }
  }

  // Scala type wrappers
  implicit def toLuaSeq[V : ToLua] = new ToLua[Seq[V]] {
    override def toLua(s: Seq[V]) = new LuaObject(LuaExecWrapper { L =>
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
    override def toLua(m: Map[K, V]) = new LuaObject(LuaExecWrapper { L =>
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
    override def toLua(t: Option[T]) = t.fold(new LuaObject(Lua.NIL))(implicitly[ToLua[T]].toLua)
  }
  implicit def fromLuaOptional[T : FromLua] = new FromLua[Option[T]] {
    override def fromLua(L: Lua, v: Any, source: String) = Lua.`type`(v) match {
      case Lua.TNIL | Lua.TNONE => None
      case _ => Some(implicitly[FromLua[T]].fromLua(L, v, source))
    }
  }
}