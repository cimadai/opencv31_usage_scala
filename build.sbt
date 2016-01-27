name := "opencv31_usage_scala"

scalaVersion := "2.11.7"

scalacOptions ++= Seq(
  "-unchecked",
  "-deprecation",
  "-optimize",
  "-Xlint"
)

javaOptions in run += "-Djava.library.path=./lib"

lazy val root = (project in file("."))

fork := true

