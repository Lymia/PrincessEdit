package moe.lymia.princess

import moe.lymia.princess.util.IOUtils
import play.api.libs.json.{Json, _}

object NativeImageProcessConfig {
  def processConfigs(): Unit = {
    val classesList = NativeImageData.jniTypeNames
    val classesSet = NativeImageData.jniTypeNames.toSet

    val rootPath = NativeImageData.nativeImageConfigPath
    val jniPath = rootPath.resolve("jni-config.json")
    val jsonBlob = Json.parse(IOUtils.readFileAsString(jniPath))

    val json = Json.toJson(jsonBlob.as[Seq[JsObject]].filter(obj => {
      val name = (obj \ "name").as[String]
      !classesSet.contains(name)
    }) ++ classesList.map(name => Json.obj(
      "name" -> name,
      "allDeclaredConstructors" -> true,
      "allDeclaredMethods" -> true,
      "allDeclaredFields" -> true,
    )))

    IOUtils.writeFile(jniPath, Json.prettyPrint(json))
  }
}
