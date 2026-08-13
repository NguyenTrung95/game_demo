package com.example.myapplication.server.games.duckrace

import com.example.myapplication.server.core.ClientConnection
import com.example.myapplication.server.core.GameDispatcher
import com.example.myapplication.server.core.GameModule
import com.example.myapplication.server.core.QrCodeGenerator
import com.example.myapplication.server.core.ServerInfo
import com.example.myapplication.server.core.broadcastRaw
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlin.random.Random

/** Port of backend/games/duckRace/roomManager.js. */
object DuckRaceModule : GameModule {

    override val wsPathMarker: String? = null

    override fun handleMessage(connection: ClientConnection, type: String, payload: JsonObject) {
        when (type) {
            "create_room" -> createRoom(connection)
            "join_room" -> joinRoom(
                connection,
                pin = (payload["pin"] as? JsonPrimitive)?.contentOrNull,
                nicknameRaw = (payload["nickname"] as? JsonPrimitive)?.contentOrNull,
            )
            "report_taps" -> reportTapCount(
                connection,
                cumulativeTaps = (payload["cumulativeTaps"] as? JsonPrimitive)?.contentOrNull?.toLongOrNull(),
            )
            "force_start" -> forceStart(connection)
        }
    }

    override fun handleDisconnect(connection: ClientConnection) {
        scope.launch {
            val room = rooms[connection.roomPin] ?: return@launch

            if (connection.role == "host") {
                broadcastToPlayers(room, "room_closed", buildJsonObject {})
                destroyRoom(room.pin)
                return@launch
            }

            if (connection.role == "player") {
                val playerId = connection.playerId ?: return@launch
                room.players.remove(playerId)
                notifyHost(room, "player_left", buildJsonObject { put("playerId", playerId) })
                notifyRoomStats(room)

                if (room.players.isEmpty()) {
                    if (room.phase == "racing") {
                        concludeRace(room, "all_players_left")
                    } else if (room.phase != "lobby") {
                        destroyRoom(room.pin)
                    }
                }
            }
        }
    }

    private const val LOBBY_DURATION_MS = 30_000L
    private const val KIOSK_RESTART_DELAY_MS = 8_000L
    private const val MAX_PLAYERS_PER_ROOM = 100

    private const val RACE_TARGET_TAPS = 100
    private const val RACE_DURATION_MS = 35_000L
    private const val RACE_TICK_MS = 200L

    // Anti-cheat rate limiting — deliberately invisible to the player (see roomManager.js).
    private const val MAX_TAPS_PER_SECOND = 12
    private const val TAP_RATE_GRACE = 3

    private val PLAYER_COLORS = listOf(
        "#e21b3c", "#1368ce", "#d89e00", "#26890c",
        "#a349a4", "#ff8c00", "#00b3a6", "#ff5ea8",
    )

    private class PlayerState(
        val playerId: String,
        val ws: ClientConnection,
        val nickname: String,
        var taps: Int = 0,
        var position: Int = 0,
        val color: String,
    )

    private class RoomState(val pin: String, val hostWs: ClientConnection) {
        val players = LinkedHashMap<String, PlayerState>()
        var phase: String = "lobby"
        var lobbyJob: Job? = null
        var raceTimeoutJob: Job? = null
        var raceTickJob: Job? = null
        var raceStartTime: Long = 0
    }

    private val rooms = LinkedHashMap<String, RoomState>()
    private val scope = CoroutineScope(GameDispatcher.single)

    private fun generateUniquePin(): String {
        var pin: String
        do {
            pin = Random.nextInt(0, 10_000).toString().padStart(4, '0')
        } while (rooms.containsKey(pin))
        return pin
    }

    // Encodes the envelope once and reuses the same JSON string for every player, instead of
    // re-serializing the identical payload per recipient — matters once the server runs on the
    // same box as the UI it's rendering.
    private fun broadcastToPlayers(room: RoomState, type: String, payload: JsonObject) {
        broadcastRaw(room.players.values.map { it.ws }, type, payload)
    }

    private fun notifyHost(room: RoomState, type: String, payload: JsonObject) {
        room.hostWs.send(type, payload)
    }

    private fun notifyRoomStats(room: RoomState) {
        notifyHost(room, "room_stats", buildJsonObject {
            put("connectedCount", room.players.size)
            put("maxCapacity", MAX_PLAYERS_PER_ROOM)
            put("phase", room.phase)
        })
    }

