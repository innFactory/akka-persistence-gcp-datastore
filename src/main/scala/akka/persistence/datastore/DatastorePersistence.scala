package akka.persistence.datastore

import akka.actor.{ActorLogging, ActorSystem}

trait DatastorePersistence extends DatastoreCommon { mixin : ActorLogging =>

  val actorSystem: ActorSystem
  protected def initialize(): Unit

}


