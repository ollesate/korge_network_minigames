package com.example.plugins

import Message
import Player
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Duration
import kotlin.system.measureTimeMillis

val json = Json {
    ignoreUnknownKeys = true
}

var webSockets = mutableListOf<DefaultWebSocketSession>()
var players = mutableMapOf<DefaultWebSocketSession, Player>()
var host: DefaultWebSocketSession? = null

var messageId: Long = 0L

fun Application.configureSockets() {
    install(WebSockets) {
        pingPeriod = Duration.ofSeconds(15)
        timeout = Duration.ofSeconds(15)
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }
    routing {
        webSocket("/") {
            val index = webSockets.size
            webSockets += this
            println("New socket connection ${webSockets.indexOf(this)}, total amount ${webSockets.size}")

            val player = Player(
                id = webSockets.indexOf(this).toString(),
                isHost = webSockets.size == 1
            )

            players += this to player

            if (host != null && host != this) {
                sendMessage<Message>(Message.PlayerJoined(player))
            }

            for (frame in incoming) {
                if (frame is Frame.Text) {
                    val text = frame.readText()
                    val message = json.decodeFromString<Message>(text)

                    println("client ${webSockets.indexOf(this)} - said $text")

                    if (message == Message.GetPlayers) {
                        host = this
                        players.entries.filter { it.key != this }.forEach {
                            sendMessage<Message>(Message.PlayerJoined(it.value))
                        }
                        continue
                    } else if (message is Message.SendControl) {
                        host!!.sendMessage<Message>(
                            Message.ReceiveControl(player, message.key)
                        )
                        continue
                    }

                    if (message is Message.UpdateState) {
                        (webSockets - this).forEach {
                            it.sendMessage(message.copy(id = messageId))
                        }
                        messageId++
                        continue
                    }

                    println("Respond to client: " + (webSockets - this).map { webSockets.indexOf(it) })

                    (webSockets - this).forEach {
                        try {
                            it.send(Frame.Text(text))
                        } catch (ex: Exception) {
                            ex.printStackTrace()
                            println("client ${webSockets.indexOf(it)} found dead ${ex.message}")
                            webSockets -= it
                            players.remove(it)
//                            closeExceptionally(ex)
                        }
                    }
                }
            }

            webSockets -= this
            val leaver = players.remove(this)
            if (host == this) {
                println("Host left remove all other players")
                webSockets.forEach { it.close() }
                webSockets.clear()
                players.clear()
            } else {
                println("player ${leaver?.id} left, ${webSockets.size} players left")
            }
        }
    }
}

suspend inline fun <reified T: Message> WebSocketSession.sendMessage(data: T) {
    try {
        send(Json.encodeToString<Message>(data).also {
            println("Send to player $it")
        })
    } catch (ex: Exception) {
        ex.printStackTrace()
        println("failed to send $ex")
    }
}
