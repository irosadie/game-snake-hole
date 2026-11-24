package com.binarydev.startergame.core.gecko

/**
 * Pure-Kotlin Weighted A* Solver untuk validasi solvabilitas level & fitur Live Hint.
 * Menggunakan binary min-heap internal agar 100% kompatibel dengan Kotlin Multiplatform (`commonMain`).
 */
object GeckoPuzzleSolver {

    private val DIRS = listOf(
        CellCoord(-1, 0),
        CellCoord(1, 0),
        CellCoord(0, -1),
        CellCoord(0, 1)
    )

    private class MinHeap<T : Comparable<T>> {
        private val items = ArrayList<T>()

        val isNotEmpty: Boolean get() = items.isNotEmpty()

        fun add(value: T) {
            items.add(value)
            siftUp(items.lastIndex)
        }

        fun poll(): T? {
            if (items.isEmpty()) return null
            val top = items[0]
            val last = items.removeAt(items.lastIndex)
            if (items.isNotEmpty()) {
                items[0] = last
                siftDown(0)
            }
            return top
        }

        private fun siftUp(startIdx: Int) {
            var idx = startIdx
            while (idx > 0) {
                val parent = (idx - 1) ushr 1
                if (items[idx] >= items[parent]) break
                val tmp = items[idx]
                items[idx] = items[parent]
                items[parent] = tmp
                idx = parent
            }
        }

        private fun siftDown(startIdx: Int) {
            var idx = startIdx
            val size = items.size
            while (true) {
                val left = (idx shl 1) + 1
                val right = left + 1
                var smallest = idx
                if (left < size && items[left] < items[smallest]) smallest = left
                if (right < size && items[right] < items[smallest]) smallest = right
                if (smallest == idx) break
                val tmp = items[idx]
                items[idx] = items[smallest]
                items[smallest] = tmp
                idx = smallest
            }
        }
    }

    private fun buildHoleDistanceMaps(state: BoardState): Map<String, Map<CellCoord, Int>> {
        return state.holes.associate { hole ->
            val dist = HashMap<CellCoord, Int>()
            val queue = ArrayDeque<CellCoord>()
            dist[hole.coord] = 0
            queue.add(hole.coord)

            while (queue.isNotEmpty()) {
                val cur = queue.removeFirst()
                val curD = dist[cur] ?: 0
                for (d in DIRS) {
                    val next = CellCoord(cur.r + d.r, cur.c + d.c)
                    if (next !in state.cells || next in dist) continue
                    if (WallEdge.of(cur.r, cur.c, next.r, next.c) in state.walls) continue
                    dist[next] = curD + 1
                    queue.add(next)
                }
            }
            hole.id to dist
        }
    }

    private fun encodeState(state: BoardState): String = buildString {
        for (g in state.geckos) {
            if (g.escaped) {
                append("X|")
            } else {
                for (s in g.segments) {
                    append(s.r).append(',').append(s.c).append(';')
                }
                append('|')
            }
        }
    }

    private fun heuristic(
        state: BoardState,
        holeDistances: Map<String, Map<CellCoord, Int>>
    ): Double {
        var h = 0.0
        for (g in state.geckos) {
            if (g.escaped) continue
            val hole = state.holes.firstOrNull { !it.sealed && it.color == g.color } ?: continue
            val d = holeDistances[hole.id]?.get(g.segments.first()) ?: 20
            h += d + 4.0
        }
        return h
    }

    private class Node(
        val state: BoardState,
        val gCost: Double,
        val fCost: Double,
        val parent: Node?,
        val move: SolverStep?
    ) : Comparable<Node> {
        override fun compareTo(other: Node): Int = fCost.compareTo(other.fCost)
    }

    fun solve(
        initialState: BoardState,
        maxExpansions: Int = 25_000,
        weight: Double = 2.5
    ): SolveResult {
        if (initialState.geckos.all { it.escaped }) {
            return SolveResult(true, emptyList(), 0)
        }

        val holeDistances = buildHoleDistanceMaps(initialState)
        val open = MinHeap<Node>()
        val bestG = HashMap<String, Double>()

        val startKey = encodeState(initialState)
        bestG[startKey] = 0.0
        open.add(Node(initialState, 0.0, heuristic(initialState, holeDistances) * weight, null, null))

        var expanded = 0
        while (open.isNotEmpty && expanded < maxExpansions) {
            val cur = open.poll() ?: break
            expanded++

            if (cur.state.geckos.all { it.escaped }) {
                val steps = ArrayList<SolverStep>()
                var ptr: Node? = cur
                while (ptr?.move != null) {
                    steps.add(ptr.move)
                    ptr = ptr.parent
                }
                steps.reverse()
                var turns = 0
                var prevId: String? = null
                for (s in steps) {
                    if (s.geckoId != prevId) {
                        turns++
                        prevId = s.geckoId
                    }
                }
                return SolveResult(true, steps, turns)
            }

            for (gecko in cur.state.geckos) {
                if (gecko.escaped) continue
                for (end in DragEnd.entries) {
                    for (target in GeckoGameRules.getValidSteps(cur.state, gecko.id, end)) {
                        val res = GeckoGameRules.stepGecko(cur.state, gecko.id, end, target)
                        if (!res.moved) continue

                        val nextKey = encodeState(res.state)
                        val switchPenalty = if (cur.move != null && cur.move.geckoId != gecko.id) 0.35 else 0.0
                        val nextG = cur.gCost + 1.0 + switchPenalty
                        val prev = bestG[nextKey]
                        if (prev != null && prev <= nextG) continue
                        bestG[nextKey] = nextG

                        val h = heuristic(res.state, holeDistances)
                        open.add(
                            Node(
                                state = res.state,
                                gCost = nextG,
                                fCost = nextG + h * weight,
                                parent = cur,
                                move = SolverStep(gecko.id, end, target)
                            )
                        )
                    }
                }
            }
        }
        return SolveResult(false, emptyList(), 0)
    }

    fun computeHint(state: BoardState): HintMove? {
        val solved = solve(state, maxExpansions = 16_000, weight = 2.6)
        if (!solved.solvable || solved.steps.isEmpty()) return null
        val first = solved.steps.first()
        val preview = mutableListOf(first.target)
        for (i in 1 until solved.steps.size) {
            val s = solved.steps[i]
            if (s.geckoId != first.geckoId || s.end != first.end) break
            preview.add(s.target)
            if (preview.size >= 6) break
        }
        return HintMove(first.geckoId, first.end, preview)
    }
}
