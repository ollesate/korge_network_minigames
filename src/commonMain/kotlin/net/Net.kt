package net

import korlibs.inject.injector
import korlibs.io.async.*
import korlibs.io.net.ws.WebSocketClient
import korlibs.korge.view.View
import korlibs.math.geom.degrees
import korlibs.time.*
import kotlinx.coroutines.*
import kotlinx.coroutines.NonCancellable.isActive
import kotlinx.coroutines.channels.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import kotlin.reflect.KMutableProperty0

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
        val isYou: Boolean
    ): Message()
    @Serializable
    @SerialName("PlayerAssigned")
    data class PlayerAssigned(val player: Player): Message()
    @Serializable
    @SerialName("ControlUpdate")
    data class ControlUpdate(val controlId: String, val key: String): Message()
    @Serializable
    @SerialName("GetPlayers")
    data object GetPlayers: Message()
}

class Client(
    val socket: ClientWithNoLag,
) {
    val inputChannel = socket.messageChannelString()
    val outputChannel = Channel<Message>()
    var player: Player? = null
    private val json = Json {
        ignoreUnknownKeys = true
    }
    val playerJoined = AsyncSignal<Message.PlayerJoined>()
    val controlUpdate = AsyncSignal<Message.ControlUpdate>()
    var times = 0

    init {
        launch(Dispatchers.CIO) {
            while (!outputChannel.isClosedForReceive) {
                val message = outputChannel.receive()
                socket.send(json.encodeToString<Message>(message))
                messageReceived(message)
            }
        }

        launch(Dispatchers.CIO) {
            for (message in inputChannel) {
                times++
                println("Recevied message $message")
                messageReceived(json.decodeFromString<Message>(message as String))
            }
        }
    }

    fun onPlayerJoined(function: suspend (Message.PlayerJoined) -> Unit) {
        playerJoined {
            function(it)
        }
    }

    fun onControlUpdate(function: suspend (Message.ControlUpdate) -> Unit) {
        controlUpdate {
            function(it)
        }
    }

    fun syncState(id: String, listener: (Map<String, String>) -> Unit) {

    }

    suspend fun listen() = launch(Dispatchers.Default) {
        socket.internalConnect()
    }

    suspend fun messageReceived(message: Message) {
        when (message) {
            is Message.PlayerJoined -> playerJoined(message)
            is Message.ControlUpdate -> controlUpdate(message)
            is Message.PlayerAssigned -> {
                player = message.player
            }
            Message.GetPlayers -> {}
        }
    }

    fun send(message: Message) {
        println("Send message $message")
        outputChannel.trySend(message)
    }
}

@Serializable
data class State(
    val id: String,
    val prototype: String,
    val props: Map<String, String>,
)

suspend fun View.sync(
    id: String,
    setters: Map<String, View.(String) -> Unit> = propSetters
) {
    injector().get<Client>().syncState(id) { props ->
        updateFromProps(props)
    }
}

fun View.updateFromProps(props: Map<String, String>) {
    props.forEach { (name, value) ->
        propSetters[name]?.invoke(this, value)
    }
}

val View.propSetters: Map<String, View.(String) -> Unit>
    get() = mapOf(
        "alpha" to { alpha = it.toDouble() },
        "rotation" to { rotation = it.toDouble().degrees },
        "x" to { x = it.toDouble() },
        "y" to { y = it.toDouble() },
    )

fun prop(prop: KMutableProperty0<Double>) {

}

fun setter(prop: KMutableProperty0<Double>): (String) -> Unit = {
    prop.set(it.toDouble())
}
