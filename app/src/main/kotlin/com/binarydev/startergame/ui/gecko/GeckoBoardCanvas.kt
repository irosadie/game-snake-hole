package com.binarydev.startergame.ui.gecko

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.binarydev.startergame.core.gecko.BoardState
import com.binarydev.startergame.core.gecko.CellCoord
import com.binarydev.startergame.core.gecko.DragEnd
import com.binarydev.startergame.core.gecko.GeckoColor
import com.binarydev.startergame.core.gecko.GeckoGameRules
import com.binarydev.startergame.core.gecko.GeckoState
import com.binarydev.startergame.core.gecko.HintMove
import com.binarydev.startergame.core.gecko.HoleState
import com.binarydev.startergame.ui.performance.GraphicsPerformanceConfig
import com.binarydev.startergame.ui.performance.LocalGraphicsPerformanceConfig
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.delay

private val CORNER_DR = floatArrayOf(-0.5f, -0.5f, 0.5f, 0.5f)
private val CORNER_DC = floatArrayOf(-0.5f, 0.5f, -0.5f, 0.5f)
private val TWO_SIDES = floatArrayOf(-1f, 1f)

private val TileLeftFaceColor = Color(0xFFBBD0E3)
private val TileRightFaceColor = Color(0xFF9BB4CC)
private val TileTopEvenColor = Color(0xFFF0F6FB)
private val TileTopOddColor = Color(0xFFE5EEF6)
private val TileOutlineColor = Color(0xFFCBDAE8)
private val WallBodyFlatColor = Color(0xFF2D4866)
private val WallShadowColor = Color(0x550B1626)
private val WallTopRailColor = Color(0xFF6A8FB5)
private val WallTopHighlightColor = Color(0xFF9AC0E3)
private val HoleSealedBaseColor = Color(0xFFD5E2EE)
private val HoleInteriorDarkColor = Color(0xFF0A1424)
private val GeckoShadowColor = Color(0x45091424)
private val HintYellowColor = Color(0xFFFFE566)

private data class IsoMetrics(
    val originX: Float,
    val originY: Float,
    val tileW: Float,
    val tileH: Float,
    val centerR: Float,
    val centerC: Float
) {
    fun gridToScreen(r: Float, c: Float): Offset {
        val dx = c - centerC
        val dz = r - centerR
        val sx = originX + (dx + dz) * (tileW * 0.5f)
        val sy = originY + (dz - dx) * (tileH * 0.5f)
        return Offset(sx, sy)
    }

    fun screenToGrid(sx: Float, sy: Float): CellCoord {
        val nx = (sx - originX) / (tileW * 0.5f)
        val ny = (sy - originY) / (tileH * 0.5f)
        val dz = (nx + ny) * 0.5f
        val dx = (nx - ny) * 0.5f
        return CellCoord(
            r = (dz + centerR).roundToInt(),
            c = (dx + centerC).roundToInt()
        )
    }
}

private data class PrecomputedWall(
    val pStart: Offset,
    val pEnd: Offset,
    val wallHeight: Float,
    val wallStroke: Float,
    val facePath: Path,
    val gradientBrush: Brush
)

/**
 * Pre-baked vector geometry for static board tiles and edge walls.
 * Rebuilt only when level or canvas dimensions change — uses only a few KB of RAM
 * and reduces per-frame tile/wall draws from ~150+ to 5 batched Path calls.
 */
private class StaticBoardGeometry(
    val levelId: Int,
    val width: Float,
    val height: Float,
    val metrics: IsoMetrics,
    val leftFacesPath: Path,
    val rightFacesPath: Path,
    val evenTopPath: Path,
    val oddTopPath: Path,
    val topOutlinePath: Path,
    val walls: List<PrecomputedWall>
)

/**
 * Continuous 1D spine-rail physics state for a single gecko.
 *
 * Instead of linearly lerping each segment independently (which cuts 90-degree corners and
 * stutters between tiles), every segment slides continuously along a shared polyline rail
 * preserving exact body length and corner hugs, with:
 * - smooth critically-damped velocity (continuous cruise across multi-tile slides + soft cushion stop),
 * - subtle organic slither undulation while moving,
 * - diagonal-couplet alternating foot scurry driven by real distance traveled,
 * - smoothed angular head turning.
 */
