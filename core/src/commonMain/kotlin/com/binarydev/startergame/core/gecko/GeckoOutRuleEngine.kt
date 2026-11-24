package com.binarydev.startergame.core.gecko

import com.binarydev.startergame.core.achievement.AchievementCriterion
import com.binarydev.startergame.core.achievement.AchievementDefinition
import com.binarydev.startergame.core.engine.HintApplicationResult
import com.binarydev.startergame.core.engine.LevelOutcome
import com.binarydev.startergame.core.engine.LevelRuleEngine
import com.binarydev.startergame.core.model.GameMetadataAndFeatures
import com.binarydev.startergame.core.model.RewardBundle
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class GeckoGameState(
    val levelId: String,
    val levelNumber: Int,
    val levelName: String,
    val levelSubtitle: String,
    val par: Int,
    val attemptId: String,
    val board: BoardState,
    val undoStack: List<BoardState> = emptyList(),
    val activeHint: HintMove? = null,
    val hintsUsed: Int = 0,
    val lastBlockedReason: BlockedReason? = null,
    val lastEscapedGeckoId: String? = null,
    val lastEscapedHoleCoord: CellCoord? = null,
    val lastEscapedColor: GeckoColor? = null,
    val lastFeedback: String? = null,
    val eventSeq: Int = 0
) {
    val isSolved: Boolean get() = board.isSolved
    val canUndo: Boolean get() = undoStack.isNotEmpty() && !isSolved
    val earnedStars: Int get() = calculateStars(board.moves.coerceAtLeast(1), par)
}

@Serializable
sealed class GeckoMove {
    @Serializable
    data class DragStep(
        val geckoId: String,
        val end: DragEnd,
        val target: CellCoord,
        val pushUndoSnapshot: Boolean = true
    ) : GeckoMove()

    @Serializable
    data object Undo : GeckoMove()

    @Serializable
    data object Restart : GeckoMove()

    @Serializable
    data object RequestSolverHint : GeckoMove()

    @Serializable
    data object ClearTransientFeedback : GeckoMove()
}

