package com.binarydev.startergame.ui.snake

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.MusicOff
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.binarydev.startergame.ui.performance.LocalGraphicsPerformanceConfig
import com.binarydev.startergame.ui.theme.StarterGameTheme
import com.binarydev.startergame.ui.theme.geckoOutThemeConfig
import com.binarydev.startergame.ui.viewmodel.CollisionType
import com.binarydev.startergame.ui.viewmodel.GridPoint
import com.binarydev.startergame.ui.viewmodel.SnakeDirection
import com.binarydev.startergame.ui.viewmodel.SnakeGameStatus
import com.binarydev.startergame.ui.viewmodel.SnakeGameUiState
import com.binarydev.startergame.ui.viewmodel.SnakeGameViewModel
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sin

private val BoardBgDark = Color(0xFF0E1C2E)
private val BoardTileEven = Color(0xFF13253B)
private val BoardTileOdd = Color(0xFF172C45)
private val BoardGridLine = Color(0xFF1F3856)
private val BoardBorderNormal = Color(0xFF2D5075)
private val BoardBorderAlert = Color(0xFFFF5C79)
private val SnakeHeadColor = Color(0xFF34D896)
private val SnakeTailColor = Color(0xFF1A8F67)
private val SnakeSpotColor = Color(0xFF9DF7D0)
private val SnakeOutlineColor = Color(0xFF0E5A3E)
private val FoodCoreColor = Color(0xFFFF5C79)
private val FoodHighlightColor = Color(0xFFFFB26B)
private val FoodLeafColor = Color(0xFF4BE3A4)
private val EYE_SIDES = floatArrayOf(-1f, 1f)

/**
 * Pre-baked static checkerboard + hairline grid paths so rendering a 20x20 grid takes
 * 2 batched draw calls instead of 438 individual rect/line calls per frame.
 */
private class CachedSnakeGridPaths(
    val cols: Int,
    val rows: Int,
    val width: Float,
    val height: Float,
    val oddTilesPath: Path,
    val gridLinesPath: Path
)

private class SnakeDrawScratch {
    val centers = ArrayList<Offset>(64)
    val shadowPath = Path()
    val spinePath = Path()
}

private fun buildSnakeGridPaths(
    cols: Int,
    rows: Int,
    width: Float,
    height: Float
): CachedSnakeGridPaths {
    val cellW = width / cols
    val cellH = height / rows
    val oddPath = Path()
    for (y in 0 until rows) {
        for (x in 0 until cols) {
            if ((x + y) % 2 != 0) {
                oddPath.addRect(
                    Rect(
                        left = x * cellW,
                        top = y * cellH,
                        right = (x + 1) * cellW + 0.5f,
                        bottom = (y + 1) * cellH + 0.5f
                    )
                )
            }
        }
    }

    val linesPath = Path()
    for (x in 1 until cols) {
        val px = x * cellW
        linesPath.moveTo(px, 0f)
        linesPath.lineTo(px, height)
    }
    for (y in 1 until rows) {
        val py = y * cellH
        linesPath.moveTo(0f, py)
        linesPath.lineTo(width, py)
    }

    return CachedSnakeGridPaths(
        cols = cols,
        rows = rows,
        width = width,
        height = height,
        oddTilesPath = oddPath,
        gridLinesPath = linesPath
    )
}

/**
 * Stateful Compose board component bound directly to [SnakeGameViewModel].
 */
@Composable
fun SnakeGameBoard(
    viewModel: SnakeGameViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    SnakeGameBoard(
        state = state,
        onDirectionChange = viewModel::changeDirection,
        onOverlayAction = viewModel::togglePause,
        modifier = modifier
    )
}

/**
 * Stateless Compose grid visualization with 60fps between-tick spine-rail interpolation,
 * corner-hugging Bezier body curves, subtle slither undulation, and pre-baked static grid paths.
 */
