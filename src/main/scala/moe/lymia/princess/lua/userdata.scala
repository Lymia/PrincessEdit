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

import moe.lymia.princess.core.TemplateException

import scala.collection.mutable
import scala.reflect.ClassTag

trait HasLuaMethods {
  def getField(L: LuaState, name: String): LuaObject
  def setField(L: LuaState, name: String, obj: Any)
}

private object LuaLookup {
  type SetPropertyFn = (LuaState, Any) => Unit
  type GetPropertyFn = (LuaState) => LuaObject
  case class Property(set: LuaLookup.SetPropertyFn, get: LuaLookup.GetPropertyFn)
}
trait LuaLookup extends HasLuaMethods {
  private val properties = new mutable.HashMap[String, LuaLookup.Property]

  protected def property(name: String, get: LuaLookup.GetPropertyFn): Unit =
    properties.put(name, LuaLookup.Property((L, _) => L.error(s"property '$name' is immutable"), get))
  protected def property[R: FromLua](name: String, get: LuaLookup.GetPropertyFn, set: (LuaState, R) => Unit): Unit =
    properties.put(name, LuaLookup.Property((L, v) => set(L, v.fromLua[R](L, Some(s"invalid property value"))), get))

  private def luaMethod(name: String)(fn: LuaClosure) =
    property(name, L => fn, (L, _ : Any) => L.error(s"cannot set method '$name'"))
  protected def method(name: String)(fn: ScalaLuaClosure) = luaMethod(name)(new LuaClosure(fn.fn))

  protected def deleteProperty(name: String): Unit = {
    if(!properties.contains(name)) throw TemplateException(s"property '$name' does not exist")
    properties.remove(name)
  }

  private def setLuaProperty(doOverride: Boolean)
                            (L: LuaState, name: String, get: Option[LuaClosure], set: Option[LuaClosure]) = {
    if(!doOverride && properties.contains(name)) L.error(s"property '$name' already defined!")
    val getFn = get.getOrElse(LuaClosure { () => L.error(s"property '$name' is immutable") ; () })
    val setFn = set.getOrElse(LuaClosure { () => L.error(s"property '$name' is write-only"); () })
    property(name, L => L.call(getFn, 1).head, (L, v: Any) => L.call(setFn, 0, v))
  }
  private def setLuaMethod(doOverride: Boolean)(L: LuaState, name: String, m: LuaClosure) = {
    if(!doOverride && properties.contains(name)) L.error(s"method '$name' already defined!")
    luaMethod(name)(m)
  }

  method("_lock")(() => for(name <- properties.keySet if name.startsWith("_")) properties.remove(name))
  method("_property")(setLuaProperty(doOverride = false) _)
  method("_overrideProperty")(setLuaProperty(doOverride = true) _)
  method("_method")(setLuaMethod(doOverride = false) _)
  method("_overrideMethod")(setLuaMethod(doOverride = true) _)
  method("_deleteProperty")(deleteProperty _)
  method("_hasProperty")((k: String) => properties.contains(k))

  override def getField(L: LuaState, name: String): LuaObject =
    properties.get(name).map(_.get(L)).getOrElse(LuaNil)
  override def setField(L: LuaState, name: String, obj: Any): Unit =
    properties.getOrElse(name, L.error(s"no such property $name")).set(L, obj)
}

class LuaUserdataType[T : ClassTag] {
  private val metatableInitializers = new mutable.ArrayBuffer[(LuaState, LuaTable) => Unit]
  protected final def metatable(fn: (LuaState, LuaTable) => Unit) = metatableInitializers.append(fn)
  final def tag = implicitly[ClassTag[T]]
  final def getMetatable(L: LuaState) = {
    val mt = new LuaTable()
    L.register(mt, "__tostring" , (o: Any) => s"${tag.toString()}: 0x${"%08x" format System.identityHashCode(o)}")
    L.rawSet  (mt, "__metatable", s"metatable for ${tag.toString()}")
    metatableInitializers.foreach(_(L, mt))
    mt
  }
}

class PropertiesUserdataType[T : ClassTag] extends LuaUserdataType[T] {
  private type SetPropertyFn = (LuaState, T, Any) => Unit
  private type GetPropertyFn = (LuaState, T) => LuaObject
  private case class Property(set: SetPropertyFn, get: GetPropertyFn)

  private val properties = new mutable.HashMap[String, Property]

  protected def property(name: String, get: GetPropertyFn): Unit =
    properties.put(name, Property((L, _, _) => L.error(s"property '$name' is immutable"), get))
  protected def property[R: FromLua](name: String, get: GetPropertyFn, set: (LuaState, T, R) => Unit): Unit =
    properties.put(name, Property((L, o, v) => set(L, o, v.fromLua[R](L, Some(s"invalid property value"))), get))

  protected def method(name: String)(fn: T => ScalaLuaClosure) =
    property(name, (L, o) => fn(o), (L, _, _ : Any) => L.error(s"cannot set method '$name'"))
  protected def unboundMethod(name: String)(fn: => ScalaLuaClosure) = {
    lazy val fnWrapper = fn
    property(name, (L, _) => fnWrapper, (L, _, _ : Any) => L.error(s"cannot set method '$name'"))
  }

  private def getField(L: LuaState, o: T, name: String): LuaObject =
    properties.get(name).map(_.get(L, o)).getOrElse(LuaNil)
  private def setField(L: LuaState, o: T, name: String, v: Any): Unit =
    properties.getOrElse(name, L.error(s"no such property $name")).set(L, o, v)

  unboundMethod("_hasProperty")((k: String) => properties.contains(k))

  metatable { (L, mt) =>
    implicit val tud = this
    L.register(mt, "__index"   , (L: LuaState, o: T, k: String) => LuaRet(getField(L, o, k)))
    L.register(mt, "__newindex", (L: LuaState, o: T, k: String, v: Any) => setField(L, o, k, v))
  }
}

class HasLuaMethodsUserdataType[T <: HasLuaMethods : ClassTag] extends LuaUserdataType[T] {
  metatable { (L, mt) =>
    implicit val tud = this
    L.register(mt, "__tostring", (o: Any) => s"${o.getClass.getName}: 0x${"%08x" format System.identityHashCode(o)}")
    L.register(mt, "__index"   , (L: LuaState, o: T, k: String) => LuaRet(o.getField(L, k)))
    L.register(mt, "__newindex", (L: LuaState, o: T, k: String, v: Any) => o.setField(L, k, v))
  }
}

trait LuaUserdataImplicits {
  implicit object LuaHasLuaMethods extends HasLuaMethodsUserdataType[HasLuaMethods]
}