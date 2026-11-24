package com.binarydev.startergame.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.highScoreDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "snake_high_score_prefs"
)

/**
 * Contract for local High Score persistence so ViewModels can save and observe
 * the player's best score cleanly while remaining unit-testable.
 */
interface HighScoreRepository {
    val highScoreFlow: Flow<Int>

    suspend fun getHighScore(): Int

    /**
     * Persists [score] if it exceeds the currently saved best score and returns
     * the resulting highest score.
     */
    suspend fun saveHighScoreIfHigher(score: Int): Int

    suspend fun resetHighScore()
}

/**
 * Lightweight in-memory [HighScoreRepository] used as default in JVM unit tests and Compose previews.
 */
class InMemoryHighScoreRepository(initialHighScore: Int = 0) : HighScoreRepository {
    private val _highScore = MutableStateFlow(initialHighScore.coerceAtLeast(0))
    override val highScoreFlow: Flow<Int> = _highScore.asStateFlow()

    override suspend fun getHighScore(): Int = _highScore.value

    override suspend fun saveHighScoreIfHigher(score: Int): Int {
        val candidate = score.coerceAtLeast(0)
        val updated = maxOf(_highScore.value, candidate)
        _highScore.value = updated
        return updated
    }

    override suspend fun resetHighScore() {
        _highScore.value = 0
    }
}

/**
 * Jetpack Preferences DataStore implementation that persists the player's local best score
 * across app restarts.
 */
class DataStoreHighScoreRepository(
    private val dataStore: DataStore<Preferences>
) : HighScoreRepository {

    override val highScoreFlow: Flow<Int> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs ->
            (prefs[KEY_HIGH_SCORE] ?: 0).coerceAtLeast(0)
        }

    override suspend fun getHighScore(): Int = highScoreFlow.first()

    override suspend fun saveHighScoreIfHigher(score: Int): Int {
        val candidate = score.coerceAtLeast(0)
        var bestScore = candidate
        dataStore.edit { prefs ->
            val current = (prefs[KEY_HIGH_SCORE] ?: 0).coerceAtLeast(0)
            bestScore = maxOf(current, candidate)
            if (bestScore > current) {
                prefs[KEY_HIGH_SCORE] = bestScore
            }
        }
        return bestScore
    }

    override suspend fun resetHighScore() {
        dataStore.edit { prefs ->
            prefs[KEY_HIGH_SCORE] = 0
        }
    }

    companion object {
        private val KEY_HIGH_SCORE = intPreferencesKey("snake_best_high_score")

        @Volatile
        private var sharedInstance: DataStoreHighScoreRepository? = null

        /**
         * Returns a process-wide [DataStoreHighScoreRepository] backed by a single DataStore
         * instance (`snake_high_score_prefs`) to prevent duplicate DataStore file conflicts.
         */
        fun fromContext(context: Context): DataStoreHighScoreRepository {
            return sharedInstance ?: synchronized(this) {
                sharedInstance ?: DataStoreHighScoreRepository(
                    context.applicationContext.highScoreDataStore
                ).also { sharedInstance = it }
            }
        }
    }
}
