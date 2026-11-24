package com.binarydev.startergame.ui.gecko

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Leaderboard
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.binarydev.startergame.R
import com.binarydev.startergame.core.gecko.BlockedReason
import com.binarydev.startergame.core.gecko.GeckoColor
import com.binarydev.startergame.core.gecko.GeckoGameState
import com.binarydev.startergame.core.gecko.GeckoLevels
import com.binarydev.startergame.core.gecko.GeckoMove
import com.binarydev.startergame.core.gecko.LevelDef
import com.binarydev.startergame.core.gecko.calculateStars
import androidx.compose.material.icons.rounded.Bolt
import com.binarydev.startergame.data.preferences.GeckoProgressSnapshot
import com.binarydev.startergame.ui.performance.LocalGraphicsPerformanceConfig
import com.binarydev.startergame.ui.performance.SampledDrawableImage
import com.binarydev.startergame.ui.viewmodel.GameplayUiState
import com.binarydev.startergame.ui.viewmodel.HomeUiState
import kotlinx.coroutines.delay

private val TerrariumBgTop = Color(0xFF0D1B2A)
private val TerrariumBgBottom = Color(0xFF1A314A)
private val TerrariumCard = Color(0xFF172B42)
private val TerrariumCardBorder = Color(0xFF284666)
private val TerrariumAccent = Color(0xFF30CF8E)
private val StarGold = Color(0xFFFFD447)
private val HintAmber = Color(0xFFFFB733)

