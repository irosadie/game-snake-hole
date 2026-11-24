package com.binarydev.startergame.core.gecko

object GeckoGameRules {

    private val ORTHOGONAL_DIRS = listOf(
        CellCoord(-1, 0),
        CellCoord(1, 0),
        CellCoord(0, -1),
        CellCoord(0, 1)
    )

    fun createBoardState(level: LevelDef): BoardState = BoardState(
        levelId = level.id,
        rows = level.rows,
        cols = level.cols,
        cells = level.cells,
        walls = level.walls,
        holes = level.holes.map { HoleState(it.id, it.color, it.coord, sealed = false) },
        geckos = level.geckos.map { GeckoState(it.id, it.color, it.segments, escaped = false) },
        moves = 0,
        lastMovedGeckoId = null
    )

    fun validateLevelDef(level: LevelDef): LevelValidationResult {
        val errors = mutableListOf<String>()

        if (level.cells.isEmpty()) {
            errors.add("Level ${level.id}: cells tidak boleh kosong")
        }
        for (cell in level.cells) {
            if (cell.r !in 0 until level.rows || cell.c !in 0 until level.cols) {
                errors.add("Level ${level.id}: sel $cell di luar ukuran ${level.rows}x${level.cols}")
            }
        }

        val holeCoords = mutableSetOf<CellCoord>()
        val holeColors = mutableSetOf<GeckoColor>()
        for (hole in level.holes) {
            if (hole.coord !in level.cells) {
                errors.add("Level ${level.id}: lubang ${hole.id} di ${hole.coord} berada di luar cells")
            }
            if (!holeCoords.add(hole.coord)) {
                errors.add("Level ${level.id}: lubang duplikat di ${hole.coord}")
            }
            holeColors.add(hole.color)
        }

        val occupied = mutableMapOf<CellCoord, String>()
        for (gecko in level.geckos) {
            if (gecko.segments.size < 2) {
                errors.add("Level ${level.id}: gecko ${gecko.id} minimal memiliki 2 segmen")
            }
            if (gecko.color !in holeColors) {
                errors.add("Level ${level.id}: gecko ${gecko.id} (${gecko.color}) tidak memiliki lubang warna cocok")
            }
            gecko.segments.forEachIndexed { idx, seg ->
                if (seg !in level.cells) {
                    errors.add("Level ${level.id}: segmen[$idx] $seg milik ${gecko.id} di luar cells")
                }
                if (seg in holeCoords) {
                    errors.add("Level ${level.id}: segmen[$idx] $seg milik ${gecko.id} menimpa lubang")
                }
                val existing = occupied.put(seg, gecko.id)
                if (existing != null) {
                    errors.add("Level ${level.id}: tabrakan segmen di $seg antara $existing dan ${gecko.id}")
                }
                if (idx > 0) {
                    val prev = gecko.segments[idx - 1]
                    if (prev.manhattan(seg) != 1) {
                        errors.add("Level ${level.id}: segmen ${gecko.id} tidak bertetangga ($prev -> $seg)")
                    }
                    if (WallEdge.of(prev, seg) in level.walls) {
                        errors.add("Level ${level.id}: segmen ${gecko.id} menembus dinding ($prev -> $seg)")
                    }
                }
            }
        }

        return LevelValidationResult(valid = errors.isEmpty(), errors = errors)
    }

