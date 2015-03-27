sbtPlugin := true

name := "sbt-webdav"

organization := "com.octanner"

version := "0.1"

scalaVersion := "2.10.4"

libraryDependencies ++= Seq(
  "org.scala-lang.modules" % "scala-xml_2.11" % "1.0.3",
  "com.github.lookfirst" % "sardine" % "5.4",
  "org.scalatest" % "scalatest_2.10" % "2.2.4" % "test",
  "com.typesafe"  % "config" % "1.2.1" % "test"
)


    