@Composable
fun GeckoHomeScreen(
    uiState: HomeUiState,
    progressSnapshot: GeckoProgressSnapshot,
    snakeHighScore: Int = 0,
    onPlayLevel: (String) -> Unit,
    onPlayClassicSnake: () -> Unit = {},
    onWatchRewardedAd: () -> Unit,
    onOpenLeaderboard: () -> Unit,
    onOpenAchievements: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val perfConfig = LocalGraphicsPerformanceConfig.current
    val activeCheckpointLevel = uiState.activeCheckpointLevelId?.let { GeckoLevels.byStringId(it) }
    val nextPlayableLevel = activeCheckpointLevel
        ?: GeckoLevels.byId(progressSnapshot.highestUnlocked.coerceIn(1, GeckoLevels.ALL.size))

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (perfConfig.lowRamMode) {
                    Modifier.background(TerrariumBgTop)
                } else {
                    Modifier.background(Brush.verticalGradient(listOf(TerrariumBgTop, TerrariumBgBottom)))
                }
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp)
                .align(Alignment.TopCenter)
        ) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 158.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                // Hero header
                item(span = { GridItemSpan(maxLineSpan) }) {
                    GeckoHomeHeader(
                        totalStars = progressSnapshot.totalStars,
                        maxStars = progressSnapshot.maxPossibleStars,
                        hintsCount = uiState.balance.hints,
                        hintsEnabled = uiState.hintsEnabled,
                        onOpenSettings = onOpenSettings
                    )
                }

                // Quick Play / Continue hero card
                item(span = { GridItemSpan(maxLineSpan) }) {
                    HeroPlayCard(
                        level = nextPlayableLevel,
                        isCheckpoint = activeCheckpointLevel != null,
                        onPlay = { onPlayLevel(nextPlayableLevel.stringId) }
                    )
                }

                // Classic Snake Mode + Local DataStore High Score card
                item(span = { GridItemSpan(maxLineSpan) }) {
                    ClassicSnakeHighScoreCard(
                        highScore = snakeHighScore,
                        onPlaySnake = onPlayClassicSnake
                    )
                }

                // Status banner if present
                if (!uiState.statusBanner.isNullOrBlank()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Surface(
                            color = TerrariumAccent.copy(alpha = 0.16f),
                            border = BorderStroke(1.dp, TerrariumAccent.copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = uiState.statusBanner,
                                color = Color(0xFFE6FFF4),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                            )
                        }
                    }
                }

                // Section title
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "10 Level Terarium",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White
                        )
                        Text(
                            text = "${progressSnapshot.records.count { it.value.stars > 0 }}/${GeckoLevels.ALL.size} Selesai",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF93B3D0)
                        )
                    }
                }

                // Level cards grid
                items(GeckoLevels.ALL, key = { it.id }) { level ->
                    val roomItem = uiState.levels.firstOrNull { it.levelId == level.stringId }
                    val unlocked = progressSnapshot.isUnlocked(level.id) || (roomItem?.unlocked == true)
                    val stars = progressSnapshot.starsFor(level.id)
                    val bestMoves = progressSnapshot.bestMovesFor(level.id)
                    val hasCheckpoint = roomItem?.hasCheckpoint == true

                    GeckoLevelCard(
                        level = level,
                        unlocked = unlocked,
                        stars = stars,
                        bestMoves = bestMoves,
                        hasCheckpoint = hasCheckpoint,
                        onClick = { if (unlocked) onPlayLevel(level.stringId) }
                    )
                }

                // Rewarded ad bonus card (when enabled)
                if (uiState.rewardedAdEnabled && uiState.hintsEnabled) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        OutlinedButton(
                            onClick = onWatchRewardedAd,
                            enabled = !uiState.isLoadingAd,
                            border = BorderStroke(1.dp, HintAmber.copy(alpha = 0.55f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = HintAmber),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("watch_rewarded_ad_button")
                        ) {
                            Icon(Icons.Rounded.Lightbulb, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (uiState.isLoadingAd) "Memuat Rewarded Ad..." else "Tonton Iklan (+Bonus Hint)",
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // Bottom utility bar
            Surface(
                color = Color(0xFF102033),
                tonalElevation = 6.dp,
                border = BorderStroke(1.dp, TerrariumCardBorder.copy(alpha = 0.7f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    UtilityBarButton(
                        icon = Icons.Rounded.Leaderboard,
                        label = "Ranking",
                        testTag = "home_leaderboard_button",
                        onClick = onOpenLeaderboard,
                        modifier = Modifier.weight(1f)
                    )
                    UtilityBarButton(
                        icon = Icons.Rounded.EmojiEvents,
                        label = "Pencapaian",
                        testTag = "home_achievements_button",
                        onClick = onOpenAchievements,
                        modifier = Modifier.weight(1f)
                    )
                    UtilityBarButton(
                        icon = Icons.Rounded.Settings,
                        label = "Pengaturan",
                        testTag = "home_settings_button",
                        onClick = onOpenSettings,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun GeckoHomeHeader(
    totalStars: Int,
    maxStars: Int,
    hintsCount: Int,
    hintsEnabled: Boolean,
    onOpenSettings: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SampledDrawableImage(
                resId = R.drawable.gecko_out_icon_fg_1795414597936,
                maxSizeDp = 48.dp,
                contentDescription = "Logo Gecko Out",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.5.dp, TerrariumAccent.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
            )
            Column {
                Text(
                    text = "Gecko Out",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White
                )
                Text(
                    text = "Sliding Snake Puzzle",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF93B3D0)
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = StarGold.copy(alpha = 0.16f),
                border = BorderStroke(1.dp, StarGold.copy(alpha = 0.45f)),
                shape = RoundedCornerShape(999.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Star,
                        contentDescription = "Total bintang",
                        tint = StarGold,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "$totalStars/$maxStars",
                        color = StarGold,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            if (hintsEnabled) {
                Surface(
                    color = HintAmber.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, HintAmber.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(999.dp),
                    modifier = Modifier.testTag("home_hints_chip")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Lightbulb,
                            contentDescription = "Saldo hint",
                            tint = HintAmber,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "$hintsCount",
                            color = HintAmber,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("header_settings_icon")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = "Pengaturan",
                    tint = Color(0xFFB5CDE3)
                )
            }
        }
    }
}

@Composable
private fun ClassicSnakeHighScoreCard(
    highScore: Int,
    onPlaySnake: () -> Unit
) {
    Surface(
        color = TerrariumCard,
        border = BorderStroke(1.dp, TerrariumAccent.copy(alpha = 0.45f)),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("home_classic_snake_card")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    color = StarGold.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, StarGold.copy(alpha = 0.45f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.EmojiEvents,
                            contentDescription = "High Score",
                            tint = StarGold,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Classic Snake",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "High Score: $highScore",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = StarGold,
                        modifier = Modifier.testTag("home_high_score_badge")
                    )
                }
            }

            Button(
                onClick = onPlaySnake,
                colors = ButtonDefaults.buttonColors(
                    containerColor = TerrariumAccent,
                    contentColor = Color(0xFF062318)
                ),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                modifier = Modifier
                    .height(48.dp)
                    .testTag("home_play_snake_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(text = "Play", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun HeroPlayCard(
    level: LevelDef,
    isCheckpoint: Boolean,
    onPlay: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(1.5.dp, TerrariumAccent.copy(alpha = 0.5f)),
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF1B3A52), Color(0xFF14353A))
                ),
                shape = RoundedCornerShape(22.dp)
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Surface(
                    color = TerrariumAccent.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Text(
                        text = if (isCheckpoint) "LANJUTKAN CHECKPOINT" else "LEVEL ${level.id.toString().padStart(2, '0')} · PAR ${level.par}",
                        color = TerrariumAccent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                    )
                }
                Text(
                    text = level.name,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White
                )
                Text(
                    text = level.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFB7D2E8),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    level.geckos.forEach { g ->
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(Color(g.color.bodyHex))
                                .border(1.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                        )
                    }
                }
            }

            Spacer(Modifier.width(12.dp))

            Button(
                onClick = onPlay,
                colors = ButtonDefaults.buttonColors(
                    containerColor = TerrariumAccent,
                    contentColor = Color(0xFF072318)
                ),
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
                modifier = Modifier
                    .height(52.dp)
                    .testTag("hero_play_button")
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text(
                    text = if (isCheckpoint) "Lanjut" else "Main",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun GeckoLevelCard(
    level: LevelDef,
    unlocked: Boolean,
    stars: Int,
    bestMoves: Int?,
    hasCheckpoint: Boolean,
    onClick: () -> Unit
) {
    val borderColor = when {
        stars == 3 -> StarGold.copy(alpha = 0.65f)
        unlocked -> TerrariumCardBorder
        else -> Color(0xFF1C3047)
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (unlocked) TerrariumCard else Color(0xFF112033)
        ),
        border = BorderStroke(1.2.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(enabled = unlocked, onClick = onClick)
            .testTag("play_${level.stringId}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = if (unlocked) TerrariumAccent.copy(alpha = 0.18f) else Color(0xFF1E3249),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = level.id.toString().padStart(2, '0'),
                        color = if (unlocked) TerrariumAccent else Color(0xFF6B85A1),
                        fontWeight = FontWeight.ExtraBold,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                if (!unlocked) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = "Terkunci",
                        tint = Color(0xFF5D7692),
                        modifier = Modifier.size(18.dp)
                    )
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        for (s in 1..3) {
                            Icon(
                                imageVector = if (s <= stars) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                                contentDescription = null,
                                tint = if (s <= stars) StarGold else Color(0xFF496684),
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                }
            }

            Text(
                text = level.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (unlocked) Color.White else Color(0xFF7B93AC),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    level.geckos.forEach { g ->
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(
                                    Color(g.color.bodyHex).copy(alpha = if (unlocked) 1f else 0.35f)
                                )
                        )
                    }
                }
                Text(
                    text = when {
                        hasCheckpoint -> "Lanjut"
                        bestMoves != null -> "$bestMoves/${level.par} mv"
                        else -> "Par ${level.par}"
                    },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (hasCheckpoint) TerrariumAccent else Color(0xFF93B3D0)
                )
            }
        }
    }
}

@Composable
private fun UtilityBarButton(
    icon: ImageVector,
    label: String,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilledTonalButton(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = Color(0xFF1A314A),
            contentColor = Color(0xFFD7E8F7)
        ),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
        modifier = modifier
            .height(48.dp)
            .testTag(testTag)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp)
    }
}

// --- Gecko Out Dedicated Full-Screen Gameplay Experience ---

@Composable
fun GeckoGameplayScreen(
    uiState: GameplayUiState<GeckoGameState>,
    soundManager: GeckoSoundManager,
    onMove: (GeckoMove) -> Unit,
    onRequestHint: () -> Unit,
    onStartLevel: (String) -> Unit,
    onBackToHome: () -> Unit,
    onToggleLowRamMode: (Boolean) -> Unit = {},
    onLevelWonPersist: (levelId: Int, moves: Int, par: Int) -> Unit
) {
    BackHandler(onBack = onBackToHome)

    val perfConfig = LocalGraphicsPerformanceConfig.current
    val state = uiState.gameState
    var elapsedSec by remember(state?.attemptId) { mutableIntStateOf(0) }
    var showVictoryDialog by remember(state?.attemptId) { mutableStateOf(false) }
    var persistedWinAttemptId by remember { mutableStateOf<String?>(null) }

    // Live timer while level is active
    LaunchedEffect(state?.attemptId, state?.isSolved) {
        if (state == null || state.isSolved) return@LaunchedEffect
        while (true) {
            delay(1_000L)
            elapsedSec++
        }
    }

    // Audio & haptic feedback reacting to gameplay events
    LaunchedEffect(state?.eventSeq) {
        if (state == null || state.eventSeq == 0) return@LaunchedEffect
        when {
            state.lastEscapedGeckoId != null -> soundManager.playEscapeArpeggio()
            state.lastBlockedReason == BlockedReason.TAIL_ON_HOLE ||
                state.lastBlockedReason == BlockedReason.WRONG_HOLE -> soundManager.playBlockedThud()
            state.activeHint != null -> soundManager.playUiClick()
            else -> soundManager.playStepPop()
        }
    }

    // Auto-dismiss transient feedback toast after 2.6 seconds
    LaunchedEffect(state?.eventSeq, state?.lastFeedback) {
        if (!state?.lastFeedback.isNullOrBlank()) {
            delay(2_600L)
            onMove(GeckoMove.ClearTransientFeedback)
        }
    }

    // Celebration delay (~480ms after final dive-into-hole) before showing Level Cleared dialog
    LaunchedEffect(state?.isSolved, state?.attemptId) {
        if (state != null && state.isSolved) {
            if (persistedWinAttemptId != state.attemptId) {
                persistedWinAttemptId = state.attemptId
                onLevelWonPersist(state.levelNumber, state.board.moves.coerceAtLeast(1), state.par)
            }
            delay(480L)
            soundManager.playVictoryFanfare()
            showVictoryDialog = true
        } else {
            showVictoryDialog = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (perfConfig.lowRamMode) {
                    Modifier.background(TerrariumBgTop)
                } else {
                    Modifier.background(Brush.verticalGradient(listOf(TerrariumBgTop, TerrariumBgBottom)))
                }
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp)
                .align(Alignment.TopCenter)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Top HUD Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = TerrariumCard,
                    border = BorderStroke(1.dp, TerrariumCardBorder),
                    shape = CircleShape,
                    modifier = Modifier.size(48.dp)
                ) {
                    IconButton(
                        onClick = {
                            soundManager.playUiClick()
                            onBackToHome()
                        },
                        modifier = Modifier.testTag("gameplay_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Kembali ke daftar level",
                            tint = Color.White
                        )
                    }
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                ) {
                    Text(
                        text = if (state != null) "Level ${state.levelNumber} · ${state.levelName}" else uiState.levelId.uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (state != null) {
                        Text(
                            text = state.levelSubtitle,
                            fontSize = 12.sp,
                            color = Color(0xFF93B3D0),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Quick one-tap Mode Ringan (Hemat RAM) toggle pill
                    Surface(
                        color = if (perfConfig.lowRamMode) TerrariumAccent.copy(alpha = 0.22f) else TerrariumCard,
                        border = BorderStroke(
                            1.dp,
                            if (perfConfig.lowRamMode) TerrariumAccent else TerrariumCardBorder
                        ),
                        shape = RoundedCornerShape(999.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .clickable {
                                soundManager.playUiClick()
                                onToggleLowRamMode(!perfConfig.lowRamMode)
                            }
                            .testTag("gameplay_low_ram_toggle")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Bolt,
                                contentDescription = if (perfConfig.lowRamMode) "Mode Ringan aktif" else "Mode Standar aktif",
                                tint = if (perfConfig.lowRamMode) TerrariumAccent else Color(0xFF93B3D0),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = if (perfConfig.lowRamMode) "Ringan" else "HD",
                                color = if (perfConfig.lowRamMode) TerrariumAccent else Color(0xFFB5CDE3),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }

                    // Timer pill
                    Surface(
                        color = TerrariumCard,
                        border = BorderStroke(1.dp, TerrariumCardBorder),
                        shape = RoundedCornerShape(999.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Timer,
                                contentDescription = "Waktu",
                                tint = Color(0xFF93B3D0),
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = formatTimer(elapsedSec),
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            // Stats Strip: Moves / Par + Remaining Geckos indicator
            if (state != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = TerrariumCard,
                        border = BorderStroke(1.dp, TerrariumCardBorder),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Langkah: ${state.board.moves}",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                modifier = Modifier.testTag("hud_moves_text")
                            )
                            Text(
                                text = "Par ${state.par}",
                                color = if (state.board.moves <= state.par) TerrariumAccent else HintAmber,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                        }
                    }

                    Surface(
                        color = TerrariumCard,
                        border = BorderStroke(1.dp, TerrariumCardBorder),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Gecko:",
                                color = Color(0xFF93B3D0),
                                fontSize = 13.sp
                            )
                            state.board.geckos.forEach { g ->
                                Box(
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (g.escaped) {
                                                Color(0xFF28425E)
                                            } else {
                                                Color(g.color.bodyHex)
                                            }
                                        )
                                        .border(
                                            width = 1.dp,
                                            color = if (g.escaped) Color(0xFF3E5E80) else Color.White.copy(alpha = 0.65f),
                                            shape = CircleShape
                                        )
                                )
                            }
                        }
                    }
                }
            }

            // Isometric Interactive Board Container
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                if (state != null) {
                    GeckoBoardCanvas(
                        board = state.board,
                        activeHint = state.activeHint,
                        onDragStep = { geckoId, end, target, pushUndo ->
                            onMove(
                                GeckoMove.DragStep(
                                    geckoId = geckoId,
                                    end = end,
                                    target = target,
                                    pushUndoSnapshot = pushUndo
                                )
                            )
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Floating feedback toast pill (for TAIL_ON_HOLE, WRONG_HOLE, Hint, Deadlock, Escaped!)
                val bannerText = state?.lastFeedback ?: uiState.statusMessage
                androidx.compose.animation.AnimatedVisibility(
                    visible = !bannerText.isNullOrBlank(),
                    enter = fadeIn() + slideInVertically { -it / 2 },
                    exit = fadeOut() + slideOutVertically { -it / 2 },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 4.dp)
                ) {
                    val isWarning = state?.lastBlockedReason != null ||
                        bannerText?.contains("buntu", ignoreCase = true) == true
                    Surface(
                        color = if (isWarning) Color(0xEE5C1D2E) else Color(0xEE143844),
                        border = BorderStroke(
                            1.dp,
                            if (isWarning) Color(0xFFFF6B8A) else TerrariumAccent.copy(alpha = 0.7f)
                        ),
                        shape = RoundedCornerShape(999.dp),
                        shadowElevation = 6.dp,
                        modifier = Modifier.testTag("gameplay_feedback_banner")
                    ) {
                        Text(
                            text = bannerText.orEmpty(),
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            // Helpful subtle gesture hint caption on Level 1 before first move
            if (state != null && state.levelNumber == 1 && state.board.moves == 0) {
                Text(
                    text = "Tip: Ketuk gecko untuk lihat titik jalur, lalu ketuk titik tujuan agar langsung jalan — atau seret langsung!",
                    color = Color(0xFF9EC0DE),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                )
            }

            // Bottom Control Dock: Undo, Hint, Restart
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ActionControlButton(
                    icon = Icons.AutoMirrored.Rounded.Undo,
                    label = if ((state?.undoStack?.size ?: 0) > 0) "Undo (${state?.undoStack?.size})" else "Undo",
                    enabled = state?.canUndo == true,
                    containerColor = TerrariumCard,
                    contentColor = Color.White,
                    testTag = "undo_button",
                    onClick = {
                        soundManager.playUiClick()
                        onMove(GeckoMove.Undo)
                    },
                    modifier = Modifier.weight(1f)
                )

                if (uiState.hintsEnabled) {
                    ActionControlButton(
                        icon = Icons.Rounded.Lightbulb,
                        label = "Hint",
                        enabled = state != null && !state.isSolved,
                        containerColor = HintAmber.copy(alpha = 0.22f),
                        contentColor = HintAmber,
                        borderColor = HintAmber.copy(alpha = 0.55f),
                        testTag = "use_hint_button",
                        onClick = {
                            soundManager.playUiClick()
                            if (uiState.balance.hints > 0) {
                                onRequestHint()
                            } else {
                                // Still allow live solver hint so player never feels stuck in offline demo
                                onMove(GeckoMove.RequestSolverHint)
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }

                ActionControlButton(
                    icon = Icons.Rounded.Refresh,
                    label = "Restart",
                    enabled = state != null && (state.board.moves > 0 || state.isSolved),
                    containerColor = TerrariumCard,
                    contentColor = Color(0xFFFF9FB2),
                    testTag = "restart_button",
                    onClick = {
                        soundManager.playUiClick()
                        elapsedSec = 0
                        showVictoryDialog = false
                        onMove(GeckoMove.Restart)
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    // Celebratory Level Cleared Dialog
    if (showVictoryDialog && state != null && state.isSolved) {
        val nextLevel = GeckoLevels.ALL.firstOrNull { it.id == state.levelNumber + 1 }
        LevelClearedDialog(
            levelName = state.levelName,
            levelNumber = state.levelNumber,
            moves = state.board.moves.coerceAtLeast(1),
            par = state.par,
            elapsedSec = elapsedSec,
            nextLevel = nextLevel,
            onNextLevel = {
                showVictoryDialog = false
                if (nextLevel != null) {
                    elapsedSec = 0
                    onStartLevel(nextLevel.stringId)
                } else {
                    onBackToHome()
                }
            },
            onReplay = {
                showVictoryDialog = false
                elapsedSec = 0
                onStartLevel(state.levelId)
            },
            onHome = {
                showVictoryDialog = false
                onBackToHome()
            }
        )
    }
}

@Composable
private fun ActionControlButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    containerColor: Color,
    contentColor: Color,
    borderColor: Color = TerrariumCardBorder,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = Color(0xFF122234),
            disabledContentColor = Color(0xFF536E88)
        ),
        border = BorderStroke(1.dp, if (enabled) borderColor else Color(0xFF1F344C)),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 12.dp),
        modifier = modifier
            .height(52.dp)
            .testTag(testTag)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(19.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun LevelClearedDialog(
    levelName: String,
    levelNumber: Int,
    moves: Int,
    par: Int,
    elapsedSec: Int,
    nextLevel: LevelDef?,
    onNextLevel: () -> Unit,
    onReplay: () -> Unit,
    onHome: () -> Unit
) {
    val stars = calculateStars(moves, par)
    var revealStars by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(120L)
        revealStars = true
    }

    Dialog(
        onDismissRequest = onHome,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false)
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = Color(0xFF14273D),
            border = BorderStroke(1.5.dp, TerrariumAccent.copy(alpha = 0.65f)),
            shadowElevation = 18.dp,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("level_cleared_dialog")
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = "Level Selesai",
                    tint = TerrariumAccent,
                    modifier = Modifier.size(44.dp)
                )

                Text(
                    text = "Level $levelNumber Selesai!",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = levelName,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFFA7C6E2)
                )

                // Animated 1-3 Stars Row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (idx in 1..3) {
                        val earned = idx <= stars
                        val scale by animateFloatAsState(
                            targetValue = if (revealStars && earned) 1f else 0.72f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessLow
                            ),
                            label = "star_$idx"
                        )
                        Icon(
                            imageVector = if (earned) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                            contentDescription = if (earned) "Bintang $idx diraih" else "Bintang $idx kosong",
                            tint = if (earned) StarGold else Color(0xFF425D7A),
                            modifier = Modifier
                                .size(if (idx == 2) 52.dp else 42.dp)
                                .scale(scale)
                        )
                    }
                }

                // Stats summary card
                Surface(
                    color = Color(0xFF0E1D2F),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp, horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StatMetric(label = "Langkah", value = "$moves")
                        StatMetric(label = "Target Par", value = "$par")
                        StatMetric(label = "Waktu", value = formatTimer(elapsedSec))
                    }
                }

                Button(
                    onClick = onNextLevel,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TerrariumAccent,
                        contentColor = Color(0xFF062318)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("victory_next_level_button")
                ) {
                    Text(
                        text = if (nextLevel != null) "Level ${nextLevel.id}: ${nextLevel.name}" else "Semua Level Tamat!",
                        fontWeight = FontWeight.Bold
                    )
                    if (nextLevel != null) {
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onReplay,
                        border = BorderStroke(1.dp, TerrariumCardBorder),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("victory_replay_button")
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Ulangi")
                    }

                    OutlinedButton(
                        onClick = onHome,
                        border = BorderStroke(1.dp, TerrariumCardBorder),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFB5CDE3)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("victory_home_button")
                    ) {
                        Text("Daftar Level")
                    }
                }
            }
        }
    }
}

@Composable
private fun StatMetric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            text = label,
            fontSize = 12.sp,
            color = Color(0xFF8AA8C6)
        )
    }
}

private fun formatTimer(totalSec: Int): String {
    val m = totalSec / 60
    val s = totalSec % 60
    return "${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
}
