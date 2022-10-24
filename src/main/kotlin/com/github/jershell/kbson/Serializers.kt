package com.github.jershell.kbson

import kotlinx.serialization.*
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.encodeCollection
import kotlinx.serialization.json.*
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.SerializersModuleBuilder
import kotlinx.serialization.modules.serializersModuleOf
import org.bson.BsonType
import org.bson.UuidRepresentation
import org.bson.types.Decimal128
import org.bson.types.ObjectId
import java.math.BigDecimal
import java.util.Date
import java.util.UUID

@SerialInfo
@Target(AnnotationTarget.PROPERTY)
annotation class NonEncodeNull


@Serializer(forClass = Date::class)
object DateSerializer : KSerializer<Date> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("DateSerializer", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Date) {
        encoder as BsonEncoder
        encoder.encodeDateTime(value.time)
    }

    @InternalSerializationApi
    override fun deserialize(decoder: Decoder): Date {
        return when (decoder) {
            is FlexibleDecoder -> {
                Date(
                        when (decoder.reader.currentBsonType) {
                            BsonType.STRING -> decoder.decodeString().toLong()
                            BsonType.DATE_TIME -> decoder.reader.readDateTime()
                            else -> throw SerializationException("Unsupported ${decoder.reader.currentBsonType} reading date")
                        }
                )
            }
            else -> throw SerializationException("Unknown decoder type")
        }
    }
}


@Serializer(forClass = BigDecimal::class)
object BigDecimalSerializer : KSerializer<BigDecimal> {
    override val descriptor: SerialDescriptor =
            PrimitiveSerialDescriptor("BigDecimalSerializer", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: BigDecimal) {
        encoder as BsonEncoder
        encoder.encodeDecimal128(Decimal128(value))
    }

    @InternalSerializationApi
    override fun deserialize(decoder: Decoder): BigDecimal {
        return when (decoder) {
            is FlexibleDecoder -> {
                when (decoder.reader.currentBsonType) {
                    BsonType.STRING -> BigDecimal(decoder.decodeString())
                    BsonType.DECIMAL128 -> decoder.reader.readDecimal128().bigDecimalValue()
                    else -> throw SerializationException("Unsupported ${decoder.reader.currentBsonType} reading decimal128")
                }
            }
            else -> throw SerializationException("Unknown decoder type")
        }
    }
}


@Serializer(forClass = ByteArray::class)
object ByteArraySerializer : KSerializer<ByteArray> {
    override val descriptor: SerialDescriptor =
            PrimitiveSerialDescriptor("ByteArraySerializer", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: ByteArray) {
        encoder as BsonEncoder
        encoder.encodeByteArray(value)
    }

    @InternalSerializationApi
    override fun deserialize(decoder: Decoder): ByteArray {
        return when (decoder) {
            is FlexibleDecoder -> {
                decoder.reader.readBinaryData().data
            }
            else -> throw SerializationException("Unknown decoder type")
        }

    }
}


@Serializer(forClass = ObjectId::class)
object ObjectIdSerializer : KSerializer<ObjectId> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("ObjectIdSerializer", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: ObjectId) {
        when (encoder) {
            is BsonEncoder -> encoder.encodeObjectId(value)
            is JsonEncoder -> encoder.encodeString(value.toString())
            else -> throw SerializationException("Unknown encoder type")
        }
    }

    @InternalSerializationApi
    override fun deserialize(decoder: Decoder): ObjectId {
        return when (decoder) {
            is FlexibleDecoder -> {
                when (decoder.reader.currentBsonType) {
                    BsonType.STRING -> ObjectId(decoder.decodeString())
                    BsonType.OBJECT_ID -> decoder.reader.readObjectId()
                    else -> throw SerializationException("Unsupported ${decoder.reader.currentBsonType} reading object id")
                }
            }
            is JsonDecoder -> ObjectId(decoder.decodeString())
            else -> throw SerializationException("Unknown decoder type")
        }

    }
}

@Serializer(forClass = UUID::class)
object UUIDSerializer : KSerializer<UUID> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("UUIDSerializer", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: UUID) {
        when (encoder) {
            is BsonEncoder -> encoder.encodeUUID(value,UuidRepresentation.STANDARD)
            is JsonEncoder -> encoder.encodeString(value.toString())
            else -> throw SerializationException("Unknown encoder type")
        }
    }

    override fun deserialize(decoder: Decoder): UUID {
        return when (decoder) {
            is FlexibleDecoder -> {
                when (decoder.reader.currentBsonType) {
                    BsonType.STRING -> UUID.fromString(decoder.decodeString())
                    BsonType.BINARY -> decoder.reader.readBinaryData().asUuid(UuidRepresentation.STANDARD)
                    else -> throw SerializationException("Unsupported ${decoder.reader.currentBsonType} reading object id")
                }
            }
            is JsonDecoder -> UUID.fromString(decoder.decodeString())
            else -> throw SerializationException("Unknown decoder type")
        }
    }
}

