package com.binarydev.startergame.core.gecko

object GeckoLevels {

    private fun rectCells(rows: Int, cols: Int, excluded: Set<CellCoord> = emptySet()): Set<CellCoord> =
        buildSet {
            for (r in 0 until rows) {
                for (c in 0 until cols) {
                    val coord = CellCoord(r, c)
                    if (coord !in excluded) add(coord)
                }
            }
        }

    private fun hWall(rowAbove: Int, cols: IntRange): List<WallEdge> =
        cols.map { c -> WallEdge.of(rowAbove, c, rowAbove + 1, c) }

    private fun vWall(rows: IntRange, colLeft: Int): List<WallEdge> =
        rows.map { r -> WallEdge.of(r, colLeft, r, colLeft + 1) }

    private fun segs(vararg pairs: Pair<Int, Int>): List<CellCoord> =
        pairs.map { (r, c) -> CellCoord(r, c) }

    val ALL: List<LevelDef> = listOf(
        // 01: First Try? (5x8 Comb, 3 Geckos, Par 4) — 1:1 reconstruction from spec
        LevelDef(
            id = 1,
            name = "First Try?",
            subtitle = "Tarik ekor Ungu, buka jalan & lakukan 3-point turn!",
            rows = 5,
            cols = 8,
            cells = buildSet {
                for (c in 1..6) add(CellCoord(0, c))
                for (c in 1..6) add(CellCoord(1, c))
                for (c in 0..6) add(CellCoord(2, c))
                for (c in 0..6) add(CellCoord(3, c))
                for (c in 2..7) add(CellCoord(4, c))
            },
            walls = buildSet {
                addAll(hWall(0, 2..6))
                addAll(hWall(1, 2..6))
                addAll(hWall(2, 1..6))
                addAll(hWall(3, 3..6))
            },
            holes = listOf(
                HoleDef("h-o1", GeckoColor.ORANGE, CellCoord(1, 6)),
                HoleDef("h-y1", GeckoColor.YELLOW, CellCoord(2, 6)),
                HoleDef("h-p1", GeckoColor.PURPLE, CellCoord(3, 6))
            ),
            geckos = listOf(
                GeckoDef(
                    "g-purple",
                    GeckoColor.PURPLE,
                    segs(3 to 1, 3 to 0, 2 to 0, 2 to 1, 1 to 1, 0 to 1)
                ),
                GeckoDef(
                    "g-orange",
                    GeckoColor.ORANGE,
                    segs(2 to 2, 2 to 3, 2 to 4, 2 to 5)
                ),
                GeckoDef(
                    "g-yellow",
                    GeckoColor.YELLOW,
                    segs(4 to 3, 4 to 2, 3 to 2, 3 to 3, 3 to 4, 3 to 5)
                )
            ),
            par = 4
        ),

        // 02: Courtyard Twist (5x5, 3 Geckos, Par 4) — 2x2 central hub turnaround + Emerald
        LevelDef(
            id = 2,
            name = "Courtyard Twist",
            subtitle = "Manfaatkan ruang terbuka 2×2 di tengah untuk berputar.",
            rows = 5,
            cols = 5,
            cells = rectCells(5, 5),
            walls = buildSet {
                addAll(hWall(0, 1..2))
                add(WallEdge.of(0, 1, 0, 2))
                addAll(hWall(2, 0..1))
                addAll(hWall(2, 3..4))
                add(WallEdge.of(3, 3, 4, 3))
                add(WallEdge.of(3, 4, 4, 4))
            },
            holes = listOf(
                HoleDef("h-p2", GeckoColor.PURPLE, CellCoord(0, 4)),
                HoleDef("h-o2", GeckoColor.ORANGE, CellCoord(4, 0)),
                HoleDef("h-e2", GeckoColor.EMERALD, CellCoord(4, 4))
            ),
            geckos = listOf(
                GeckoDef(
                    "g-purple",
                    GeckoColor.PURPLE,
                    segs(2 to 1, 1 to 1, 1 to 0, 0 to 0)
                ),
                GeckoDef(
                    "g-orange",
                    GeckoColor.ORANGE,
                    segs(1 to 2, 1 to 3, 1 to 4, 2 to 4)
                ),
                GeckoDef(
                    "g-emerald",
                    GeckoColor.EMERALD,
                    segs(3 to 1, 3 to 2, 3 to 3, 3 to 4)
                )
            ),
            par = 4
        ),

        // 03: Twin Hairpins (5x6, 4 Geckos: Purple, Orange, Coral, Sky, Par 5)
        LevelDef(
            id = 3,
            name = "Twin Hairpins",
            subtitle = "Koridor zig-zag S — parkir di tikungan untuk membuka jalan.",
            rows = 5,
            cols = 6,
            cells = rectCells(5, 6),
            walls = buildSet {
                addAll(hWall(0, 1..4))
                addAll(hWall(1, 0..3))
                addAll(hWall(2, 2..5))
                addAll(hWall(3, 1..4))
            },
            holes = listOf(
                HoleDef("h-p3", GeckoColor.PURPLE, CellCoord(0, 5)),
                HoleDef("h-o3", GeckoColor.ORANGE, CellCoord(1, 0)),
                HoleDef("h-c3", GeckoColor.CORAL, CellCoord(3, 5)),
                HoleDef("h-s3", GeckoColor.SKY, CellCoord(4, 0))
            ),
            geckos = listOf(
                GeckoDef("g-purple", GeckoColor.PURPLE, segs(0 to 1, 0 to 2, 0 to 3)),
                GeckoDef("g-orange", GeckoColor.ORANGE, segs(1 to 4, 1 to 3, 1 to 2)),
                GeckoDef("g-coral", GeckoColor.CORAL, segs(2 to 1, 2 to 2, 2 to 3)),
                GeckoDef("g-sky", GeckoColor.SKY, segs(3 to 4, 3 to 3, 3 to 2))
            ),
            par = 5
        ),

        // 04: Crossroad Jam (6x6, 4 Geckos, Par 5) — 4-way symmetric crossroads
        LevelDef(
            id = 4,
            name = "Crossroad Jam",
            subtitle = "Persimpangan 4 arah — mundur bergantian dari pusat.",
            rows = 6,
            cols = 6,
            cells = rectCells(
                6,
                6,
                excluded = setOf(
                    CellCoord(0, 0), CellCoord(0, 5),
                    CellCoord(5, 0), CellCoord(5, 5)
                )
            ),
            walls = buildSet {
                add(WallEdge.of(1, 1, 1, 2))
                add(WallEdge.of(1, 3, 1, 4))
                add(WallEdge.of(4, 1, 4, 2))
                add(WallEdge.of(4, 3, 4, 4))
                add(WallEdge.of(1, 1, 2, 1))
                add(WallEdge.of(1, 4, 2, 4))
                add(WallEdge.of(3, 1, 4, 1))
                add(WallEdge.of(3, 4, 4, 4))
            },
            holes = listOf(
                HoleDef("h-p4", GeckoColor.PURPLE, CellCoord(0, 3)),
                HoleDef("h-o4", GeckoColor.ORANGE, CellCoord(3, 5)),
                HoleDef("h-y4", GeckoColor.YELLOW, CellCoord(5, 2)),
                HoleDef("h-e4", GeckoColor.EMERALD, CellCoord(2, 0))
            ),
            geckos = listOf(
                GeckoDef("g-purple", GeckoColor.PURPLE, segs(3 to 2, 4 to 2, 4 to 3, 5 to 3)),
                GeckoDef("g-orange", GeckoColor.ORANGE, segs(2 to 2, 2 to 1, 3 to 1, 3 to 0)),
                GeckoDef("g-yellow", GeckoColor.YELLOW, segs(2 to 3, 1 to 3, 1 to 2, 0 to 2)),
                GeckoDef("g-emerald", GeckoColor.EMERALD, segs(3 to 3, 3 to 4, 2 to 4, 2 to 5))
            ),
            par = 5
        ),

        // 05: Mirror Lanes (5x7, 4 Geckos, Par 5) — Twin opposing combs sharing central bridge
        LevelDef(
            id = 5,
            name = "Mirror Lanes",
            subtitle = "Dua sisir berhadapan berbagi satu jembatan tengah.",
            rows = 5,
            cols = 7,
            cells = rectCells(
                5,
                7,
                excluded = setOf(CellCoord(0, 3), CellCoord(4, 3))
            ),
            walls = buildSet {
                // Left comb partitions (with U-turn parking pocket at c=0 between lanes 1-2 & 2-3)
                addAll(hWall(0, 0..1))
                add(WallEdge.of(1, 1, 2, 1))
                add(WallEdge.of(2, 1, 3, 1))
                addAll(hWall(3, 0..1))
                // Right comb partitions (mirrored U-turn parking pocket at c=6)
                addAll(hWall(0, 5..6))
                add(WallEdge.of(1, 5, 2, 5))
                add(WallEdge.of(2, 5, 3, 5))
                addAll(hWall(3, 5..6))
                // Narrow central bridge walls (only row 2 connects left and right)
                add(WallEdge.of(1, 2, 1, 3))
                add(WallEdge.of(3, 2, 3, 3))
                add(WallEdge.of(1, 3, 1, 4))
                add(WallEdge.of(3, 3, 3, 4))
            },
            holes = listOf(
                HoleDef("h-p5", GeckoColor.PURPLE, CellCoord(0, 6)),
                HoleDef("h-o5", GeckoColor.ORANGE, CellCoord(4, 6)),
                HoleDef("h-e5", GeckoColor.EMERALD, CellCoord(0, 0)),
                HoleDef("h-c5", GeckoColor.CORAL, CellCoord(4, 0))
            ),
            geckos = listOf(
                GeckoDef("g-purple", GeckoColor.PURPLE, segs(1 to 2, 1 to 1, 1 to 0)),
                GeckoDef("g-orange", GeckoColor.ORANGE, segs(3 to 2, 3 to 1, 3 to 0)),
                GeckoDef("g-emerald", GeckoColor.EMERALD, segs(1 to 4, 1 to 5, 1 to 6)),
                GeckoDef("g-coral", GeckoColor.CORAL, segs(3 to 4, 3 to 5, 3 to 6))
            ),
            par = 5
        ),

        // 06: Spiral Vault (6x6, 4 Geckos, Par 6) — Spiral labyrinth guarding inner vault
        LevelDef(
            id = 6,
            name = "Spiral Vault",
            subtitle = "Urai koridor luar dulu untuk membuka brankas spiral.",
            rows = 6,
            cols = 6,
            cells = rectCells(6, 6),
            walls = buildSet {
                // Outer ring to inner ring walls
                addAll(hWall(0, 0..4))
                addAll(vWall(1..4, 4))
                addAll(hWall(4, 1..4))
                addAll(vWall(2..4, 0))
                // Inner vault walls
                addAll(hWall(1, 2..3))
                add(WallEdge.of(2, 3, 3, 3))
                add(WallEdge.of(3, 2, 4, 2))
            },
            holes = listOf(
                HoleDef("h-p6", GeckoColor.PURPLE, CellCoord(2, 2)),
                HoleDef("h-y6", GeckoColor.YELLOW, CellCoord(3, 3)),
                HoleDef("h-e6", GeckoColor.EMERALD, CellCoord(0, 0)),
                HoleDef("h-s6", GeckoColor.SKY, CellCoord(5, 5))
            ),
            geckos = listOf(
                GeckoDef("g-purple", GeckoColor.PURPLE, segs(0 to 4, 0 to 3, 0 to 2, 0 to 1)),
                GeckoDef("g-yellow", GeckoColor.YELLOW, segs(4 to 5, 3 to 5, 2 to 5, 1 to 5)),
                GeckoDef("g-emerald", GeckoColor.EMERALD, segs(5 to 1, 5 to 2, 5 to 3, 5 to 4)),
                GeckoDef("g-sky", GeckoColor.SKY, segs(2 to 1, 3 to 1, 4 to 1))
            ),
            par = 6
        ),

        // 07: Switchback Yard (6x6, 5 Geckos, Par 7) — Train yard with passing sidings
        LevelDef(
            id = 7,
            name = "Switchback Yard",
            subtitle = "5 gecko model rel langsir — tukar urutan keluar lewat jalur parkir.",
            rows = 6,
            cols = 6,
            cells = rectCells(6, 6),
            walls = buildSet {
                addAll(hWall(0, 1..4))
                addAll(hWall(1, 2..5))
                addAll(hWall(2, 0..3))
                addAll(hWall(3, 2..5))
                addAll(hWall(4, 0..3))
            },
            holes = listOf(
                HoleDef("h-p7", GeckoColor.PURPLE, CellCoord(0, 5)),
                HoleDef("h-o7", GeckoColor.ORANGE, CellCoord(1, 0)),
                HoleDef("h-y7", GeckoColor.YELLOW, CellCoord(3, 5)),
                HoleDef("h-e7", GeckoColor.EMERALD, CellCoord(4, 0)),
                HoleDef("h-c7", GeckoColor.CORAL, CellCoord(5, 5))
            ),
            geckos = listOf(
                GeckoDef("g-purple", GeckoColor.PURPLE, segs(0 to 2, 0 to 1, 0 to 0)),
                GeckoDef("g-orange", GeckoColor.ORANGE, segs(1 to 2, 1 to 3, 1 to 4)),
                GeckoDef("g-yellow", GeckoColor.YELLOW, segs(2 to 3, 2 to 2, 2 to 1)),
                GeckoDef("g-emerald", GeckoColor.EMERALD, segs(3 to 2, 3 to 3, 3 to 4)),
                GeckoDef("g-coral", GeckoColor.CORAL, segs(4 to 3, 4 to 2, 4 to 1))
            ),
            par = 7
        ),

        // 08: Grand Comb (6x8 Comb, 4 Long Geckos, Par 6) — Evolution of Level 1
        LevelDef(
            id = 8,
            name = "Grand Comb",
            subtitle = "Evolusi Level 1 — 4 gecko panjang bertumpuk di 6 lorong sisir.",
            rows = 6,
            cols = 8,
            cells = buildSet {
                for (c in 1..7) add(CellCoord(0, c))
                for (c in 1..6) add(CellCoord(1, c))
                for (c in 0..6) add(CellCoord(2, c))
                for (c in 0..6) add(CellCoord(3, c))
                for (c in 1..6) add(CellCoord(4, c))
                for (c in 2..7) add(CellCoord(5, c))
            },
            walls = buildSet {
                addAll(hWall(0, 2..6))
                addAll(hWall(1, 2..6))
                addAll(hWall(2, 1..6))
                addAll(hWall(3, 2..6))
                addAll(hWall(4, 3..6))
            },
            holes = listOf(
                HoleDef("h-p8", GeckoColor.PURPLE, CellCoord(3, 6)),
                HoleDef("h-o8", GeckoColor.ORANGE, CellCoord(1, 6)),
                HoleDef("h-y8", GeckoColor.YELLOW, CellCoord(2, 6)),
                HoleDef("h-e8", GeckoColor.EMERALD, CellCoord(4, 6))
            ),
            geckos = listOf(
                GeckoDef(
                    "g-purple",
                    GeckoColor.PURPLE,
                    segs(3 to 1, 3 to 0, 2 to 0, 2 to 1, 1 to 1, 0 to 1)
                ),
                GeckoDef(
                    "g-orange",
                    GeckoColor.ORANGE,
                    segs(2 to 2, 2 to 3, 2 to 4, 2 to 5)
                ),
                GeckoDef(
                    "g-yellow",
                    GeckoColor.YELLOW,
                    segs(3 to 2, 3 to 3, 3 to 4, 3 to 5)
                ),
                GeckoDef(
                    "g-emerald",
                    GeckoColor.EMERALD,
                    segs(5 to 3, 5 to 2, 4 to 2, 4 to 3, 4 to 4, 4 to 5)
                )
            ),
            par = 6
        ),

        // 09: Pillar Garden (6x6 with 4 void pillars, 5 Geckos, Par 7) — Looping around pillars
        LevelDef(
            id = 9,
            name = "Pillar Garden",
            subtitle = "Taman terbuka dengan 4 pilar — kejar ekor sendiri mengelilingi pilar!",
            rows = 6,
            cols = 6,
            cells = rectCells(
                6,
                6,
                excluded = setOf(
                    CellCoord(1, 1), CellCoord(1, 4),
                    CellCoord(4, 1), CellCoord(4, 4)
                )
            ),
            walls = buildSet {
                add(WallEdge.of(2, 2, 2, 3))
            },
            holes = listOf(
                HoleDef("h-p9", GeckoColor.PURPLE, CellCoord(0, 5)),
                HoleDef("h-o9", GeckoColor.ORANGE, CellCoord(5, 0)),
                HoleDef("h-y9", GeckoColor.YELLOW, CellCoord(0, 0)),
                HoleDef("h-e9", GeckoColor.EMERALD, CellCoord(5, 5)),
                HoleDef("h-s9", GeckoColor.SKY, CellCoord(2, 5))
            ),
            geckos = listOf(
                GeckoDef("g-purple", GeckoColor.PURPLE, segs(0 to 2, 0 to 3, 1 to 3, 2 to 3)),
                GeckoDef("g-orange", GeckoColor.ORANGE, segs(3 to 1, 3 to 0, 4 to 0)),
                GeckoDef("g-yellow", GeckoColor.YELLOW, segs(2 to 1, 2 to 0, 1 to 0)),
                GeckoDef("g-emerald", GeckoColor.EMERALD, segs(3 to 4, 3 to 5, 4 to 5)),
                GeckoDef("g-sky", GeckoColor.SKY, segs(3 to 3, 4 to 3, 4 to 2, 5 to 2))
            ),
            par = 7
        ),

        // 10: Master Terrarium (6x7, 6 Geckos — all 6 colors, Par 8) — Grand Finale
        LevelDef(
            id = 10,
            name = "Master Terrarium",
            subtitle = "Finale: seluruh 6 warna gecko dalam satu terarium bersekat!",
            rows = 6,
            cols = 7,
            cells = rectCells(6, 7),
            walls = buildSet {
                addAll(hWall(0, 1..5))
                addAll(hWall(1, 0..2))
                addAll(hWall(1, 4..6))
                addAll(hWall(2, 1..2))
                addAll(hWall(2, 4..5))
                addAll(hWall(3, 0..2))
                addAll(hWall(3, 4..6))
                addAll(hWall(4, 1..5))
            },
            holes = listOf(
                HoleDef("h-p10", GeckoColor.PURPLE, CellCoord(0, 6)),
                HoleDef("h-o10", GeckoColor.ORANGE, CellCoord(0, 0)),
                HoleDef("h-y10", GeckoColor.YELLOW, CellCoord(2, 6)),
                HoleDef("h-e10", GeckoColor.EMERALD, CellCoord(3, 0)),
                HoleDef("h-c10", GeckoColor.CORAL, CellCoord(5, 6)),
                HoleDef("h-s10", GeckoColor.SKY, CellCoord(5, 0))
            ),
            geckos = listOf(
                GeckoDef("g-purple", GeckoColor.PURPLE, segs(0 to 3, 0 to 2, 0 to 1)),
                GeckoDef("g-orange", GeckoColor.ORANGE, segs(1 to 2, 1 to 1, 1 to 0)),
                GeckoDef("g-yellow", GeckoColor.YELLOW, segs(1 to 4, 1 to 5, 1 to 6)),
                GeckoDef("g-emerald", GeckoColor.EMERALD, segs(4 to 2, 4 to 1, 4 to 0)),
                GeckoDef("g-coral", GeckoColor.CORAL, segs(4 to 4, 4 to 5, 4 to 6)),
                GeckoDef("g-sky", GeckoColor.SKY, segs(5 to 3, 5 to 4, 5 to 5))
            ),
            par = 8
        )
    )

    fun byId(id: Int): LevelDef = ALL.first { it.id == id }

    fun byStringId(stringId: String): LevelDef? = ALL.firstOrNull { it.stringId == stringId }
}
