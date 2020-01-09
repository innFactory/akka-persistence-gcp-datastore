val akkaVer = "2.5.23"
val akkaQueryVer = "2.6.1"
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

  "com.typesafe.akka"             %% "akka-persistence"            % akkaVer                % "compile",
  "com.fasterxml.uuid"            %  "java-uuid-generator"         % "3.2.0"                % "compile",
  "com.typesafe.akka" %% "akka-serialization-jackson" % "2.6.1",
  "com.fasterxml.jackson.core" % "jackson-databind" % "2.10.1",
  "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.10.1" % "compile",
  "com.typesafe.play"             %% "play-json"                   % "2.8.1"                % "compile",
  "com.google.cloud"              %  "google-cloud-datastore"      % "1.101.0",
  "com.typesafe.akka"             %% "akka-persistence-query"      % akkaQueryVer ,
  "com.typesafe.akka"             %% "akka-persistence-tck"        % "2.6.0"
)

testOptions += Tests.Setup(_ => sys.props("testing") = "true")

lazy val root = (project in file(".")).
  settings(

  )
