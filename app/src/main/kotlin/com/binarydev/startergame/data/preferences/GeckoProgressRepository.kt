package com.binarydev.startergame.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.binarydev.startergame.core.gecko.GeckoLevels
import com.binarydev.startergame.core.gecko.calculateStars
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class GeckoLevelRecord(
    val levelId: Int,
    val stars: Int = 0,
    val bestMoves: Int? = null
)

data class GeckoProgressSnapshot(
    val highestUnlocked: Int = 1,
    val records: Map<Int, GeckoLevelRecord> = emptyMap()
) {
    val totalStars: Int get() = records.values.sumOf { it.stars }
    val maxPossibleStars: Int get() = GeckoLevels.ALL.size * 3

    fun isUnlocked(levelId: Int): Boolean = levelId <= highestUnlocked
    fun starsFor(levelId: Int): Int = records[levelId]?.stars ?: 0
    fun bestMovesFor(levelId: Int): Int? = records[levelId]?.bestMoves
}

/**
 * Menyimpan progres khusus Gecko Out (highestUnlocked, bintang tertinggi 1–3, best moves) di DataStore.
 */
class GeckoProgressRepository(
    private val dataStore: DataStore<Preferences>,
    private val totalLevels: Int = GeckoLevels.ALL.size
) {
    private val highestUnlockedKey = intPreferencesKey("gecko_highest_unlocked")
    private fun starsKey(levelId: Int) = intPreferencesKey("gecko_stars_$levelId")
    private fun bestMovesKey(levelId: Int) = intPreferencesKey("gecko_best_moves_$levelId")

    val progressFlow: Flow<GeckoProgressSnapshot> = dataStore.data.map { prefs ->
        val highestUnlocked = (prefs[highestUnlockedKey] ?: 1).coerceIn(1, totalLevels)
        val records = buildMap {
            for (levelId in 1..totalLevels) {
                val stars = (prefs[starsKey(levelId)] ?: 0).coerceIn(0, 3)
                val bestMoves = prefs[bestMovesKey(levelId)]
                if (stars > 0 || bestMoves != null) {
                    put(levelId, GeckoLevelRecord(levelId = levelId, stars = stars, bestMoves = bestMoves))
                }
            }
        }
        GeckoProgressSnapshot(highestUnlocked = highestUnlocked, records = records)
    }

    suspend fun getSnapshot(): GeckoProgressSnapshot = progressFlow.first()

    /**
     * Mencatat penyelesaian Level N:
     * - Membuka Level N+1 (`highestUnlocked = max(prev, min(totalLevels, levelId + 1))`)
     * - Menyimpan bintang tertinggi (`max(prevStars, newStars)`)
     * - Menyimpan jumlah langkah terbaik (`min(prevBestMoves, moves)`)
     */
    suspend fun recordLevelCompleted(
        levelId: Int,
        moves: Int,
        par: Int
    ): GeckoProgressSnapshot {
        val earnedStars = calculateStars(moves.coerceAtLeast(1), par)
        dataStore.edit { prefs ->
            val currentUnlocked = prefs[highestUnlockedKey] ?: 1
            val nextUnlocked = (levelId + 1).coerceAtMost(totalLevels)
            prefs[highestUnlockedKey] = maxOf(currentUnlocked, nextUnlocked)

            val prevStars = prefs[starsKey(levelId)] ?: 0
            prefs[starsKey(levelId)] = maxOf(prevStars, earnedStars)

            val prevBest = prefs[bestMovesKey(levelId)]
            prefs[bestMovesKey(levelId)] = if (prevBest == null) moves else minOf(prevBest, moves)
        }
        return getSnapshot()
    }
}