private class GeckoRailTrack(
    initialSegments: List<CellCoord>
) {
    val rail = ArrayList<Offset>(initialSegments.size + 8)
    var lastSyncedSegments: List<CellCoord> = initialSegments
    val segmentCount: Int get() = lastSyncedSegments.size

    /** Current rail index of the gecko's HEAD (tail sits at `u + segmentCount - 1`). */
    var u: Float = 0f

    /** Target rail index for the gecko's HEAD. */
    var targetU: Float = 0f

    /** Current scalar rail velocity in tiles/second. */
    var velocity: Float = 0f

    /** Cumulative distance traveled in tiles (drives realistic alternating feet & slither wave). */
    var distanceTraveled: Float = 0f

    /** Smoothed movement intensity in [0f, 1f] so slither wave & stride fade cleanly to rest. */
    var motionIntensity: Float = 0f

    /** Smoothed head orientation angle in degrees for fluid corner turns. */
    var smoothedHeadAngleDeg: Float? = null

    init {
        resetTo(initialSegments)
    }

    fun resetTo(segments: List<CellCoord>) {
        rail.clear()
        for (s in segments) {
            rail.add(Offset(s.c.toFloat(), s.r.toFloat()))
        }
        lastSyncedSegments = segments
        u = 0f
        targetU = 0f
        velocity = 0f
        motionIntensity = 0f
    }

    /**
     * Synchronizes with an updated logical [newSegments] state from [BoardState].
     * Detects 1-tile Head advance, 1-tile Tail advance, or multi-step continuous advances
     * and appends/prepends to the rail without losing momentum.
     */
    fun syncWithSegments(newSegments: List<CellCoord>) {
        if (newSegments == lastSyncedSegments) return
        val prev = lastSyncedSegments
        if (newSegments.size != prev.size || newSegments.size < 2) {
            resetTo(newSegments)
            return
        }

        // Case 1: Head moved 1 step forward (new[1..last] == prev[0..last-1])
        var isHeadStep = true
        for (i in 1 until newSegments.size) {
            if (newSegments[i] != prev[i - 1]) {
                isHeadStep = false
                break
            }
        }
        if (isHeadStep) {
            val newHead = newSegments[0]
            rail.add(0, Offset(newHead.c.toFloat(), newHead.r.toFloat()))
            u += 1f
            targetU = 0f
            lastSyncedSegments = newSegments
            trimExcessRail()
            return
        }

        // Case 2: Tail moved 1 step forward (new[0..last-1] == prev[1..last])
        var isTailStep = true
        for (i in 0 until newSegments.lastIndex) {
            if (newSegments[i] != prev[i + 1]) {
                isTailStep = false
                break
            }
        }
        if (isTailStep) {
            val newTail = newSegments[newSegments.lastIndex]
            rail.add(Offset(newTail.c.toFloat(), newTail.r.toFloat()))
            targetU = (rail.size - newSegments.size).toFloat().coerceAtLeast(0f)
            lastSyncedSegments = newSegments
            trimExcessRail()
            return
        }

        // Fallback (Undo / Restart / multi-cell jump): reset rail cleanly
        resetTo(newSegments)
    }

    val isMoving: Boolean
        get() = abs(targetU - u) > 0.002f || abs(velocity) > 0.015f || motionIntensity > 0.01f

    /**
     * Advances smooth rail physics by [dt] seconds using a responsive critically-damped
     * cruise-and-cushion controller so multi-tile auto-walks glide seamlessly with zero pauses.
     */
    fun stepPhysics(dt: Float) {
        val diff = targetU - u
        val dist = abs(diff)
        val prevU = u

        if (dist <= 0.0015f && abs(velocity) <= 0.02f) {
            u = targetU
            velocity = 0f
            compactRailWhenSettled()
        } else {
            // Smooth velocity ramps up for multi-tile slides and eases out naturally to a soft stop
            val desiredSpeed = (dist * 16.5f).coerceAtMost(17.5f) * sign(diff)
            val blend = 1f - exp(-24f * dt)
            velocity += (desiredSpeed - velocity) * blend

            val step = velocity * dt
            if (abs(step) >= dist && sign(step) == sign(diff)) {
                u = targetU
                velocity = 0f
            } else {
                u += step
            }
            val maxU = (rail.size - segmentCount).toFloat().coerceAtLeast(0f)
            u = u.coerceIn(0f, maxU)
        }

        val moved = abs(u - prevU)
        distanceTraveled += moved
        val instantSpeed = if (dt > 0.0001f) moved / dt else 0f
        val targetIntensity = (instantSpeed / 7.5f).coerceIn(0f, 1f)
        val intensityBlend = 1f - exp((if (targetIntensity > motionIntensity) -18f else -10f) * dt)
        motionIntensity += (targetIntensity - motionIntensity) * intensityBlend
        if (motionIntensity < 0.008f && dist <= 0.002f) {
            motionIntensity = 0f
        }
    }

    /**
     * Samples the gecko's live spine points in screen coordinates directly into [outScreenPoints],
     * explicitly preserving every intermediate rail corner vertex so the body hugs 90-degree bends
     * with exact constant length, plus subtle organic slither undulation while moving.
     */
    fun sampleScreenSpine(
        metrics: IsoMetrics,
        outScreenPoints: MutableList<Offset>,
        outSampleIndices: MutableList<Float>
    ) {
        outScreenPoints.clear()
        outSampleIndices.clear()
        if (rail.isEmpty() || segmentCount < 2) return

        val span = (segmentCount - 1).toFloat()
        val maxStart = (rail.size - segmentCount).toFloat().coerceAtLeast(0f)
        val startIdx = u.coerceIn(0f, maxStart)
        val endIdx = (startIdx + span).coerceAtMost(rail.lastIndex.toFloat())

        // 1. Exact head position
        outScreenPoints.add(sampleGridToScreen(startIdx, metrics))
        outSampleIndices.add(0f)

        // 2. Every integer corner vertex strictly between head and tail (prevents corner cutting!)
        val firstInt = floor(startIdx).toInt() + 1
        val lastInt = floor(endIdx).toInt()
        for (idx in firstInt..lastInt) {
            if (idx > startIdx + 0.015f && idx < endIdx - 0.015f) {
                val p = rail[idx]
                outScreenPoints.add(metrics.gridToScreen(p.y, p.x))
                outSampleIndices.add(idx - startIdx)
            }
        }

        // 3. Exact tail position
        outScreenPoints.add(sampleGridToScreen(endIdx, metrics))
        outSampleIndices.add(span)

        // 4. Subtle realistic lateral slither undulation while actively moving (fades smoothly to 0 at rest)
        if (motionIntensity > 0.02f && outScreenPoints.size >= 3) {
            val waveAmp = metrics.tileW * 0.028f * motionIntensity
            val phase = distanceTraveled * 6.8f
            for (i in 1 until outScreenPoints.lastIndex) {
                val prev = outScreenPoints[i - 1]
                val next = outScreenPoints[i + 1]
                val tx = next.x - prev.x
                val ty = next.y - prev.y
                val tLen = hypot(tx, ty)
                if (tLen > 1f) {
                    val nx = -ty / tLen
                    val ny = tx / tLen
                    val arcT = outSampleIndices[i]
                    // Taper wave near head & tail so snout and tail tip stay grounded cleanly
                    val envelope = sin((arcT / span).coerceIn(0f, 1f) * PI).toFloat()
                    val offset = sin(phase - arcT * 2.15f) * waveAmp * envelope
                    val cur = outScreenPoints[i]
                    outScreenPoints[i] = Offset(cur.x + nx * offset, cur.y + ny * offset)
                }
            }
        }
    }

    /**
     * Samples the live grid-space spine points (used to seamlessly hand off into a head-first hole dive).
     */
    fun snapshotCurrentGridSpine(): List<Offset> {
        if (rail.isEmpty() || segmentCount < 2) return emptyList()
        val span = (segmentCount - 1).toFloat()
        val maxStart = (rail.size - segmentCount).toFloat().coerceAtLeast(0f)
        val startIdx = u.coerceIn(0f, maxStart)
        val endIdx = (startIdx + span).coerceAtMost(rail.lastIndex.toFloat())
        val pts = ArrayList<Offset>(segmentCount + 2)
        pts.add(sampleGrid(startIdx))
        val firstInt = floor(startIdx).toInt() + 1
        val lastInt = floor(endIdx).toInt()
        for (idx in firstInt..lastInt) {
            if (idx > startIdx + 0.015f && idx < endIdx - 0.015f) {
                pts.add(rail[idx])
            }
        }
        pts.add(sampleGrid(endIdx))
        return pts
    }

    private fun sampleGrid(railPos: Float): Offset {
        val clamped = railPos.coerceIn(0f, rail.lastIndex.toFloat())
        val i0 = floor(clamped).toInt()
        val i1 = (i0 + 1).coerceAtMost(rail.lastIndex)
        val frac = clamped - i0
        val p0 = rail[i0]
        val p1 = rail[i1]
        return Offset(
            x = p0.x + (p1.x - p0.x) * frac,
            y = p0.y + (p1.y - p0.y) * frac
        )
    }

    private fun sampleGridToScreen(railPos: Float, metrics: IsoMetrics): Offset {
        val g = sampleGrid(railPos)
        return metrics.gridToScreen(g.y, g.x)
    }

    private fun compactRailWhenSettled() {
        val startInt = targetU.roundToInt().coerceIn(0, (rail.size - segmentCount).coerceAtLeast(0))
        if (startInt > 0) {
            repeat(startInt) { rail.removeAt(0) }
            u = 0f
            targetU = 0f
        }
        while (rail.size > segmentCount) {
            rail.removeAt(rail.lastIndex)
        }
    }

    private fun trimExcessRail() {
        // Keep memory tiny if player drags very fast across many tiles
        val minNeededStart = floor(minOf(u, targetU)).toInt().coerceAtLeast(0)
        if (minNeededStart > 2) {
            val dropFront = minNeededStart - 1
            repeat(dropFront) { rail.removeAt(0) }
            u -= dropFront
            targetU -= dropFront
        }
        val maxNeededEnd = (floor(maxOf(u, targetU)).toInt() + segmentCount + 2)
        while (rail.size > maxNeededEnd && rail.size > segmentCount) {
            rail.removeAt(rail.lastIndex)
        }
    }
}

/**
 * Reusable scratch buffers for zero-allocation steady-state Canvas rendering.
 */
private class GeckoDrawScratch {
    val bodyPath = Path()
    val shadowPath = Path()
    val highlightPath = Path()
    val hintPath = Path()
    val walkTrailPath = Path()
    val screenPoints = ArrayList<Offset>(20)
    val sampleIndices = ArrayList<Float>(20)
    val shadowPoints = ArrayList<Offset>(20)
    val highlightPoints = ArrayList<Offset>(20)
    val divePoints = ArrayList<Offset>(20)
    val activeGeckos = ArrayList<GeckoState>(8)
}

/**
 * Realistic follow-the-leader plunge animation: the gecko's head dives into the hole first
 * while the rest of its body feeds smoothly along its own spine into the portal!
 */
private data class DivingGeckoAnim(
    val geckoId: String,
    val color: GeckoColor,
    val railPoints: List<Offset>, // [hole, head, seg1, ..., tail]
    var progress: Float = 0f // 0f -> 1f as full spine feeds into index 0 (the hole)
)

private data class ConfettiParticle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val radius: Float,
    val color: Color,
    var life: Float = 1f
)

private data class ActiveDrag(
    val geckoId: String,
    val end: DragEnd,
    var lastCell: CellCoord,
    var pushedUndo: Boolean = false
)

/**
 * Queued step-by-step walk plan triggered when the user clicks any straight-line placeholder dot.
 */
private data class AutoWalkPlan(
    val id: Long,
    val geckoId: String,
    val end: DragEnd,
    val steps: List<CellCoord>
)

private fun computeIsoMetrics(board: BoardState, width: Float, height: Float): IsoMetrics {
    if (board.cells.isEmpty() || width <= 1f || height <= 1f) {
        return IsoMetrics(width * 0.5f, height * 0.5f, 64f, 40f, 0f, 0f)
    }
    var minR = Float.POSITIVE_INFINITY
    var maxR = Float.NEGATIVE_INFINITY
    var minC = Float.POSITIVE_INFINITY
    var maxC = Float.NEGATIVE_INFINITY
    for (cell in board.cells) {
        val rf = cell.r.toFloat()
        val cf = cell.c.toFloat()
        if (rf < minR) minR = rf
        if (rf > maxR) maxR = rf
        if (cf < minC) minC = cf
        if (cf > maxC) maxC = cf
    }
    val centerR = (minR + maxR) * 0.5f
    val centerC = (minC + maxC) * 0.5f

    var minNx = Float.POSITIVE_INFINITY
    var maxNx = Float.NEGATIVE_INFINITY
    var minNy = Float.POSITIVE_INFINITY
    var maxNy = Float.NEGATIVE_INFINITY

    for (cell in board.cells) {
        for (k in 0 until 4) {
            val dx = (cell.c + CORNER_DC[k]) - centerC
            val dz = (cell.r + CORNER_DR[k]) - centerR
            val nx = (dx + dz) * 0.5f
            val ny = (dz - dx) * 0.5f
            if (nx < minNx) minNx = nx
            if (nx > maxNx) maxNx = nx
            if (ny < minNy) minNy = ny
            if (ny > maxNy) maxNy = ny
        }
    }

    val spanX = (maxNx - minNx).coerceAtLeast(1f)
    val spanY = (maxNy - minNy).coerceAtLeast(1f)
    val aspect = 0.62f

    val usableW = width * 0.92f
    val usableH = height * 0.86f

    val tileWFromW = usableW / spanX
    val tileWFromH = usableH / (spanY * aspect + 0.45f)
    val tileW = minOf(tileWFromW, tileWFromH).coerceIn(36f, 155f)
    val tileH = tileW * aspect

    val midNx = (minNx + maxNx) * 0.5f
    val midNy = (minNy + maxNy) * 0.5f
    val originX = width * 0.5f - midNx * tileW
    val originY = height * 0.5f - midNy * tileH - tileH * 0.08f

    return IsoMetrics(
        originX = originX,
        originY = originY,
        tileW = tileW,
        tileH = tileH,
        centerR = centerR,
        centerC = centerC
    )
}