    private fun raceSnapshot(room: RoomState): JsonArray = buildJsonArray {
        for (player in room.players.values) {
            add(buildJsonObject {
                put("playerId", player.playerId)
                put("nickname", player.nickname)
                put("taps", player.taps)
                put("position", player.position)
                put("color", player.color)
            })
        }
    }

    private fun clearLobbyTimer(room: RoomState) {
        room.lobbyJob?.cancel()
        room.lobbyJob = null
    }

    private fun clearRaceTimers(room: RoomState) {
        room.raceTimeoutJob?.cancel()
        room.raceTimeoutJob = null
        room.raceTickJob?.cancel()
        room.raceTickJob = null
    }

    private fun destroyRoom(pin: String) {
        val room = rooms[pin] ?: return
        clearLobbyTimer(room)
        clearRaceTimers(room)
        rooms.remove(pin)
    }

    private fun createRoom(hostWs: ClientConnection) {
        scope.launch { createRoomInternal(hostWs) }
    }

    private suspend fun createRoomInternal(hostWs: ClientConnection) {
        val pin = generateUniquePin()
        val room = RoomState(pin, hostWs)
        rooms[pin] = room
        hostWs.roomPin = pin
        hostWs.role = "host"

        val joinUrl = ServerInfo.buildJoinUrl(pin)
        val qrDataUrl = withContext(Dispatchers.Default) {
            QrCodeGenerator.generateQrDataUrl(joinUrl)
        }

        hostWs.send("room_created", buildJsonObject {
            put("pin", pin)
            put("joinUrl", joinUrl)
            put("qrDataUrl", qrDataUrl)
            put("maxCapacity", MAX_PLAYERS_PER_ROOM)
            put("lobbyDurationMs", LOBBY_DURATION_MS)
            put("targetTaps", RACE_TARGET_TAPS)
        })
        notifyRoomStats(room)

        room.lobbyJob = scope.launch {
            delay(LOBBY_DURATION_MS)
            tryAutoStart(room)
        }
    }

    /** Kiosk auto-starts when the lobby timer runs out; forceStart lets the host skip the wait. */
    private fun tryAutoStart(room: RoomState) {
        if (room.phase != "lobby") return
        if (room.players.isEmpty()) {
            room.lobbyJob = scope.launch {
                delay(LOBBY_DURATION_MS)
                tryAutoStart(room)
            }
            return
        }
        startRace(room)
    }

    private fun forceStart(ws: ClientConnection) {
        scope.launch {
            val room = rooms[ws.roomPin] ?: return@launch
            if (ws.role != "host" || room.phase != "lobby" || room.players.isEmpty()) return@launch
            startRace(room)
        }
    }

    private fun joinRoom(ws: ClientConnection, pin: String?, nicknameRaw: String?) {
        scope.launch {
            val room = rooms[pin]
            if (room == null || room.phase != "lobby") {
                ws.send("join_ack", buildJsonObject {
                    put("success", false)
                    put("error", "invalid_pin_or_started")
                })
                return@launch
            }
            if (room.players.size >= MAX_PLAYERS_PER_ROOM) {
                ws.send("join_ack", buildJsonObject {
                    put("success", false)
                    put("error", "room_full")
                })
                return@launch
            }
            val nickname = (nicknameRaw ?: "").trim().take(20)
            if (nickname.isEmpty()) {
                ws.send("join_ack", buildJsonObject {
                    put("success", false)
                    put("error", "nickname_required")
                })
                return@launch
            }

            val playerId = java.util.UUID.randomUUID().toString()
            val color = PLAYER_COLORS[room.players.size % PLAYER_COLORS.size]
            room.players[playerId] = PlayerState(playerId, ws, nickname, color = color)

            ws.roomPin = room.pin
            ws.role = "player"
            ws.playerId = playerId

            ws.send("join_ack", buildJsonObject {
                put("success", true)
                put("playerId", playerId)
                put("targetTaps", RACE_TARGET_TAPS)
                put("color", color)
            })
            notifyHost(room, "player_joined", buildJsonObject {
                put("playerId", playerId)
                put("nickname", nickname)
                put("color", color)
            })
            notifyRoomStats(room)
        }
    }

