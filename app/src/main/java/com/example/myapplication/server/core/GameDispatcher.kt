package com.example.myapplication.server.core

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors

/**
 * The Node backend this app replaces runs on a single-threaded event loop, so every room
 * mutation (join, tap report, race tick...) is implicitly serialized. Ktor/CIO can service
 * WebSocket frames from multiple coroutines at once, so every room manager funnels its mutating
 * calls through this single-threaded dispatcher to keep the exact same "one thing happens at a
 * time per room" semantics the original room-state logic was written against.
 */
object GameDispatcher {
    val single: CoroutineDispatcher = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "game-room-thread")
    }.asCoroutineDispatcher()
}
