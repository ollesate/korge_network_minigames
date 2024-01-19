import korlibs.event.*
import korlibs.korge.*
import korlibs.korge.scene.*
import korlibs.korge.view.*
import korlibs.image.color.*
import korlibs.image.format.*
import korlibs.inject.*
import korlibs.io.file.std.*
import korlibs.korge.input.*
import korlibs.math.geom.*
import net.*

suspend fun main() = Korge(windowSize = Size(750, 750), backgroundColor = Colors["#2b2b2b"]) {
    val focusables = (0 until 4).map {
        Focusable(it == 0)
    }

    listOf(
        Point(0, 0) to Colors.RED,
        Point(width / 2, 0) to Colors.GREEN,
        Point(0, height / 2) to Colors.BLUE,
        Point(width / 2, height / 2) to Colors.ORANGE,
    ).forEachIndexed { index, (point, color) ->
        sceneContainer(
            size = Size(width / 2, height / 2)
        ).apply {
            onDown {
                focusables.forEach { it.isFocused = false }
                focusables[index].isFocused = true
            }

            pos = point
            val client = client()
            println("client created")
            client.listen()

            changeTo(
                injects = arrayOf(
                    client,
                    focusables[index],
                )
            ) { MainScene(color) }
        }
    }
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

            onPlayerJoined { playerJoined ->
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
): Controls {
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
                println("Blomma ${injector()}")
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

suspend fun Container.onPlayerJoined(block: suspend Container.(playerJoined: Message.PlayerJoined) -> Unit) {
    val client = injector().get<Client>()
    client.onPlayerJoined {
        println("PLAYER_JOINED")
        block(this, it)
    }
}

fun Scene.onHost(block: () -> Unit) {

}

suspend inline fun Container.spaceship(block: Image.() -> Unit) {
    image(resourcesVfs["spaceship.png"].readBitmap()) {
        anchor(.5, .5)
        position(256, 256)
    }.also(block)
}