    private fun startRace(room: RoomState) {
        clearLobbyTimer(room)
        room.phase = "racing"
        room.raceStartTime = System.currentTimeMillis()

        for (player in room.players.values) {
            player.taps = 0
            player.position = 0
        }

        // Host needs the player list (name + color) to render the pre-race lineup; players only
        // ever see their own tap button, so they don't need it.
        notifyHost(room, "race_start", buildJsonObject {
            put("durationMs", RACE_DURATION_MS)
            put("targetTaps", RACE_TARGET_TAPS)
            put("players", raceSnapshot(room))
        })
        broadcastToPlayers(room, "race_start", buildJsonObject {
            put("durationMs", RACE_DURATION_MS)
            put("targetTaps", RACE_TARGET_TAPS)
        })
        notifyRoomStats(room)

        clearRaceTimers(room)
        room.raceTimeoutJob = scope.launch {
            delay(RACE_DURATION_MS)
            concludeRace(room, "timeout")
        }
        room.raceTickJob = scope.launch {
            while (isActive) {
                delay(RACE_TICK_MS)
                if (!isActive) break
                val remainingMs = (RACE_DURATION_MS - (System.currentTimeMillis() - room.raceStartTime)).coerceAtLeast(0)
                val progressPayload = buildJsonObject {
                    put("positions", raceSnapshot(room))
                    put("remainingMs", remainingMs)
                }
                notifyHost(room, "race_progress", progressPayload)
                broadcastToPlayers(room, "race_progress", progressPayload)
            }
        }
    }

    /**
     * Input arrives as a running cumulative total per fixed-interval packet, not one message per
     * tap — a dropped packet doesn't drop a tap, the next packet just catches it back up.
     *
     * Anti-cheat is bounded by TOTAL elapsed race time, not the gap between two packets — bounding
     * by inter-packet gap would let a delayed packet (slow network, or a client that went quiet for
     * a while) accidentally accumulate allowance and wave a burst through.
     */
    private fun reportTapCount(ws: ClientConnection, cumulativeTaps: Long?) {
        scope.launch {
            val room = rooms[ws.roomPin] ?: return@launch
            if (ws.role != "player" || room.phase != "racing") return@launch
            val player = room.players[ws.playerId] ?: return@launch

            val reportedTotal = cumulativeTaps ?: return@launch
            if (reportedTotal <= player.taps) return@launch

            val secondsSinceRaceStart = ((System.currentTimeMillis() - room.raceStartTime) / 1000.0).coerceAtLeast(0.0)
            val maxTapsAllowedByNow = kotlin.math.ceil(MAX_TAPS_PER_SECOND * secondsSinceRaceStart).toInt() + TAP_RATE_GRACE

            player.taps = minOf(reportedTotal, maxTapsAllowedByNow.toLong()).toInt()
            player.position = minOf(100, (player.taps.toFloat() / RACE_TARGET_TAPS * 100).toInt())

            if (player.taps >= RACE_TARGET_TAPS) {
                concludeRace(room, "finished")
            }
        }
    }

    private fun concludeRace(room: RoomState, reason: String) {
        if (room.phase != "racing") return
        clearRaceTimers(room)
        room.phase = "finished"

        val ranking = room.players.values.sortedByDescending { it.taps }

        notifyHost(room, "race_over", buildJsonObject {
            put("ranking", buildJsonArray {
                for (r in ranking) add(buildJsonObject {
                    put("playerId", r.playerId)
                    put("nickname", r.nickname)
                    put("taps", r.taps)
                    put("position", r.position)
                    put("color", r.color)
                })
            })
            put("reason", reason)
        })

        val top10 = ranking.take(10)
        val top10Json = buildJsonArray {
            top10.forEachIndexed { idx, r ->
                add(buildJsonObject {
                    put("rank", idx + 1)
                    put("nickname", r.nickname)
                    put("taps", r.taps)
                    put("color", r.color)
                })
            }
        }

        ranking.forEachIndexed { index, entry ->
            entry.ws.send("race_over", buildJsonObject {
                put("rank", index + 1)
                put("totalPlayers", ranking.size)
                put("taps", entry.taps)
                put("top10", top10Json)
                put("reason", reason)
            })
        }

        notifyRoomStats(room)
        scheduleKioskRestart(room)
    }

    private fun scheduleKioskRestart(room: RoomState) {
        val hostWs = room.hostWs
        scope.launch {
            delay(KIOSK_RESTART_DELAY_MS)
            destroyRoom(room.pin)
            if (hostWs.session.coroutineContext.isActive) {
                createRoomInternal(hostWs)
            }
        }
    }
}
