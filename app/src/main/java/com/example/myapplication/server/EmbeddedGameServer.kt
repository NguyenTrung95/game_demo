package com.example.myapplication.server

import android.content.Context
import com.example.myapplication.server.core.AssetSync
import com.example.myapplication.server.core.ServerInfo
import com.example.myapplication.server.core.configureRouting
import com.example.myapplication.server.games.duckrace.DuckRaceModule
import com.example.myapplication.server.games.timing.TimingGameModule
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import kotlinx.coroutines.CompletableDeferred

/**
 * Owns the lifecycle of the in-app game server. Started once from the Application subclass so it
 * survives Activity recreation; GameWebViewScreen awaits [awaitReady] before loading the WebView,
 * since embeddedServer(...).start(wait = false) returns before the port is actually bound.
 *
 * Adding a new game = add its module here. No dispatch code to edit elsewhere.
 */
object EmbeddedGameServer {

    private val readyDeferred = CompletableDeferred<Unit>()
    private var server: EmbeddedServer<*, *>? = null

    @Synchronized
    fun start(context: Context) {
        if (server != null) return

        ServerInfo.init(context)
        val webRoot = AssetSync.ensureWebRootCopied(context)
        val modules = listOf(DuckRaceModule, TimingGameModule)

        server = embeddedServer(CIO, port = ServerInfo.PORT, host = "0.0.0.0") {
            configureRouting(webRoot, modules)
            monitor.subscribe(ApplicationStarted) {
                readyDeferred.complete(Unit)
            }
        }.start(wait = false)
    }

    suspend fun awaitReady() {
        readyDeferred.await()
    }

    fun localHostUrl(path: String): String = "http://127.0.0.1:${ServerInfo.PORT}$path"
}