    fun canStepGecko(
        state: BoardState,
        geckoId: String,
        end: DragEnd,
        target: CellCoord
    ): BlockedReason? {
        val gecko = state.geckos.firstOrNull { it.id == geckoId } ?: return BlockedReason.INVALID_CELL
        if (gecko.escaped) return BlockedReason.ALREADY_ESCAPED
        if (target !in state.cells) return BlockedReason.INVALID_CELL

        val endCell = if (end == DragEnd.HEAD) gecko.segments.first() else gecko.segments.last()
        if (endCell.manhattan(target) != 1) return BlockedReason.NOT_ADJACENT
        if (WallEdge.of(endCell, target) in state.walls) return BlockedReason.WALL

        // Hole check
        val unsealedHole = state.holes.firstOrNull { !it.sealed && it.coord == target }
        if (unsealedHole != null) {
            if (unsealedHole.color != gecko.color) return BlockedReason.WRONG_HOLE
            if (end != DragEnd.HEAD) return BlockedReason.TAIL_ON_HOLE
            // Head entering matching unsealed hole -> valid escape!
            return null
        }

        // Body collision & self-tail chasing check
        val oppositeIdx = if (end == DragEnd.HEAD) gecko.segments.lastIndex else 0
        for (other in state.geckos) {
            if (other.escaped) continue
            other.segments.forEachIndexed { idx, seg ->
                if (seg == target) {
                    val isSelfTailChase = other.id == gecko.id &&
                        idx == oppositeIdx &&
                        gecko.segments.size >= 4
                    if (!isSelfTailChase) {
                        return BlockedReason.OCCUPIED
                    }
                }
            }
        }

        return null
    }

    fun stepGecko(
        state: BoardState,
        geckoId: String,
        end: DragEnd,
        target: CellCoord
    ): StepResult {
        val blocked = canStepGecko(state, geckoId, end, target)
        if (blocked != null) {
            return StepResult(moved = false, state = state, blockedReason = blocked)
        }

        val gecko = state.geckos.first { it.id == geckoId }
        val isNewTurn = state.lastMovedGeckoId != geckoId
        val nextMoves = if (isNewTurn) state.moves + 1 else state.moves

        val matchingHole = state.holes.firstOrNull { !it.sealed && it.coord == target && it.color == gecko.color }
        if (matchingHole != null && end == DragEnd.HEAD) {
            val sealedHole = matchingHole.copy(sealed = true)
            val updatedHoles = state.holes.map { if (it.id == matchingHole.id) sealedHole else it }
            val updatedGeckos = state.geckos.map {
                if (it.id == geckoId) it.copy(escaped = true) else it
            }
            return StepResult(
                moved = true,
                state = state.copy(
                    holes = updatedHoles,
                    geckos = updatedGeckos,
                    moves = nextMoves,
                    lastMovedGeckoId = geckoId
                ),
                escapedGeckoId = geckoId,
                escapedHole = sealedHole
            )
        }

        val newSegments = if (end == DragEnd.HEAD) {
            buildList(gecko.segments.size) {
                add(target)
                for (i in 0 until gecko.segments.lastIndex) {
                    add(gecko.segments[i])
                }
            }
        } else {
            buildList(gecko.segments.size) {
                for (i in 1 until gecko.segments.size) {
                    add(gecko.segments[i])
                }
                add(target)
            }
        }

        val updatedGeckos = state.geckos.map {
            if (it.id == geckoId) it.copy(segments = newSegments) else it
        }
        return StepResult(
            moved = true,
            state = state.copy(
                geckos = updatedGeckos,
                moves = nextMoves,
                lastMovedGeckoId = geckoId
            )
        )
    }

    fun getValidSteps(state: BoardState, geckoId: String, end: DragEnd): List<CellCoord> {
        val gecko = state.geckos.firstOrNull { it.id == geckoId } ?: return emptyList()
        if (gecko.escaped) return emptyList()
        val endCell = if (end == DragEnd.HEAD) gecko.segments.first() else gecko.segments.last()
        val results = ArrayList<CellCoord>(4)
        for (d in ORTHOGONAL_DIRS) {
            val cand = CellCoord(endCell.r + d.r, endCell.c + d.c)
            if (canStepGecko(state, geckoId, end, cand) == null) {
                results.add(cand)
            }
        }
        return results
    }

