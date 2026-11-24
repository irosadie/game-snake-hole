package com.binarydev.startergame.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.binarydev.startergame.data.preferences.DataStoreHighScoreRepository
import com.binarydev.startergame.data.preferences.HighScoreRepository
import com.binarydev.startergame.data.preferences.InMemoryHighScoreRepository
import com.binarydev.startergame.ui.audio.GameSoundController
import com.binarydev.startergame.ui.audio.NoOpSoundController
import com.binarydev.startergame.ui.audio.SoundManager
import kotlin.random.Random
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class GridPoint(
    val x: Int,
    val y: Int
)

enum class SnakeDirection(val dx: Int, val dy: Int) {
    UP(0, -1),
    DOWN(0, 1),
    LEFT(-1, 0),
    RIGHT(1, 0);

    fun isOpposite(other: SnakeDirection): Boolean =
        dx == -other.dx && dy == -other.dy
}

enum class SnakeGameStatus {
    READY,
    RUNNING,
    PAUSED,
    GAME_OVER,
    WON
}

enum class CollisionType {
    NONE,
    WALL,
    SELF
}

data class SnakeGameUiState(
    val gridWidth: Int = DEFAULT_GRID_WIDTH,
    val gridHeight: Int = DEFAULT_GRID_HEIGHT,
    val snake: List<GridPoint> = emptyList(),
    val direction: SnakeDirection = SnakeDirection.RIGHT,
    val pendingDirection: SnakeDirection = SnakeDirection.RIGHT,
    val food: GridPoint? = null,
    val score: Int = 0,
    val highScore: Int = 0,
    val isNewHighScore: Boolean = false,
    val status: SnakeGameStatus = SnakeGameStatus.READY,
    val collisionType: CollisionType = CollisionType.NONE,
    val tickIntervalMs: Long = BASE_TICK_INTERVAL_MS,
    val isMusicEnabled: Boolean = true,
    val isSoundEffectsEnabled: Boolean = true
) {
    val head: GridPoint? get() = snake.firstOrNull()

    companion object {
        const val DEFAULT_GRID_WIDTH = 20
        const val DEFAULT_GRID_HEIGHT = 20
        const val INITIAL_SNAKE_LENGTH = 3
        const val BASE_TICK_INTERVAL_MS = 150L
        const val MIN_TICK_INTERVAL_MS = 60L
        const val TICK_DECREASE_PER_SEGMENT_MS = 4L
    }
}

/**
 * ViewModel managing classic grid-based Snake game state:
 * snake body segments, buffered directional input, unoccupied food spawning,
 * wall/self collision detection, score tracking, and a coroutine tick loop
 * whose tick duration gradually decreases as the snake grows longer.
 */
