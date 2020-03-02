import sbt._

object Dependencies {

  val akkaVer =                  "2.5.23"
  val akkaQueryVer =             "2.6.1"
  val akkaPersistenceQueryVer =  "2.6.1"
  val scalaVer =                 "2.13.1"
  val circeVersion =              "0.12.3"

  val akkaPersistence =         "com.typesafe.akka"             %% "akka-persistence"             % akkaVer
  val akkaPersistenceQuery =    "com.typesafe.akka"             %% "akka-persistence-query"       % akkaQueryVer
  val akkaTCK=                  "com.typesafe.akka"             %% "akka-persistence-tck"         % akkaPersistenceQueryVer
  val javaUUIDGenerator =       "com.fasterxml.uuid"            %  "java-uuid-generator"          % "3.2.0"
  val playJson =                "com.typesafe.play"             %% "play-json"                    % "2.8.1"
  val googleDatastore =         "com.google.cloud"              %  "google-cloud-datastore"       % "1.102.0"

  lazy val list = Seq(
    akkaPersistence % "compile",
    akkaPersistenceQuery,
    akkaTCK,
    javaUUIDGenerator % "compile",
    googleDatastore  % "compile",
    playJson  % "compile"
  )
}
