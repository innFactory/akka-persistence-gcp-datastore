package akka.persistence.datastore.serialization
import akka.actor.ActorSystem
import akka.serialization.{Serialization, SerializationExtension, Serializers}

class DatastoreSerializer(actorSystem: ActorSystem)  {

  implicit lazy val serialization: Serialization = SerializationExtension(actorSystem)

  def serialize(data: Any): SerializedPayload = {
    serializeVersionOne(data)
  }

  def deserialize(data: SerializedPayload): Any = {
    deserializeVersionOne(data)
  }

  def serializeSnapshot(snapshot: Any): SerializedSnapshot = {
    val snapshotData = snapshot.asInstanceOf[AnyRef]
    val serializer = serialization.findSerializerFor(snapshotData)
    val manifest = Serializers.manifestFor(serializer, snapshotData)
    SerializedSnapshot(serialization.serialize(snapshotData).get, serializer.identifier, manifest)
  }

  def deserializeSnapshot(serializedSnapshot: SerializedSnapshot): Any = {
    serialization.deserialize(serializedSnapshot.data, serializedSnapshot.serializerId, serializedSnapshot.manifest).get
  }

  private def deserializeVersionOne(serializedPayload: SerializedPayload): Any = {
    serialization.deserialize(serializedPayload.data, serializedPayload.serializerId, serializedPayload.manifest).get
  }

  private def serializeVersionOne(data: Any): SerializedPayload = {
    val snapshotData = data.asInstanceOf[AnyRef]
    val serializer = serialization.findSerializerFor(snapshotData)
    val manifest = Serializers.manifestFor(serializer, snapshotData)
    SerializedPayload(serialization.serialize(snapshotData).get, serializer.identifier, manifest)
  }

}




