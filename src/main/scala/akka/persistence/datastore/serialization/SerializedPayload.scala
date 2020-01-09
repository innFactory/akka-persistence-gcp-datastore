package akka.persistence.datastore.serialization

case class SerializedPayload(data: Array[Byte], serializerId: Int, manifest: String)
