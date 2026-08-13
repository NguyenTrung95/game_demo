package com.example.myapplication.server.core

import kotlinx.serialization.json.JsonObject

/**
 * One entry per game, mirroring the Node backend's games/<name>/roomManager.js export shape
 * ({wsPathMarker, handlers, handleDisconnect}). GameRouter picks a module per WebSocket
 * connection by matching [wsPathMarker] against the route path; exactly one module in the
 * list passed to GameRouter must have wsPathMarker == null to serve as the default.
 */
interface GameModule {
    val wsPathMarker: String?

    fun handleMessage(connection: ClientConnection, type: String, payload: JsonObject)

    fun handleDisconnect(connection: ClientConnection)
}
