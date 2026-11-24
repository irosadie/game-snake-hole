package com.binarydev.startergame.core.gecko

import kotlin.math.abs
import kotlin.math.ceil
import kotlinx.serialization.Serializable

@Serializable
enum class GeckoColor(
    val bodyHex: Long,
    val spotHex: Long,
    val holeRimHex: Long,
    val displayName: String
) {
    PURPLE(0xFFB974F8, 0xFFDFB6FF, 0xFFC989FF, "Ungu"),
    ORANGE(0xFFF98324, 0xFFFFC15E, 0xFFFF9A3C, "Oranye"),
    YELLOW(0xFFFAD321, 0xFFFFF38A, 0xFFFFE047, "Kuning"),
    EMERALD(0xFF30CF8E, 0xFF8BF3C4, 0xFF4BE3A4, "Hijau"),
    CORAL(0xFFFB5C79, 0xFFFFA1B3, 0xFFFF758F, "Koral"),
    SKY(0xFF35BDF5, 0xFF97E2FF, 0xFF59CEFF, "Biru")
}

@Serializable
enum class DragEnd {
    HEAD,
    TAIL
}

@Serializable
enum class BlockedReason {
    INVALID_CELL,
    NOT_ADJACENT,
    WALL,
    OCCUPIED,
    WRONG_HOLE,
    TAIL_ON_HOLE,
    ALREADY_ESCAPED
}

@Serializable
data class CellCoord(val r: Int, val c: Int) {
    fun manhattan(other: CellCoord): Int = abs(r - other.r) + abs(c - other.c)
}

/**
 * Dinding sekat tipis selalu dinormalisasi agar WallEdge(A, B) == WallEdge(B, A).
 */
@ConsistentCopyVisibility
@Serializable
data class WallEdge internal constructor(val a: CellCoord, val b: CellCoord) {
    companion object {
        fun of(r1: Int, c1: Int, r2: Int, c2: Int): WallEdge {
            val p1 = CellCoord(r1, c1)
            val p2 = CellCoord(r2, c2)
            return if (r1 < r2 || (r1 == r2 && c1 < c2)) WallEdge(p1, p2) else WallEdge(p2, p1)
        }

        fun of(p1: CellCoord, p2: CellCoord): WallEdge = of(p1.r, p1.c, p2.r, p2.c)
    }
}

@Serializable
data class HoleDef(
    val id: String,
    val color: GeckoColor,
    val coord: CellCoord
)

@Serializable
data class HoleState(
    val id: String,
    val color: GeckoColor,
    val coord: CellCoord,
    val sealed: Boolean = false
)

/** segments[0] = HEAD, segments.last() = TAIL */
@Serializable
data class GeckoDef(
    val id: String,
    val color: GeckoColor,
    val segments: List<CellCoord>
)

@Serializable
data class GeckoState(
    val id: String,
    val color: GeckoColor,
    val segments: List<CellCoord>,
    val escaped: Boolean = false
)

@Serializable
data class LevelDef(
    val id: Int,
    val name: String,
    val subtitle: String,
    val rows: Int,
    val cols: Int,
    val cells: Set<CellCoord>,
    val walls: Set<WallEdge>,
    val holes: List<HoleDef>,
    val geckos: List<GeckoDef>,
    val par: Int
) {
    val stringId: String get() = "level-$id"
}

@Serializable
data class BoardState(
    val levelId: Int,
    val rows: Int,
    val cols: Int,
    val cells: Set<CellCoord>,
    val walls: Set<WallEdge>,
    val holes: List<HoleState>,
    val geckos: List<GeckoState>,
    val moves: Int = 0,
    val lastMovedGeckoId: String? = null
) {
    val isSolved: Boolean get() = geckos.all { it.escaped }
    val remainingGeckoCount: Int get() = geckos.count { !it.escaped }
}

data class StepResult(
    val moved: Boolean,
    val state: BoardState,
    val escapedGeckoId: String? = null,
    val escapedHole: HoleState? = null,
    val blockedReason: BlockedReason? = null
)

data class LevelValidationResult(
    val valid: Boolean,
    val errors: List<String>
)

@Serializable
data class SolverStep(
    val geckoId: String,
    val end: DragEnd,
    val target: CellCoord
)

@Serializable
data class HintMove(
    val geckoId: String,
    val end: DragEnd,
    val previewPath: List<CellCoord>
)

data class SolveResult(
    val solvable: Boolean,
    val steps: List<SolverStep>,
    val turns: Int
)

fun calculateStars(moves: Int, par: Int): Int = when {
    moves <= par -> 3
    moves <= par + maxOf(2, ceil(par * 0.4).toInt()) -> 2
    else -> 1
}
