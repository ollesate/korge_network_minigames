package net

import korlibs.encoding.*
import korlibs.io.async.*
import korlibs.io.experimental.*
import korlibs.io.lang.*
import korlibs.io.net.*
import korlibs.io.net.http.*
import korlibs.io.net.ws.*
import korlibs.io.stream.*
import korlibs.io.util.*
import korlibs.memory.*
import korlibs.platform.*
import korlibs.time.*
import kotlinx.coroutines.*
import kotlin.coroutines.*
import kotlin.random.*

suspend fun ClientWithNoLag(
    url: String,
    protocols: List<String>? = null,
    origin: String? = null,
    wskey: String = DEFAULT_WSKEY,
    debug: Boolean = false,
    connect: Boolean = true,
    headers: Http.Headers = Http.Headers(),
    masked: Boolean = true,
    init: WebSocketClient.() -> Unit = {},
): ClientWithNoLag {
    if (Platform.isJsBrowserOrWorker) error("RawSocketWebSocketClient is not supported on JS browser. Use WebSocketClient instead")
    val uri = URL(url)
    val secure: Boolean = uri.isSecureScheme
    return ClientWithNoLag(
        coroutineContext,
        AsyncClient.create(secure = secure),
        uri,
        protocols,
        debug,
        origin,
        wskey,
        headers,
        masked
    ).also {
        init(it)
        if (connect) it.internalConnect()
    }
}

