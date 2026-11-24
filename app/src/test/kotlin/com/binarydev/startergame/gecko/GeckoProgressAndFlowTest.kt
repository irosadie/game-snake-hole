package com.binarydev.startergame.gecko

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.binarydev.startergame.core.gecko.GeckoGameRules
import com.binarydev.startergame.core.gecko.GeckoLevels
import com.binarydev.startergame.core.gecko.GeckoMove
import com.binarydev.startergame.core.gecko.GeckoOutRuleEngine
import com.binarydev.startergame.core.gecko.GeckoPuzzleSolver
import com.binarydev.startergame.data.local.StarterGameDatabase
import com.binarydev.startergame.data.preferences.GeckoProgressRepository
import com.binarydev.startergame.di.StarterAppContainer
import com.binarydev.startergame.ui.viewmodel.GameplayViewModel
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class GeckoProgressAndFlowTest {

    private lateinit var context: Context
    private lateinit var dbFile: File
    private lateinit var prefsFile: File
    private lateinit var dataStoreScope: CoroutineScope
    private lateinit var database: StarterGameDatabase
    private lateinit var dataStore: DataStore<Preferences>

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        context = ApplicationProvider.getApplicationContext()
        val suffix = UUID.randomUUID().toString()
        dbFile = File(context.cacheDir, "gecko_test_$suffix.db")
        prefsFile = File(context.cacheDir, "gecko_prefs_$suffix.preferences_pb")
        database = Room.databaseBuilder(context, StarterGameDatabase::class.java, dbFile.absolutePath)
            .allowMainThreadQueries()
            .build()
        dataStoreScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        dataStore = PreferenceDataStoreFactory.create(
            scope = dataStoreScope,
            produceFile = { prefsFile }
        )
    }

    @After
    fun tearDown() {
        database.close()
        dataStoreScope.cancel()
        Dispatchers.resetMain()
        dbFile.delete()
        prefsFile.delete()
    }

    @Test
    fun test5_completingLevelUnlocksNextLevelAndPreservesBestStarsAndBestMoves() = runTest {
        val repo = GeckoProgressRepository(dataStore)

        // Awal: hanya Level 1 terbuka, 0 bintang
        val initial = repo.getSnapshot()
        assertEquals(1, initial.highestUnlocked)
        assertTrue(initial.isUnlocked(1))
        assertFalse(initial.isUnlocked(2))
        assertEquals(0, initial.starsFor(1))

        // Selesaikan Level 1 (par=4) dengan 6 langkah -> 2 bintang, Level 2 terbuka
        val afterFirstClear = repo.recordLevelCompleted(levelId = 1, moves = 6, par = 4)
        assertEquals(2, afterFirstClear.highestUnlocked)
        assertTrue(afterFirstClear.isUnlocked(2))
        assertEquals(2, afterFirstClear.starsFor(1))
        assertEquals(6, afterFirstClear.bestMovesFor(1))

        // Ulangi Level 1 dengan 4 langkah (Par!) -> naik ke 3 bintang & bestMoves 4
        val afterPerfectClear = repo.recordLevelCompleted(levelId = 1, moves = 4, par = 4)
        assertEquals(3, afterPerfectClear.starsFor(1))
        assertEquals(4, afterPerfectClear.bestMovesFor(1))

        // Ulangi Level 1 dengan 9 langkah (1 bintang) -> bintang tertinggi & langkah terbaik TIDAK turun!
        val afterWorseReplay = repo.recordLevelCompleted(levelId = 1, moves = 9, par = 4)
        assertEquals(3, afterWorseReplay.starsFor(1))
        assertEquals(4, afterWorseReplay.bestMovesFor(1))
        assertEquals(2, afterWorseReplay.highestUnlocked)
    }

    @Test
    fun endToEndLevel1SolveThroughGameplayViewModelUnlocksLevel2() = runTest {
        val container = StarterAppContainer(
            context = context,
            ruleEngine = GeckoOutRuleEngine(),
            isDemoMode = true,
            databaseOverride = database,
            dataStoreOverride = dataStore
        )
        val vm = GameplayViewModel(container)
        vm.startLevelSuspend("level-1", "gecko-e2e-1")

        val level1 = GeckoLevels.byId(1)
        val solution = GeckoPuzzleSolver.solve(GeckoGameRules.createBoardState(level1))
        assertTrue(solution.solvable)

        for (step in solution.steps) {
            vm.onMoveSuspend(
                GeckoMove.DragStep(
                    geckoId = step.geckoId,
                    end = step.end,
                    target = step.target,
                    pushUndoSnapshot = false
                )
            )
        }

        val summary = vm.uiState.value.latestResult
        assertNotNull(summary)
        assertTrue(summary!!.won)
        assertEquals("level-2", summary.nextLevelUnlockedId)
        assertTrue(container.progressRepository.getLevels()["level-2"]?.unlocked == true)
    }
}