class SnakeGameViewModel(
    private val gridWidth: Int = SnakeGameUiState.DEFAULT_GRID_WIDTH,
    private val gridHeight: Int = SnakeGameUiState.DEFAULT_GRID_HEIGHT,
    private val baseTickIntervalMs: Long = SnakeGameUiState.BASE_TICK_INTERVAL_MS,
    private val minTickIntervalMs: Long = SnakeGameUiState.MIN_TICK_INTERVAL_MS,
    private val tickDecreasePerSegmentMs: Long = SnakeGameUiState.TICK_DECREASE_PER_SEGMENT_MS,
    private val random: Random = Random.Default,
    private val soundController: GameSoundController = NoOpSoundController,
    private val releaseSoundOnClear: Boolean = false,
    private val highScoreRepository: HighScoreRepository = InMemoryHighScoreRepository()
) : ViewModel() {

    private val effectiveMinTickIntervalMs: Long = minOf(baseTickIntervalMs, minTickIntervalMs)

    init {
        require(gridWidth >= 4) { "gridWidth must be at least 4" }
        require(gridHeight >= 4) { "gridHeight must be at least 4" }
        require(baseTickIntervalMs > 0L) { "baseTickIntervalMs must be positive" }
        require(minTickIntervalMs > 0L) { "minTickIntervalMs must be positive" }
        require(tickDecreasePerSegmentMs >= 0L) { "tickDecreasePerSegmentMs must be non-negative" }
    }

    private val _uiState = MutableStateFlow(createInitialState(highScore = 0))
    val uiState: StateFlow<SnakeGameUiState> = _uiState.asStateFlow()

    private var gameLoopJob: Job? = null

    init {
        viewModelScope.launch {
            highScoreRepository.highScoreFlow.collect { savedBest ->
                _uiState.update { current ->
                    val mergedBest = maxOf(current.highScore, savedBest)
                    if (mergedBest != current.highScore) {
                        current.copy(highScore = mergedBest)
                    } else {
                        current
                    }
                }
            }
        }
    }

    fun startGame() {
        val current = _uiState.value
        when (current.status) {
            SnakeGameStatus.RUNNING -> return
            SnakeGameStatus.GAME_OVER, SnakeGameStatus.WON -> {
                _uiState.value = createInitialState(highScore = current.highScore)
                    .copy(status = SnakeGameStatus.RUNNING)
                soundController.startBackgroundMusic()
            }
            SnakeGameStatus.READY -> {
                _uiState.update { it.copy(status = SnakeGameStatus.RUNNING) }
                soundController.startBackgroundMusic()
            }
            SnakeGameStatus.PAUSED -> {
                _uiState.update { it.copy(status = SnakeGameStatus.RUNNING) }
                soundController.resumeBackgroundMusic()
            }
        }
        launchGameLoop()
    }

    fun pauseGame() {
        if (_uiState.value.status != SnakeGameStatus.RUNNING) return
        stopGameLoop()
        _uiState.update { it.copy(status = SnakeGameStatus.PAUSED) }
        soundController.pauseBackgroundMusic()
    }

    fun resumeGame() {
        if (_uiState.value.status != SnakeGameStatus.PAUSED) return
        _uiState.update { it.copy(status = SnakeGameStatus.RUNNING) }
        soundController.resumeBackgroundMusic()
        launchGameLoop()
    }

    fun togglePause() {
        when (_uiState.value.status) {
            SnakeGameStatus.RUNNING -> pauseGame()
            SnakeGameStatus.PAUSED -> resumeGame()
            SnakeGameStatus.READY -> startGame()
            SnakeGameStatus.GAME_OVER, SnakeGameStatus.WON -> restartGame(autoStart = true)
        }
    }

    fun restartGame(autoStart: Boolean = true) {
        stopGameLoop()
        val preservedHighScore = _uiState.value.highScore
        val nextStatus = if (autoStart) SnakeGameStatus.RUNNING else SnakeGameStatus.READY
        _uiState.value = createInitialState(highScore = preservedHighScore).copy(status = nextStatus)
        if (autoStart) {
            soundController.startBackgroundMusic()
            launchGameLoop()
        } else {
            soundController.stopBackgroundMusic()
        }
    }

    fun setMusicEnabled(enabled: Boolean) {
        soundController.isMusicEnabled = enabled
        _uiState.update { it.copy(isMusicEnabled = enabled) }
    }

    fun toggleMusic() {
        setMusicEnabled(!_uiState.value.isMusicEnabled)
    }

    fun setSoundEffectsEnabled(enabled: Boolean) {
        soundController.isSoundEffectsEnabled = enabled
        _uiState.update { it.copy(isSoundEffectsEnabled = enabled) }
    }

    fun toggleSoundEffects() {
        setSoundEffectsEnabled(!_uiState.value.isSoundEffectsEnabled)
    }

    /**
     * Queues the next movement direction for the upcoming tick.
     * Prevents 180-degree self-reversal against the currently committed movement direction.
     */
    fun changeDirection(newDirection: SnakeDirection) {
        _uiState.update { current ->
            if (current.status != SnakeGameStatus.RUNNING && current.status != SnakeGameStatus.READY) {
                return@update current
            }
            if (newDirection.isOpposite(current.direction)) {
                return@update current
            }
            current.copy(pendingDirection = newDirection)
        }
    }

    /**
     * Advances the simulation by a single discrete grid step:
     * resolves direction, checks wall & self collisions, grows snake on food pickup,
     * and spawns the next food item on an unoccupied cell.
     */
    fun stepTick() {
        val current = _uiState.value
        if (current.status != SnakeGameStatus.RUNNING) return
        val currentHead = current.snake.firstOrNull() ?: return

        val activeDirection = if (current.pendingDirection.isOpposite(current.direction)) {
            current.direction
        } else {
            current.pendingDirection
        }

        val nextHead = GridPoint(
            x = currentHead.x + activeDirection.dx,
            y = currentHead.y + activeDirection.dy
        )

        // 1. Wall collision check
        if (isWallCollision(nextHead, current.gridWidth, current.gridHeight)) {
            stopGameLoop()
            val bestScore = maxOf(current.highScore, current.score)
            _uiState.value = current.copy(
                direction = activeDirection,
                pendingDirection = activeDirection,
                highScore = bestScore,
                status = SnakeGameStatus.GAME_OVER,
                collisionType = CollisionType.WALL
            )
            persistHighScoreIfNeeded(bestScore)
            soundController.stopBackgroundMusic()
            soundController.playGameOver()
            return
        }

        val ateFood = current.food != null && nextHead == current.food

        // 2. Self collision check (tail vacates on the same tick unless the snake is growing)
        val bodyToCheck = if (ateFood) current.snake else current.snake.dropLast(1)
        if (nextHead in bodyToCheck) {
            stopGameLoop()
            val bestScore = maxOf(current.highScore, current.score)
            _uiState.value = current.copy(
                direction = activeDirection,
                pendingDirection = activeDirection,
                highScore = bestScore,
                status = SnakeGameStatus.GAME_OVER,
                collisionType = CollisionType.SELF
            )
            persistHighScoreIfNeeded(bestScore)
            soundController.stopBackgroundMusic()
            soundController.playGameOver()
            return
        }

        // 3. Advance snake position
        val updatedSnake = buildList(current.snake.size + if (ateFood) 1 else 0) {
            add(nextHead)
            if (ateFood) {
                addAll(current.snake)
            } else {
                for (i in 0 until current.snake.lastIndex) {
                    add(current.snake[i])
                }
            }
        }

        val updatedScore = if (ateFood) current.score + 1 else current.score
        val beatHighScore = updatedScore > current.highScore
        val updatedHighScore = maxOf(current.highScore, updatedScore)
        val updatedTickMs = calculateTickDurationMs(updatedSnake.size)

        // 4. Spawn new food if eaten; win if no empty cells remain
        val updatedFood = if (ateFood) {
            spawnFood(occupied = updatedSnake.toSet(), width = current.gridWidth, height = current.gridHeight)
        } else {
            current.food
        }

        val boardFilled = ateFood && updatedFood == null
        if (boardFilled) {
            stopGameLoop()
        }

        _uiState.value = current.copy(
            snake = updatedSnake,
            direction = activeDirection,
            pendingDirection = activeDirection,
            food = updatedFood,
            score = updatedScore,
            highScore = updatedHighScore,
            isNewHighScore = current.isNewHighScore || beatHighScore,
            status = if (boardFilled) SnakeGameStatus.WON else SnakeGameStatus.RUNNING,
            collisionType = CollisionType.NONE,
            tickIntervalMs = updatedTickMs
        )

        if (beatHighScore || boardFilled) {
            persistHighScoreIfNeeded(updatedHighScore)
        }

        if (boardFilled) {
            soundController.stopBackgroundMusic()
            soundController.playWin()
        } else if (ateFood) {
            soundController.playEatFood()
        }
    }

    private fun persistHighScoreIfNeeded(score: Int) {
        if (score <= 0) return
        viewModelScope.launch {
            highScoreRepository.saveHighScoreIfHigher(score)
        }
    }

    internal fun setStateForTesting(state: SnakeGameUiState) {
        stopGameLoop()
        _uiState.value = state
    }

    private fun launchGameLoop() {
        stopGameLoop()
        gameLoopJob = viewModelScope.launch {
            while (isActive && _uiState.value.status == SnakeGameStatus.RUNNING) {
                val currentTickDurationMs = calculateTickDurationMs(_uiState.value.snake.size)
                if (_uiState.value.tickIntervalMs != currentTickDurationMs) {
                    _uiState.update { it.copy(tickIntervalMs = currentTickDurationMs) }
                }
                delay(currentTickDurationMs)
                if (_uiState.value.status == SnakeGameStatus.RUNNING) {
                    stepTick()
                }
            }
        }
    }

    private fun stopGameLoop() {
        gameLoopJob?.cancel()
        gameLoopJob = null
    }

    private fun createInitialState(highScore: Int): SnakeGameUiState {
        val centerX = gridWidth / 2
        val centerY = gridHeight / 2
        val initialSnake = listOf(
            GridPoint(centerX, centerY),
            GridPoint(centerX - 1, centerY),
            GridPoint(centerX - 2, centerY)
        )
        val initialFood = spawnFood(
            occupied = initialSnake.toSet(),
            width = gridWidth,
            height = gridHeight
        )
        return SnakeGameUiState(
            gridWidth = gridWidth,
            gridHeight = gridHeight,
            snake = initialSnake,
            direction = SnakeDirection.RIGHT,
            pendingDirection = SnakeDirection.RIGHT,
            food = initialFood,
            score = 0,
            highScore = highScore,
            status = SnakeGameStatus.READY,
            collisionType = CollisionType.NONE,
            tickIntervalMs = baseTickIntervalMs,
            isMusicEnabled = soundController.isMusicEnabled,
            isSoundEffectsEnabled = soundController.isSoundEffectsEnabled
        )
    }

    private fun isWallCollision(point: GridPoint, width: Int, height: Int): Boolean =
        point.x !in 0 until width || point.y !in 0 until height

    private fun spawnFood(
        occupied: Set<GridPoint>,
        width: Int,
        height: Int
    ): GridPoint? {
        val totalCells = width * height
        if (occupied.size >= totalCells) return null

        val freeCells = ArrayList<GridPoint>(totalCells - occupied.size)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val candidate = GridPoint(x, y)
                if (candidate !in occupied) {
                    freeCells.add(candidate)
                }
            }
        }
        if (freeCells.isEmpty()) return null
        return freeCells[random.nextInt(freeCells.size)]
    }

    /**
     * Computes the tick duration in milliseconds from the current snake length.
     * Each segment grown beyond [SnakeGameUiState.INITIAL_SNAKE_LENGTH] reduces the tick
     * duration by [tickDecreasePerSegmentMs] down to a playable floor of [effectiveMinTickIntervalMs].
     */
    fun calculateTickDurationMs(snakeLength: Int): Long {
        val extraSegments = (snakeLength - SnakeGameUiState.INITIAL_SNAKE_LENGTH).coerceAtLeast(0)
        val reductionMs = extraSegments.toLong() * tickDecreasePerSegmentMs
        return (baseTickIntervalMs - reductionMs).coerceAtLeast(effectiveMinTickIntervalMs)
    }

    override fun onCleared() {
        stopGameLoop()
        soundController.stopBackgroundMusic()
        if (releaseSoundOnClear) {
            soundController.release()
        }
        super.onCleared()
    }

    companion object {
        fun provideFactory(
            context: Context,
            gridWidth: Int = SnakeGameUiState.DEFAULT_GRID_WIDTH,
            gridHeight: Int = SnakeGameUiState.DEFAULT_GRID_HEIGHT,
            highScoreRepository: HighScoreRepository = DataStoreHighScoreRepository.fromContext(context)
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val manager = SoundManager(context.applicationContext)
                return SnakeGameViewModel(
                    gridWidth = gridWidth,
                    gridHeight = gridHeight,
                    soundController = manager,
                    releaseSoundOnClear = true,
                    highScoreRepository = highScoreRepository
                ) as T
            }
        }

        fun provideFactory(
            soundController: GameSoundController,
            releaseSoundOnClear: Boolean = false,
            gridWidth: Int = SnakeGameUiState.DEFAULT_GRID_WIDTH,
            gridHeight: Int = SnakeGameUiState.DEFAULT_GRID_HEIGHT,
            highScoreRepository: HighScoreRepository = InMemoryHighScoreRepository()
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SnakeGameViewModel(
                    gridWidth = gridWidth,
                    gridHeight = gridHeight,
                    soundController = soundController,
                    releaseSoundOnClear = releaseSoundOnClear,
                    highScoreRepository = highScoreRepository
                ) as T
            }
        }
    }
}
