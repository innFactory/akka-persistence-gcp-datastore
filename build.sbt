import sbt.Keys.parallelExecution

name := "akka-persistence-gcp-datastore"

val mainScala = "2.13.2"
val allScala  = Seq("2.12.11", mainScala)

val akkaVersion              = "2.6.10"
val javaUUIDGeneratorVersion = "4.0.1"
val playJsonVersion          = "2.9.2"
val googleDatastoreVersion   = "1.105.6"

val akkaPersistence      = "com.typesafe.akka" %% "akka-persistence"       % akkaVersion
val akkaPersistenceQuery = "com.typesafe.akka" %% "akka-persistence-query" % akkaVersion
val akkaTCK              = "com.typesafe.akka" %% "akka-persistence-tck"   % akkaVersion
val javaUUIDGenerator    = "com.fasterxml.uuid" % "java-uuid-generator"    % javaUUIDGeneratorVersion
val playJson             = "com.typesafe.play" %% "play-json"              % playJsonVersion
val googleDatastore      = "com.google.cloud"   % "google-cloud-datastore" % googleDatastoreVersion

lazy val root = project
  .in(file("."))
  .settings(
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

addCommandAlias("fmt", "all scalafmtSbt scalafmt test:scalafmt")
addCommandAlias(
  "check",
  "all scalafmtSbtCheck scalafmtCheck test:scalafmtCheck"
)

parallelExecution in ThisBuild := false
parallelExecution in Test := false
logBuffered in Test := false
testOptions += Tests.Setup(_ => sys.props("testing") = "true")
scalaVersion := mainScala
crossScalaVersions := allScala
startYear := Some(2020)
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
  case _             => Nil
})

// Publishing
organization := "de.innfactory"
homepage := Some(url("https://github.com/innFactory/akka-persistence-gcp-datastore"))
licenses := List(
  "Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")
)

organizationName := "innFactory GmbH | innfactory.de"
bintrayOrganization := Some("innfactory")
bintrayRepository := "sbt-plugins"
bintrayPackageLabels := Seq(
  "JWT",
  "Scala",
  "akka-persistence",
  "akka-typed",
  "gcp",
  "datastore",
  "firestore",
  "event sourcing",
  "cqrs"
)
bintrayVcsUrl := Some("https://github.com/innFactory/akka-persistence-gcp-datastore")
homepage := Some(url("https://github.com/innFactory/akka-persistence-gcp-datastore"))
publishMavenStyle := true
pomExtra :=
  <scm>
    <url>git@github.com:innFactory/akka-persistence-gcp-datastore.git</url>
    <connection>scm:git:git@github.com:innFactory/akka-persistence-gcp-datastore.git</connection>
  </scm>
    <developers>
      <developer>
        <id>jona7o</id>
        <name>Tobias Jonas</name>
        <email>info@innFactory.de</email>
        <url>https://innFactory.de/</url>
        <organization>innFactory</organization>
        <organizationUrl>https://innFactory.de/</organizationUrl>
      </developer>
      <developer>
        <id>pasta_32</id>
        <name>Patrick Stadler</name>
        <email>info@innFactory.de</email>
        <url>https://innFactory.de/</url>
        <organization>innFactory</organization>
        <organizationUrl>https://innFactory.de/</organizationUrl>
      </developer>
    </developers>