@Serializer(forClass = JsonElement::class)
object JsonElementSerializer : KSerializer<JsonElement> {

    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("JsonElementSerializer", PrimitiveKind.STRING)

    private val ListSerializer = ListSerializer(JsonElementSerializer)

    override fun serialize(encoder: Encoder,value: JsonElement) {
        when (encoder) {
            is BsonEncoder -> when (value) {
                is JsonNull -> {
                    encoder.encodeNull()
                }
                is JsonObject -> encoder.encodeSerializableValue(JsonObjectSerializer,value)
                is JsonPrimitive -> {

                    if (value.isString) {
                        return encoder.encodeString(value.content)
                    }

                    value.longOrNull?.let { return encoder.encodeLong(it) }

                    value.content.toULongOrNull()?.let {
                        encoder.encodeInline(ULong.serializer().descriptor).encodeLong(it.toLong())
                        return
                    }

                    value.doubleOrNull?.let { return encoder.encodeDouble(it) }
                    value.booleanOrNull?.let { return encoder.encodeBoolean(it) }
                }
                is JsonArray -> {
                    encoder.encodeSerializableValue(ListSerializer,value)
                }
                else -> throw SerializationException("Unknown JsonElement")
            }
            is JsonEncoder -> JsonElement.serializer().serialize(encoder,value)
            else -> throw SerializationException("Unknown encoder type")
        }

    }

    override fun deserialize(decoder: Decoder): JsonElement {
        return when (decoder) {
            is FlexibleDecoder -> {

                when (decoder.reader.currentBsonType) {
                    BsonType.NULL -> decoder.reader.readNull().let{ JsonNull }
                    BsonType.STRING -> JsonPrimitive(decoder.decodeString())
                    BsonType.BOOLEAN -> JsonPrimitive(decoder.decodeBoolean())
                    BsonType.DOUBLE -> JsonPrimitive(decoder.decodeDouble())
                    BsonType.INT32 -> JsonPrimitive(decoder.decodeInt())
                    BsonType.INT64 -> JsonPrimitive(decoder.decodeLong())
                    BsonType.ARRAY -> {
                        JsonArray(decoder.decodeSerializableValue(ListSerializer))
                    }
                    else -> throw SerializationException("Unknown JsonElement")
                }
            }
            is JsonDecoder -> JsonElement.serializer().deserialize(decoder)

            else -> throw SerializationException("Unknown decoder type")
        }
    }

}

@Serializer(forClass = JsonObject::class)
object JsonObjectSerializer : KSerializer<JsonObject> {

    private object JsonObjectDescriptor : SerialDescriptor by MapSerializer(String.serializer(),JsonElementSerializer).descriptor {
        override val serialName: String = "kotlinx.serialization.json.JsonObject"
    }

    override val descriptor: SerialDescriptor = JsonObjectDescriptor

    override fun serialize(encoder: Encoder, value: JsonObject) {
        when (encoder) {
            is BsonEncoder -> MapSerializer(String.serializer(),JsonElementSerializer).serialize(encoder,value)
            is JsonEncoder -> JsonObject.serializer().serialize(encoder,value)
            else -> throw SerializationException("Unknown encoder type")
        }

    }

    override fun deserialize(decoder: Decoder): JsonObject {
        return when (decoder) {
            is BsonFlexibleDecoder -> JsonObject(MapSerializer(String.serializer(),JsonElementSerializer).deserialize(decoder))
            is JsonDecoder -> JsonObject.serializer().deserialize(decoder)
            else -> throw SerializationException("Unknown decoder type")
        }
    }

}

val DefaultModule = SerializersModule {
    contextual(ObjectId::class, ObjectIdSerializer)
    contextual(BigDecimal::class, BigDecimalSerializer)
    contextual(ByteArray::class, ByteArraySerializer)
    contextual(Date::class, DateSerializer)
    contextual(UUID::class, UUIDSerializer)

    contextual(JsonObject::class, JsonObjectSerializer)
    contextual(JsonElement::class, JsonElementSerializer)
}
