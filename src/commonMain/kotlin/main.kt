import korlibs.event.*
import korlibs.korge.*
import korlibs.korge.scene.*
import korlibs.korge.view.*
import korlibs.image.color.*
import korlibs.image.format.*
import korlibs.inject.*
import korlibs.io.async.*
import korlibs.io.file.std.*
import korlibs.korge.input.*
import korlibs.math.geom.*
import kotlinx.coroutines.*
import net.*

val colors = listOf(
    Colors.RED, Colors.GREEN, Colors.BLUE, Colors.YELLOW
)

suspend fun Container.multiClientKorge(numberOfClients: Int, sceneBlocks: suspend SceneContainer.(Pair<Injector, Int>) -> Unit) {
    val viewToShow = AsyncSignal<Int>()

    listOf(Key.N1, Key.N2, Key.N3, Key.N4, Key.N5, Key.N6).take(numberOfClients).forEachIndexed { index, key ->
        keys.down(key) {
            println("Pressed $key")
            viewToShow(index)
        }
    }
    class MyScene: Scene()

    val clients = (0 until numberOfClients).map {
        async(Dispatchers.Default) {
            client(connect = true)
        }
    }.awaitAll()

    val sceneContainers = (0 until numberOfClients).map { index ->
        sceneContainer {
            val scene = changeTo(
                clients[index]
            ) {
                MyScene()
            }
            sceneBlocks(this, scene.injector to index)
        }
    }

    viewToShow { index ->
        println("Change to scene $index")
        sceneContainers.forEachIndexed { current, sceneContainer ->
            sceneContainer.visible = current == index
        }
    }

    viewToShow(0)
}

suspend fun main() = Korge(windowSize = Size(750, 750), backgroundColor = Colors["#2b2b2b"]) {
    multiClientKorge(4) { (injector, index) ->
        onPlayerJoined(injector) {
            println("player joined $it")
            solidRect(50, 50) {
                playerControls(injector, "box_" + it.player.id) {
                    onReceive {
                        println("onReceive control update $it")
                        when (it) {
                            "A" -> x -= 10
                            "D" -> x += 10
                            "W" -> y -= 10
                            "S" -> y += 10
                        }
                    }

                    if (it.isYou) {
                        colorMul = Colors.RED
                        keys {
                            down(Key.A) {
                                if (this@multiClientKorge.visible) send("A")
                            }
                            down(Key.D) {
                                if (this@multiClientKorge.visible) send("D")
                            }
                            down(Key.W) {
                                if (this@multiClientKorge.visible) send("W")
                            }
                            down(Key.S) {
                                if (this@multiClientKorge.visible) send("S")
                            }
                        }
                    }
                }
            }
        }
//        solidRect(50, 50, color = colors[index]) {
//            keys {
//                down(Key.A) {
//                    if (this@multiClientKorge.visible) x -= 10
//                }
//                down(Key.D) {
//                    if (this@multiClientKorge.visible) x += 10
//                }
//            }
//        }
    }
}

interface Controls2 {
    fun send(string: String)
    fun onReceive(block: (String) -> Unit)
}

suspend fun View.playerControls(injector: Injector, controlId: String, block: Controls2.() -> Unit): Controls2 {
    val client = injector.get<Client>()
    val callbacks = mutableListOf<(String) -> Unit>()
    client.onControlUpdate { message ->
        if (message.controlId == controlId) {
            callbacks.forEach {
                it.invoke(message.key)
            }
        }
    }
    return object: Controls2 {
        override fun send(string: String) {
            client.send(Message.ControlUpdate(controlId, string))
        }

        override fun onReceive(block: (String) -> Unit) {
            callbacks += block
        }
    }.also(block)
}

suspend fun View.send(message: Message) {
    val client = injector().get<Client>()
    client.send(message)
}

class Focusable(
    var isFocused: Boolean
)

class MainScene(
    val sceneColor: RGBA
) : Scene() {
    override suspend fun SContainer.sceneMain() {
        val focusable = injector.get<Focusable>()
        fixedSizeContainer(size, clip = true) {
            solidRect(size, sceneColor) {
                addUpdater {
                    color = sceneColor.withAf(
                        0.7f.takeIf { focusable.isFocused } ?: 0.2f
                    )
                }
            }
            onHost {

            }

            onPlayerJoined(injector()) { playerJoined ->
                spaceship {
                    controls(injector, "player_spaceship_${playerJoined.player.id}", playerJoined) {
                        it.pressing(Key.W) {
                            y -= 10
                        }
                        it.pressing(Key.S) {
                            y += 10
                        }
                        it.pressing(Key.A) {
                            x -= 10
                        }
                        it.pressing(Key.D) {
                            x += 10
                        }
                    }
                }
            }
        }
    }
}

interface Controls {
    fun pressing(key: Key, block: () -> Unit)
}

class PlayerControls(
    val controlId: String,
    val focusable: Focusable,
    val view: View,
    val client: Client,
    val isOwner: Boolean
) : Controls {
    private val actions = mutableMapOf<String, () -> Unit>()

    init {
        client.onControlUpdate {
            if (it.controlId == controlId) {
                actions[it.key]?.invoke()
            }
        }
    }

    override fun pressing(key: Key, block: () -> Unit) {
        actions += key.name to block
        if (isOwner) {
            println("Send controls $key")
            view.keys.down(key) {
                // Frames.append()
                if (focusable.isFocused) {
                    client.send(Message.ControlUpdate(controlId, key.name))
                }
            }
        }
    }
}

suspend fun View.controls(
    injector: Injector,
    controlId: String,
    playerJoined: Message.PlayerJoined,
    block: (controls: Controls) -> Unit
) {
    val client = injector.get<Client>()
    val myPlayer = client.player
    println("controls and player is $myPlayer, is owner? ${playerJoined.isYou}")
    PlayerControls(
        controlId,
        injector.get<Focusable>(),
        view = this,
        client = client,
        isOwner = playerJoined.isYou
    ).also(block)
}

suspend fun Container.onPlayerJoined(injector: Injector, block: suspend Container.(playerJoined: Message.PlayerJoined) -> Unit) {
    val client = injector.get<Client>()
    client.onPlayerJoined {
        block(this, it)
    }
    client.send(Message.GetPlayers)
}

fun Scene.onHost(block: () -> Unit) {

}

suspend inline fun Container.spaceship(block: Image.() -> Unit) {
    image(resourcesVfs["spaceship.png"].readBitmap()) {
        anchor(.5, .5)
        position(256, 256)
    }.also(block)
}
