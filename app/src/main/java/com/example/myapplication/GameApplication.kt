package com.example.myapplication

import android.app.Application
import com.example.myapplication.server.EmbeddedGameServer

/** Starts the embedded game server once, tied to the app's own lifecycle rather than any Activity. */
class GameApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        EmbeddedGameServer.start(this)
    }
}
