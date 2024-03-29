package net

import Message
import Player
import ViewState
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

private val json = Json {
    ignoreUnknownKeys = true
}

class Client(
    val socket: ClientWithNoLag,
) {
    private val inputChannel = socket.messageChannelString()
    private val outputChannel = Channel<Message>(Channel.UNLIMITED)
    private val playerJoined = AsyncSignal<Message.PlayerJoined>()
    private val controlUpdate = AsyncSignal<Message.ReceiveControl>()
    private val updateState = AsyncSignal<Message.UpdateState>()

    init {
        launch(Dispatchers.CIO) {
            while (!outputChannel.isClosedForReceive) {
                val message = outputChannel.receive()
                println("Send message $message")
                socket.send(json.encodeToString<Message>(message))
            }
            error("Send channel closed")
        }

        launch(Dispatchers.CIO) {
            for (message in inputChannel) {
                println("Received message $message")
                messageReceived(json.decodeFromString<Message>(message as String))
            }
            error("Receive channel closed")
        }

        socket.onError {
            it.printStackTrace()
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

    fun onControlUpdate(function: (Message.ReceiveControl) -> Unit) {
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
        outputChannel.trySend(message).also { println("try send $message ${it.isSuccess}") }
    }
}

suspend fun Container.onPlayerJoined(injector: Injector, block: suspend Container.(playerJoined: Message.PlayerJoined) -> Unit) {
    val client = injector.get<Client>()
    client.onPlayerJoined {
        block(this, it)
    }
    client.send(Message.GetPlayers)
}

fun onKeysReceived(injector: Injector, block: (Pair<Player, List<Key>>) -> Unit) {
    val client = injector.get<Client>()
    client.onControlUpdate {
        block(it.player to it.key)
    }
}

suspend fun Container.onState(injector: Injector, block: suspend Container.(playerJoined: Message.UpdateState) -> Unit) {
    val client = injector.get<Client>()
    client.onUpdateState {
        block(this, it)
    }
}
