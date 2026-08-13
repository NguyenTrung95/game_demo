package com.example.myapplication.server.games.timing

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
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToLong
import kotlin.random.Random

/**
 * Port of backend/games/timingGame/roomManager.js. See that file's ASSUMPTION comments for the
 * open business-rule questions (blackout scoring, room-wide vs per-player speed band, explaining
 * timeout fallback) — this port makes the exact same calls, kept in sync by hand since Kotlin and
 * JS don't share a source of truth here.
 */
object TimingGameModule : GameModule {

    override val wsPathMarker: String = "/timing-game-ws"

    override fun handleMessage(connection: ClientConnection, type: String, payload: JsonObject) {
        when (type) {
            "create_room" -> createRoom(connection)
            "join_room" -> joinRoom(connection, payload)
            "submit_setup" -> submitSetup(connection, payload)
            "force_start" -> forceStart(connection)
            "tap" -> handleTap(connection, payload)
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
            }
        }
    }

    // Spec caps this room at 30 real phones ("最大30人" / "実スマートフォン30台") — unlike Duck
    // Race/Tug of War's 100, this is specific to the Timing Game customer's requirement.
    private const val MAX_PLAYERS_PER_ROOM = 30

    private const val EXPLAINING_TIMEOUT_MS = 5 * 60 * 1000L
    private const val COUNTDOWN_MS = 3_000L
    private const val APPROACH_MS = 5_000L
    private const val VISIBLE_PLAY_MS = 10_000L
    private const val BLACKOUT_MS = 20_000L
    private const val PLAY_TICK_MS = 200L
    private const val RESULT_PAGE_MS = 5_000L
    private const val RESULT_ROWS_PER_PAGE = 8
    private const val KIOSK_RESTART_MIN_MS = 8_000L

    private class HitPoint(val target: Long) {
        var hit: Boolean = false
        var error: Double? = null
    }

    private class PlayerState(val playerId: String, val ws: ClientConnection, val nickname: String) {
        var mode: String? = null
        var hitState: MutableMap<String, MutableList<HitPoint>>? = null
        var totalError: Long = 0
    }

    private class RoomState(val pin: String, val hostWs: ClientConnection) {
        val players = LinkedHashMap<String, PlayerState>()
        var phase: String = "lobby"
        var speedBand: String = HitPointGenerator.DEFAULT_SPEED_BAND
        var raceConfig: HitPointGenerator.RaceConfig? = null
        var playStartTime: Long = 0

        var explainingJob: Job? = null
        var countdownJob: Job? = null
        var approachJob: Job? = null
        var playTickJob: Job? = null
        var playEndJob: Job? = null
        var resultRestartJob: Job? = null
    }

    private class RankEntry(val playerId: String, val nickname: String, val totalErrorMs: Long, var rank: Int = 0)

    private val rooms = LinkedHashMap<String, RoomState>()
    private val scope = CoroutineScope(GameDispatcher.single)

    private fun generateUniquePin(): String {
        var pin: String
        do {
            pin = Random.nextInt(0, 10_000).toString().padStart(4, '0')
        } while (rooms.containsKey(pin))
        return pin
    }

    private fun notifyHost(room: RoomState, type: String, payload: JsonObject) {
        room.hostWs.send(type, payload)
    }

    private fun broadcastToPlayers(room: RoomState, type: String, payload: JsonObject) {
        broadcastRaw(room.players.values.map { it.ws }, type, payload)
    }

    private fun notifyRoomStats(room: RoomState) {
        notifyHost(room, "room_stats", buildJsonObject {
            put("connectedCount", room.players.size)
            put("maxCapacity", MAX_PLAYERS_PER_ROOM)
            put("phase", room.phase)
        })
    }

    private fun clearRoomTimers(room: RoomState) {
        room.explainingJob?.cancel(); room.explainingJob = null
        room.countdownJob?.cancel(); room.countdownJob = null
        room.approachJob?.cancel(); room.approachJob = null
        room.playTickJob?.cancel(); room.playTickJob = null
        room.playEndJob?.cancel(); room.playEndJob = null
        room.resultRestartJob?.cancel(); room.resultRestartJob = null
    }

    private fun destroyRoom(pin: String) {
        val room = rooms[pin] ?: return
        clearRoomTimers(room)
        rooms.remove(pin)
    }

    private fun createRoom(hostWs: ClientConnection) {
        scope.launch {
            val pin = generateUniquePin()
            val room = RoomState(pin, hostWs)
            rooms[pin] = room
            hostWs.roomPin = pin
            hostWs.role = "host"

            val joinUrl = ServerInfo.buildTimingGameJoinUrl(pin)
            val qrDataUrl = withContext(Dispatchers.Default) {
                QrCodeGenerator.generateQrDataUrl(joinUrl)
            }

            hostWs.send("room_created", buildJsonObject {
                put("pin", pin)
                put("joinUrl", joinUrl)
                put("qrDataUrl", qrDataUrl)
                put("maxCapacity", MAX_PLAYERS_PER_ROOM)
            })
            notifyRoomStats(room)
        }
    }

    private fun joinRoom(ws: ClientConnection, payload: JsonObject) {
        scope.launch {
            val pin = (payload["pin"] as? JsonPrimitive)?.contentOrNull
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
            val nickname = ((payload["nickname"] as? JsonPrimitive)?.contentOrNull ?: "").trim().take(20)
            if (nickname.isEmpty()) {
                ws.send("join_ack", buildJsonObject {
                    put("success", false)
                    put("error", "nickname_required")
                })
                return@launch
            }

            val playerId = java.util.UUID.randomUUID().toString()
            room.players[playerId] = PlayerState(playerId, ws, nickname)
            ws.roomPin = room.pin
            ws.role = "player"
            ws.playerId = playerId

            ws.send("join_ack", buildJsonObject {
                put("success", true)
                put("playerId", playerId)
                put("speedBand", room.speedBand)
            })
            notifyHost(room, "player_joined", buildJsonObject {
                put("playerId", playerId)
                put("nickname", nickname)
            })
            notifyRoomStats(room)
        }
    }

    private fun submitSetup(ws: ClientConnection, payload: JsonObject) {
        scope.launch {
            val room = rooms[ws.roomPin] ?: return@launch
            if (ws.role != "player") return@launch
            val player = room.players[ws.playerId] ?: return@launch

            val speedBand = (payload["speedBand"] as? JsonPrimitive)?.contentOrNull
            if (!speedBand.isNullOrEmpty()) {
                // ASSUMPTION (see roomManager.js §3.2): room-wide setting, last submission wins.
                room.speedBand = speedBand
            }
            val mode = (payload["mode"] as? JsonPrimitive)?.contentOrNull
            if (mode == "1" || mode == "2") {
                player.mode = mode
            }
            ws.send("setup_ack", buildJsonObject {
                put("speedBand", room.speedBand)
                put("mode", player.mode)
            })
        }
    }

    private fun forceStart(ws: ClientConnection) {
        scope.launch {
            val room = rooms[ws.roomPin] ?: return@launch
            if (ws.role != "host") return@launch

            when (room.phase) {
                "lobby" -> {
                    if (room.players.isEmpty()) return@launch
                    enterExplaining(room)
                }
                "explaining" -> startCountdown(room)
            }
        }
    }

    private fun enterExplaining(room: RoomState) {
        room.phase = "explaining"
        notifyHost(room, "phase_changed", buildJsonObject { put("phase", "explaining") })
        broadcastToPlayers(room, "phase_changed", buildJsonObject {
            put("phase", "explaining")
            put("speedBand", room.speedBand)
        })
        notifyRoomStats(room)

        clearRoomTimers(room)
        room.explainingJob = scope.launch {
            delay(EXPLAINING_TIMEOUT_MS)
            if (room.phase == "explaining") returnToLobby(room)
        }
    }

    private fun returnToLobby(room: RoomState) {
        clearRoomTimers(room)
        room.phase = "lobby"
        val payload = buildJsonObject { put("phase", "lobby") }
        notifyHost(room, "phase_changed", payload)
        broadcastToPlayers(room, "phase_changed", payload)
        notifyRoomStats(room)
    }

    private fun startCountdown(room: RoomState) {
        clearRoomTimers(room)
        room.phase = "countdown"
        val payload = buildJsonObject { put("phase", "countdown"); put("durationMs", COUNTDOWN_MS) }
        notifyHost(room, "phase_changed", payload)
        broadcastToPlayers(room, "phase_changed", payload)
        notifyRoomStats(room)

        room.countdownJob = scope.launch {
            delay(COUNTDOWN_MS)
            startApproach(room)
        }
    }

    private fun startApproach(room: RoomState) {
        room.phase = "approach"
        val raceConfig = HitPointGenerator.generateHitPoints(room.speedBand, VISIBLE_PLAY_MS, BLACKOUT_MS)
        room.raceConfig = raceConfig

        for (player in room.players.values) {
            player.hitState = buildPlayerHitState(raceConfig)
            player.totalError = 0
        }

        val payload = buildJsonObject {
            put("phase", "approach")
            put("durationMs", APPROACH_MS)
            put("fastColor", raceConfig.fastColor)
            put("slowColor", raceConfig.slowColor)
            put("beatIntervalMsByColor", buildJsonObject {
                for ((color, ms) in raceConfig.beatIntervalMsByColor) put(color, ms)
            })
        }
        notifyHost(room, "phase_changed", payload)
        broadcastToPlayers(room, "phase_changed", payload)
        notifyRoomStats(room)

        room.approachJob = scope.launch {
            delay(APPROACH_MS)
            startVisiblePlay(room)
        }
    }

    private fun startVisiblePlay(room: RoomState) {
        room.phase = "visiblePlay"
        room.playStartTime = System.currentTimeMillis()
        val raceConfig = room.raceConfig ?: return

        val hostPayload = buildJsonObject {
            put("phase", "visiblePlay")
            put("durationMs", VISIBLE_PLAY_MS)
            put("hitPointsByColor", buildJsonObject {
                for ((color, points) in raceConfig.hitPointsByColor) {
                    put(color, buildJsonArray { for (p in points) add(JsonPrimitive(p)) })
                }
            })
        }
        // Host needs the hit-point timelines to draw the shared lane grid; players only need
        // their own two buttons and don't render a lane, so they just get the phase/timer.
        notifyHost(room, "phase_changed", hostPayload)
        broadcastToPlayers(room, "phase_changed", buildJsonObject {
            put("phase", "visiblePlay")
            put("durationMs", VISIBLE_PLAY_MS)
        })
        notifyRoomStats(room)

        scheduleBlackout(room)
        startPlayTicker(room)
    }

    private fun scheduleBlackout(room: RoomState) {
        room.playEndJob = scope.launch {
            delay(VISIBLE_PLAY_MS)
            startBlackout(room)
        }
    }

    private fun startBlackout(room: RoomState) {
        room.phase = "blackout"
        val payload = buildJsonObject { put("phase", "blackout"); put("durationMs", BLACKOUT_MS) }
        notifyHost(room, "phase_changed", payload)
        broadcastToPlayers(room, "phase_changed", payload)
        notifyRoomStats(room)

        room.playEndJob = scope.launch {
            delay(BLACKOUT_MS)
            concludeRace(room)
        }
    }

    private fun startPlayTicker(room: RoomState) {
        room.playTickJob = scope.launch {
            while (isActive) {
                delay(PLAY_TICK_MS)
                if (!isActive) break
                val elapsedMs = System.currentTimeMillis() - room.playStartTime
                val remainingMs = (VISIBLE_PLAY_MS + BLACKOUT_MS - elapsedMs).coerceAtLeast(0)
                val top3 = liveTop3(room)
                notifyHost(room, "race_progress", buildJsonObject {
                    put("elapsedMs", elapsedMs)
                    put("remainingMs", remainingMs)
                    put("top3", top3)
                })
                for (player in room.players.values) {
                    player.ws.send("race_progress", buildJsonObject {
                        put("elapsedMs", elapsedMs)
                        put("remainingMs", remainingMs)
                        put("myTotalError", runningError(player).roundToLong())
                    })
                }
            }
        }
    }

    private fun buildPlayerHitState(raceConfig: HitPointGenerator.RaceConfig): MutableMap<String, MutableList<HitPoint>> {
        val state = mutableMapOf<String, MutableList<HitPoint>>()
        for (color in listOf("red", "blue")) {
            state[color] = (raceConfig.hitPointsByColor[color] ?: emptyList())
                .map { HitPoint(it) }
                .toMutableList()
        }
        return state
    }

    /**
     * Sai số tạm tính khi đang chơi: điểm đã bấm dùng sai số thật, điểm chưa tới hạn coi như 0
     * (chưa thể biết trước sẽ trượt) — chỉ dùng để hiện tạm trong lúc chơi, không phải điểm cuối.
     */
    private fun runningError(player: PlayerState): Double {
        val hitState = player.hitState ?: return 0.0
        var total = 0.0
        for (color in listOf("red", "blue")) {
            for (point in hitState[color] ?: emptyList()) {
                if (point.hit) total += point.error ?: 0.0
            }
        }
        return total
    }

    private fun liveTop3(room: RoomState): JsonArray = buildJsonArray {
        room.players.values
            .map { Triple(it.playerId, it.nickname, runningError(it).roundToLong()) }
            .sortedBy { it.third }
            .take(3)
            .forEach { (playerId, nickname, totalError) ->
                add(buildJsonObject {
                    put("playerId", playerId)
                    put("nickname", nickname)
                    put("totalError", totalError)
                })
            }
    }

    private fun handleTap(ws: ClientConnection, payload: JsonObject) {
        scope.launch {
            val room = rooms[ws.roomPin] ?: return@launch
            if (ws.role != "player") return@launch
            if (room.phase != "visiblePlay" && room.phase != "blackout") return@launch

            val player = room.players[ws.playerId] ?: return@launch
            val hitState = player.hitState ?: return@launch
            val raceConfig = room.raceConfig ?: return@launch

            val color = (payload["color"] as? JsonPrimitive)?.contentOrNull
            if (color != "red" && color != "blue") return@launch

            val elapsedMs = (payload["elapsedMs"] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull() ?: return@launch
            val beatIntervalMs = raceConfig.beatIntervalMsByColor[color] ?: return@launch
            val lane = hitState[color] ?: return@launch

            var best: HitPoint? = null
            var bestDist = Double.POSITIVE_INFINITY
            for (point in lane) {
                val dist = abs(elapsedMs - point.target)
                if (dist < bestDist) {
                    bestDist = dist
                    best = point
                }
            }
            // Ngoài phạm vi nửa nhịp so với điểm đích gần nhất -> không tính (NFR §7.4 "không có
            // âm báo lỗi").
            if (best == null || bestDist > beatIntervalMs / 2.0) return@launch

            // Cùng điểm đích, nhiều lần bấm -> giữ sai số nhỏ nhất (Business Rules §7.1).
            val currentError = best.error
            if (currentError == null || bestDist < currentError) {
                best.error = bestDist
                best.hit = true
            }

            ws.send("tap_result", buildJsonObject {
                put("color", color)
                put("errorMs", best.error!!.roundToLong())
                put("targetMs", best.target)
            })
        }
    }

    private fun finalizeScoring(room: RoomState) {
        val raceConfig = room.raceConfig ?: return
        for (player in room.players.values) {
            val hitState = player.hitState ?: continue
            var total = 0.0
            for (color in listOf("red", "blue")) {
                val beatIntervalMs = raceConfig.beatIntervalMsByColor[color] ?: 0L
                for (point in hitState[color] ?: emptyList()) {
                    // Không bấm hoặc bấm quá muộn -> phạt cố định 1 nhịp (Business Rules §7.1).
                    total += point.error?.takeIf { point.hit } ?: beatIntervalMs.toDouble()
                }
            }
            player.totalError = total.roundToLong()
        }
    }

    private fun rankingSnapshot(room: RoomState): List<RankEntry> {
        return room.players.values
            .map { RankEntry(it.playerId, it.nickname, it.totalError) }
            .sortedBy { it.totalErrorMs }
    }

    private fun concludeRace(room: RoomState) {
        clearRoomTimers(room)
        finalizeScoring(room)
        room.phase = "result"

        val ranking = rankingSnapshot(room)
        // Đồng hạng khi bằng sai số (Business Rules §7.2).
        var lastError: Long? = null
        var lastRank = 0
        ranking.forEachIndexed { index, entry ->
            if (entry.totalErrorMs != lastError) {
                lastRank = index + 1
                lastError = entry.totalErrorMs
            }
            entry.rank = lastRank
        }

        notifyHost(room, "race_over", buildJsonObject {
            put("ranking", buildJsonArray {
                for (entry in ranking) add(buildJsonObject {
                    put("playerId", entry.playerId)
                    put("nickname", entry.nickname)
                    put("totalErrorMs", entry.totalErrorMs)
                    put("rank", entry.rank)
                })
            })
            put("rowsPerPage", RESULT_ROWS_PER_PAGE)
            put("pageDurationMs", RESULT_PAGE_MS)
        })
        for (player in room.players.values) {
            val mine = ranking.find { it.playerId == player.playerId }
            player.ws.send("race_over", buildJsonObject {
                put("rank", mine?.rank)
                put("totalErrorMs", player.totalError)
                put("totalPlayers", ranking.size)
            })
        }
        notifyRoomStats(room)

        val pageCount = maxOf(1, ceil(ranking.size / RESULT_ROWS_PER_PAGE.toDouble()).toInt())
        val displayMs = maxOf(KIOSK_RESTART_MIN_MS, pageCount * RESULT_PAGE_MS + 2_000L)
        room.resultRestartJob = scope.launch {
            delay(displayMs)
            returnToLobby(room)
        }
    }
}
