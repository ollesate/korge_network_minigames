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

class ClientServerGame(
    val playerKeyState: MutableMap<Player, List<Key>>
) {
    fun getPlayerInput(player: Player) = playerKeyState[player].orEmpty()
}

suspend fun Container.clientServerGame(injector: Injector, index: Int, isHost: Boolean, block: suspend ClientServerGame.() -> Unit) {
    val client = injector.get<Client>()
    val playerKeyState = mutableMapOf<Player, List<Key>>()
    var myKeyState = listOf<Key>()
    val validKeys = listOf(
        listOf(Key.A, Key.D, Key.W, Key.S),
        listOf(Key.LEFT, Key.RIGHT, Key.UP, Key.DOWN),
        listOf(Key.I, Key.J, Key.K, Key.L),
    )

    if (isHost) {
        onKeysReceived(injector) { (player, keys) ->
            println("Received control")
            playerKeyState[player] = keys
        }
    } else {
        addUpdater {
            val keyState = validKeys[index - 1].filter {
                stage?.keys?.pressing(it) == true
            }
            if (myKeyState != keyState) {
                client.send(
                    Message.SendControl(keyState)
                )
                myKeyState = keyState
            }
        }
    }

    ClientServerGame(
        playerKeyState
    ).also {
        block(it)
    }
}

val keysLeft = listOf(Key.A, Key.J, Key.LEFT)
val keysRight = listOf(Key.D, Key.L, Key.RIGHT)
val keysUp = listOf(Key.W, Key.I, Key.UP)
val keysDown = listOf(Key.S, Key.K, Key.DOWN)

suspend fun main() = Korge(windowSize = Size(750, 750), backgroundColor = Colors["#2b2b2b"]) {
    multiClientKorge(3) { (injector, index) ->
        val isHost = index == 0
        clientServerGame(injector, index, isHost) {
            if (isHost) {
                println("Host started")
                onPlayerJoined(injector) { playerJoined ->
                    println("OnPlayerJoined ${playerJoined.player.id}")
                    solidRect(50, 50) {
                        spawn(injector, "rect_${playerJoined.player.id}")
                        addUpdater {
                            getPlayerInput(playerJoined.player).forEach { key ->
                                when (key) {
                                    in keysLeft -> x -= 10
                                    in keysRight -> x += 10
                                    in keysUp -> y -= 10
                                    in keysDown -> y += 10
                                    else -> {}
                                }
                            }
                        }
                    }
                }
            } else {
                println("Client started")
                onState(injector) {
                    solidRect(50, 50) {
                        x = it.x
                        y = it.y
                    }
                }
            }
        }
    }
}

fun View.spawn(injector: Injector, name: String) {
    val client = injector.get<Client>()
    var oldX = x
    var oldY = y
    addUpdater {
        if (x != oldX || y != oldY) {
            oldX = x
            oldY = y
            client.send(Message.UpdateState(name, x, y))
        }
    }
    client.send(Message.UpdateState(name, x, y))
}

fun playerInput(injector: Injector, player: Player, function: (keys: List<Key>) -> Unit) {
    injector.get<Client>().onControlUpdate {
        if (it.player == player) {
            function(it.key)
        }
    }
}

suspend inline fun Container.spaceship(block: Image.() -> Unit) {
    image(resourcesVfs["spaceship.png"].readBitmap()) {
        anchor(.5, .5)
        position(256, 256)
    }.also(block)
}
