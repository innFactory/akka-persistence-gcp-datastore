organization := "de.innfactory"

name := "akka-persistence-gcp-datastore"
version := "0.3.0"

scalaVersion := Dependencies.scalaVer
scalacOptions ++= Seq(
  "-encoding", "UTF-8",
  "-deprecation",
  "-unchecked",
  "-feature",
  "-language:postfixOps",
  "-target:jvm-1.8")

parallelExecution in ThisBuild := false

parallelExecution in Test := false
logBuffered in Test := false

libraryDependencies ++= Dependencies.list

testOptions += Tests.Setup(_ => sys.props("testing") = "true")

lazy val root = (project in file(".")).
  settings(

  )