private fun buildStaticBoardGeometry(
    board: BoardState,
    width: Float,
    height: Float
): StaticBoardGeometry {
    val metrics = computeIsoMetrics(board, width, height)
    val thickness = (metrics.tileH * 0.28f).coerceIn(8f, 22f)

    val leftFaces = Path()
    val rightFaces = Path()
    val evenTop = Path()
    val oddTop = Path()
    val topOutline = Path()

    val sortedCells = board.cells.sortedWith(
        compareBy<CellCoord> { it.r - it.c }.thenBy { it.r }.thenBy { it.c }
    )
    for (cell in sortedCells) {
        val r = cell.r.toFloat()
        val c = cell.c.toFloat()
        val top = metrics.gridToScreen(r - 0.5f, c + 0.5f)
        val right = metrics.gridToScreen(r + 0.5f, c + 0.5f)
        val bottom = metrics.gridToScreen(r + 0.5f, c - 0.5f)
        val left = metrics.gridToScreen(r - 0.5f, c - 0.5f)

        leftFaces.moveTo(left.x, left.y)
        leftFaces.lineTo(bottom.x, bottom.y)
        leftFaces.lineTo(bottom.x, bottom.y + thickness)
        leftFaces.lineTo(left.x, left.y + thickness)
        leftFaces.close()

        rightFaces.moveTo(bottom.x, bottom.y)
        rightFaces.lineTo(right.x, right.y)
        rightFaces.lineTo(right.x, right.y + thickness)
        rightFaces.lineTo(bottom.x, bottom.y + thickness)
        rightFaces.close()

        val targetTop = if ((cell.r + cell.c) % 2 == 0) evenTop else oddTop
        targetTop.moveTo(top.x, top.y)
        targetTop.lineTo(right.x, right.y)
        targetTop.lineTo(bottom.x, bottom.y)
        targetTop.lineTo(left.x, left.y)
        targetTop.close()

        topOutline.moveTo(top.x, top.y)
        topOutline.lineTo(right.x, right.y)
        topOutline.lineTo(bottom.x, bottom.y)
        topOutline.lineTo(left.x, left.y)
        topOutline.close()
    }

    val wallHeight = (metrics.tileH * 0.34f).coerceIn(10f, 26f)
    val wallStroke = (metrics.tileW * 0.085f).coerceIn(4.5f, 10f)
    val sortedWalls = board.walls.sortedBy { (it.a.r + it.b.r) - (it.a.c + it.b.c) }
    val precomputedWalls = sortedWalls.map { wall ->
        val a = wall.a
        val b = wall.b
        val pStart: Offset
        val pEnd: Offset
        if (b.r == a.r + 1 && b.c == a.c) {
            pStart = metrics.gridToScreen(a.r + 0.5f, a.c - 0.5f)
            pEnd = metrics.gridToScreen(a.r + 0.5f, a.c + 0.5f)
        } else {
            pStart = metrics.gridToScreen(a.r - 0.5f, a.c + 0.5f)
            pEnd = metrics.gridToScreen(a.r + 0.5f, a.c + 0.5f)
        }
        val facePath = Path().apply {
            moveTo(pStart.x, pStart.y)
            lineTo(pEnd.x, pEnd.y)
            lineTo(pEnd.x, pEnd.y - wallHeight)
            lineTo(pStart.x, pStart.y - wallHeight)
            close()
        }
        val brush = Brush.verticalGradient(
            colors = listOf(Color(0xFF3E5E80), Color(0xFF21364F)),
            startY = minOf(pStart.y, pEnd.y) - wallHeight,
            endY = maxOf(pStart.y, pEnd.y)
        )
        PrecomputedWall(
            pStart = pStart,
            pEnd = pEnd,
            wallHeight = wallHeight,
            wallStroke = wallStroke,
            facePath = facePath,
            gradientBrush = brush
        )
    }

    return StaticBoardGeometry(
        levelId = board.levelId,
        width = width,
        height = height,
        metrics = metrics,
        leftFacesPath = leftFaces,
        rightFacesPath = rightFaces,
        evenTopPath = evenTop,
        oddTopPath = oddTop,
        topOutlinePath = topOutline,
        walls = precomputedWalls
    )
}

