package net

import korlibs.inject.injector
import korlibs.io.async.CIO
import korlibs.io.async.launch
import korlibs.io.net.ws.WebSocketClient
import korlibs.korge.view.Container
import korlibs.korge.view.View
import korlibs.korge.view.addUpdater
import korlibs.korge.view.solidRect
import korlibs.math.geom.Size
import korlibs.math.geom.degrees
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.Serializable
import kotlin.reflect.KMutableProperty0

const val SOCKET_URL = "ws://localhost:8080/"
//const val SOCKET_URL = "ws://ktor-minigames-075959892b51.herokuapp.com/"

suspend fun client() = Client(
    WebSocketClient(SOCKET_URL)
)

class Client(
    val socket: WebSocketClient,
) {
    private val listeners = mutableListOf<(String) -> Unit>()

    init {

    }

    fun onMessage(function: (String) -> Unit) {
        listeners += function
    }

    fun syncState(id: String, listener: (Map<String, String>) -> Unit) {

    }

    suspend fun listen() = launch(Dispatchers.Default) {
        socket.onStringMessage { message ->
            println("onString message")
            listeners.forEach { listener ->
                listener(message)
            }
        }
    }

    fun send(message: String) {
        listeners.forEach { listener ->
            listener(message)
        }
        launch(Dispatchers.CIO) {
            socket.send(message)
        }
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
