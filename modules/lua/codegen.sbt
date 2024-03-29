Compile / sourceGenerators += Def.task {
  val maxTuple = 5
  def makeImplicit(count: Int) = {
    val n = 1 to count

    val comma          = if(count == 0) "" else ", "
    val typeParameters = n.map(x => s"T$x : FromLua").mkString(", ")
    val fullTypeParams = if(typeParameters.nonEmpty) s"[$typeParameters]" else ""
    val fnTpParameters = n.map(x => s"T$x").mkString(", ")
    val passParameters = n.map(x => s"""implicitly[FromLua[T$x]].fromLua(L, L.value($x), LuaGeneratedImplicits.badArgFn$x())""").mkString(", ")

    def sub(functionNameHeader: String, luaStateSig: String, luaStateParam: String) = {
      def tuple(tupleCount: Int) = {
        val tn = 1 to tupleCount
        val tupleParams = tn.map(x => s"U$x : ToLua").mkString(", ")
        val tupleType = s"(${tn.map(x => s"U$x").mkString(", ")})"
        s"""  implicit def function${count}_tuple${tupleCount}_${functionNameHeader}2luaClosure[$typeParameters$comma$tupleParams](fn: ($luaStateSig$fnTpParameters) => $tupleType): ScalaLuaClosure =
           |    ScalaLuaClosure(L => {
           |      val t = fn($luaStateParam$passParameters)
           |      ${tn.map(x => s"L.push(implicitly[ToLua[U$x]].toLua(t._$x).toLua(new LuaState(L)))").mkString("\n      ")}
           |      $tupleCount
           |    })
         """.stripMargin.trim
      }

      s"""  implicit def luaObjectFunction${count}_${functionNameHeader}2luaClosure$fullTypeParams(fn: ($luaStateSig$fnTpParameters) => LuaObject): ScalaLuaClosure =
         |    ScalaLuaClosure(L => { L.push(fn($luaStateParam$passParameters).toLua(new LuaState(L))); 1 })
         |  implicit def singleFunction${count}_${functionNameHeader}2luaClosure[$typeParameters${comma}ReturnT: ToLua](fn: ($luaStateSig$fnTpParameters) => ReturnT): ScalaLuaClosure =
         |    ScalaLuaClosure(L => { L.push(implicitly[ToLua[ReturnT]].toLua(fn($luaStateParam$passParameters)).toLua(new LuaState(L))); 1 })
         |  implicit def function${count}_${functionNameHeader}2luaClosure$fullTypeParams(fn: ($luaStateSig$fnTpParameters) => Seq[LuaObject]): ScalaLuaClosure =
         |    ScalaLuaClosure(L => { LuaGeneratedImplicits.pushSeqRet(L, fn($luaStateParam$passParameters)) })
         |  implicit def unitFunction${count}_${functionNameHeader}2luaClosure$fullTypeParams(fn: ($luaStateSig$fnTpParameters) => Unit): ScalaLuaClosure =
         |    ScalaLuaClosure(L => { fn($luaStateParam$passParameters); 0 })
         |  ${(2 to maxTuple).map(tuple).mkString("\n  ")}
       """.stripMargin.trim
    }

    s"""  // Functions for $count parameters
       |  ${sub("withoutLuaState", "", "")}
       |  ${sub("withLuaState", s"LuaState$comma", s"new LuaState(L)$comma")}
     """.stripMargin.trim
  }

  val maxFunction = 10
  val generatedFile =
    s"""package moe.lymia.lua
       |
       |import scala.language.implicitConversions
       |
       |object LuaGeneratedImplicits {
       |  @inline private def pushSeqRet(L: Lua, ret: LuaRet) = {
       |    for(v <- ret) L.push(v.toLua(new LuaState(L)))
       |    ret.length
       |  }
       |  ${(1 to maxFunction).map(n => s"""private val badArg$n = Some("bad argument $n")""").mkString("\n  ")}
       |  ${(1 to maxFunction).map(n => s"private val badArgFn$n = () => badArg$n").mkString("\n  ")}
       |}
       |class LuaGeneratedImplicits {
       |  ${(0 to maxFunction).map(makeImplicit).mkString("\n\n  ")}
       |}
     """.stripMargin

  val out = (Compile / sourceManaged).value / "moe" / "lymia" / "lua" / "LuaGeneratedImplicits.scala"
  IO.write(out, generatedFile)
  Seq(out)
}.taskValue
