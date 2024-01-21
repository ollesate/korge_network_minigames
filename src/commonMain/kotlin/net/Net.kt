package net

import korlibs.event.*
import korlibs.inject.*
import korlibs.io.async.*
import korlibs.korge.view.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*

const val SOCKET_URL = "ws://localhost:8080/"
//const val SOCKET_URL = "ws://ktor-minigames-075959892b51.herokuapp.com/"

suspend fun client(connect: Boolean = true) = Client(
    ClientWithNoLag(SOCKET_URL, connect = connect)
)

@Serializable
data class Player(
    val id: String,
    val isHost: Boolean,
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
        val name: String,
        val x: Double,
        val y: Double
    ): Message()
}

private val json = Json {
    ignoreUnknownKeys = true
}

class Client(
    val socket: ClientWithNoLag,
) {
    private val inputChannel = socket.messageChannelString()
    private val outputChannel = Channel<Message>()
    private val playerJoined = AsyncSignal<Message.PlayerJoined>()
    private val controlUpdate = AsyncSignal<Message.ReceiveControl>()
    private val updateState = AsyncSignal<Message.UpdateState>()

    init {
        launch(Dispatchers.CIO) {
            while (!outputChannel.isClosedForReceive) {
                val message = outputChannel.receive()
                socket.send(json.encodeToString<Message>(message))
//                messageReceived(message)
            }
        }

        launch(Dispatchers.CIO) {
            for (message in inputChannel) {
                println("Received message $message")
                messageReceived(json.decodeFromString<Message>(message as String))
            }
        }
    }

    fun onPlayerJoined(function: suspend (Message.PlayerJoined) -> Unit) {
        playerJoined {
            function(it)
        }
    }

    fun onUpdateState(function: suspend (Message.UpdateState) -> Unit) {
        updateState {
            function(it)
        }
    }

    fun onControlUpdate(function: suspend (Message.ReceiveControl) -> Unit) {
        controlUpdate {
            function(it)
        }
    }

    private suspend fun messageReceived(message: Message) {
        when (message) {
            is Message.PlayerJoined -> playerJoined(message)
            is Message.ReceiveControl -> controlUpdate(message)
            is Message.UpdateState -> updateState(message)
            Message.GetPlayers -> {}
            else -> {}
        }
    }

    fun send(message: Message) {
        println("Send message $message")
        outputChannel.trySend(message)
    }
}

suspend fun Container.onPlayerJoined(injector: Injector, block: suspend Container.(playerJoined: Message.PlayerJoined) -> Unit) {
    val client = injector.get<Client>()
    client.onPlayerJoined {
        block(this, it)
    }
    client.send(Message.GetPlayers)
}

suspend fun Container.onKeysReceived(injector: Injector, block: suspend Container.(Pair<Player, List<Key>>) -> Unit) {
    val client = injector.get<Client>()
    client.onControlUpdate {
        block(this, it.player to it.key)
    }
}

suspend fun Container.onState(injector: Injector, block: suspend Container.(playerJoined: Message.UpdateState) -> Unit) {
    val client = injector.get<Client>()
    client.onUpdateState {
        block(this, it)
    }
}
