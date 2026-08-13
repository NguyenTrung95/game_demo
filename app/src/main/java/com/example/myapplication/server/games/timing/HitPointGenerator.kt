package com.example.myapplication.server.games.timing

import kotlin.math.roundToLong
import kotlin.random.Random

/** Port of backend/games/timingGame/hitPointGenerator.js. */
object HitPointGenerator {

    val SPEED_BANDS: Map<String, Int> = mapOf("slow" to 70, "medium" to 100, "fast" to 130)
    const val DEFAULT_SPEED_BAND = "medium"

    class RaceConfig(
        val fastColor: String,
        val slowColor: String,
        val beatIntervalMsByColor: Map<String, Long>,
        val hitPointsByColor: Map<String, List<Long>>,
    )

    fun generateHitPoints(speedBand: String, visiblePlayMs: Long, blackoutMs: Long): RaceConfig {
        val baseBpm = SPEED_BANDS[speedBand] ?: SPEED_BANDS[DEFAULT_SPEED_BAND]!!
        val slowIntervalMs = 60_000.0 / baseBpm
        val fastIntervalMs = slowIntervalMs / 2.0
        val fastColor = if (Random.nextBoolean()) "red" else "blue"
        val slowColor = if (fastColor == "red") "blue" else "red"
        val totalMs = (visiblePlayMs + blackoutMs).toDouble()

        val beatIntervalMsByColor = mapOf(
            fastColor to fastIntervalMs.roundToLong(),
            slowColor to slowIntervalMs.roundToLong(),
        )
        val hitPointsByColor = mapOf(
            fastColor to buildTimeline(fastIntervalMs, totalMs),
            slowColor to buildTimeline(slowIntervalMs, totalMs),
        )
        return RaceConfig(fastColor, slowColor, beatIntervalMsByColor, hitPointsByColor)
    }

    private fun buildTimeline(intervalMs: Double, totalMs: Double): List<Long> {
        val points = mutableListOf<Long>()
        var t = intervalMs
        while (t < totalMs) {
            points.add(t.roundToLong())
            t += intervalMs
        }
        return points
    }
}
