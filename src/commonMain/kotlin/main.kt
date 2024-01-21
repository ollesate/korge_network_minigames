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
        val client = injector.get<Client>()
        val playerRects = mutableMapOf<String, View>()
        fun playerRect() = solidRect(50, 50)
        val validKeys = listOf(Key.A, Key.D, Key.W, Key.S)

        if (index == 0) {
            onPlayerJoined(injector) { playerJoined ->
                println("Received on player join ${playerJoined.player.id}")
                playerRects += playerJoined.player.id to playerRect()
            }
            onKeysReceived(injector) { (player, key) ->
                println("Received control")
                playerRects[player.id]?.run {
                    if (key !in validKeys) return@run
                    when (key) {
                        Key.A -> x -= 10
                        Key.D -> x += 10
                        Key.W -> y -= 10
                        Key.S -> y += 10
                        else -> {}
                    }
                    client.send(Message.UpdateState(player.id, x, y))
                }
            }
        } else {
            onState(injector) {
                playerRects.getOrPut(it.name) {
                    playerRect()
                }.apply {
                    x = it.x
                    y = it.y
                }
            }
            keys {
                validKeys.forEach { key ->
                    down(key) {
                        if (this@multiClientKorge.visible) {
                            client.send(Message.SendControl(key))
                        }
                    }
                }
            }
        }
    }
}

suspend inline fun Container.spaceship(block: Image.() -> Unit) {
    image(resourcesVfs["spaceship.png"].readBitmap()) {
        anchor(.5, .5)
        position(256, 256)
    }.also(block)
}
