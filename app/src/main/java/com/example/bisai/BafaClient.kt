package com.example.bisai

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket

/**
 * 极简巴法云 TCP 客户端：负责连接、订阅、心跳、解析消息。
 * 协议：形如 cmd=2&uid=xxxx&topic=light001&msg=on\r\n
 */
class BafaClient(
    private val host: String = DEFAULT_HOST,
    private val port: Int = DEFAULT_PORT,
    private val uid: String,
) {
    data class HistoryEntry(val topic: String, val msg: String, val timestamp: Long)
    data class Config(
        val topics: List<String>,
        val pullHistoryOnStart: Boolean = true,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var socket: Socket? = null
    private var reader: BufferedReader? = null
    private var writer: BufferedWriter? = null

    // 最近一次消息值，key 为 topic。
    val latestValues = MutableStateFlow<Map<String, String>>(emptyMap())
    // 历史消息列表（限长，每个主题保留最近 200 条）
    val historyByTopic = MutableStateFlow<Map<String, List<HistoryEntry>>>(emptyMap())

    fun start(config: Config) {
        scope.launch {
            var retry = 0
            while (isActive) {
                try {
                    connect()
                    subscribe(config.topics)
                    // 连接成功后逐个轻微延迟拉取历史，避免瞬时多指令被服务端丢弃
                    if (config.pullHistoryOnStart) {
                        for (topic in config.topics) {
                            requestHistoryOnce(topic)
                            delay(80)
                        }
                    }
                    // 主循环读取
                    readLoop()
                } catch (e: Exception) {
                    // 连接断开，指数退避重连
                } finally {
                    closeSilently()
                }
                retry = (retry + 1).coerceAtMost(6)
                delay(500L * (1 shl retry))
            }
        }

        // 心跳
        scope.launch {
            while (isActive) {
                delay(30_000)
                try {
                    sendRaw("ping\r\n")
                } catch (_: Exception) {
                }
            }
        }
    }

    fun stop() {
        closeSilently()
        scope.cancel()
    }

    fun subscribe(topics: List<String>) {
        // 在 IO 线程执行网络发送，避免触发主线程网络异常
        scope.launch {
            // 更稳妥：逐个主题发送订阅指令，避免服务器不支持逗号分隔导致部分订阅失效
            topics.filter { it.isNotBlank() }.forEach { t ->
                sendRawSafe("cmd=1&uid=${uid}&topic=${t}\r\n")
            }
        }
    }

    fun requestLastOnce(topic: String) { // 保留：获取一次已发消息
        scope.launch { sendRawSafe("cmd=9&uid=${uid}&topic=${topic}\r\n") }
    }

    fun requestHistoryOnce(topic: String) { // 订阅并获取一条历史
        scope.launch { sendRawSafe("cmd=3&uid=${uid}&topic=${topic}\r\n") }
    }

    fun publish(topic: String, msg: String) {
        // 在 IO 线程执行，防止 NetworkOnMainThreadException
        scope.launch { sendRawSafe("cmd=2&uid=${uid}&topic=${topic}&msg=${msg}\r\n") }
    }

    private fun connect() {
        Log.i(TAG, "Connecting to $host:$port ...")
        val s = Socket()
        s.tcpNoDelay = true
        s.connect(InetSocketAddress(host, port), 5000)
        socket = s
        reader = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8))
        writer = BufferedWriter(OutputStreamWriter(s.getOutputStream(), Charsets.UTF_8))
        Log.i(TAG, "Connected")
    }

    private suspend fun readLoop() {
        val r = reader ?: return
        while (scope.isActive) {
            val line = r.readLine() ?: break
            Log.d(TAG, "RECV: $line")
            handleLine(line)
        }
    }

    private fun handleLine(lineIn: String) {
        val line = lineIn.trim()
        // 只关心包含 topic 与 msg 的行
        if (!line.contains("topic=") || !line.contains("msg=")) return
        val map = parsePairs(line)
        val topic = map["topic"] ?: return
        val msg = map["msg"] ?: return
        latestValues.update { it + (topic to msg) }
        val now = System.currentTimeMillis()
        historyByTopic.update { old ->
            val prev = old[topic] ?: emptyList()
            val appended = (prev + HistoryEntry(topic, msg, now)).takeLast(200)
            old + (topic to appended)
        }
    }

    private fun parsePairs(s: String): Map<String, String> {
        // 形如 a=1&b=2，前缀可能有 cmd=2
        val parts = s.split('&')
        val res = mutableMapOf<String, String>()
        for (p in parts) {
            val idx = p.indexOf('=')
            if (idx > 0 && idx < p.length - 1) {
                val k = p.substring(0, idx)
                val v = p.substring(idx + 1)
                res[k] = v
            }
        }
        return res
    }

    private fun sendRawSafe(cmd: String) {
        try {
            sendRaw(cmd)
        } catch (e: Exception) {
            val type = e::class.java.simpleName
            Log.w(TAG, "SEND FAILED [${type}]: ${e.message}")
        }
    }

    @Synchronized
    private fun sendRaw(cmd: String) {
        val w = writer ?: throw IllegalStateException("Not connected")
        Log.d(TAG, "SEND: ${cmd.trim()}")
        w.write(cmd)
        w.flush()
    }

    private fun closeSilently() {
        try { reader?.close() } catch (_: Exception) {}
        try { writer?.close() } catch (_: Exception) {}
        try { socket?.close() } catch (_: Exception) {}
        reader = null; writer = null; socket = null
    }

    companion object {
        const val DEFAULT_HOST = "bemfa.com"
        const val DEFAULT_PORT = 8344
        private const val TAG = "Bafa"
    }
}

object BafaDefaults {
    // 你的 UID（私钥）
    const val UID: String = "e7a1c889becf42b7b25439a0e4618c6a"

    // —— 距离相关 ——
    const val TOPIC_DISTANCE = "distance"
    const val TOPIC_DISTANCE_DOWN = "distancedown"
    const val TOPIC_DISTANCE_UP = "distanceup"
}
