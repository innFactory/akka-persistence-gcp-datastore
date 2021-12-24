/*
 * Copyright 2020 innFactory GmbH | innfactory.de
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package akka.persistence.datastore.serialization
import akka.actor.ActorSystem
import akka.serialization.{Serialization, SerializationExtension, Serializers}

class DatastoreSerializer(actorSystem: ActorSystem) {

  implicit lazy val serialization: Serialization = SerializationExtension(actorSystem)

  def serialize(data: Any): SerializedPayload =
    serializeVersionOne(data)

  def deserialize(data: SerializedPayload): Any =
    deserializeVersionOne(data)

  def serializeSnapshot(snapshot: Any): SerializedSnapshot = {
    val snapshotData = snapshot.asInstanceOf[AnyRef]
    val serializer = serialization.findSerializerFor(snapshotData)
    val manifest = Serializers.manifestFor(serializer, snapshotData)
    SerializedSnapshot(serialization.serialize(snapshotData).get, serializer.identifier, manifest)
  }

  def deserializeSnapshot(serializedSnapshot: SerializedSnapshot): Any =
    serialization.deserialize(serializedSnapshot.data, serializedSnapshot.serializerId, serializedSnapshot.manifest).get

  private def deserializeVersionOne(serializedPayload: SerializedPayload): Any =
    serialization.deserialize(serializedPayload.data, serializedPayload.serializerId, serializedPayload.manifest).get

  private def serializeVersionOne(data: Any): SerializedPayload = {
    val dataToSerialize = data.asInstanceOf[AnyRef]
    val serializer = serialization.findSerializerFor(dataToSerialize)
    val manifest = Serializers.manifestFor(serializer, dataToSerialize)
    SerializedPayload(serialization.serialize(dataToSerialize).get, serializer.identifier, manifest)
  }

}