class ClientWithNoLag(
    val coroutineContext: CoroutineContext,
    val client: AsyncClient,
    val urlUrl: URL,
    protocols: List<String>? = null,
    debug: Boolean = false,
    val origin: String? = null,
    val key: String = DEFAULT_WSKEY,
    val headers: Http.Headers = Http.Headers(),
    val masked: Boolean = true,
    val random: Random = Random,
) : WebSocketClient(urlUrl.fullUrl, protocols, debug) {
    private var frameIsBinary = false
    val host = urlUrl.host ?: "127.0.0.1"
    val port = urlUrl.port

    init {
        if (key.length != 16) error("key must be 16 bytes (enforced by ws.js)")
    }

    internal fun buildHeader(): String {
        val baseHeaders = Http.Headers.build {
            put("Host", "$host:$port")
            put("Pragma", "no-cache")
            put("Cache-Control", "no-cache")
            put("Upgrade", "websocket")
            protocols?.let {
                put("Sec-WebSocket-Protocol", it.joinToString(", "))
            }

            put("Sec-WebSocket-Version", "13")
            put("Connection", "Upgrade")
            put("Sec-WebSocket-Key", key.toByteArray().toBase64())
            if (origin != null) {
                put("Origin", origin)
            }
            put("User-Agent", HttpClient.DEFAULT_USER_AGENT)
        }
        val computedHeaders = baseHeaders.withReplaceHeaders(headers)
        return (buildList {
            add("GET ${urlUrl.pathWithQuery.takeIf { it.isNotEmpty() } ?: "/"} HTTP/1.1")
            for (item in computedHeaders) {
                add("${item.first}: ${item.second}")
            }
        }.joinToString("\r\n") + "\r\n\r\n")
    }

    private var readPacketsJob: Job? = null

    suspend fun internalConnect() {
        if (Platform.isJsBrowserOrWorker) error("RawSocketWebSocketClient is not supported on JS browser. Use WebSocketClient instead")

        println("internalConnect 1")

        client.connect(host, port)
        println("internalConnect 2")
        client.writeBytes(buildHeader().toByteArray())
        println("internalConnect 3")

        // Read response
        val headers = arrayListOf<String>()
        while (true) {
            val line = client.readLine().trimEnd()
            if (line.isEmpty()) {
                headers += line
                break
            }
        }

        println("internalConnect 4")
        readPacketsJob = launchImmediately(coroutineContext) {
            delay(1.milliseconds)
            println("internalConnect 5")
            launch(Dispatchers.Default) {
                internalReadPackets()
            }
            println("internalConnect 6")
        }
    }

    private val chunks = arrayListOf<ByteArray>()
    private var isTextFrame = false

    @KorioInternal
    suspend fun internalReadPackets() {
        var close = CloseInfo(CloseReasons.NORMAL, null, false)
        onOpen(Unit)
        try {
            loop@ while (!closed) {
                val frame = withContext(Dispatchers.Default) {
                    readWsFrameOrNull()
                } ?: break

                if (frame.type == WsOpcode.Close) {
                    val closeReason = if (frame.data.size >= 2) frame.data.getU16BE(0) else CloseReasons.UNEXPECTED
                    val closeMessage = if (frame.data.size >= 3) frame.data.readString(2, frame.data.size - 2) else null
                    close = CloseInfo(closeReason, closeMessage, true)
                }

                when (frame.type) {
                    WsOpcode.Ping -> {
                        sendWsFrame(WsFrame(frame.data, WsOpcode.Pong, masked = masked))
                    }

                    WsOpcode.Pong -> {
                        lastPong = DateTime.now()
                    }

                    WsOpcode.Text, WsOpcode.Binary, WsOpcode.Continuation -> {

                        if (frame.type != WsOpcode.Continuation) {
                            chunks.clear()
                            isTextFrame = (frame.type == WsOpcode.Text)
                        }
                        chunks.add(frame.data)
                        if (frame.isFinal) {
                            val payloadBinary = chunks.join()
                            chunks.clear()
                            val payload: Any = if (isTextFrame) payloadBinary.toString(UTF8) else payloadBinary
                            when (payload) {
                                is String -> onStringMessage(payload)
                                is ByteArray -> onBinaryMessage(payload)
                            }
                            onAnyMessage(payload)
                        }

                    }
                }
            }
        } catch (e: Throwable) {
            //e.printStackTrace()
            onError(e)
            if (e is CancellationException) throw e
        } finally {
            onClose(close)
        }
    }

    private var lastPong: DateTime? = null

    var closed = false

    override fun close(code: Int, reason: String) {
        closed = true

        launchImmediately(coroutineContext) {
            sendWsFrame(WsCloseInfo(code, reason).toFrame(masked))
            readPacketsJob?.cancel()
        }
    }

    override suspend fun send(message: String) {
        sendWsFrame(WsFrame(message.toByteArray(UTF8), WsOpcode.Text, masked = masked))
    }

    override suspend fun send(message: ByteArray) {
        sendWsFrame(WsFrame(message, WsOpcode.Binary, masked = masked))
    }

    companion object {
        suspend fun readWsFrame(s: AsyncInputStream): WsFrame = WsFrame.readWsFrame(s)
        suspend fun readWsFrameOrNull(s: AsyncInputStream): WsFrame? = readFrameOrNull(s)
    }

    suspend fun readWsFrame(): WsFrame = readWsFrame(client)
    suspend fun readWsFrameOrNull(): WsFrame? = readWsFrameOrNull(client)

    suspend fun sendWsFrame(frame: WsFrame, random: Random = this.random) {
        // masked should be true (since sent from the client)
        client.writeBytes(frame.toByteArray(random))
    }
}

val times = mutableListOf<Double>()

suspend fun readFrameOrNull(s: AsyncInputStream): WsFrame? {
    times.clear()

    times += DateTime.nowUnixMillis()

    val b0 = s.read()

    times += DateTime.nowUnixMillis() // 16

    if (b0 < 0) return null
    val b1 = s.readU8()

    times += DateTime.nowUnixMillis() // 16

    val isFinal = b0.extract(7)
    val opcode = WsOpcode(b0.extract(0, 4))

    val partialLength = b1.extract(0, 7)
    val isMasked = b1.extract(7)

    val length = when (partialLength) {
        126 -> s.readU16BE()
        127 -> {
            val hi = s.readS32BE()
            if (hi != 0) error("message too long > 2**32")
            s.readS32BE()
        }

        else -> partialLength
    }

    times += DateTime.nowUnixMillis() // 0

    val mask = if (isMasked) s.readBytesExact(4) else null

    times += DateTime.nowUnixMillis() // 0

    val unmaskedData = s.readBytesExact(length)

    times += DateTime.nowUnixMillis() // 16

    val finalData = WsFrame.applyMask(unmaskedData, mask)

    times += DateTime.nowUnixMillis() // 0

//    println("read input " + times.mapIndexed { index, d ->
//        (d - (times.getOrNull(index - 1) ?: d))
//    })

    return WsFrame(finalData, opcode, isFinal, isMasked)
}


