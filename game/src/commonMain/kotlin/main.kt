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

class GameServer(
    val injector: Injector,
    override val stage: Stage,
): Container() {
    private val client = injector.get<Client>()
    private val playerKeyState = mutableMapOf<Player, List<Key>>()
    val spawnedViews = mutableListOf<View>()

    init {
        onKeysReceived(injector) { (player, keys) ->
            println("Received control")
            playerKeyState[player] = keys
        }
    }

    fun getPlayerInput(player: Player) = playerKeyState[player].orEmpty()

    fun View.spawn(id: String) {
        spawnedViews += this
        name = id
        update()
    }

    fun View.update() {
        client.send(
            Message.UpdateState(
                name = name!!,
                viewState = getState()
            )
        )
    }
}

class GameClient(
    val injector: Injector,
    val keysToUse: List<Key>,
): Container() {
    private val client = injector.get<Client>()
    private var myKeyState = listOf<Key>()
    private val validKeys = listOf(
        Key.A, Key.D, Key.W, Key.S, Key.LEFT, Key.RIGHT, Key.UP, Key.DOWN,
    )

    init {
        addUpdater {
            val keyState = validKeys.filter {
                it in keysToUse && stage?.keys?.pressing(it) == true
            }
            if (myKeyState != keyState) {
                client.send(
                    Message.SendControl(keyState)
                )
                myKeyState = keyState
            }
        }
        client.onUpdateState { message ->
            val view = findViewByName(message.name) ?: message.viewState.construct()
                .also { it.name(message.name) }
            view.x = message.viewState.props["x"]?.toDouble() ?: view.x
            view.y = message.viewState.props["y"]?.toDouble() ?: view.y
        }
    }

    private fun ViewState.construct(): View {
        return when (type) {
            SolidRect::class.simpleName -> {
                solidRect(
                    width = props["width"]!!.toDouble(),
                    height = props["height"]!!.toDouble(),
                )
            }
            else -> error("Can't construct $type")
        }
    }
}

private fun View.getState(): ViewState {
    return ViewState(
        type = this::class.simpleName!!,
        props = when (this) {
            is SolidRect -> listOf(
                this::x,
                this::y,
                this::width,
                this::height,
            )

            else -> error("Failed to find state for $this")
        }.associate {
            it.name to it.get().toString()
        }
    )
}

suspend fun Container.gameServer(injector: Injector, block: suspend GameServer.() -> Unit) {
    GameServer(injector, stage!!).also {
        block(it)
        addChild(it)
    }
}


val keysLeft = listOf(Key.A, Key.J, Key.LEFT)
val keysRight = listOf(Key.D, Key.L, Key.RIGHT)
val keysUp = listOf(Key.W, Key.I, Key.UP)
val keysDown = listOf(Key.S, Key.K, Key.DOWN)

suspend fun main() = Korge(windowSize = Size(750, 750), backgroundColor = Colors["#2b2b2b"]) {
    multiClientKorge(3) { (injector, index) ->
        if (index == 0) {
            asteroidGame()
        } else {
            GameClient(
                injector,
                listOf(
                    listOf(Key.A, Key.D, Key.W, Key.S),
                    listOf(Key.LEFT, Key.RIGHT, Key.UP, Key.DOWN)
                )[index - 1]
            ).also {
                addChild(it)
            }
        }
    }
}

suspend fun SceneContainer.asteroidGame() = gameServer(currentScene!!.injector) {
    onPlayerJoined(injector) { playerJoined ->
        println("OnPlayerJoined ${playerJoined.player.id}")
        solidRect(50, 50) {
            x = spawnedViews.size * 100.0
            spawn(playerJoined.player.id)
            addUpdater {
                val input = getPlayerInput(playerJoined.player)
                input.forEach { key ->
                    when (key) {
                        in keysLeft -> x -= 10
                        in keysRight -> x += 10
                        in keysUp -> y -= 10
                        in keysDown -> y += 10
                        else -> {}
                    }
                }
                if (input.isNotEmpty()) {
                    update()
                }
            }
        }
    }
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
