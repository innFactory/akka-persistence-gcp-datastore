package akka.persistence.datastore.serialization

case class SerializedSnapshot(data: Array[Byte], serializerId: Int, manifest: String)
