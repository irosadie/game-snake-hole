package com.binarydev.startergame.ui.viewmodel

import com.binarydev.startergame.data.preferences.InMemoryHighScoreRepository
import com.binarydev.startergame.ui.audio.GameSoundController
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SnakeGameViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialStatePlacesSnakeInsideBoundsAndFoodOnUnoccupiedCell() {
        val vm = SnakeGameViewModel(gridWidth = 10, gridHeight = 10, random = Random(42))
        val state = vm.uiState.value

        assertEquals(SnakeGameStatus.READY, state.status)
        assertEquals(3, state.snake.size)
        assertEquals(SnakeDirection.RIGHT, state.direction)
        assertEquals(0, state.score)
        assertEquals(CollisionType.NONE, state.collisionType)

        state.snake.forEach { segment ->
            assertTrue(segment.x in 0 until 10)
            assertTrue(segment.y in 0 until 10)
        }
        assertNotNull(state.food)
        assertFalse(state.food in state.snake)
    }

    @Test
    fun directionChangeMovesSnakeAndRejectsOppositeReversal() {
        val vm = SnakeGameViewModel(gridWidth = 10, gridHeight = 10, random = Random(7))
        vm.setStateForTesting(
            SnakeGameUiState(
                gridWidth = 10,
                gridHeight = 10,
                snake = listOf(GridPoint(5, 5), GridPoint(4, 5), GridPoint(3, 5)),
                direction = SnakeDirection.RIGHT,
                pendingDirection = SnakeDirection.RIGHT,
                food = GridPoint(0, 0),
                status = SnakeGameStatus.RUNNING
            )
        )

        // Direct 180-degree reversal is ignored
        vm.changeDirection(SnakeDirection.LEFT)
        assertEquals(SnakeDirection.RIGHT, vm.uiState.value.pendingDirection)

        // Valid 90-degree turn is queued
        vm.changeDirection(SnakeDirection.UP)
        assertEquals(SnakeDirection.UP, vm.uiState.value.pendingDirection)

        // Rapid second input within the same tick cannot reverse into the committed RIGHT direction
        vm.changeDirection(SnakeDirection.LEFT)
        assertEquals(SnakeDirection.UP, vm.uiState.value.pendingDirection)

        vm.stepTick()
        val afterTurn = vm.uiState.value
        assertEquals(SnakeDirection.UP, afterTurn.direction)
        assertEquals(listOf(GridPoint(5, 4), GridPoint(5, 5), GridPoint(4, 5)), afterTurn.snake)
    }

    @Test
    fun eatingFoodGrowsSnakeIncrementsScoreAndSpawnsUnoccupiedFood() {
        val vm = SnakeGameViewModel(gridWidth = 8, gridHeight = 8, random = Random(123))
        vm.setStateForTesting(
            SnakeGameUiState(
                gridWidth = 8,
                gridHeight = 8,
                snake = listOf(GridPoint(3, 3), GridPoint(2, 3), GridPoint(1, 3)),
                direction = SnakeDirection.RIGHT,
                pendingDirection = SnakeDirection.RIGHT,
                food = GridPoint(4, 3),
                score = 0,
                highScore = 0,
                status = SnakeGameStatus.RUNNING
            )
        )

        vm.stepTick()
        val state = vm.uiState.value

        assertEquals(4, state.snake.size)
        assertEquals(GridPoint(4, 3), state.head)
        assertEquals(1, state.score)
        assertEquals(1, state.highScore)
        assertEquals(SnakeGameStatus.RUNNING, state.status)
        assertNotNull(state.food)
        assertFalse("New food must not spawn on the snake body", state.food in state.snake)
    }

    @Test
    fun hittingWallTriggersGameOverWithWallCollision() {
        val vm = SnakeGameViewModel(gridWidth = 6, gridHeight = 6, random = Random(1))
        vm.setStateForTesting(
            SnakeGameUiState(
                gridWidth = 6,
                gridHeight = 6,
                snake = listOf(GridPoint(5, 2), GridPoint(4, 2), GridPoint(3, 2)),
                direction = SnakeDirection.RIGHT,
                pendingDirection = SnakeDirection.RIGHT,
                food = GridPoint(0, 0),
                status = SnakeGameStatus.RUNNING
            )
        )

        vm.stepTick()
        val state = vm.uiState.value

        assertEquals(SnakeGameStatus.GAME_OVER, state.status)
        assertEquals(CollisionType.WALL, state.collisionType)
    }

    @Test
    fun hittingOwnBodyTriggersSelfCollisionWhileChasingVacatingTailIsAllowed() {
        val vm = SnakeGameViewModel(gridWidth = 6, gridHeight = 6, random = Random(1))

        // 1. Moving into own non-tail body triggers SELF collision
        vm.setStateForTesting(
            SnakeGameUiState(
                gridWidth = 6,
                gridHeight = 6,
                snake = listOf(
                    GridPoint(2, 2),
                    GridPoint(3, 2),
                    GridPoint(3, 3),
                    GridPoint(2, 3),
                    GridPoint(1, 3),
                    GridPoint(1, 2)
                ),
                direction = SnakeDirection.LEFT,
                pendingDirection = SnakeDirection.DOWN,
                food = GridPoint(5, 5),
                status = SnakeGameStatus.RUNNING
            )
        )
        vm.stepTick()
        assertEquals(SnakeGameStatus.GAME_OVER, vm.uiState.value.status)
        assertEquals(CollisionType.SELF, vm.uiState.value.collisionType)

        // 2. Moving into the vacating tail cell (when not eating) is valid
        vm.setStateForTesting(
            SnakeGameUiState(
                gridWidth = 6,
                gridHeight = 6,
                snake = listOf(
                    GridPoint(2, 2),
                    GridPoint(3, 2),
                    GridPoint(3, 3),
                    GridPoint(2, 3)
                ),
                direction = SnakeDirection.LEFT,
                pendingDirection = SnakeDirection.DOWN,
                food = GridPoint(5, 5),
                status = SnakeGameStatus.RUNNING
            )
        )
        vm.stepTick()
        assertEquals(SnakeGameStatus.RUNNING, vm.uiState.value.status)
        assertEquals(CollisionType.NONE, vm.uiState.value.collisionType)
        assertEquals(GridPoint(2, 3), vm.uiState.value.head)
    }

    @Test
    fun fillingEntireBoardTriggersWinStateWithNullFood() {
        val vm = SnakeGameViewModel(gridWidth = 4, gridHeight = 4, random = Random(1))
        val almostFullSnake = listOf(
            GridPoint(0, 1), GridPoint(1, 1), GridPoint(2, 1), GridPoint(3, 1),
            GridPoint(3, 0), GridPoint(2, 0), GridPoint(1, 0),
            GridPoint(1, 2), GridPoint(0, 2), GridPoint(0, 3), GridPoint(1, 3),
            GridPoint(2, 3), GridPoint(2, 2), GridPoint(3, 2), GridPoint(3, 3)
        )
        vm.setStateForTesting(
            SnakeGameUiState(
                gridWidth = 4,
                gridHeight = 4,
                snake = almostFullSnake,
                direction = SnakeDirection.LEFT,
                pendingDirection = SnakeDirection.UP,
                food = GridPoint(0, 0),
                score = 12,
                highScore = 12,
                status = SnakeGameStatus.RUNNING
            )
        )

        vm.stepTick()
        val state = vm.uiState.value

        assertEquals(SnakeGameStatus.WON, state.status)
        assertNull(state.food)
        assertEquals(16, state.snake.size)
        assertEquals(13, state.score)
        assertEquals(13, state.highScore)
    }

    @Test
    fun gameLoopAdvancesAutomaticallyAndPauseRestartPreserveHighScore() = runTest {
        val vm = SnakeGameViewModel(
            gridWidth = 10,
            gridHeight = 10,
            baseTickIntervalMs = 100L,
            random = Random(9)
        )
        val initialHead = vm.uiState.value.head!!

        vm.startGame()
        assertEquals(SnakeGameStatus.RUNNING, vm.uiState.value.status)

        advanceTimeBy(100L)
        runCurrent()
        assertEquals(GridPoint(initialHead.x + 1, initialHead.y), vm.uiState.value.head)

        vm.pauseGame()
        assertEquals(SnakeGameStatus.PAUSED, vm.uiState.value.status)
        val pausedHead = vm.uiState.value.head

        advanceTimeBy(500L)
        runCurrent()
        assertEquals(pausedHead, vm.uiState.value.head)

        // Simulate a higher score then restart
        vm.setStateForTesting(vm.uiState.value.copy(score = 5, highScore = 5, status = SnakeGameStatus.GAME_OVER))
        vm.restartGame(autoStart = false)
        assertEquals(SnakeGameStatus.READY, vm.uiState.value.status)
        assertEquals(0, vm.uiState.value.score)
        assertEquals(5, vm.uiState.value.highScore)
        assertEquals(CollisionType.NONE, vm.uiState.value.collisionType)
    }

    @Test
    fun tickDurationGraduallyDecreasesAsSnakeLengthIncreasesAndRespectsFloor() {
        val vm = SnakeGameViewModel(
            gridWidth = 12,
            gridHeight = 12,
            baseTickIntervalMs = 150L,
            minTickIntervalMs = 60L,
            tickDecreasePerSegmentMs = 5L,
            random = Random(3)
        )

        assertEquals(150L, vm.calculateTickDurationMs(3))
        assertEquals(145L, vm.calculateTickDurationMs(4))
        assertEquals(125L, vm.calculateTickDurationMs(8))
        assertEquals(60L, vm.calculateTickDurationMs(21))
        assertEquals(60L, vm.calculateTickDurationMs(50))
    }

    @Test
    fun gameLoopUsesShorterTickDelayImmediatelyAfterSnakeGrows() = runTest {
        val vm = SnakeGameViewModel(
            gridWidth = 10,
            gridHeight = 10,
            baseTickIntervalMs = 120L,
            minTickIntervalMs = 60L,
            tickDecreasePerSegmentMs = 20L,
            random = Random(11)
        )
        // Place food directly ahead of the 3-segment snake so tick #1 grows snake to length 4 (120ms -> 100ms)
        vm.setStateForTesting(
            SnakeGameUiState(
                gridWidth = 10,
                gridHeight = 10,
                snake = listOf(GridPoint(3, 5), GridPoint(2, 5), GridPoint(1, 5)),
                direction = SnakeDirection.RIGHT,
                pendingDirection = SnakeDirection.RIGHT,
                food = GridPoint(4, 5),
                status = SnakeGameStatus.READY,
                tickIntervalMs = 120L
            )
        )

        vm.startGame()

        // First tick fires at 120ms and eats food -> length becomes 4, next tick interval becomes 100ms
        advanceTimeBy(120L)
        runCurrent()
        assertEquals(4, vm.uiState.value.snake.size)
        assertEquals(GridPoint(4, 5), vm.uiState.value.head)
        assertEquals(100L, vm.uiState.value.tickIntervalMs)

        // Second tick fires faster at +100ms (before the old 120ms interval!)
        advanceTimeBy(100L)
        runCurrent()
        assertEquals(GridPoint(5, 5), vm.uiState.value.head)

        vm.pauseGame()
    }

    @Test
    fun viewModelTriggersBackgroundMusicAndEatFoodAndGameOverSounds() {
        val sounds = RecordingSoundController()
        val vm = SnakeGameViewModel(
            gridWidth = 6,
            gridHeight = 6,
            random = Random(5),
            soundController = sounds
        )

        // Start, pause, resume, restart background music
        vm.startGame()
        assertEquals(listOf("startBgm"), sounds.events)

        vm.pauseGame()
        assertEquals(listOf("startBgm", "pauseBgm"), sounds.events)

        vm.resumeGame()
        assertEquals(listOf("startBgm", "pauseBgm", "resumeBgm"), sounds.events)

        vm.restartGame(autoStart = false)
        assertEquals(listOf("startBgm", "pauseBgm", "resumeBgm", "stopBgm"), sounds.events)

        // Eating food triggers eat SFX
        sounds.events.clear()
        vm.setStateForTesting(
            SnakeGameUiState(
                gridWidth = 6,
                gridHeight = 6,
                snake = listOf(GridPoint(2, 2), GridPoint(1, 2), GridPoint(0, 2)),
                direction = SnakeDirection.RIGHT,
                pendingDirection = SnakeDirection.RIGHT,
                food = GridPoint(3, 2),
                status = SnakeGameStatus.RUNNING
            )
        )
        vm.stepTick()
        assertEquals(listOf("eatFood"), sounds.events)

        // Wall collision stops BGM and triggers game over SFX
        sounds.events.clear()
        vm.setStateForTesting(
            SnakeGameUiState(
                gridWidth = 6,
                gridHeight = 6,
                snake = listOf(GridPoint(5, 2), GridPoint(4, 2), GridPoint(3, 2)),
                direction = SnakeDirection.RIGHT,
                pendingDirection = SnakeDirection.RIGHT,
                food = GridPoint(0, 0),
                status = SnakeGameStatus.RUNNING
            )
        )
        vm.stepTick()
        assertEquals(listOf("stopBgm", "gameOver"), sounds.events)
    }

    @Test
    fun viewModelTriggersWinSoundAndSyncsAudioToggles() {
        val sounds = RecordingSoundController()
        val vm = SnakeGameViewModel(
            gridWidth = 4,
            gridHeight = 4,
            random = Random(1),
            soundController = sounds
        )

        val almostFullSnake = listOf(
            GridPoint(0, 1), GridPoint(1, 1), GridPoint(2, 1), GridPoint(3, 1),
            GridPoint(3, 0), GridPoint(2, 0), GridPoint(1, 0),
            GridPoint(1, 2), GridPoint(0, 2), GridPoint(0, 3), GridPoint(1, 3),
            GridPoint(2, 3), GridPoint(2, 2), GridPoint(3, 2), GridPoint(3, 3)
        )
        vm.setStateForTesting(
            SnakeGameUiState(
                gridWidth = 4,
                gridHeight = 4,
                snake = almostFullSnake,
                direction = SnakeDirection.LEFT,
                pendingDirection = SnakeDirection.UP,
                food = GridPoint(0, 0),
                status = SnakeGameStatus.RUNNING
            )
        )

        vm.stepTick()
        assertEquals(listOf("stopBgm", "win"), sounds.events)

        vm.toggleMusic()
        assertFalse(vm.uiState.value.isMusicEnabled)
        assertFalse(sounds.isMusicEnabled)

        vm.toggleSoundEffects()
        assertFalse(vm.uiState.value.isSoundEffectsEnabled)
        assertFalse(sounds.isSoundEffectsEnabled)
    }

    @Test
    fun viewModelLoadsPersistedHighScoreAndSavesNewRecordToRepository() = runTest {
        val repo = InMemoryHighScoreRepository(initialHighScore = 5)
        val vm = SnakeGameViewModel(
            gridWidth = 8,
            gridHeight = 8,
            random = Random(42),
            highScoreRepository = repo
        )
        runCurrent()

        // Persisted high score is loaded into UI state immediately
        assertEquals(5, vm.uiState.value.highScore)
        assertFalse(vm.uiState.value.isNewHighScore)

        // Scoring 6 beats saved high score of 5 -> updates UI and persists to repository
        vm.setStateForTesting(
            SnakeGameUiState(
                gridWidth = 8,
                gridHeight = 8,
                snake = listOf(GridPoint(3, 3), GridPoint(2, 3), GridPoint(1, 3)),
                direction = SnakeDirection.RIGHT,
                pendingDirection = SnakeDirection.RIGHT,
                food = GridPoint(4, 3),
                score = 5,
                highScore = 5,
                status = SnakeGameStatus.RUNNING
            )
        )
        vm.stepTick()
        runCurrent()

        assertEquals(6, vm.uiState.value.score)
        assertEquals(6, vm.uiState.value.highScore)
        assertTrue(vm.uiState.value.isNewHighScore)
        assertEquals(6, repo.getHighScore())

        // Restarting preserves saved high score and clears isNewHighScore flag for the new run
        vm.restartGame(autoStart = false)
        assertEquals(0, vm.uiState.value.score)
        assertEquals(6, vm.uiState.value.highScore)
        assertFalse(vm.uiState.value.isNewHighScore)
    }

    private class RecordingSoundController : GameSoundController {
        override var isMusicEnabled: Boolean = true
        override var isSoundEffectsEnabled: Boolean = true
        val events = mutableListOf<String>()

        override fun startBackgroundMusic() { events.add("startBgm") }
        override fun pauseBackgroundMusic() { events.add("pauseBgm") }
        override fun resumeBackgroundMusic() { events.add("resumeBgm") }
        override fun stopBackgroundMusic() { events.add("stopBgm") }
        override fun playEatFood() { events.add("eatFood") }
        override fun playGameOver() { events.add("gameOver") }
        override fun playWin() { events.add("win") }
        override fun release() { events.add("release") }
    }
}
