import korlibs.event.*
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import kotlinx.serialization.json.*
import kotlinx.serialization.modules.*

val json = Json {
    ignoreUnknownKeys = true
}

class AnySerializer: KSerializer<Any> {
    override val descriptor: SerialDescriptor
        get() = PrimitiveSerialDescriptor("any", PrimitiveKind.INT)

    override fun deserialize(decoder: Decoder): Any {
        return decoder.decodeString()
    }

    override fun serialize(encoder: Encoder, value: Any) {

    }
}

fun main() {

}

@Serializable
data class Player(
    val id: String,
    val isHost: Boolean,
)

@Serializable
data class ViewState(
    val type: String,
    val props: Map<String, String>
)

@Serializable
sealed class Message {
    @Serializable
    @SerialName("PlayerJoined")
    data class PlayerJoined(
        val player: Player,
    ): Message()

    @Serializable
    @SerialName("ReceiveControl")
    data class ReceiveControl(
        val player: Player,
        val key: List<Key>
    ): Message()

    @Serializable
    @SerialName("SendControl")
    data class SendControl(
        val key: List<Key>
    ): Message()

    @Serializable
    @SerialName("GetPlayers")
    data object GetPlayers: Message()

    @Serializable
    @SerialName("UpdateState")
    data class UpdateState(
        val id: Long = 0L,
        val name: String,
        val viewState: ViewState
    ): Message()
}