    /**
     * Bounded Mini-BFS drag (maxSteps = 5) agar geseran cepat melewati tikungan 90° tetap mulus.
     */
    fun stepGeckoToward(
        state: BoardState,
        geckoId: String,
        end: DragEnd,
        targetCell: CellCoord,
        maxSteps: Int = 5
    ): StepResult {
        val gecko = state.geckos.firstOrNull { it.id == geckoId }
            ?: return StepResult(false, state, blockedReason = BlockedReason.INVALID_CELL)
        if (gecko.escaped) return StepResult(false, state, blockedReason = BlockedReason.ALREADY_ESCAPED)

        val endCell = if (end == DragEnd.HEAD) gecko.segments.first() else gecko.segments.last()
        if (endCell == targetCell) {
            return StepResult(moved = false, state = state)
        }

        if (endCell.manhattan(targetCell) == 1) {
            val direct = stepGecko(state, geckoId, end, targetCell)
            if (direct.moved ||
                direct.blockedReason == BlockedReason.TAIL_ON_HOLE ||
                direct.blockedReason == BlockedReason.WRONG_HOLE ||
                direct.blockedReason == BlockedReason.INVALID_CELL
            ) {
                return direct
            }
            // Fall through to bounded mini-BFS so dragging across a thin WallEdge routes smoothly around corners!
        }

        data class BfsNode(
            val board: BoardState,
            val headOrTail: CellCoord,
            val depth: Int,
            val path: List<CellCoord>,
            val escapedGeckoId: String? = null,
            val escapedHole: HoleState? = null
        )

        val visited = HashSet<List<CellCoord>>()
        visited.add(gecko.segments)
        val queue = ArrayDeque<BfsNode>()
        queue.add(BfsNode(state, endCell, 0, emptyList()))

        var exactMatch: BfsNode? = null
        var bestClosest: BfsNode? = null
        var bestDist = endCell.manhattan(targetCell)

        while (queue.isNotEmpty()) {
            val cur = queue.removeFirst()
            if (cur.depth >= maxSteps) continue

            val validNext = getValidSteps(cur.board, geckoId, end)
            for (nextCoord in validNext) {
                val res = stepGecko(cur.board, geckoId, end, nextCoord)
                if (!res.moved) continue

                val nextPath = cur.path + nextCoord
                val nextNode = BfsNode(
                    board = res.state,
                    headOrTail = nextCoord,
                    depth = cur.depth + 1,
                    path = nextPath,
                    escapedGeckoId = res.escapedGeckoId,
                    escapedHole = res.escapedHole
                )

                if (nextCoord == targetCell) {
                    exactMatch = nextNode
                    break
                }

                val dist = nextCoord.manhattan(targetCell)
                if (dist < bestDist) {
                    bestDist = dist
                    bestClosest = nextNode
                }

                if (res.escapedGeckoId == null) {
                    val movedGecko = res.state.geckos.first { it.id == geckoId }
                    if (visited.add(movedGecko.segments)) {
                        queue.add(nextNode)
                    }
                }
            }
            if (exactMatch != null) break
        }

        val chosen = exactMatch ?: bestClosest
        if (chosen == null || chosen.path.isEmpty()) {
            // Check if immediate step toward target gives a meaningful blocked reason
            val dr = (targetCell.r - endCell.r).coerceIn(-1, 1)
            val dc = (targetCell.c - endCell.c).coerceIn(-1, 1)
            val probe = if (dr != 0) CellCoord(endCell.r + dr, endCell.c) else CellCoord(endCell.r, endCell.c + dc)
            val reason = canStepGecko(state, geckoId, end, probe)
            return StepResult(moved = false, state = state, blockedReason = reason)
        }

        return StepResult(
            moved = true,
            state = chosen.board,
            escapedGeckoId = chosen.escapedGeckoId,
            escapedHole = chosen.escapedHole
        )
    }