//class JvmNioAsyncClient(private var client: AsynchronousSocketChannel? = null) : AsyncClient {
//    private val readQueue = AsyncThread2()
//    private val writeQueue = AsyncThread2()
//
//    override val address: AsyncAddress get() = client?.remoteAddress.toAsyncAddress()
//
//    override suspend fun connect(host: String, port: Int) {
//        client?.close()
//        val client = doIo { AsynchronousSocketChannel.open(
//            //AsynchronousChannelGroup.withThreadPool(EventLoopExecutorService(coroutineContext))
//        ) }
//        this.client = client
//        nioSuspendCompletion<Void> { client.connect(InetSocketAddress(host, port), Unit, it) }
//    }
//
//    override val connected: Boolean get() = client?.isOpen ?: false
//
//    private val clientSure: AsynchronousSocketChannel get() = client ?: throw IOException("Not connected")
//
//    override suspend fun read(buffer: ByteArray, offset: Int, len: Int): Int = readQueue {
//        nioSuspendCompletion<Int> {
//            clientSure.read(ByteBuffer.wrap(buffer, offset, len), 0L, TimeUnit.MILLISECONDS, Unit, it)
//        }.toInt()
//    }
//
//    override suspend fun write(buffer: ByteArray, offset: Int, len: Int): Unit = writeQueue {
//        val b = ByteBuffer.wrap(buffer, offset, len)
//        while (b.hasRemaining()) writePartial(b)
//    }
//
//    private suspend fun writePartial(buffer: ByteBuffer): Int {
//        AsyncClient.Stats.writeCountStart.incrementAndGet()
//        try {
//            return nioSuspendCompletion<Int> {
//                clientSure.write(buffer, 0L, TimeUnit.MILLISECONDS, Unit, it)
//            }.also {
//                AsyncClient.Stats.writeCountEnd.incrementAndGet()
//            }
//        } catch (e: Throwable) {
//            AsyncClient.Stats.writeCountError.incrementAndGet()
//            throw e
//        }
//    }
//
//    override suspend fun close() {
//        client?.close()
//        client = null
//    }
//}


interface SocketInterface {
    suspend fun read(buffer: ByteArray, offset: Int, len: Int): Int
    suspend fun read(): Int
    suspend fun write(buffer: ByteArray, offset: Int = 0, len: Int = buffer.size - offset)
    suspend fun write(byte: Int)
    suspend fun close()
}

//class SocketWrapper(private val socket: Socket) : SocketInterface {
//
//    private val inputStream: InputStream = socket.getInputStream()
//    private val outputStream: OutputStream = socket.getOutputStream()
//
//    override suspend fun read(buffer: ByteArray, offset: Int, len: Int): Int = withContext(Dispatchers.IO) {
//        return@withContext inputStream.read(buffer, offset, len)
//    }
//
//    override suspend fun read(): Int = withContext(Dispatchers.IO) {
//        return@withContext inputStream.read()
//    }
//
//    override suspend fun write(buffer: ByteArray, offset: Int, len: Int) = withContext(Dispatchers.IO) {
//        outputStream.write(buffer, offset, len)
//    }
//
//    override suspend fun write(byte: Int) = withContext(Dispatchers.IO) {
//        outputStream.write(byte)
//    }
//
//    override suspend fun close() = withContext(Dispatchers.IO) {
//        socket.close()
//    }
//}
