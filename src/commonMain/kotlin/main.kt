import korlibs.event.*
import korlibs.korge.*
import korlibs.korge.scene.*
import korlibs.korge.view.*
import korlibs.image.color.*
import korlibs.image.format.*
import korlibs.inject.injector
import korlibs.io.async.CIO
import korlibs.io.async.launch
import korlibs.io.file.std.*
import korlibs.io.lang.cancel
import korlibs.io.lang.cancellable
import korlibs.io.net.ws.WebSocketClient
import korlibs.korge.input.onDown
import korlibs.math.geom.*
import kotlinx.coroutines.Dispatchers
import net.Client
import net.SOCKET_URL
import net.client
import kotlin.coroutines.coroutineContext

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
            changeTo(
                injects = arrayOf(
                    client(),
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
            player()
        }
	}
}

fun Scene.onPlayer() {

}

fun Scene.onHost() {

}


suspend fun Container.player() {
    val client = injector().get<Client>()
    val context = coroutineContext
    val focusable = injector().get<Focusable>()

    client.listen()

    image(resourcesVfs["spaceship.png"].readBitmap()) {
        anchor(.5, .5)
        position(256, 256)

        client.onMessage { message ->
            when (message) {
                "W" -> y -= 10
                "S" -> y += 10
                "A" -> x -= 10
                "D" -> x += 10
            }
        }

        addUpdater {
            if (!focusable.isFocused) return@addUpdater
            when {
                stage!!.input.keys.pressing(Key.W) -> {
                    client.send("W")
                }
                stage!!.input.keys.pressing(Key.S) -> {
                    client.send("S")
                }
                stage!!.input.keys.pressing(Key.A) -> {
                    client.send("A")
                }
                stage!!.input.keys.pressing(Key.D) -> {
                    client.send("D")
                }
            }
        }
    }
}
