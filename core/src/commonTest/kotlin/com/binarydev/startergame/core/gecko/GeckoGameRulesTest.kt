package com.binarydev.startergame.core.gecko

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GeckoGameRulesTest {

    @Test
    fun test1_validateAllTenLevelDefsPassStructuralChecks() {
        assertEquals(10, GeckoLevels.ALL.size, "Harus ada tepat 10 level Gecko Out")
        for (level in GeckoLevels.ALL) {
            val validation = GeckoGameRules.validateLevelDef(level)
            assertTrue(
                validation.valid,
                "Level ${level.id} (${level.name}) gagal validasi: ${validation.errors.joinToString("; ")}"
            )
        }
    }

    @Test
    fun test2_weightedAStarSolvesAndReplaysAllTenLevelsToVictory() {
        for (level in GeckoLevels.ALL) {
            val initial = GeckoGameRules.createBoardState(level)
            val solveResult = GeckoPuzzleSolver.solve(initial, maxExpansions = 35_000, weight = 2.5)
            assertTrue(
                solveResult.solvable,
                "Level ${level.id} (${level.name}) tidak ditemukan solusinya oleh Weighted A* Solver!"
            )
            assertTrue(
                solveResult.steps.isNotEmpty(),
                "Level ${level.id} (${level.name}) tidak boleh kosong langkah solusinya"
            )

            // Replay step-by-step untuk memastikan setiap langkah sah dan mencapai 100% victory
            var current = initial
            for ((idx, step) in solveResult.steps.withIndex()) {
                val res = GeckoGameRules.stepGecko(current, step.geckoId, step.end, step.target)
                assertTrue(
                    res.moved,
                    "Level ${level.id} gagal replay pada langkah #$idx ($step): ${res.blockedReason}"
                )
                current = res.state
            }
            assertTrue(
                current.geckos.all { it.escaped },
                "Level ${level.id} (${level.name}) tidak semua gecko keluar setelah replay!"
            )
            assertTrue(
                current.holes.all { it.sealed },
                "Level ${level.id} (${level.name}) semua lubang harus tersegel setelah selesai!"
            )
        }
    }

    @Test
    fun test3_headFirstEscapeAndWrongHoleRulesAreStrictlyEnforced() {
        // Papan mini 1x5: [P_HEAD(0,1), P_TAIL(0,2)], lubang Ungu di (0,3) dan lubang Oranye di (0,0)
        val level = LevelDef(
            id = 99,
            name = "Rule Probe",
            subtitle = "Test",
            rows = 1,
            cols = 5,
            cells = (0..4).map { CellCoord(0, it) }.toSet(),
            walls = emptySet(),
            holes = listOf(
                HoleDef("h-orange", GeckoColor.ORANGE, CellCoord(0, 0)),
                HoleDef("h-purple", GeckoColor.PURPLE, CellCoord(0, 3))
            ),
            geckos = listOf(
                GeckoDef(
                    id = "g-p",
                    color = GeckoColor.PURPLE,
                    segments = listOf(CellCoord(0, 1), CellCoord(0, 2))
                )
            ),
            par = 1
        )
        val board = GeckoGameRules.createBoardState(level)

        // 1. Menarik EKOR ke lubang warna sama -> TAIL_ON_HOLE
        val tailOnHole = GeckoGameRules.stepGecko(board, "g-p", DragEnd.TAIL, CellCoord(0, 3))
        assertFalse(tailOnHole.moved)
        assertEquals(BlockedReason.TAIL_ON_HOLE, tailOnHole.blockedReason)

        // 2. Menarik KEPALA ke lubang warna lain -> WRONG_HOLE
        val wrongHole = GeckoGameRules.stepGecko(board, "g-p", DragEnd.HEAD, CellCoord(0, 0))
        assertFalse(wrongHole.moved)
        assertEquals(BlockedReason.WRONG_HOLE, wrongHole.blockedReason)

        // 3. Pindahkan posisi sehingga kepala bisa masuk lubang ungu
        val boardHeadFacingPurple = board.copy(
            geckos = listOf(
                GeckoState(
                    id = "g-p",
                    color = GeckoColor.PURPLE,
                    segments = listOf(CellCoord(0, 2), CellCoord(0, 1))
                )
            )
        )
        val escaped = GeckoGameRules.stepGecko(boardHeadFacingPurple, "g-p", DragEnd.HEAD, CellCoord(0, 3))
        assertTrue(escaped.moved)
        assertEquals("g-p", escaped.escapedGeckoId)
        assertNotNull(escaped.escapedHole)
        assertTrue(escaped.state.geckos.first().escaped)
        assertTrue(escaped.state.holes.first { it.id == "h-purple" }.sealed)
    }

    @Test
    fun test4_continuousDragAroundCornerCountsAsOneMoveAndUndoRestoresState() {
        val engine = GeckoOutRuleEngine()
        val level1 = GeckoLevels.byId(1)
        val initial = engine.createInitialState(level1, "attempt-test-4")

        // Tarik ekor Ungu dari (0,1) ke (0,6) melewati beberapa sel dalam 1 tarikan
        val afterPurplePark = engine.applyMove(
            initial,
            GeckoMove.DragStep(
                geckoId = "g-purple",
                end = DragEnd.TAIL,
                target = CellCoord(0, 6),
                pushUndoSnapshot = true
            )
        )
        assertEquals(1, afterPurplePark.board.moves)
        assertEquals(CellCoord(0, 6), afterPurplePark.board.geckos.first { it.id == "g-purple" }.segments.last())

        // Tarik kepala Oranye dari (2,2) melewati tikungan 90° ke (1,2) dalam 1 tarikan (stepGeckoToward BFS!)
        val afterOrangeCorner = engine.applyMove(
            afterPurplePark,
            GeckoMove.DragStep(
                geckoId = "g-orange",
                end = DragEnd.HEAD,
                target = CellCoord(1, 2),
                pushUndoSnapshot = true
            )
        )
        assertEquals(2, afterOrangeCorner.board.moves)
        assertEquals(CellCoord(1, 2), afterOrangeCorner.board.geckos.first { it.id == "g-orange" }.segments.first())

        // Lanjutkan tarik kepala Oranye ke (1,5) — masih gecko yang sama -> moves tetap 2!
        val afterOrangeContinue = engine.applyMove(
            afterOrangeCorner,
            GeckoMove.DragStep(
                geckoId = "g-orange",
                end = DragEnd.HEAD,
                target = CellCoord(1, 5),
                pushUndoSnapshot = false
            )
        )
        assertEquals(2, afterOrangeContinue.board.moves)

        // Undo mengembalikan state sebelum tarikan Oranye beserta moves == 1
        val afterUndo = engine.applyMove(afterOrangeContinue, GeckoMove.Undo)
        assertEquals(afterPurplePark.board, afterUndo.board)
        assertEquals(1, afterUndo.board.moves)

        // Undo kedua mengembalikan ke posisi awal beserta moves == 0
        val afterUndo2 = engine.applyMove(afterUndo, GeckoMove.Undo)
        assertEquals(initial.board, afterUndo2.board)
        assertEquals(0, afterUndo2.board.moves)
    }

    @Test
    fun testStarCalculationMatchesMasteryFormula() {
        assertEquals(3, calculateStars(moves = 4, par = 4))
        assertEquals(3, calculateStars(moves = 3, par = 4))
        assertEquals(2, calculateStars(moves = 6, par = 4))
        assertEquals(1, calculateStars(moves = 7, par = 4))
    }

    @Test
    fun testComputeReachablePathsFindsValidMultiStepDotTargetsAndEscapeHole() {
        val initialBoard = GeckoGameRules.createBoardState(GeckoLevels.byId(1))

        // 1. Purple TAIL can walk all the way to (0,6) along the top corridor
        val purpleTailPaths = GeckoGameRules.computeReachablePaths(initialBoard, "g-purple", DragEnd.TAIL)
        assertTrue(purpleTailPaths.isNotEmpty(), "Expected reachable path dots for Level 1 g-purple TAIL")
        assertTrue(
            purpleTailPaths.containsKey(CellCoord(0, 6)),
            "Expected (0,6) to be reachable by g-purple TAIL"
        )

        var boardAfterPurple = initialBoard
        for (step in purpleTailPaths.getValue(CellCoord(0, 6))) {
            val res = GeckoGameRules.stepGecko(boardAfterPurple, "g-purple", DragEnd.TAIL, step)
            assertTrue(res.moved, "Step $step for g-purple TAIL must be legal")
            boardAfterPurple = res.state
        }

        // 2. Now Orange HEAD can route around the 90-degree corner all the way into its matching hole (1,6)
        val orangeHole = CellCoord(1, 6)
        val orangeHeadPaths = GeckoGameRules.computeReachablePaths(boardAfterPurple, "g-orange", DragEnd.HEAD)
        assertTrue(
            orangeHeadPaths.containsKey(orangeHole),
            "Expected matching escape hole $orangeHole to be reachable by g-orange HEAD"
        )

        var simBoard = boardAfterPurple
        val pathToHole = orangeHeadPaths.getValue(orangeHole)
        assertTrue(pathToHole.size >= 4, "Expected multi-step path around corner into hole")
        for (step in pathToHole) {
            val res = GeckoGameRules.stepGecko(simBoard, "g-orange", DragEnd.HEAD, step)
            assertTrue(res.moved, "Step $step along computed path must be legal")
            simBoard = res.state
        }
        assertTrue(simBoard.geckos.first { it.id == "g-orange" }.escaped)

        // 3. TAIL must never list an open escape hole as a reachable destination
        val orangeTailPaths = GeckoGameRules.computeReachablePaths(boardAfterPurple, "g-orange", DragEnd.TAIL)
        assertFalse(
            orangeTailPaths.containsKey(orangeHole),
            "TAIL must not be allowed to target an open escape hole"
        )
    }

    @Test
    fun testComputeLocalSlidePathsStaysStraightAndDoesNotSpoilCorners() {
        val initialBoard = GeckoGameRules.createBoardState(GeckoLevels.byId(1))

        // 1. Purple TAIL at (0,1) slides straight ALL the way right until wall/board edge at (0,6)
        val purpleLocal = GeckoGameRules.computeLocalSlidePaths(
            state = initialBoard,
            geckoId = "g-purple",
            end = DragEnd.TAIL
        )
        assertEquals(
            setOf(CellCoord(0, 2), CellCoord(0, 3), CellCoord(0, 4), CellCoord(0, 5), CellCoord(0, 6)),
            purpleLocal.keys
        )

        // 2. Move Purple TAIL out of the way, then check Orange HEAD at (2,2):
        //    Only straight-line left (2,1) and (2,0) appear — NOT around the corner to (1,1) or hole (1,6)!
        var boardAfterPurple = initialBoard
        for (step in purpleLocal.getValue(CellCoord(0, 6))) {
            boardAfterPurple = GeckoGameRules.stepGecko(boardAfterPurple, "g-purple", DragEnd.TAIL, step).state
        }
        val orangeBeforeTurn = GeckoGameRules.computeLocalSlidePaths(
            state = boardAfterPurple,
            geckoId = "g-orange",
            end = DragEnd.HEAD
        )
        assertTrue(orangeBeforeTurn.containsKey(CellCoord(2, 1)), "Straight cell (2,1) should appear")
        assertFalse(
            orangeBeforeTurn.containsKey(CellCoord(1, 1)),
            "Turn-around-corner cell (1,1) must NOT appear in straight-line dots"
        )
        assertFalse(
            orangeBeforeTurn.containsKey(CellCoord(1, 6)),
            "Hole around a corner must NOT be spoiled"
        )

        // 3. Once Orange HEAD turns into row 1 at (1,1) facing straight toward hole (1,6),
        //    the straight ray extends all the way straight INTO the matching hole (1,6)!
        var boardOrangeAligned = GeckoGameRules.stepGecko(boardAfterPurple, "g-orange", DragEnd.HEAD, CellCoord(2, 1)).state
        boardOrangeAligned = GeckoGameRules.stepGecko(boardOrangeAligned, "g-orange", DragEnd.HEAD, CellCoord(1, 1)).state
        val orangeAlignedLocal = GeckoGameRules.computeLocalSlidePaths(
            state = boardOrangeAligned,
            geckoId = "g-orange",
            end = DragEnd.HEAD
        )
        assertTrue(
            orangeAlignedLocal.containsKey(CellCoord(1, 6)),
            "Matching hole directly straight ahead at (1,6) MUST be reachable in one straight slide"
        )
        assertEquals(
            listOf(CellCoord(1, 2), CellCoord(1, 3), CellCoord(1, 4), CellCoord(1, 5), CellCoord(1, 6)),
            orangeAlignedLocal.getValue(CellCoord(1, 6))
        )
    }
}
