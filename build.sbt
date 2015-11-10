name := """s3-streaming"""
version := s"1.0-$playVersion"

resolvers ++= Seq()
scalaVersion := "2.11.7"

val playVersion = "2.4.3"
libraryDependencies ++= Seq(
  "com.typesafe.play" %% "play" % playVersion % "provided",
  "com.amazonaws" % "aws-java-sdk" % "1.10.32"
)