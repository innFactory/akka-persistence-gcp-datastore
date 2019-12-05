package akka.persistence.datastore

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, ObjectInputStream, ObjectOutputStream}
import akka.actor.{ActorLogging, ActorSystem}
import akka.serialization.{Serialization, SerializationExtension, SerializerWithStringManifest}

trait DatastorePersistence extends DatastoreCommon { mixin : ActorLogging =>

  val actorSystem: ActorSystem
  protected lazy val serialization: Serialization = SerializationExtension(actorSystem)
  protected def initialize(): Unit

  def serialise(value: Any): Array[Byte] = {
    val stream: ByteArrayOutputStream = new ByteArrayOutputStream()
    val oos = new ObjectOutputStream(stream)
    oos.writeObject(value)
    oos.close()
    stream.toByteArray
  }

  def deserialise(value: Array[Byte]): Any = {
    val ois = new ObjectInputStream(new ByteArrayInputStream(value))
    val v = ois.readObject
    ois.close()
    v
  }

}


