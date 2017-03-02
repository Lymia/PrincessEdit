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
  case class Property(get: LuaLookup.GetPropertyFn, set: LuaLookup.SetPropertyFn)
  val lockProps = Set("_lock", "_property", "_overideProperty", "_method", "_overrideMethod", "_deleteProperty")
  val fullLockProps = Set("_listProperties", "_getProperty", "_hasProperty")
}

private case class LuaGetFn(fn: LuaClosure) extends LuaLookup.GetPropertyFn {
  override def apply(L: LuaState): LuaObject = L.call(fn, 1).head
}
private case class LuaSetFn(fn: LuaClosure) extends LuaLookup.SetPropertyFn {
  override def apply(L: LuaState, v: Any): Unit = L.call(fn, 0, v)
}

trait LuaLookup extends HasLuaMethods {
  private val properties = new mutable.HashMap[String, LuaLookup.Property]

  protected def property(name: String, get: LuaLookup.GetPropertyFn): Unit =
    properties.put(name, LuaLookup.Property(get, (L, _) => L.error(s"property '$name' is immutable")))
  protected def property[R: FromLua](name: String, get: LuaLookup.GetPropertyFn, set: (LuaState, R) => Unit): Unit =
    properties.put(name, LuaLookup.Property(get, (L, v) => set(L, v.fromLua[R](L, Some(s"invalid property value")))))

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
    property(name, LuaGetFn(getFn), LuaSetFn(setFn))
  }
  private def setLuaMethod(doOverride: Boolean)(L: LuaState, name: String, m: LuaClosure) = {
    if(!doOverride && properties.contains(name)) L.error(s"method '$name' already defined!")
    luaMethod(name)(m)
  }
  protected def lock(full: Boolean = true): Unit =
    for(name <- properties.keySet if (full && LuaLookup.fullLockProps.contains(name)) ||
                                     LuaLookup.lockProps.contains(name)) properties.remove(name)

  method("_lock")(() => lock(false))
  method("_property")(setLuaProperty(doOverride = false) _)
  method("_overrideProperty")(setLuaProperty(doOverride = true) _)
  method("_method")(setLuaMethod(doOverride = false) _)
  method("_overrideMethod")(setLuaMethod(doOverride = true) _)
  method("_deleteProperty")(deleteProperty _)

  method("_getProperty")((L: LuaState, k: String) => properties.get(k) match {
    case Some(x) =>
      val get: LuaObject = x.get match {
        case LuaGetFn(closure) => closure
        case fn                => fn : ScalaLuaClosure
      }
      val set: LuaObject = x.set match {
        case LuaSetFn(closure) => closure
        case fn                => fn : ScalaLuaClosure
      }
      LuaRet(get, set)
    case None    => LuaRet()
  })
  method("_listProperties")(() => properties.keySet.toSeq)
  method("_hasProperty")((k: String) => properties.contains(k))

  override def getField(L: LuaState, name: String): LuaObject =
    properties.get(name).map(_.get(L)).getOrElse(LuaNil)
  override def setField(L: LuaState, name: String, obj: Any): Unit =
    properties.getOrElse(name, L.error(s"no such property $name")).set(L, obj)
}

class LuaUserdataInput[T : ClassTag] extends FromLua[T] {
  final def tag = implicitly[ClassTag[T]]

  override def fromLua(L: Lua, v: Any, source: => Option[String]) = v match {
    case v: LuaUserdata =>
      val obj = v.getUserdata
      if(!tag.runtimeClass.isAssignableFrom(obj.getClass))
        typerror(L, source, obj.getClass.toString, tag.toString)
      obj.asInstanceOf[T]
    case _ => typerror(L, source, v, Lua.TUSERDATA)
  }
}
class LuaUserdataType[T : ClassTag] extends LuaUserdataInput[T] with ToLua[T] {
  private val metatableInitializers = new mutable.ArrayBuffer[(LuaState, LuaTable) => Unit]
  protected final def metatable(fn: (LuaState, LuaTable) => Unit) = metatableInitializers.append(fn)
  final def getMetatable(L: LuaState) = {
    val mt = new LuaTable()
    L.register(mt, "__tostring" , (o: Any) => s"${tag.toString()}: 0x${"%08x" format System.identityHashCode(o)}")
    L.rawSet  (mt, "__metatable", s"metatable for ${tag.toString()}")
    metatableInitializers.foreach(_(L, mt))
    mt
  }

  override def toLua(t: T) = new LuaObject(LuaExecWrapper { L =>
    val cache = L.getRegistry(LuaUserdataType.metatableCache, new LuaUserdataType.MetatableCacheType)
    val mt = cache.getOrElseUpdate(this, this.getMetatable(L))
    val ud = new LuaUserdata(t)
    ud.setMetatable(mt)
    ud
  })
}
object LuaUserdataType {
  private type MetatableCacheType = mutable.HashMap[LuaUserdataType[_], LuaTable]
  private val metatableCache = LuaRegistryEntry[MetatableCacheType]()
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

class LuaLightUserdata

trait LuaUserdataImplicits {
  implicit object LuaLightUserdata extends LuaUserdataType[LuaLightUserdata]
  implicit object LuaHasLuaMethods extends HasLuaMethodsUserdataType[HasLuaMethods]
}