@Composable
fun GeckoBoardCanvas(
    board: BoardState,
    activeHint: HintMove?,
    onDragStep: (geckoId: String, end: DragEnd, target: CellCoord, pushUndoSnapshot: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val perfConfig = LocalGraphicsPerformanceConfig.current
    val latestBoard by rememberUpdatedState(board)
    val latestHint by rememberUpdatedState(activeHint)
    val latestPerfConfig by rememberUpdatedState(perfConfig)
    val latestOnDragStep by rememberUpdatedState(onDragStep)

    // Continuous spine-rail tracks per gecko (zero corner-cutting, fluid glide)
    val railTracks = remember(board.levelId) { HashMap<String, GeckoRailTrack>() }
    val divingAnims = remember(board.levelId) { mutableListOf<DivingGeckoAnim>() }
    val particles = remember(board.levelId) { mutableListOf<ConfettiParticle>() }
    val prevEscapedIds = remember(board.levelId) { mutableSetOf<String>() }
    val drawScratch = remember { GeckoDrawScratch() }

    // Cached static geometry rebuilt only when level or canvas size changes
    var cachedGeometry by remember(board.levelId) { mutableStateOf<StaticBoardGeometry?>(null) }
    var canvasSize by remember { mutableStateOf(Size.Zero) }
    var animTimeSec by remember { mutableFloatStateOf(0f) }
    var frameTick by remember { mutableLongStateOf(0L) }
    var activeDrag by remember(board.levelId) { mutableStateOf<ActiveDrag?>(null) }
    var autoWalkPlan by remember(board.levelId) { mutableStateOf<AutoWalkPlan?>(null) }
    var autoWalkStepIdx by remember(board.levelId) { mutableIntStateOf(0) }
    var nextPlanId by remember(board.levelId) { mutableLongStateOf(1L) }

    // Straight-line slide targets until hitting a wall, another gecko, or straight-ahead matching hole
    val selectedGeckoId = activeDrag?.geckoId
    val selectedEnd = activeDrag?.end
    val reachablePaths: Map<CellCoord, List<CellCoord>> = remember(
        board.geckos,
        board.holes,
        selectedGeckoId,
        selectedEnd,
        autoWalkPlan
    ) {
        if (selectedGeckoId == null || selectedEnd == null || autoWalkPlan != null) {
            emptyMap()
        } else {
            GeckoGameRules.computeLocalSlidePaths(
                state = board,
                geckoId = selectedGeckoId,
                end = selectedEnd
            )
        }
    }
    val latestReachablePaths by rememberUpdatedState(reachablePaths)

    // Synchronize rail tracks immediately whenever logical board.geckos changes
    LaunchedEffect(board.geckos, board.levelId) {
        val currentMetrics = cachedGeometry?.metrics ?: if (canvasSize.width > 0f && canvasSize.height > 0f) {
            computeIsoMetrics(board, canvasSize.width, canvasSize.height)
        } else {
            null
        }
        val particleBudget = latestPerfConfig.maxConfettiParticles

        for (g in board.geckos) {
            if (g.escaped) {
                if (prevEscapedIds.add(g.id)) {
                    val hole = board.holes.firstOrNull { it.color == g.color }
                    if (hole != null) {
                        val existingTrack = railTracks[g.id]
                        val liveSpine = existingTrack?.snapshotCurrentGridSpine()?.takeIf { it.size >= 2 }
                            ?: g.segments.map { Offset(it.c.toFloat(), it.r.toFloat()) }
                        val holeGrid = Offset(hole.coord.c.toFloat(), hole.coord.r.toFloat())
                        val diveRail = ArrayList<Offset>(liveSpine.size + 1).apply {
                            add(holeGrid)
                            addAll(liveSpine)
                        }
                        divingAnims.add(
                            DivingGeckoAnim(
                                geckoId = g.id,
                                color = g.color,
                                railPoints = diveRail,
                                progress = 0f
                            )
                        )
                        if (currentMetrics != null && particleBudget > 0) {
                            val center = currentMetrics.gridToScreen(hole.coord.r.toFloat(), hole.coord.c.toFloat())
                            val bodyColor = Color(g.color.bodyHex)
                            val spotColor = Color(g.color.spotHex)
                            repeat(particleBudget) { i ->
                                val angle = (i * (2.0 * PI / particleBudget.toDouble())) + Random.nextDouble(-0.22, 0.22)
                                val speed = Random.nextFloat() * 195f + 90f
                                particles.add(
                                    ConfettiParticle(
                                        x = center.x,
                                        y = center.y,
                                        vx = (cos(angle) * speed).toFloat(),
                                        vy = (sin(angle) * speed).toFloat() - 70f,
                                        radius = Random.nextFloat() * 4.5f + 3.2f,
                                        color = when (i % 3) {
                                            0 -> bodyColor
                                            1 -> spotColor
                                            else -> Color.White
                                        },
                                        life = 1f
                                    )
                                )
                            }
                        }
                    }
                }
            } else {
                prevEscapedIds.remove(g.id)
                val track = railTracks[g.id]
                if (track == null) {
                    railTracks[g.id] = GeckoRailTrack(g.segments)
                } else {
                    track.syncWithSegments(g.segments)
                }
            }
        }
    }

    // Seamless continuous auto-walk when clicking a placeholder dot:
    // Feeds the next step BEFORE the rail slows down (~62ms cadence) so multi-tile glides flow
    // in one uninterrupted motion and gently cushion to a stop on the final tile!
    LaunchedEffect(autoWalkPlan) {
        val plan = autoWalkPlan ?: return@LaunchedEffect
        for (idx in plan.steps.indices) {
            autoWalkStepIdx = idx
            val currentBoard = latestBoard
            val gecko = currentBoard.geckos.firstOrNull { it.id == plan.geckoId }
            if (gecko == null || gecko.escaped) break

            val targetStep = plan.steps[idx]
            latestOnDragStep(plan.geckoId, plan.end, targetStep, idx == 0)

            val afterGecko = latestBoard.geckos.firstOrNull { it.id == plan.geckoId }
            if (afterGecko == null || afterGecko.escaped) {
                activeDrag = null
                break
            }

            if (idx < plan.steps.lastIndex) {
                delay(62L)
            }
        }

        val finalGecko = latestBoard.geckos.firstOrNull { it.id == plan.geckoId }
        if (finalGecko != null && !finalGecko.escaped) {
            val endCell = if (plan.end == DragEnd.HEAD) finalGecko.segments.first() else finalGecko.segments.last()
            activeDrag = ActiveDrag(plan.geckoId, plan.end, endCell, pushedUndo = false)
        } else {
            activeDrag = null
        }
        autoWalkPlan = null
        autoWalkStepIdx = 0
    }

    // Clear selection if selected gecko already escaped
    LaunchedEffect(board.geckos) {
        val current = activeDrag
        if (current != null) {
            val g = board.geckos.firstOrNull { it.id == current.geckoId }
            if (g == null || g.escaped) {
                activeDrag = null
            }
        }
    }

    // Smooth 60fps active physics loop + 0fps zero-drain idle sleep in Mode Ringan
    LaunchedEffect(board.levelId) {
        var lastNanos = 0L
        var lastRenderNanos = 0L
        while (true) {
            val cfg = latestPerfConfig
            val hasWork = divingAnims.isNotEmpty() ||
                particles.isNotEmpty() ||
                latestHint != null ||
                autoWalkPlan != null ||
                hasAnyMovingTrack(latestBoard, railTracks)

            if (!hasWork && !cfg.enableIdleAnimations) {
                lastNanos = 0L
                delay(32L)
                continue
            }

            withFrameNanos { nowNanos ->
                val minInterval = if (hasWork) cfg.targetFrameIntervalNanos else cfg.idleFrameIntervalNanos
                if (lastRenderNanos != 0L && (nowNanos - lastRenderNanos) < minInterval) {
                    return@withFrameNanos
                }

                val dt = if (lastNanos == 0L) {
                    0.016f
                } else {
                    ((nowNanos - lastNanos) / 1_000_000_000f).coerceIn(0.001f, 0.045f)
                }
                lastNanos = nowNanos
                lastRenderNanos = nowNanos
                animTimeSec += dt

                val b = latestBoard
                for (g in b.geckos) {
                    if (g.escaped) continue
                    val track = railTracks.getOrPut(g.id) { GeckoRailTrack(g.segments) }
                    track.syncWithSegments(g.segments)
                    track.stepPhysics(dt)
                }

                // Update realistic follow-the-leader hole-plunge animations (~380ms)
                val diveIter = divingAnims.iterator()
                while (diveIter.hasNext()) {
                    val anim = diveIter.next()
                    anim.progress += dt / 0.38f
                    if (anim.progress >= 1.0f) {
                        diveIter.remove()
                    }
                }

                // Update confetti particles
                val pIter = particles.iterator()
                while (pIter.hasNext()) {
                    val p = pIter.next()
                    p.x += p.vx * dt
                    p.y += p.vy * dt
                    p.vy += 420f * dt
                    p.life -= dt * 1.65f
                    if (p.life <= 0f) {
                        pIter.remove()
                    }
                }

                frameTick = nowNanos
            }
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .semantics {
                contentDescription = "Papan puzzle isometrik Gecko Out level ${board.levelId}, tersisa ${board.remainingGeckoCount} gecko"
            }
            .testTag("gecko_board_canvas")
            .pointerInput(board.levelId) {
                detectTapGestures { offset ->
                    val b = latestBoard
                    val metrics = cachedGeometry?.metrics
                        ?: computeIsoMetrics(b, size.width.toFloat(), size.height.toFloat())
                    val tappedCell = metrics.screenToGrid(offset.x, offset.y)

                    if (autoWalkPlan != null) {
                        autoWalkPlan = null
                    }

                    // 1. Tap any straight-line placeholder dot (or straight-ahead hole) to smoothly slide there
                    val selected = activeDrag
                    val paths = latestReachablePaths
                    if (selected != null && paths.isNotEmpty()) {
                        val matchedCell = pickReachableDotCell(paths, metrics, offset, tappedCell)
                        if (matchedCell != null) {
                            val walkSteps = paths[matchedCell]
                            if (!walkSteps.isNullOrEmpty()) {
                                nextPlanId++
                                autoWalkPlan = AutoWalkPlan(
                                    id = nextPlanId,
                                    geckoId = selected.geckoId,
                                    end = selected.end,
                                    steps = walkSteps
                                )
                                return@detectTapGestures
                            }
                        }
                    }

                    // 2. Tap along active glowing Hint trail (first 3 visible steps) to follow it
                    val hint = latestHint
                    if (hint != null) {
                        val visibleHintSteps = hint.previewPath.take(3)
                        val hintIdx = visibleHintSteps.indexOf(tappedCell)
                        if (hintIdx >= 0) {
                            val steps = visibleHintSteps.subList(0, hintIdx + 1)
                            nextPlanId++
                            activeDrag = ActiveDrag(hint.geckoId, hint.end, tappedCell, pushedUndo = false)
                            autoWalkPlan = AutoWalkPlan(
                                id = nextPlanId,
                                geckoId = hint.geckoId,
                                end = hint.end,
                                steps = steps
                            )
                            return@detectTapGestures
                        }
                    }

                    // 3. Select tapped gecko head/tail (auto-switching to movable end if blocked)
                    val picked = pickGeckoEnd(b, metrics, offset)
                    activeDrag = picked?.let {
                        ActiveDrag(it.first, it.second, it.third, pushedUndo = false)
                    }
                }
            }
            .pointerInput(board.levelId) {
                detectDragGestures(
                    onDragStart = { startOffset ->
                        autoWalkPlan = null
                        val b = latestBoard
                        val metrics = cachedGeometry?.metrics
                            ?: computeIsoMetrics(b, size.width.toFloat(), size.height.toFloat())
                        val picked = pickGeckoEnd(b, metrics, startOffset)
                        activeDrag = picked?.let {
                            ActiveDrag(
                                geckoId = it.first,
                                end = it.second,
                                lastCell = it.third,
                                pushedUndo = false
                            )
                        }
                    },
                    onDragEnd = { activeDrag = null },
                    onDragCancel = { activeDrag = null },
                    onDrag = { change, _ ->
                        change.consume()
                        val drag = activeDrag ?: return@detectDragGestures
                        val b = latestBoard
                        val gecko = b.geckos.firstOrNull { it.id == drag.geckoId }
                        if (gecko == null || gecko.escaped) {
                            activeDrag = null
                            return@detectDragGestures
                        }

                        val metrics = cachedGeometry?.metrics
                            ?: computeIsoMetrics(b, size.width.toFloat(), size.height.toFloat())
                        val targetCell = metrics.screenToGrid(change.position.x, change.position.y)
                        val currentEndCell = if (drag.end == DragEnd.HEAD) {
                            gecko.segments.first()
                        } else {
                            gecko.segments.last()
                        }

                        if (targetCell != currentEndCell && targetCell != drag.lastCell) {
                            val beforeSegments = gecko.segments
                            val beforeEscaped = gecko.escaped
                            latestOnDragStep(drag.geckoId, drag.end, targetCell, !drag.pushedUndo)
                            val updatedGecko = latestBoard.geckos.firstOrNull { it.id == drag.geckoId }
                            if (updatedGecko != null && (updatedGecko.segments != beforeSegments || updatedGecko.escaped != beforeEscaped)) {
                                drag.pushedUndo = true
                                drag.lastCell = if (drag.end == DragEnd.HEAD) {
                                    updatedGecko.segments.first()
                                } else {
                                    updatedGecko.segments.last()
                                }
                            } else {
                                drag.lastCell = targetCell
                            }
                        }
                    }
                )
            }
    ) {
        @Suppress("UNUSED_VARIABLE")
        val tick = frameTick

        if (size.width <= 1f || size.height <= 1f) return@Canvas
        canvasSize = size

        val geometry = cachedGeometry?.takeIf {
            it.levelId == board.levelId && it.width == size.width && it.height == size.height
        } ?: buildStaticBoardGeometry(board, size.width, size.height).also {
            cachedGeometry = it
        }
        val metrics = geometry.metrics

        // 1. Batched static isometric stone tiles (5 draw calls total, zero alloc)
        drawPath(geometry.leftFacesPath, color = TileLeftFaceColor)
        drawPath(geometry.rightFacesPath, color = TileRightFaceColor)
        drawPath(geometry.evenTopPath, color = TileTopEvenColor)
        drawPath(geometry.oddTopPath, color = TileTopOddColor)
        drawPath(geometry.topOutlinePath, color = TileOutlineColor, style = Stroke(width = 1.1f))

        // 2. Holes (open portals or sealed floor caps)
        for (i in board.holes.indices) {
            drawIsoHole(
                metrics = metrics,
                hole = board.holes[i],
                animTimeSec = animTimeSec,
                perfConfig = perfConfig
            )
        }

        // 3. Pre-baked 3D edge walls
        for (i in geometry.walls.indices) {
            drawPrecomputedWall(geometry.walls[i], useGradients = perfConfig.enableShadersAndGradients)
        }

        // 4. Straight-line placeholder dots until wall / another gecko / straight-ahead matching hole
        val selected = activeDrag
        if (selected != null && reachablePaths.isNotEmpty() && autoWalkPlan == null) {
            drawReachablePlaceholderDots(
                metrics = metrics,
                board = board,
                selected = selected,
                reachablePaths = reachablePaths
            )
        }

        // 5. Remaining trail while smoothly auto-walking to clicked dot
        val activePlan = autoWalkPlan
        if (activePlan != null) {
            drawActiveWalkTrail(
                metrics = metrics,
                board = board,
                plan = activePlan,
                currentStepIdx = autoWalkStepIdx,
                scratch = drawScratch
            )
        }

        // 6. Hint path overlay (first 3 steps)
        if (activeHint != null) {
            drawHintOverlay(
                metrics = metrics,
                board = board,
                hint = activeHint,
                animTimeSec = animTimeSec,
                scratch = drawScratch
            )
        }

        // 7. Active geckos sorted back-to-front by average screen Y
        drawScratch.activeGeckos.clear()
        for (g in board.geckos) {
            if (!g.escaped) drawScratch.activeGeckos.add(g)
        }
        drawScratch.activeGeckos.sortBy { g ->
            computeAverageScreenY(g, railTracks[g.id], metrics)
        }

        for (i in drawScratch.activeGeckos.indices) {
            val gecko = drawScratch.activeGeckos[i]
            val track = railTracks[gecko.id]
            if (track != null) {
                track.sampleScreenSpine(metrics, drawScratch.screenPoints, drawScratch.sampleIndices)
            } else {
                drawScratch.screenPoints.clear()
                for (seg in gecko.segments) {
                    drawScratch.screenPoints.add(metrics.gridToScreen(seg.r.toFloat(), seg.c.toFloat()))
                }
            }
            val draggedEnd = if (selected?.geckoId == gecko.id) selected.end else null
            drawGeckoSpline(
                metrics = metrics,
                geckoId = gecko.id,
                geckoColor = gecko.color,
                screenPoints = drawScratch.screenPoints,
                animTimeSec = animTimeSec,
                draggedEnd = draggedEnd,
                scaleFactor = 1f,
                stridePhase = (track?.distanceTraveled ?: 0f) * 7.2f,
                motionIntensity = track?.motionIntensity ?: 0f,
                trackForHeadSmoothing = track,
                perfConfig = perfConfig,
                scratch = drawScratch
            )
        }

        // 8. Realistic head-first follow-the-leader plunge into the escape hole!
        for (i in divingAnims.indices) {
            val dive = divingAnims[i]
            sampleDivingSpineInto(dive, metrics, drawScratch.divePoints)
            if (drawScratch.divePoints.size >= 2) {
                val tailShrink = (1f - (dive.progress * 0.45f)).coerceIn(0.45f, 1f)
                val hideHead = dive.progress > 0.22f
                drawGeckoSpline(
                    metrics = metrics,
                    geckoId = dive.geckoId,
                    geckoColor = dive.color,
                    screenPoints = drawScratch.divePoints,
                    animTimeSec = animTimeSec,
                    draggedEnd = null,
                    scaleFactor = tailShrink,
                    stridePhase = dive.progress * 14f,
                    motionIntensity = 1f,
                    trackForHeadSmoothing = null,
                    perfConfig = perfConfig,
                    scratch = drawScratch,
                    drawHead = !hideHead
                )
            }
        }

        // 9. Confetti burst particles
        for (i in particles.indices) {
            val p = particles[i]
            drawCircle(
                color = p.color.copy(alpha = p.life.coerceIn(0f, 1f)),
                radius = p.radius * p.life.coerceIn(0.3f, 1f),
                center = Offset(p.x, p.y)
            )
        }
    }
}

/**
 * Feeds the gecko's spine head-first along [DivingGeckoAnim.railPoints] into index 0 (the hole),
 * shortening smoothly from the front so the body visually slides down into the portal!
 */
private fun sampleDivingSpineInto(
    dive: DivingGeckoAnim,
    metrics: IsoMetrics,
    outPoints: MutableList<Offset>
) {
    outPoints.clear()
    val rail = dive.railPoints
    if (rail.size < 2) return

    // Ease-in acceleration as the gecko dives enthusiastically into the hole
    val eased = dive.progress * dive.progress * (2f - dive.progress)
    val maxIdx = rail.lastIndex.toFloat()
    // Tail travels from maxIdx -> 0f while front is clamped at 0f (inside the hole!)
    val tailPos = ((1f - eased) * maxIdx).coerceAtLeast(0f)
    if (tailPos <= 0.04f) return

    // Front enters index 0 (the hole center)
    val headPos = ((1f - eased * 3.8f) * 1.0f).coerceAtLeast(0f)
    outPoints.add(sampleRailPointScreen(rail, headPos, metrics))

    val firstInt = floor(headPos).toInt() + 1
    val lastInt = floor(tailPos).toInt()
    for (idx in firstInt..lastInt) {
        if (idx > headPos + 0.015f && idx < tailPos - 0.015f) {
            val p = rail[idx]
            outPoints.add(metrics.gridToScreen(p.y, p.x))
        }
    }

    outPoints.add(sampleRailPointScreen(rail, tailPos, metrics))
}

private fun sampleRailPointScreen(rail: List<Offset>, pos: Float, metrics: IsoMetrics): Offset {
    val clamped = pos.coerceIn(0f, rail.lastIndex.toFloat())
    val i0 = floor(clamped).toInt()
    val i1 = (i0 + 1).coerceAtMost(rail.lastIndex)
    val frac = clamped - i0
    val p0 = rail[i0]
    val p1 = rail[i1]
    return metrics.gridToScreen(
        r = p0.y + (p1.y - p0.y) * frac,
        c = p0.x + (p1.x - p0.x) * frac
    )
}

private fun hasAnyMovingTrack(
    board: BoardState,
    tracks: HashMap<String, GeckoRailTrack>
): Boolean {
    for (g in board.geckos) {
        if (g.escaped) continue
        val track = tracks[g.id] ?: return true
        if (track.lastSyncedSegments != g.segments || track.isMoving) return true
    }
    return false
}

private fun pickReachableDotCell(
    reachablePaths: Map<CellCoord, List<CellCoord>>,
    metrics: IsoMetrics,
    tapOffset: Offset,
    exactCell: CellCoord
): CellCoord? {
    if (reachablePaths.containsKey(exactCell)) return exactCell

    val maxTapDist = metrics.tileW * 0.48f
    var bestCell: CellCoord? = null
    var bestDist = Float.POSITIVE_INFINITY
    for (cell in reachablePaths.keys) {
        val pt = metrics.gridToScreen(cell.r.toFloat(), cell.c.toFloat())
        val dist = hypot(tapOffset.x - pt.x, tapOffset.y - pt.y)
        if (dist < maxTapDist && dist < bestDist) {
            bestDist = dist
            bestCell = cell
        }
    }
    return bestCell
}

private fun computeAverageScreenY(
    gecko: GeckoState,
    track: GeckoRailTrack?,
    metrics: IsoMetrics
): Float {
    if (track != null && track.rail.isNotEmpty()) {
        val start = floor(track.u).toInt().coerceIn(0, track.rail.lastIndex)
        val end = (start + track.segmentCount - 1).coerceAtMost(track.rail.lastIndex)
        var sum = 0f
        var count = 0
        for (i in start..end) {
            val p = track.rail[i]
            sum += metrics.gridToScreen(p.y, p.x).y
            count++
        }
        if (count > 0) return sum / count
    }
    if (gecko.segments.isEmpty()) return 0f
    var sum = 0f
    for (s in gecko.segments) {
        sum += metrics.gridToScreen(s.r.toFloat(), s.c.toFloat()).y
    }
    return sum / gecko.segments.size
}

private fun pickGeckoEnd(
    board: BoardState,
    metrics: IsoMetrics,
    touchOffset: Offset
): Triple<String, DragEnd, CellCoord>? {
    val maxHitDist = metrics.tileW * 0.95f
    var bestGeckoId: String? = null
    var bestEnd: DragEnd = DragEnd.HEAD
    var bestCell: CellCoord? = null
    var bestDist = Float.POSITIVE_INFINITY

    for (g in board.geckos) {
        if (g.escaped || g.segments.isEmpty()) continue
        val headCell = g.segments.first()
        val tailCell = g.segments.last()

        val headScreen = metrics.gridToScreen(headCell.r.toFloat(), headCell.c.toFloat())
        val tailScreen = metrics.gridToScreen(tailCell.r.toFloat(), tailCell.c.toFloat())

        val dHead = hypot(touchOffset.x - headScreen.x, touchOffset.y - headScreen.y)
        if (dHead < maxHitDist && dHead < bestDist) {
            bestDist = dHead
            bestGeckoId = g.id
            bestEnd = DragEnd.HEAD
            bestCell = headCell
        }

        val dTail = hypot(touchOffset.x - tailScreen.x, touchOffset.y - tailScreen.y)
        if (dTail < maxHitDist && dTail < bestDist) {
            bestDist = dTail
            bestGeckoId = g.id
            bestEnd = DragEnd.TAIL
            bestCell = tailCell
        }
    }

    if (bestGeckoId != null && bestCell != null) {
        return resolveMovableEnd(board, bestGeckoId, bestEnd, bestCell)
    }

    val touchedGrid = metrics.screenToGrid(touchOffset.x, touchOffset.y)
    for (g in board.geckos) {
        if (g.escaped) continue
        val idx = g.segments.indexOf(touchedGrid)
        if (idx >= 0) {
            val pickHead = idx <= g.segments.lastIndex / 2
            val end = if (pickHead) DragEnd.HEAD else DragEnd.TAIL
            val cell = if (pickHead) g.segments.first() else g.segments.last()
            return resolveMovableEnd(board, g.id, end, cell)
        }
    }
    return null
}

private fun resolveMovableEnd(
    board: BoardState,
    geckoId: String,
    preferredEnd: DragEnd,
    preferredCell: CellCoord
): Triple<String, DragEnd, CellCoord> {
    if (GeckoGameRules.getValidSteps(board, geckoId, preferredEnd).isNotEmpty()) {
        return Triple(geckoId, preferredEnd, preferredCell)
    }
    val otherEnd = if (preferredEnd == DragEnd.HEAD) DragEnd.TAIL else DragEnd.HEAD
    if (GeckoGameRules.getValidSteps(board, geckoId, otherEnd).isNotEmpty()) {
        val gecko = board.geckos.firstOrNull { it.id == geckoId }
        val otherCell = if (gecko != null) {
            if (otherEnd == DragEnd.HEAD) gecko.segments.first() else gecko.segments.last()
        } else {
            preferredCell
        }
        return Triple(geckoId, otherEnd, otherCell)
    }
    return Triple(geckoId, preferredEnd, preferredCell)
}

private fun DrawScope.drawPrecomputedWall(
    wall: PrecomputedWall,
    useGradients: Boolean
) {
    if (useGradients) {
        drawPath(path = wall.facePath, brush = wall.gradientBrush)
    } else {
        drawPath(path = wall.facePath, color = WallBodyFlatColor)
    }

    drawLine(
        color = WallShadowColor,
        start = Offset(wall.pStart.x, wall.pStart.y + 1.5f),
        end = Offset(wall.pEnd.x, wall.pEnd.y + 1.5f),
        strokeWidth = wall.wallStroke * 0.7f,
        cap = StrokeCap.Round
    )
    drawLine(
        color = WallTopRailColor,
        start = Offset(wall.pStart.x, wall.pStart.y - wall.wallHeight),
        end = Offset(wall.pEnd.x, wall.pEnd.y - wall.wallHeight),
        strokeWidth = wall.wallStroke,
        cap = StrokeCap.Round
    )
    if (useGradients) {
        drawLine(
            color = WallTopHighlightColor,
            start = Offset(wall.pStart.x, wall.pStart.y - wall.wallHeight - 1f),
            end = Offset(wall.pEnd.x, wall.pEnd.y - wall.wallHeight - 1f),
            strokeWidth = wall.wallStroke * 0.35f,
            cap = StrokeCap.Round
        )
    }
}

private fun DrawScope.drawIsoHole(
    metrics: IsoMetrics,
    hole: HoleState,
    animTimeSec: Float,
    perfConfig: GraphicsPerformanceConfig
) {
    val center = metrics.gridToScreen(hole.coord.r.toFloat(), hole.coord.c.toFloat())
    val rx = metrics.tileW * 0.28f
    val ry = metrics.tileH * 0.30f
    val rimColor = Color(hole.color.holeRimHex)
    val bodyColor = Color(hole.color.bodyHex)

    if (hole.sealed) {
        drawOval(
            color = HoleSealedBaseColor,
            topLeft = Offset(center.x - rx, center.y - ry),
            size = Size(rx * 2f, ry * 2f)
        )
        drawOval(
            color = bodyColor.copy(alpha = 0.45f),
            topLeft = Offset(center.x - rx * 0.88f, center.y - ry * 0.88f),
            size = Size(rx * 1.76f, ry * 1.76f),
            style = Stroke(width = 3f)
        )
        drawOval(
            color = bodyColor.copy(alpha = 0.25f),
            topLeft = Offset(center.x - rx * 0.42f, center.y - ry * 0.42f),
            size = Size(rx * 0.84f, ry * 0.84f)
        )
        return
    }

    val pulse = if (perfConfig.enableIdleAnimations) (sin(animTimeSec * 3.4f) * 0.5f + 0.5f) else 0.45f
    val glowScale = 1.15f + pulse * 0.14f
    drawOval(
        color = rimColor.copy(alpha = 0.22f + pulse * 0.12f),
        topLeft = Offset(center.x - rx * glowScale, center.y - ry * glowScale),
        size = Size(rx * 2f * glowScale, ry * 2f * glowScale)
    )

    drawOval(
        color = bodyColor,
        topLeft = Offset(center.x - rx * 1.08f, center.y - ry * 1.08f),
        size = Size(rx * 2.16f, ry * 2.16f)
    )
    drawOval(
        color = rimColor,
        topLeft = Offset(center.x - rx * 1.04f, center.y - ry * 1.08f),
        size = Size(rx * 2.08f, ry * 1.96f),
        style = Stroke(width = (metrics.tileW * 0.055f).coerceIn(2.5f, 5.5f))
    )

    drawOval(
        color = HoleInteriorDarkColor,
        topLeft = Offset(center.x - rx * 0.80f, center.y - ry * 0.78f),
        size = Size(rx * 1.60f, ry * 1.56f)
    )
    drawOval(
        color = bodyColor.copy(alpha = 0.28f),
        topLeft = Offset(center.x - rx * 0.62f, center.y - ry * 0.58f),
        size = Size(rx * 1.24f, ry * 1.16f),
        style = Stroke(width = 2.2f)
    )
}

/**
 * Renders crisp placeholder dots along straight-line moves until blocked by a wall,
 * another gecko, or a straight-ahead matching escape hole (no corner-turning spoilers).
 */
private fun DrawScope.drawReachablePlaceholderDots(
    metrics: IsoMetrics,
    board: BoardState,
    selected: ActiveDrag,
    reachablePaths: Map<CellCoord, List<CellCoord>>
) {
    val gecko = board.geckos.firstOrNull { it.id == selected.geckoId && !it.escaped } ?: return
    val accentColor = Color(gecko.color.bodyHex)
    val baseDotR = (metrics.tileW * 0.092f).coerceIn(3.8f, 8.2f)

    for ((cell, path) in reachablePaths) {
        val pt = metrics.gridToScreen(cell.r.toFloat(), cell.c.toFloat())
        val isMatchingHole = board.holes.any { !it.sealed && it.coord == cell && it.color == gecko.color }

        if (isMatchingHole) {
            drawCircle(
                color = Color.White.copy(alpha = 0.88f),
                radius = metrics.tileW * 0.25f,
                center = pt,
                style = Stroke(width = 3f)
            )
            drawCircle(
                color = accentColor,
                radius = baseDotR * 0.95f,
                center = pt
            )
        } else {
            val stepFade = (1f - (path.size - 1) * 0.06f).coerceIn(0.68f, 1f)
            val dotR = baseDotR * stepFade

            drawCircle(
                color = Color.White.copy(alpha = 0.82f * stepFade),
                radius = dotR * 1.18f,
                center = pt
            )
            drawCircle(
                color = accentColor.copy(alpha = 0.78f * stepFade),
                radius = dotR * 0.76f,
                center = pt
            )
        }
    }
}

private fun DrawScope.drawActiveWalkTrail(
    metrics: IsoMetrics,
    board: BoardState,
    plan: AutoWalkPlan,
    currentStepIdx: Int,
    scratch: GeckoDrawScratch
) {
    val gecko = board.geckos.firstOrNull { it.id == plan.geckoId && !it.escaped } ?: return
    if (currentStepIdx >= plan.steps.size) return

    val accentColor = Color(gecko.color.bodyHex)
    val goalCell = plan.steps.last()
    val goalPt = metrics.gridToScreen(goalCell.r.toFloat(), goalCell.c.toFloat())
    val dotR = (metrics.tileW * 0.085f).coerceIn(3.5f, 8f)

    // Subtle destination marker while gecko glides smoothly toward it
    drawCircle(
        color = Color.White.copy(alpha = 0.82f),
        radius = dotR * 1.65f,
        center = goalPt,
        style = Stroke(width = 2.6f)
    )
    drawCircle(
        color = accentColor.copy(alpha = 0.85f),
        radius = dotR * 0.95f,
        center = goalPt
    )
}

private fun DrawScope.drawHintOverlay(
    metrics: IsoMetrics,
    board: BoardState,
    hint: HintMove,
    animTimeSec: Float,
    scratch: GeckoDrawScratch
) {
    val gecko = board.geckos.firstOrNull { it.id == hint.geckoId && !it.escaped } ?: return
    val startCoord = if (hint.end == DragEnd.HEAD) gecko.segments.first() else gecko.segments.last()
    val startPt = metrics.gridToScreen(startCoord.r.toFloat(), startCoord.c.toFloat())

    val pulse = sin(animTimeSec * 5.5f) * 0.5f + 0.5f

    val ringR = metrics.tileW * (0.28f + pulse * 0.08f)
    drawCircle(
        color = HintYellowColor.copy(alpha = 0.88f),
        radius = ringR,
        center = startPt,
        style = Stroke(width = 4.5f)
    )

    val previewCount = minOf(hint.previewPath.size, 3)
    scratch.hintPath.reset()
    scratch.hintPath.moveTo(startPt.x, startPt.y)
    for (i in 0 until previewCount) {
        val coord = hint.previewPath[i]
        val pt = metrics.gridToScreen(coord.r.toFloat(), coord.c.toFloat())
        scratch.hintPath.lineTo(pt.x, pt.y)
    }
    drawPath(
        path = scratch.hintPath,
        color = HintYellowColor.copy(alpha = 0.50f),
        style = Stroke(width = 6f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )

    for (idx in 0 until previewCount) {
        val coord = hint.previewPath[idx]
        val pt = metrics.gridToScreen(coord.r.toFloat(), coord.c.toFloat())
        val wave = sin(animTimeSec * 6.5f - idx * 0.7f) * 0.5f + 0.5f
        val dotR = metrics.tileW * (0.085f + wave * 0.035f)
        drawCircle(
            color = Color.White.copy(alpha = 0.9f),
            radius = dotR,
            center = pt
        )
        drawCircle(
            color = HintYellowColor,
            radius = dotR * 0.65f,
            center = pt
        )
    }
}

private fun DrawScope.drawGeckoSpline(
    metrics: IsoMetrics,
    geckoId: String,
    geckoColor: GeckoColor,
    screenPoints: List<Offset>,
    animTimeSec: Float,
    draggedEnd: DragEnd?,
    scaleFactor: Float,
    stridePhase: Float,
    motionIntensity: Float,
    trackForHeadSmoothing: GeckoRailTrack?,
    perfConfig: GraphicsPerformanceConfig,
    scratch: GeckoDrawScratch,
    drawHead: Boolean = true
) {
    if (screenPoints.size < 2) return

    val bodyColor = Color(geckoColor.bodyHex)
    val spotColor = Color(geckoColor.spotHex)
    val outlineColor = Color(
        red = (bodyColor.red * 0.62f).coerceIn(0f, 1f),
        green = (bodyColor.green * 0.62f).coerceIn(0f, 1f),
        blue = (bodyColor.blue * 0.62f).coerceIn(0f, 1f),
        alpha = 1f
    )

    val bodyWidth = (metrics.tileW * 0.36f * scaleFactor).coerceIn(10f, 44f)
    buildFilletedPathInto(scratch.bodyPath, screenPoints, filletFraction = 0.36f)

    // Layer 1: Soft ground drop shadow (Standard mode)
    if (!perfConfig.lowRamMode) {
        val shadowOffsetY = (metrics.tileH * 0.14f).coerceIn(4f, 10f)
        scratch.shadowPoints.clear()
        for (i in screenPoints.indices) {
            val pt = screenPoints[i]
            scratch.shadowPoints.add(Offset(pt.x, pt.y + shadowOffsetY))
        }
        buildFilletedPathInto(scratch.shadowPath, scratch.shadowPoints, filletFraction = 0.36f)
        drawPath(
            path = scratch.shadowPath,
            color = GeckoShadowColor,
            style = Stroke(width = bodyWidth * 1.06f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }

    // Animated diagonal-couplet lizard feet near shoulder (~22%) & hip (~74%) along spine arc
    if (scaleFactor > 0.5f && screenPoints.size >= 2) {
        val shoulder = samplePolylinePointAndTangent(screenPoints, 0.22f)
        drawGeckoFeet(
            joint = shoulder.first,
            tangent = shoulder.second,
            bodyWidth = bodyWidth,
            outlineColor = outlineColor,
            bodyColor = bodyColor,
            strideOffset = sin(stridePhase) * motionIntensity,
            isFrontPair = true
        )
        val hip = samplePolylinePointAndTangent(screenPoints, 0.74f)
        drawGeckoFeet(
            joint = hip.first,
            tangent = hip.second,
            bodyWidth = bodyWidth * 0.90f,
            outlineColor = outlineColor,
            bodyColor = bodyColor,
            strideOffset = -sin(stridePhase) * motionIntensity,
            isFrontPair = false
        )
    }

    // Layer 2: Darker 3D body outline / contour
    drawPath(
        path = scratch.bodyPath,
        color = outlineColor,
        style = Stroke(width = bodyWidth * 1.12f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )

    // Layer 3: Plump main body color
    drawPath(
        path = scratch.bodyPath,
        color = bodyColor,
        style = Stroke(width = bodyWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )

    // Layer 4: Playful dorsal spots evenly spaced along arc length (never jittering during slides!)
    val spotCount = (screenPoints.size - 1).coerceIn(1, 7)
    for (i in 1..spotCount) {
        val frac = (i.toFloat() / (spotCount + 1).toFloat()).coerceIn(0.12f, 0.88f)
        val pt = samplePolylinePointAndTangent(screenPoints, frac).first
        val taper = 1f - frac * 0.30f
        val spotR = bodyWidth * 0.21f * taper
        drawCircle(
            color = spotColor.copy(alpha = 0.84f),
            radius = spotR,
            center = Offset(pt.x, pt.y - bodyWidth * 0.05f)
        )
    }

    // Layer 5: Glossy specular ridge highlight (Standard graphics only)
    if (perfConfig.enableSpecularHighlights) {
        scratch.highlightPoints.clear()
        val ridgeShift = bodyWidth * 0.20f
        for (i in screenPoints.indices) {
            val pt = screenPoints[i]
            scratch.highlightPoints.add(Offset(pt.x, pt.y - ridgeShift))
        }
        buildFilletedPathInto(scratch.highlightPath, scratch.highlightPoints, filletFraction = 0.36f)
        drawPath(
            path = scratch.highlightPath,
            color = Color.White.copy(alpha = 0.30f),
            style = Stroke(width = bodyWidth * 0.22f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }

    // Subtle tapered tail tip accent & grab halo
    val tailPt = screenPoints.last()
    drawCircle(
        color = spotColor.copy(alpha = 0.9f),
        radius = bodyWidth * 0.19f,
        center = tailPt
    )
    if (draggedEnd == DragEnd.TAIL) {
        drawCircle(
            color = Color.White.copy(alpha = 0.88f),
            radius = bodyWidth * 0.72f,
            center = tailPt,
            style = Stroke(width = 3.5f)
        )
    }

    if (!drawHead) return

    // Layer 6: Expressive Gecko Head with smoothly damped angular turn!
    val headPt = screenPoints[0]
    val neckPt = samplePolylinePointAndTangent(screenPoints, 0.14f).first
    val dirX = headPt.x - neckPt.x
    val dirY = headPt.y - neckPt.y
    val rawAngleDeg = if (hypot(dirX, dirY) > 0.5f) {
        (atan2(dirY.toDouble(), dirX.toDouble()) * 180.0 / PI).toFloat()
    } else {
        val fallbackX = headPt.x - screenPoints[1].x
        val fallbackY = headPt.y - screenPoints[1].y
        (atan2(fallbackY.toDouble(), fallbackX.toDouble()) * 180.0 / PI).toFloat()
    }

    val angleDeg = if (trackForHeadSmoothing != null) {
        val prev = trackForHeadSmoothing.smoothedHeadAngleDeg
        val smoothed = if (prev == null) {
            rawAngleDeg
        } else {
            val delta = shortestAngleDeltaDeg(prev, rawAngleDeg)
            prev + delta * 0.28f
        }
        trackForHeadSmoothing.smoothedHeadAngleDeg = smoothed
        smoothed
    } else {
        rawAngleDeg
    }

    if (draggedEnd == DragEnd.HEAD) {
        drawCircle(
            color = Color.White.copy(alpha = 0.88f),
            radius = bodyWidth * 0.84f,
            center = headPt,
            style = Stroke(width = 3.5f)
        )
    }

    // Subtle organic breathing scale at rest
    val breath = if (perfConfig.enableIdleAnimations) 1f + sin(animTimeSec * 2.4f + (geckoId.hashCode() and 7)) * 0.022f else 1f

    rotate(degrees = angleDeg, pivot = headPt) {
        val headRx = bodyWidth * 0.66f * breath
        val headRy = bodyWidth * 0.58f * breath

        // Periodic playful pink tongue flick (Standard mode)
        if (perfConfig.enableIdleAnimations && scaleFactor > 0.6f) {
            val tongueCycle = (animTimeSec * 1.15f + (geckoId.hashCode() and 7) * 0.45f) % 4.2f
            if (tongueCycle < 0.34f) {
                val extend = sin((tongueCycle / 0.34f) * PI).toFloat()
                val tongueLen = headRx * (0.55f + extend * 0.55f)
                val tipX = headPt.x + headRx * 0.8f + tongueLen
                drawLine(
                    color = Color(0xFFFF5C8A),
                    start = Offset(headPt.x + headRx * 0.8f, headPt.y),
                    end = Offset(tipX, headPt.y),
                    strokeWidth = (bodyWidth * 0.14f).coerceAtLeast(2.5f),
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = Color(0xFFFF5C8A),
                    start = Offset(tipX, headPt.y),
                    end = Offset(tipX + 4f, headPt.y - 3f),
                    strokeWidth = 2f,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = Color(0xFFFF5C8A),
                    start = Offset(tipX, headPt.y),
                    end = Offset(tipX + 4f, headPt.y + 3f),
                    strokeWidth = 2f,
                    cap = StrokeCap.Round
                )
            }
        }

        // Rounded snout outline & fill
        drawOval(
            color = outlineColor,
            topLeft = Offset(headPt.x - headRx * 0.96f, headPt.y - headRy * 1.05f),
            size = Size(headRx * 2.04f, headRy * 2.10f)
        )
        drawOval(
            color = bodyColor,
            topLeft = Offset(headPt.x - headRx * 0.88f, headPt.y - headRy * 0.95f),
            size = Size(headRx * 1.90f, headRy * 1.90f)
        )

        // Friendly cartoon eyes + pupils looking slightly into turns
        val eyeOffsetX = headRx * 0.18f
        val eyeOffsetY = headRy * 0.56f
        val eyeR = bodyWidth * 0.25f

        for (side in TWO_SIDES) {
            val eyeCenter = Offset(headPt.x + eyeOffsetX, headPt.y + side * eyeOffsetY)
            drawCircle(color = outlineColor, radius = eyeR * 1.16f, center = eyeCenter)
            drawCircle(color = Color.White, radius = eyeR, center = eyeCenter)
            val pupilCenter = Offset(eyeCenter.x + eyeR * 0.24f, eyeCenter.y)
            drawCircle(color = Color(0xFF0E1726), radius = eyeR * 0.55f, center = pupilCenter)
            if (!perfConfig.lowRamMode) {
                drawCircle(
                    color = Color.White,
                    radius = eyeR * 0.22f,
                    center = Offset(pupilCenter.x + eyeR * 0.16f, pupilCenter.y - eyeR * 0.20f)
                )
            }
        }
    }
}

private fun shortestAngleDeltaDeg(fromDeg: Float, toDeg: Float): Float {
    var diff = (toDeg - fromDeg) % 360f
    if (diff > 180f) diff -= 360f
    if (diff < -180f) diff += 360f
    return diff
}

/**
 * Computes the exact arc-length interpolated point and unit forward tangent (pointing toward head)
 * at normalized distance [fraction] in `[0f, 1f]` along [points] (0 = head, 1 = tail).
 */
private fun samplePolylinePointAndTangent(
    points: List<Offset>,
    fraction: Float
): Pair<Offset, Offset> {
    if (points.size < 2) {
        val p = points.firstOrNull() ?: Offset.Zero
        return p to Offset(1f, 0f)
    }
    var totalLen = 0f
    for (i in 1 until points.size) {
        totalLen += hypot(points[i].x - points[i - 1].x, points[i].y - points[i - 1].y)
    }
    if (totalLen < 0.001f) {
        return points[0] to Offset(1f, 0f)
    }

    val targetDist = (fraction.coerceIn(0f, 1f)) * totalLen
    var accum = 0f
    for (i in 1 until points.size) {
        val a = points[i - 1]
        val b = points[i]
        val segLen = hypot(b.x - a.x, b.y - a.y)
        if (accum + segLen >= targetDist && segLen > 0.001f) {
            val localT = ((targetDist - accum) / segLen).coerceIn(0f, 1f)
            val pt = Offset(a.x + (b.x - a.x) * localT, a.y + (b.y - a.y) * localT)
            // Forward tangent points toward head (from b -> a)
            val tx = (a.x - b.x) / segLen
            val ty = (a.y - b.y) / segLen
            return pt to Offset(tx, ty)
        }
        accum += segLen
    }
    val last = points.last()
    val prev = points[points.lastIndex - 1]
    val len = hypot(prev.x - last.x, prev.y - last.y).coerceAtLeast(1f)
    return last to Offset((prev.x - last.x) / len, (prev.y - last.y) / len)
}

/**
 * Draws a pair of cute lizard feet with realistic alternating forward/backward stride motion
 * driven by cumulative distance traveled.
 */
private fun DrawScope.drawGeckoFeet(
    joint: Offset,
    tangent: Offset,
    bodyWidth: Float,
    outlineColor: Color,
    bodyColor: Color,
    strideOffset: Float,
    isFrontPair: Boolean
) {
    val tx = tangent.x
    val ty = tangent.y
    val nx = -ty
    val ny = tx
    val footDist = bodyWidth * 0.56f
    val footR = bodyWidth * 0.21f
    val strideAmp = bodyWidth * 0.24f
    val restSweep = if (isFrontPair) bodyWidth * 0.08f else -bodyWidth * 0.08f

    for (sign in TWO_SIDES) {
        // Opposite legs swing in opposite phase (true diagonal-couplet lizard gait!)
        val swing = restSweep + strideOffset * strideAmp * sign
        val fx = joint.x + nx * footDist * sign + tx * swing
        val fy = joint.y + ny * footDist * sign + ty * swing
        drawCircle(color = outlineColor, radius = footR * 1.18f, center = Offset(fx, fy))
        drawCircle(color = bodyColor, radius = footR, center = Offset(fx, fy))
    }
}

/**
 * Corner-Filleted Quadratic Bezier Spline populated directly into a reusable [Path] (zero alloc).
 */
private fun buildFilletedPathInto(
    targetPath: Path,
    points: List<Offset>,
    filletFraction: Float
) {
    targetPath.reset()
    if (points.isEmpty()) return
    targetPath.moveTo(points[0].x, points[0].y)
    if (points.size == 2) {
        targetPath.lineTo(points[1].x, points[1].y)
        return
    }
    for (i in 1 until points.lastIndex) {
        val prev = points[i - 1]
        val curr = points[i]
        val next = points[i + 1]

        val inX = curr.x + (prev.x - curr.x) * filletFraction
        val inY = curr.y + (prev.y - curr.y) * filletFraction
        val outX = curr.x + (next.x - curr.x) * filletFraction
        val outY = curr.y + (next.y - curr.y) * filletFraction

        targetPath.lineTo(inX, inY)
        targetPath.quadraticTo(curr.x, curr.y, outX, outY)
    }
    val last = points[points.lastIndex]
    targetPath.lineTo(last.x, last.y)
}
