addSbtPlugin("com.typesafe.sbt" % "sbt-git" % "0.8.5")
addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.14.3")
addSbtPlugin("com.typesafe.sbt" % "sbt-proguard" % "0.2.2")

resolvers += "SpringSource" at "http://repository.springsource.com/maven/bundles/external"

libraryDependencies += "net.sf.launch4j" % "launch4j" % "3.9"