class GeckoOutRuleEngine(
    override val metadata: GameMetadataAndFeatures = defaultMetadata(),
    override val levels: List<LevelDef> = GeckoLevels.ALL,
    override val achievements: List<AchievementDefinition> = defaultAchievements()
) : LevelRuleEngine<LevelDef, GeckoGameState, GeckoMove> {

    private val json = Json { ignoreUnknownKeys = true }
    private val maxUndoDepth = 120

    override fun getLevelId(level: LevelDef): String = level.stringId

    override fun createInitialState(level: LevelDef, attemptId: String): GeckoGameState = GeckoGameState(
        levelId = level.stringId,
        levelNumber = level.id,
        levelName = level.name,
        levelSubtitle = level.subtitle,
        par = level.par,
        attemptId = attemptId,
        board = GeckoGameRules.createBoardState(level),
        undoStack = emptyList(),
        activeHint = null,
        hintsUsed = 0,
        lastBlockedReason = null,
        lastEscapedGeckoId = null,
        lastEscapedHoleCoord = null,
        lastEscapedColor = null,
        lastFeedback = null,
        eventSeq = 0
    )

    override fun getAttemptId(state: GeckoGameState): String = state.attemptId

    override fun getHintsUsedCount(state: GeckoGameState): Int = state.hintsUsed

    override fun applyMove(state: GeckoGameState, move: GeckoMove): GeckoGameState {
        return when (move) {
            is GeckoMove.ClearTransientFeedback -> state.copy(
                lastBlockedReason = null,
                lastFeedback = null
            )

            is GeckoMove.Restart -> {
                val level = levels.firstOrNull { it.stringId == state.levelId } ?: return state
                createInitialState(level, state.attemptId).copy(
                    hintsUsed = state.hintsUsed,
                    eventSeq = state.eventSeq + 1,
                    lastFeedback = "Papan direset ke posisi awal."
                )
            }

            is GeckoMove.Undo -> {
                if (state.undoStack.isEmpty() || state.isSolved) return state
                val previousBoard = state.undoStack.last()
                state.copy(
                    board = previousBoard,
                    undoStack = state.undoStack.dropLast(1),
                    activeHint = null,
                    lastBlockedReason = null,
                    lastEscapedGeckoId = null,
                    lastEscapedHoleCoord = null,
                    lastEscapedColor = null,
                    lastFeedback = "Langkah dikembalikan.",
                    eventSeq = state.eventSeq + 1
                )
            }

            is GeckoMove.RequestSolverHint -> {
                if (state.isSolved) return state
                val hint = GeckoPuzzleSolver.computeHint(state.board)
                if (hint == null) {
                    state.copy(
                        activeHint = null,
                        lastBlockedReason = null,
                        lastFeedback = "Posisi saat ini buntu — gunakan Undo atau Restart.",
                        eventSeq = state.eventSeq + 1
                    )
                } else {
                    val gecko = state.board.geckos.firstOrNull { it.id == hint.geckoId }
                    val endLabel = if (hint.end == DragEnd.HEAD) "kepala" else "ekor"
                    val colorLabel = gecko?.color?.displayName ?: "Gecko"
                    state.copy(
                        activeHint = hint,
                        hintsUsed = state.hintsUsed + 1,
                        lastBlockedReason = null,
                        lastFeedback = "Petunjuk: Tarik $endLabel Gecko $colorLabel mengikuti panah bercahaya!",
                        eventSeq = state.eventSeq + 1
                    )
                }
            }

            is GeckoMove.DragStep -> {
                if (state.isSolved) return state
                val result = GeckoGameRules.stepGeckoToward(
                    state = state.board,
                    geckoId = move.geckoId,
                    end = move.end,
                    targetCell = move.target
                )
                if (!result.moved) {
                    val feedback = when (result.blockedReason) {
                        BlockedReason.TAIL_ON_HOLE -> "Hanya kepala gecko yang bisa masuk ke lubang!"
                        BlockedReason.WRONG_HOLE -> "Lubang warna lain belum tersegel — tidak bisa dilewati!"
                        else -> null
                    }
                    return if (feedback != null) {
                        state.copy(
                            lastBlockedReason = result.blockedReason,
                            lastFeedback = feedback,
                            eventSeq = state.eventSeq + 1
                        )
                    } else {
                        state
                    }
                }

                val nextUndoStack = if (move.pushUndoSnapshot) {
                    val appended = state.undoStack + state.board
                    if (appended.size > maxUndoDepth) appended.drop(appended.size - maxUndoDepth) else appended
                } else {
                    state.undoStack
                }

                val escapedHole = result.escapedHole
                val escapedColor = escapedHole?.color
                val feedback = if (escapedColor != null) {
                    "Gecko ${escapedColor.displayName} berhasil keluar! Lubang tersegel."
                } else {
                    null
                }

                state.copy(
                    board = result.state,
                    undoStack = nextUndoStack,
                    activeHint = null,
                    lastBlockedReason = null,
                    lastEscapedGeckoId = result.escapedGeckoId,
                    lastEscapedHoleCoord = escapedHole?.coord,
                    lastEscapedColor = escapedColor,
                    lastFeedback = feedback,
                    eventSeq = state.eventSeq + 1
                )
            }
        }
    }

    override fun applyHint(state: GeckoGameState): HintApplicationResult<GeckoGameState> {
        if (state.isSolved) {
            return HintApplicationResult.NotApplicable("Level sudah selesai.")
        }
        val hint = GeckoPuzzleSolver.computeHint(state.board)
            ?: return HintApplicationResult.NotApplicable("Posisi saat ini buntu — gunakan Undo atau Restart.")

        val gecko = state.board.geckos.firstOrNull { it.id == hint.geckoId }
        val endLabel = if (hint.end == DragEnd.HEAD) "kepala" else "ekor"
        val colorLabel = gecko?.color?.displayName ?: "Gecko"
        val description = "Petunjuk: Tarik $endLabel Gecko $colorLabel mengikuti jalur bercahaya."
        val updated = state.copy(
            activeHint = hint,
            hintsUsed = state.hintsUsed + 1,
            lastBlockedReason = null,
            lastFeedback = description,
            eventSeq = state.eventSeq + 1
        )
        return HintApplicationResult.Applied(updated, description)
    }

    override fun evaluateOutcome(state: GeckoGameState): LevelOutcome {
        if (!state.isSolved) return LevelOutcome.InProgress
        val stars = calculateStars(state.board.moves.coerceAtLeast(1), state.par)
        val efficiencyBonus = maxOf(0, (state.par * 2 - state.board.moves) * 50)
        val score = (stars * 1000) + efficiencyBonus
        return LevelOutcome.Won(
            score = score,
            validatorPayload = mapOf(
                "levelId" to state.levelId,
                "attemptId" to state.attemptId,
                "moves" to state.board.moves.toString(),
                "par" to state.par.toString(),
                "stars" to stars.toString(),
                "hintsUsed" to state.hintsUsed.toString()
            )
        )
    }

    override fun serializeCheckpoint(state: GeckoGameState): String = json.encodeToString(state)

    override fun deserializeCheckpoint(level: LevelDef, json: String): GeckoGameState =
        this.json.decodeFromString<GeckoGameState>(json)

    companion object {
        fun defaultMetadata(): GameMetadataAndFeatures = GameMetadataAndFeatures(
            gameId = "gecko-out-v1",
            title = "Gecko Out",
            hintsEnabled = true,
            livesEnabled = false,
            initialHints = 5,
            initialLives = 5,
            maxLives = 5,
            levelCompletionReward = RewardBundle(hints = 1, lives = 0),
            rewardedAdReward = RewardBundle(hints = 2, lives = 0),
            privacyPolicyUrl = "https://example.com/privacy"
        )

        fun defaultAchievements(): List<AchievementDefinition> = listOf(
            AchievementDefinition(
                id = "ach_first_escape",
                title = "Pelarian Pertama",
                description = "Selesaikan Level 1 (First Try?) dengan selamat.",
                targetValue = 1,
                criterion = AchievementCriterion.CompleteFirstLevel,
                reward = RewardBundle(hints = 1, lives = 0)
            ),
            AchievementDefinition(
                id = "ach_no_hint_master",
                title = "Insting Alami",
                description = "Selesaikan satu level tanpa menggunakan Hint.",
                targetValue = 1,
                criterion = AchievementCriterion.CompleteLevelWithoutHints,
                reward = RewardBundle(hints = 2, lives = 0)
            ),
            AchievementDefinition(
                id = "ach_five_levels",
                title = "Penjelajah Terarium",
                description = "Selesaikan 5 level Gecko Out.",
                targetValue = 5,
                criterion = AchievementCriterion.CompleteLevelsCount(5),
                reward = RewardBundle(hints = 2, lives = 0)
            ),
            AchievementDefinition(
                id = "ach_all_ten",
                title = "Master Gecko Out",
                description = "Taklukkan seluruh 10 level Gecko Out!",
                targetValue = 10,
                criterion = AchievementCriterion.CompleteLevelsCount(10),
                reward = RewardBundle(hints = 5, lives = 0)
            )
        )
    }
}
