package com.example.myapplication.server.core

import android.content.Context

/** Port of backend/core/serverInfo.js. Initialized once by EmbeddedGameServer at startup. */
object ServerInfo {
    // Fixed at 3000 per the Timing Game spec's store-LAN requirement ("店舗LAN ... TCP 3000") —
    // shared by every game in this app, not just Timing Game, since they all run on one embedded
    // server on the same device.
    const val PORT: Int = 3000

    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private fun getBaseUrl(): String = "http://${NetworkUtils.getLanIpAddress(appContext)}:$PORT"

    fun buildJoinUrl(pin: String): String = "${getBaseUrl()}/?pin=$pin"

    fun buildTugOfWarJoinUrl(pin: String): String = "${getBaseUrl()}/tug-of-war/?pin=$pin"

    fun buildTimingGameJoinUrl(pin: String): String = "${getBaseUrl()}/timing-game/?pin=$pin"
}
