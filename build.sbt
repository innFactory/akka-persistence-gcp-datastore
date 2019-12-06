val akkaVer = "2.5.23"
val scalaVer = "2.13.1"
val circeVersion = "0.12.3"
organization := "de.innfactory"

name := "akka-persistence-gcp-datastore"
version := "0.2.0"

scalaVersion := scalaVer
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

libraryDependencies ++= Seq(

  "com.typesafe.akka"   %% "akka-persistence"            % akkaVer                % "compile",
  "com.google.cloud"    %  "google-cloud-datastore"      % "1.101.0",
  "io.circe"            %% "circe-core" % circeVersion,
  "io.circe"            %% "circe-generic" % circeVersion,
  "io.circe"            %% "circe-parser" % circeVersion,
  "com.typesafe.akka" %% "akka-persistence-tck" % "2.6.0"
)

testOptions += Tests.Setup(_ => sys.props("testing") = "true")

lazy val root = (project in file(".")).
  settings(

  )
