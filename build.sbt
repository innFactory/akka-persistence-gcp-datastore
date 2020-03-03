import sbt.Keys.parallelExecution

val mainScala = "2.13.1"
val allScala  = Seq("2.12.10", mainScala)

val akkaVersion              = "2.6.3"
val javaUUIDGeneratorVersion = "4.0.1"
val playJsonVersion          = "2.8.1"
val googleDatastoreVersion   = "1.102.0"

val akkaPersistence      = "com.typesafe.akka"  %% "akka-persistence"       % akkaVersion
val akkaPersistenceQuery = "com.typesafe.akka"  %% "akka-persistence-query" % akkaVersion
val akkaTCK              = "com.typesafe.akka"  %% "akka-persistence-tck"   % akkaVersion
val javaUUIDGenerator    = "com.fasterxml.uuid" % "java-uuid-generator"     % javaUUIDGeneratorVersion
val playJson             = "com.typesafe.play"  %% "play-json"              % playJsonVersion
val googleDatastore      = "com.google.cloud"   % "google-cloud-datastore"  % googleDatastoreVersion

name := "akka-persistence-gcp-datastore"
version := "1.0.0"

addCommandAlias("fmt", "all scalafmtSbt scalafmt test:scalafmt")
addCommandAlias(
  "check",
  "all scalafmtSbtCheck scalafmtCheck test:scalafmtCheck"
)

inThisBuild(
  List(
    organization := "de.innfactory",
    homepage := Some(url("https://github.com/ghostdogpr/caliban")),
    licenses := List(
      "Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")
    ),
    parallelExecution in ThisBuild := false,
    parallelExecution in Test := false,
    logBuffered in Test := false,
    testOptions += Tests.Setup(_ => sys.props("testing") = "true"),
    organizationName := "innFactory GmbH | innfactory.de"
  )
)

val commonSettings = Def.settings(
  scalaVersion := mainScala,
  crossScalaVersions := allScala,
  scalacOptions ++= Seq(
    "-deprecation",
    "-encoding",
    "UTF-8",
    "-explaintypes",
    "-Yrangepos",
    "-feature",
    "-language:higherKinds",
    "-language:existentials",
    "-unchecked",
    "-Xlint:_,-type-parameter-shadow",
    "-Xfatal-warnings",
    "-Ywarn-numeric-widen",
    "-Ywarn-unused:patvars,-implicits",
    "-Ywarn-value-discard"
  ) ++ (CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((2, 12)) =>
      Seq(
        "-Xsource:2.13",
        "-Yno-adapted-args",
        "-Ypartial-unification",
        "-Ywarn-extra-implicit",
        "-Ywarn-inaccessible",
        "-Ywarn-infer-any",
        "-Ywarn-nullary-override",
        "-Ywarn-nullary-unit",
        "-opt-inline-from:<source>",
        "-opt-warnings",
        "-opt:l:inline"
      )
    case _ => Nil
  })
)

lazy val root = project
  .in(file("."))
  .settings(commonSettings)
  .settings(
    startYear := Some(2020),
    libraryDependencies ++= Seq(
      akkaPersistence   % "compile",
      javaUUIDGenerator % "compile",
      googleDatastore   % "compile",
      playJson          % "compile",
      akkaPersistenceQuery,
      akkaTCK
    )
  )
  .enablePlugins(AutomateHeaderPlugin)