@Composable
fun SnakeGameBoard(
    state: SnakeGameUiState,
    onDirectionChange: (SnakeDirection) -> Unit = {},
    onOverlayAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val perfConfig = LocalGraphicsPerformanceConfig.current
    val latestOnDirectionChange by rememberUpdatedState(onDirectionChange)
    val scratch = remember { SnakeDrawScratch() }
    var cachedGrid by remember { mutableStateOf<CachedSnakeGridPaths?>(null) }

    // Continuous 60fps sub-tick interpolation between previous and current logical snake positions
    var previousSnake by remember { mutableStateOf(state.snake) }
    var lastObservedSnake by remember { mutableStateOf(state.snake) }
    var tickProgress by remember { mutableFloatStateOf(1f) }
    var slitherPhase by remember { mutableFloatStateOf(0f) }

    if (state.snake != lastObservedSnake) {
        previousSnake = lastObservedSnake
        lastObservedSnake = state.snake
        tickProgress = 0f
    }

    LaunchedEffect(state.snake, state.status, state.tickIntervalMs) {
        if (state.status != SnakeGameStatus.RUNNING || previousSnake == state.snake) {
            tickProgress = 1f
            return@LaunchedEffect
        }
        val durationNanos = (state.tickIntervalMs.coerceAtLeast(30L)) * 1_000_000L
        var startNanos = 0L
        var lastNanos = 0L
        while (tickProgress < 1f) {
            withFrameNanos { nowNanos ->
                if (startNanos == 0L) {
                    startNanos = nowNanos
                    lastNanos = nowNanos
                }
                val dt = ((nowNanos - lastNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
                lastNanos = nowNanos
                slitherPhase += dt * 11f
                tickProgress = ((nowNanos - startNanos).toFloat() / durationNanos.toFloat()).coerceIn(0f, 1f)
            }
        }
    }

    val shouldPulseFood = state.status == SnakeGameStatus.RUNNING && perfConfig.enableIdleAnimations
    val foodPulse = if (shouldPulseFood) {
        val infiniteTransition = rememberInfiniteTransition(label = "snake_board_pulse")
        val anim by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 850, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "food_pulse"
        )
        anim
    } else {
        0.5f
    }

    val aspectRatio = (state.gridWidth.toFloat() / state.gridHeight.coerceAtLeast(1).toFloat())
        .coerceIn(0.4f, 2.5f)
    val isCollisionBorder = state.status == SnakeGameStatus.GAME_OVER &&
        state.collisionType == CollisionType.WALL

    val boardDescription = buildString {
        append("Snake game grid ${state.gridWidth} by ${state.gridHeight}. ")
        append("Score ${state.score}, length ${state.snake.size}, status ${state.status.name.lowercase()}.")
    }

    BoxWithConstraints(
        modifier = modifier.testTag("snake_game_board"),
        contentAlignment = Alignment.Center
    ) {
        val boardShape = RoundedCornerShape(20.dp)
        Surface(
            shape = boardShape,
            color = BoardBgDark,
            border = BorderStroke(
                width = if (isCollisionBorder) 2.5.dp else 1.5.dp,
                color = if (isCollisionBorder) BoardBorderAlert else BoardBorderNormal
            ),
            shadowElevation = if (perfConfig.lowRamMode) 2.dp else 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(aspectRatio)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(boardShape)
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .semantics { contentDescription = boardDescription }
                        .testTag("snake_game_canvas")
                        .pointerInput(Unit) {
                            var accumX = 0f
                            var accumY = 0f
                            val swipeThresholdPx = 24f
                            detectDragGestures(
                                onDragStart = {
                                    accumX = 0f
                                    accumY = 0f
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    accumX += dragAmount.x
                                    accumY += dragAmount.y
                                    if (abs(accumX) >= swipeThresholdPx || abs(accumY) >= swipeThresholdPx) {
                                        val dir = if (abs(accumX) > abs(accumY)) {
                                            if (accumX > 0f) SnakeDirection.RIGHT else SnakeDirection.LEFT
                                        } else {
                                            if (accumY > 0f) SnakeDirection.DOWN else SnakeDirection.UP
                                        }
                                        latestOnDirectionChange(dir)
                                        accumX = 0f
                                        accumY = 0f
                                    }
                                }
                            )
                        }
                ) {
                    val cols = state.gridWidth.coerceAtLeast(1)
                    val rows = state.gridHeight.coerceAtLeast(1)
                    val cellW = size.width / cols
                    val cellH = size.height / rows

                    val gridPaths = cachedGrid?.takeIf {
                        it.cols == cols && it.rows == rows && it.width == size.width && it.height == size.height
                    } ?: buildSnakeGridPaths(cols, rows, size.width, size.height).also {
                        cachedGrid = it
                    }

                    drawRect(color = BoardTileEven, size = size)
                    drawPath(path = gridPaths.oddTilesPath, color = BoardTileOdd)
                    if (!perfConfig.lowRamMode) {
                        drawPath(
                            path = gridPaths.gridLinesPath,
                            color = BoardGridLine.copy(alpha = 0.50f),
                            style = Stroke(width = 1f)
                        )
                    }

                    state.food?.let { foodPoint ->
                        drawFoodOrb(
                            food = foodPoint,
                            cellW = cellW,
                            cellH = cellH,
                            pulse = foodPulse,
                            lowRamMode = perfConfig.lowRamMode
                        )
                    }

                    if (state.snake.isNotEmpty()) {
                        drawSnakeBody(
                            prevSnake = previousSnake,
                            currSnake = state.snake,
                            progress = if (state.status == SnakeGameStatus.RUNNING) tickProgress else 1f,
                            slitherPhase = slitherPhase,
                            isRunning = state.status == SnakeGameStatus.RUNNING,
                            direction = state.direction,
                            isGameOver = state.status == SnakeGameStatus.GAME_OVER,
                            cellW = cellW,
                            cellH = cellH,
                            lowRamMode = perfConfig.lowRamMode,
                            scratch = scratch
                        )
                    }
                }

                if (state.status != SnakeGameStatus.RUNNING) {
                    BoardStatusOverlay(
                        status = state.status,
                        collisionType = state.collisionType,
                        score = state.score,
                        highScore = state.highScore,
                        isNewHighScore = state.isNewHighScore,
                        onClick = onOverlayAction,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawFoodOrb(
    food: GridPoint,
    cellW: Float,
    cellH: Float,
    pulse: Float,
    lowRamMode: Boolean
) {
    val cx = (food.x + 0.5f) * cellW
    val cy = (food.y + 0.5f) * cellH
    val baseR = minOf(cellW, cellH) * 0.36f
    val pulsedR = if (lowRamMode) baseR else baseR * (0.94f + pulse * 0.14f)

    drawCircle(
        color = FoodCoreColor.copy(alpha = if (lowRamMode) 0.24f else 0.22f + pulse * 0.16f),
        radius = pulsedR * 1.44f,
        center = Offset(cx, cy)
    )
    drawCircle(
        color = FoodCoreColor,
        radius = pulsedR,
        center = Offset(cx, cy)
    )
    drawCircle(
        color = FoodHighlightColor.copy(alpha = 0.78f),
        radius = pulsedR * 0.28f,
        center = Offset(cx - pulsedR * 0.28f, cy - pulsedR * 0.28f)
    )
    drawOval(
        color = FoodLeafColor,
        topLeft = Offset(cx - pulsedR * 0.08f, cy - pulsedR * 1.24f),
        size = Size(pulsedR * 0.56f, pulsedR * 0.34f)
    )
}

/**
 * Populates smooth corner-hugging interpolated spine points from [prevSnake] -> [currSnake]
 * so the head glides forward seamlessly, corners stay pinned on grid intersections, and the
 * tail retracts cleanly (or stays anchored while growing after eating food).
 */
private fun buildInterpolatedSnakeCenters(
    prevSnake: List<GridPoint>,
    currSnake: List<GridPoint>,
    progress: Float,
    slitherPhase: Float,
    isRunning: Boolean,
    cellW: Float,
    cellH: Float,
    outCenters: MutableList<Offset>
) {
    outCenters.clear()
    if (currSnake.isEmpty()) return

    val canInterpolate = progress < 0.999f &&
        prevSnake.size >= 2 &&
        currSnake.size >= 2 &&
        currSnake[1] == prevSnake[0] &&
        (currSnake.size == prevSnake.size || currSnake.size == prevSnake.size + 1)

    if (!canInterpolate) {
        for (i in currSnake.indices) {
            val pt = currSnake[i]
            outCenters.add(Offset((pt.x + 0.5f) * cellW, (pt.y + 0.5f) * cellH))
        }
        return
    }

    val t = progress.coerceIn(0f, 1f)
    val prevHead = prevSnake[0]
    val currHead = currSnake[0]
    outCenters.add(
        Offset(
            x = (prevHead.x + (currHead.x - prevHead.x) * t + 0.5f) * cellW,
            y = (prevHead.y + (currHead.y - prevHead.y) * t + 0.5f) * cellH
        )
    )

    // Keep all interior body vertices pinned accurately on grid corners
    val grew = currSnake.size == prevSnake.size + 1
    val interiorEnd = if (grew) currSnake.lastIndex else currSnake.lastIndex
    for (i in 1..interiorEnd) {
        val pt = currSnake[i]
        outCenters.add(Offset((pt.x + 0.5f) * cellW, (pt.y + 0.5f) * cellH))
    }

    // If not growing, smoothly retract the trailing tail tip from prevTail -> currTail
    if (!grew) {
        val prevTail = prevSnake.last()
        val currTail = currSnake.last()
        outCenters.add(
            Offset(
                x = (prevTail.x + (currTail.x - prevTail.x) * t + 0.5f) * cellW,
                y = (prevTail.y + (currTail.y - prevTail.y) * t + 0.5f) * cellH
            )
        )
    }

    // Subtle organic slither wave perpendicular to interior spine while running
    if (isRunning && outCenters.size >= 4) {
        val waveAmp = minOf(cellW, cellH) * 0.045f
        val maxI = outCenters.lastIndex
        for (i in 1 until maxI) {
            val prev = outCenters[i - 1]
            val next = outCenters[i + 1]
            val tx = next.x - prev.x
            val ty = next.y - prev.y
            val len = hypot(tx, ty)
            if (len > 1f) {
                val nx = -ty / len
                val ny = tx / len
                val env = sin((i.toFloat() / maxI.toFloat()) * PI).toFloat()
                val wobble = sin(slitherPhase - i * 1.25f) * waveAmp * env
                val c = outCenters[i]
                outCenters[i] = Offset(c.x + nx * wobble, c.y + ny * wobble)
            }
        }
    }
}

private fun buildSmoothSnakePath(path: Path, points: List<Offset>, yOffset: Float = 0f) {
    path.reset()
    if (points.isEmpty()) return
    path.moveTo(points[0].x, points[0].y + yOffset)
    if (points.size == 2) {
        path.lineTo(points[1].x, points[1].y + yOffset)
        return
    }
    val fillet = 0.34f
    for (i in 1 until points.lastIndex) {
        val prev = points[i - 1]
        val curr = points[i]
        val next = points[i + 1]
        val inX = curr.x + (prev.x - curr.x) * fillet
        val inY = curr.y + (prev.y - curr.y) * fillet + yOffset
        val outX = curr.x + (next.x - curr.x) * fillet
        val outY = curr.y + (next.y - curr.y) * fillet + yOffset
        path.lineTo(inX, inY)
        path.quadraticTo(curr.x, curr.y + yOffset, outX, outY)
    }
    val last = points.last()
    path.lineTo(last.x, last.y + yOffset)
}

private fun DrawScope.drawSnakeBody(
    prevSnake: List<GridPoint>,
    currSnake: List<GridPoint>,
    progress: Float,
    slitherPhase: Float,
    isRunning: Boolean,
    direction: SnakeDirection,
    isGameOver: Boolean,
    cellW: Float,
    cellH: Float,
    lowRamMode: Boolean,
    scratch: SnakeDrawScratch
) {
    val minCell = minOf(cellW, cellH)
    val bodyStroke = minCell * 0.72f

    buildInterpolatedSnakeCenters(
        prevSnake = prevSnake,
        currSnake = currSnake,
        progress = progress,
        slitherPhase = slitherPhase,
        isRunning = isRunning,
        cellW = cellW,
        cellH = cellH,
        outCenters = scratch.centers
    )
    val centers = scratch.centers
    if (centers.isEmpty()) return

    if (centers.size >= 2) {
        if (!lowRamMode) {
            buildSmoothSnakePath(scratch.shadowPath, centers, yOffset = minCell * 0.10f)
            drawPath(
                path = scratch.shadowPath,
                color = Color(0x4D060D17),
                style = Stroke(width = bodyStroke * 1.06f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }

        buildSmoothSnakePath(scratch.spinePath, centers)
        drawPath(
            path = scratch.spinePath,
            color = SnakeOutlineColor,
            style = Stroke(width = bodyStroke * 1.12f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        if (lowRamMode) {
            drawPath(
                path = scratch.spinePath,
                color = SnakeHeadColor,
                style = Stroke(width = bodyStroke * 0.90f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        } else {
            for (i in centers.lastIndex downTo 1) {
                val t = 1f - (i.toFloat() / centers.lastIndex.coerceAtLeast(1).toFloat())
                val segColor = lerp(SnakeTailColor, SnakeHeadColor, t)
                val widthFactor = 0.76f + 0.24f * t
                drawLine(
                    color = segColor,
                    start = centers[i],
                    end = centers[i - 1],
                    strokeWidth = bodyStroke * widthFactor,
                    cap = StrokeCap.Round
                )
                drawCircle(
                    color = SnakeSpotColor.copy(alpha = 0.55f),
                    radius = minCell * (0.09f + 0.04f * t),
                    center = centers[i]
                )
            }
        }
    }

    // Expressive Head smoothly gliding at centers[0]
    val head = centers[0]
    val headSize = minCell * 0.84f
    val headColor = if (isGameOver) Color(0xFFE55C73) else SnakeHeadColor

    drawRoundRect(
        color = SnakeOutlineColor,
        topLeft = Offset(head.x - headSize * 0.54f, head.y - headSize * 0.54f),
        size = Size(headSize * 1.08f, headSize * 1.08f),
        cornerRadius = CornerRadius(headSize * 0.38f, headSize * 0.38f)
    )
    drawRoundRect(
        color = headColor,
        topLeft = Offset(head.x - headSize * 0.48f, head.y - headSize * 0.48f),
        size = Size(headSize * 0.96f, headSize * 0.96f),
        cornerRadius = CornerRadius(headSize * 0.34f, headSize * 0.34f)
    )

    val forwardX = direction.dx.toFloat()
    val forwardY = direction.dy.toFloat()
    val perpX = -forwardY
    val perpY = forwardX

    val eyeForward = headSize * 0.16f
    val eyeSpread = headSize * 0.24f
    val eyeRadius = (minCell * 0.135f).coerceAtLeast(2.5f)

    for (side in EYE_SIDES) {
        val ex = head.x + forwardX * eyeForward + perpX * eyeSpread * side
        val ey = head.y + forwardY * eyeForward + perpY * eyeSpread * side

        drawCircle(color = Color.White, radius = eyeRadius, center = Offset(ex, ey))
        if (isGameOver) {
            val cross = eyeRadius * 0.55f
            drawLine(
                color = Color(0xFF0E1726),
                start = Offset(ex - cross, ey - cross),
                end = Offset(ex + cross, ey + cross),
                strokeWidth = 2f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color(0xFF0E1726),
                start = Offset(ex + cross, ey - cross),
                end = Offset(ex - cross, ey + cross),
                strokeWidth = 2f,
                cap = StrokeCap.Round
            )
        } else {
            val pupilOffset = eyeRadius * 0.28f
            drawCircle(
                color = Color(0xFF0E1726),
                radius = eyeRadius * 0.55f,
                center = Offset(ex + forwardX * pupilOffset, ey + forwardY * pupilOffset)
            )
        }
    }
}

@Composable
private fun BoardStatusOverlay(
    status: SnakeGameStatus,
    collisionType: CollisionType,
    score: Int,
    highScore: Int,
    isNewHighScore: Boolean,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val title = when (status) {
        SnakeGameStatus.READY -> "Ready to Play"
        SnakeGameStatus.PAUSED -> "Paused"
        SnakeGameStatus.GAME_OVER -> "Game Over"
        SnakeGameStatus.WON -> "Board Cleared!"
        SnakeGameStatus.RUNNING -> ""
    }
    val subtitle = when (status) {
        SnakeGameStatus.READY -> if (highScore > 0) {
            "Best Score: $highScore · Tap or swipe to start"
        } else {
            "Tap here or swipe to start"
        }
        SnakeGameStatus.PAUSED -> "Tap to resume · Best $highScore"
        SnakeGameStatus.GAME_OVER -> when (collisionType) {
            CollisionType.WALL -> "Hit the wall · Score $score · Best $highScore"
            CollisionType.SELF -> "Ran into yourself · Score $score · Best $highScore"
            CollisionType.NONE -> "Score $score · Best $highScore"
        }
        SnakeGameStatus.WON -> "Perfect run · Score $score · Best $highScore"
        SnakeGameStatus.RUNNING -> ""
    }

    val shape = RoundedCornerShape(18.dp)
    Surface(
        shape = shape,
        color = Color(0xE60D1B2A),
        border = BorderStroke(
            width = 1.2.dp,
            color = when {
                isNewHighScore && (status == SnakeGameStatus.GAME_OVER || status == SnakeGameStatus.WON) -> Color(0xFFFFD447)
                status == SnakeGameStatus.GAME_OVER -> BoardBorderAlert
                else -> SnakeHeadColor.copy(alpha = 0.65f)
            }
        ),
        shadowElevation = 6.dp,
        modifier = modifier
            .padding(20.dp)
            .clip(shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .testTag("snake_status_overlay")
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (isNewHighScore && (status == SnakeGameStatus.GAME_OVER || status == SnakeGameStatus.WON)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.testTag("snake_new_high_score_banner")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.EmojiEvents,
                        contentDescription = "New high score",
                        tint = Color(0xFFFFD447),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "NEW HIGH SCORE!",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFFFFD447)
                    )
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFB4CEE6),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun SnakeGameScreen(
    viewModel: SnakeGameViewModel = viewModel(
        factory = SnakeGameViewModel.provideFactory(LocalContext.current)
    ),
    onBack: (() -> Unit)? = null
) {
    val perfConfig = LocalGraphicsPerformanceConfig.current
    val state by viewModel.uiState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                viewModel.pauseGame()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (perfConfig.lowRamMode) {
                    Modifier.background(Color(0xFF0D1B2A))
                } else {
                    Modifier.background(Brush.verticalGradient(listOf(Color(0xFF0D1B2A), Color(0xFF193049))))
                }
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
                .align(Alignment.TopCenter)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (onBack != null) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("snake_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                    } else {
                        Text(
                            text = "Snake",
                            style = MaterialTheme.typography.headlineMedium,
                            color = Color.White
                        )
                    }

                    IconButton(
                        onClick = { viewModel.toggleMusic() },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("snake_music_toggle_button")
                    ) {
                        Icon(
                            imageVector = if (state.isMusicEnabled) {
                                Icons.Rounded.MusicNote
                            } else {
                                Icons.Rounded.MusicOff
                            },
                            contentDescription = if (state.isMusicEnabled) {
                                "Mute background music"
                            } else {
                                "Enable background music"
                            },
                            tint = if (state.isMusicEnabled) SnakeHeadColor else Color(0xFF7B99B5)
                        )
                    }

                    IconButton(
                        onClick = { viewModel.toggleSoundEffects() },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("snake_sfx_toggle_button")
                    ) {
                        Icon(
                            imageVector = if (state.isSoundEffectsEnabled) {
                                Icons.AutoMirrored.Rounded.VolumeUp
                            } else {
                                Icons.AutoMirrored.Rounded.VolumeOff
                            },
                            contentDescription = if (state.isSoundEffectsEnabled) {
                                "Mute sound effects"
                            } else {
                                "Enable sound effects"
                            },
                            tint = if (state.isSoundEffectsEnabled) SnakeHeadColor else Color(0xFF7B99B5)
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ScorePill(
                        label = "Score",
                        value = state.score.toString(),
                        accent = SnakeHeadColor,
                        modifier = Modifier.testTag("snake_score_badge")
                    )
                    ScorePill(
                        label = "Best",
                        value = state.highScore.toString(),
                        accent = Color(0xFFFFD447),
                        icon = Icons.Rounded.EmojiEvents,
                        highlighted = state.isNewHighScore,
                        modifier = Modifier.testTag("snake_high_score_badge")
                    )
                }
            }

            SnakeGameBoard(
                state = state,
                onDirectionChange = viewModel::changeDirection,
                onOverlayAction = viewModel::togglePause,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val primaryLabel = when (state.status) {
                    SnakeGameStatus.RUNNING -> "Pause"
                    SnakeGameStatus.PAUSED -> "Resume"
                    SnakeGameStatus.READY -> "Start"
                    SnakeGameStatus.GAME_OVER, SnakeGameStatus.WON -> "Play Again"
                }
                val primaryIcon = if (state.status == SnakeGameStatus.RUNNING) {
                    Icons.Rounded.Pause
                } else {
                    Icons.Rounded.PlayArrow
                }

                Button(
                    onClick = { viewModel.togglePause() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SnakeHeadColor,
                        contentColor = Color(0xFF062318)
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("snake_toggle_pause_button")
                ) {
                    Icon(primaryIcon, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(primaryLabel, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = { viewModel.restartGame(autoStart = true) },
                    border = BorderStroke(1.dp, BoardBorderNormal),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("snake_restart_button")
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Restart", modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Restart")
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                DirectionButton(
                    icon = Icons.Rounded.KeyboardArrowUp,
                    contentDescription = "Move Up",
                    testTag = "snake_dpad_up",
                    onClick = { viewModel.changeDirection(SnakeDirection.UP) }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(44.dp)) {
                    DirectionButton(
                        icon = Icons.Rounded.KeyboardArrowLeft,
                        contentDescription = "Move Left",
                        testTag = "snake_dpad_left",
                        onClick = { viewModel.changeDirection(SnakeDirection.LEFT) }
                    )
                    DirectionButton(
                        icon = Icons.Rounded.KeyboardArrowRight,
                        contentDescription = "Move Right",
                        testTag = "snake_dpad_right",
                        onClick = { viewModel.changeDirection(SnakeDirection.RIGHT) }
                    )
                }
                DirectionButton(
                    icon = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = "Move Down",
                    testTag = "snake_dpad_down",
                    onClick = { viewModel.changeDirection(SnakeDirection.DOWN) }
                )
            }
        }
    }
}

@Composable
private fun ScorePill(
    label: String,
    value: String,
    accent: Color,
    icon: ImageVector? = null,
    highlighted: Boolean = false,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (highlighted) accent.copy(alpha = 0.18f) else Color(0xFF15293E),
        border = BorderStroke(1.dp, if (highlighted) accent else BoardBorderNormal),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = accent,
                    modifier = Modifier.size(15.dp)
                )
            }
            Text(text = label, fontSize = 12.sp, color = Color(0xFF93B3D0))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = accent
            )
        }
    }
}

@Composable
private fun DirectionButton(
    icon: ImageVector,
    contentDescription: String,
    testTag: String,
    onClick: () -> Unit
) {
    FilledTonalIconButton(
        onClick = onClick,
        shape = CircleShape,
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = Color(0xFF1B334E),
            contentColor = Color.White
        ),
        modifier = Modifier
            .size(54.dp)
            .testTag(testTag)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(28.dp)
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SnakeGameBoardPreview() {
    StarterGameTheme(themeConfig = geckoOutThemeConfig()) {
        Box(
            modifier = Modifier
                .background(Color(0xFF0D1B2A))
                .padding(16.dp)
        ) {
            SnakeGameBoard(
                state = SnakeGameUiState(
                    gridWidth = 12,
                    gridHeight = 12,
                    snake = listOf(
                        GridPoint(6, 5),
                        GridPoint(5, 5),
                        GridPoint(4, 5),
                        GridPoint(4, 6),
                        GridPoint(3, 6)
                    ),
                    direction = SnakeDirection.RIGHT,
                    pendingDirection = SnakeDirection.RIGHT,
                    food = GridPoint(9, 3),
                    score = 4,
                    highScore = 10,
                    status = SnakeGameStatus.RUNNING
                )
            )
        }
    }
}
