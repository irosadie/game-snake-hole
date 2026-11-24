package com.binarydev.startergame.data.preferences

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class HighScoreRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun dataStoreHighScoreRepositoryPersistsOnlyHigherScoresAndResetsCleanly() = runTest {
        val dataStoreFile = File(tempFolder.newFolder(), "test_high_score.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { dataStoreFile }
        )
        val repository = DataStoreHighScoreRepository(dataStore)

        // Default initial value is 0
        assertEquals(0, repository.highScoreFlow.first())
        assertEquals(0, repository.getHighScore())

        // Saving a higher score updates DataStore
        assertEquals(12, repository.saveHighScoreIfHigher(12))
        assertEquals(12, repository.getHighScore())
        assertEquals(12, repository.highScoreFlow.first())

        // Lower or equal score does not overwrite existing best score
        assertEquals(12, repository.saveHighScoreIfHigher(7))
        assertEquals(12, repository.saveHighScoreIfHigher(12))
        assertEquals(12, repository.getHighScore())

        // Higher score updates persisted value again
        assertEquals(25, repository.saveHighScoreIfHigher(25))
        assertEquals(25, repository.highScoreFlow.first())

        // Reset restores 0
        repository.resetHighScore()
        assertEquals(0, repository.getHighScore())
    }
}
