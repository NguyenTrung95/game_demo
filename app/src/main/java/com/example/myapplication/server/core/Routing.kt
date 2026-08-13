package com.example.myapplication.server.core

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.http.content.staticFiles
import io.ktor.server.routing.routing
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import java.io.File

/**
 * Port of backend/index.js's routing + WebSocket dispatch, generalized to N games via
 * [GameModule]. Exactly one module must have wsPathMarker == null (the default, mounted at "/");
 * every other module gets its own webSocket route at its wsPathMarker path. Unlike the Node
 * version — which resolves the module per-connection from a single wss by matching req.url —
 * Ktor already routes by path natively, so each module's path is wired to its own route block.
 *
 * Adding a new game = add its module to the list passed in from EmbeddedGameServer. No dispatch
 * code to edit here.
 */
fun Application.configureRouting(webRoot: File, modules: List<GameModule>) {
    install(WebSockets)

    val defaultModule = modules.singleOrNull { it.wsPathMarker == null }
        ?: error("configureRouting: exactly one module must have wsPathMarker == null")
    val taggedModules = modules.filter { it.wsPathMarker != null }

    routing {
        staticFiles("/shared", File(webRoot, "shared"))
        staticFiles("/host", File(webRoot, "host"))
        staticFiles("/timing-game/host", File(webRoot, "timing-game/host"))
        staticFiles("/timing-game", File(webRoot, "timing-game/player"))
        staticFiles("/", File(webRoot, "player"))

        webSocket("/") {
            runGameSocket(this, defaultModule)
        }
        for (module in taggedModules) {
            webSocket(module.wsPathMarker!!) {
                runGameSocket(this, module)
            }
        }
    }
}

private suspend fun runGameSocket(session: DefaultWebSocketServerSession, module: GameModule) {
    val connection = ClientConnection(session)
    try {
        for (frame in session.incoming) {
            if (frame !is Frame.Text) continue
            dispatchMessage(connection, frame.readText(), module)
        }
    } finally {
        module.handleDisconnect(connection)
    }
}

private fun dispatchMessage(connection: ClientConnection, raw: String, module: GameModule) {
    val envelope = runCatching { Json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return
    val type = (envelope["type"] as? JsonPrimitive)?.contentOrNull ?: return
    val payload = envelope["payload"] as? JsonObject ?: JsonObject(emptyMap())
    module.handleMessage(connection, type, payload)
}
