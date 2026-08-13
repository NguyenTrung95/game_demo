package com.example.myapplication.server.core

import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.websocket.Frame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private val json = Json { encodeDefaults = true }

/** Fire-and-forget send scope, mirroring how JS's ws.send() never blocks the caller. */
private val sendScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

/**
 * Node attaches roomPin/role/playerId directly onto the `ws` object; Kotlin can't do that to a
 * DefaultWebSocketServerSession, so this wrapper carries the same three fields alongside it.
 */
class ClientConnection(val session: DefaultWebSocketServerSession) {
    @Volatile var roomPin: String? = null
    @Volatile var role: String? = null // "host" | "player"
    @Volatile var playerId: String? = null

    /** Equivalent of sendJson(ws, type, payload) in the JS room managers. */
    fun send(type: String, payload: JsonObject) {
        sendRaw(encodeEnvelope(type, payload))
    }

    /** Sends an already-encoded envelope — see [encodeEnvelope] / [broadcastRaw]. */
    fun sendRaw(text: String) {
        sendScope.launch {
            runCatching { session.send(Frame.Text(text)) }
        }
    }
}

fun jsonObjectOf(vararg pairs: Pair<String, JsonElement>): JsonObject = buildJsonObject {
    for ((key, value) in pairs) put(key, value)
}

/** Encodes {type, payload} once so a broadcast to many connections doesn't re-serialize per recipient. */
fun encodeEnvelope(type: String, payload: JsonObject): String {
    val envelope = buildJsonObject {
        put("type", type)
        put("payload", payload)
    }
    return json.encodeToString(JsonObject.serializer(), envelope)
}

fun broadcastRaw(connections: Collection<ClientConnection>, type: String, payload: JsonObject) {
    val raw = encodeEnvelope(type, payload)
    for (connection in connections) connection.sendRaw(raw)
}
