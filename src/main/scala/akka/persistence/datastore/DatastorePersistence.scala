package akka.persistence.datastore

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, ObjectInputStream, ObjectOutputStream}

import akka.actor.{ActorLogging, ActorSystem}
import akka.serialization.{Serialization, SerializationExtension, SerializerWithStringManifest}

trait DatastorePersistence extends DatastoreCommon { mixin : ActorLogging =>

  val actorSystem: ActorSystem
  protected def initialize(): Unit

}


