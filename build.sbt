name := "opencv31_usage_scala"

scalaVersion := "2.11.7"

scalacOptions ++= Seq(
  "-unchecked",
  "-deprecation",
  "-optimize",
  "-Xlint"
)

javaOptions in run += "-Djava.library.path=./lib"

libraryDependencies += "org.scala-lang" % "scala-swing" % "2.11.0-M7"
libraryDependencies += "com.typesafe.akka" %% "akka-actor" % "2.4.1"

lazy val root = project in file(".")

fork := true