    /**
     * Computes all board cells reachable by dragging/walking [geckoId]'s [end] (HEAD or TAIL)
     * using a bounded BFS over actual snake-body movement rules.
     *
     * Returns a map of `destinationCell -> shortestStepByStepPath` so the UI can render
     * placeholder dots on every traversable tile and animate the gecko walking straight there
     * when a dot is tapped.
     */
    fun computeReachablePaths(
        state: BoardState,
        geckoId: String,
        end: DragEnd,
        maxDepth: Int = 18,
        maxStates: Int = 96
    ): Map<CellCoord, List<CellCoord>> {
        val gecko = state.geckos.firstOrNull { it.id == geckoId } ?: return emptyMap()
        if (gecko.escaped || gecko.segments.isEmpty()) return emptyMap()

        val startEndCell = if (end == DragEnd.HEAD) gecko.segments.first() else gecko.segments.last()

        data class ReachableNode(
            val board: BoardState,
            val depth: Int,
            val path: List<CellCoord>
        )

        val shortestPathByCell = LinkedHashMap<CellCoord, List<CellCoord>>()
        val visitedSegments = HashSet<List<CellCoord>>()
        visitedSegments.add(gecko.segments)

        val queue = ArrayDeque<ReachableNode>()
        queue.add(ReachableNode(state, 0, emptyList()))
        var exploredStates = 0

        while (queue.isNotEmpty()) {
            val cur = queue.removeFirst()
            if (cur.depth >= maxDepth) continue

            val validNext = getValidSteps(cur.board, geckoId, end)
            for (nextCoord in validNext) {
                val res = stepGecko(cur.board, geckoId, end, nextCoord)
                if (!res.moved) continue

                val nextPath = cur.path + nextCoord
                if (nextCoord != startEndCell && !shortestPathByCell.containsKey(nextCoord)) {
                    shortestPathByCell[nextCoord] = nextPath
                }

                // Do not expand beyond an escape hole (gecko exits the board here)
                if (res.escapedGeckoId == null && exploredStates < maxStates) {
                    val movedGecko = res.state.geckos.firstOrNull { it.id == geckoId } ?: continue
                    if (visitedSegments.add(movedGecko.segments)) {
                        exploredStates++
                        queue.add(
                            ReachableNode(
                                board = res.state,
                                depth = cur.depth + 1,
                                path = nextPath
                            )
                        )
                    }
                }
            }
        }

        return shortestPathByCell
    }

    /**
     * Computes straight-line slide paths (no turning around corners) from [geckoId]'s
     * selected [end] in each cardinal direction all the way until hitting a wall, board edge,
     * another gecko, or entering a straight-ahead matching escape hole.
     */
    fun computeLocalSlidePaths(
        state: BoardState,
        geckoId: String,
        end: DragEnd,
        maxStepsPerDirection: Int = maxOf(state.rows, state.cols)
    ): Map<CellCoord, List<CellCoord>> {
        val gecko = state.geckos.firstOrNull { it.id == geckoId } ?: return emptyMap()
        if (gecko.escaped || gecko.segments.isEmpty()) return emptyMap()

        val limit = maxStepsPerDirection.coerceAtLeast(1)
        val result = LinkedHashMap<CellCoord, List<CellCoord>>()
        for (dir in ORTHOGONAL_DIRS) {
            var curBoard = state
            var curCell = if (end == DragEnd.HEAD) gecko.segments.first() else gecko.segments.last()
            val rayPath = ArrayList<CellCoord>()

            for (stepIdx in 0 until limit) {
                val nextCell = CellCoord(curCell.r + dir.r, curCell.c + dir.c)
                val stepRes = stepGecko(curBoard, geckoId, end, nextCell)
                if (!stepRes.moved) break

                rayPath.add(nextCell)
                result[nextCell] = rayPath.toList()

                if (stepRes.escapedGeckoId != null) break
                curBoard = stepRes.state
                curCell = nextCell
            }
        }
        return result
    }
}
