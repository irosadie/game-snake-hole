package com.binarydev.startergame

import android.content.ComponentCallbacks2
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import com.binarydev.startergame.core.contracts.DevicePreferences
import com.binarydev.startergame.core.gecko.GeckoGameState
import com.binarydev.startergame.core.gecko.GeckoMove
import com.binarydev.startergame.core.gecko.LevelDef
import com.binarydev.startergame.data.preferences.GeckoProgressSnapshot
import com.binarydev.startergame.di.StarterAppContainer
import com.binarydev.startergame.ui.audio.SoundManager
import com.binarydev.startergame.ui.gecko.GeckoGameplayScreen
import com.binarydev.startergame.ui.gecko.GeckoHomeScreen
import com.binarydev.startergame.ui.gecko.GeckoSoundManager
import com.binarydev.startergame.ui.navigation.StarterGameApp
import com.binarydev.startergame.ui.performance.DevicePerformanceInfo
import com.binarydev.startergame.ui.performance.GraphicsPerformanceConfig
import com.binarydev.startergame.ui.performance.LocalGraphicsPerformanceConfig
import com.binarydev.startergame.ui.theme.StarterGameTheme
import com.binarydev.startergame.ui.theme.geckoOutThemeConfig
import com.google.android.gms.ads.MobileAds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var appContainer: StarterAppContainer<LevelDef, GeckoGameState, GeckoMove>
    private lateinit var soundManager: GeckoSoundManager
    private lateinit var mediaSoundManager: SoundManager

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val perfInfo = DevicePerformanceInfo.detect(applicationContext)
        appContainer = StarterAppContainer.createDefaultGeckoOutContainer(applicationContext)
        appContainer.updateCurrentActivity(this)
        appContainer.simulatedAdAdapter.interactiveUiAttached = true
        soundManager = GeckoSoundManager(applicationContext)
        mediaSoundManager = SoundManager(applicationContext)

        // Defer AdMob SDK init to IO background thread so low-end CPUs launch the first frame immediately
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                MobileAds.initialize(this@MainActivity) {
                    appContainer.adMobAdapter.preloadAd()
                }
            }
        }

        appContainer.fcmAdapter.fetchAndRegisterFirebaseTokenIfAvailable()
        lifecycle.addObserver(
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> {
                        mediaSoundManager.onAppForegrounded()
                        lifecycleScope.launch {
                            appContainer.configRepository.refreshIfNeeded(System.currentTimeMillis())
                        }
                    }
                    Lifecycle.Event.ON_PAUSE -> {
                        mediaSoundManager.onAppBackgrounded()
                    }
                    else -> Unit
                }
            }
        )

        setContent {
            val initialPrefs = remember(perfInfo) {
                DevicePreferences(lowRamGraphicsMode = perfInfo.recommendedLowRamMode)
            }
            val devicePrefs by appContainer.devicePreferencesRepository.preferencesFlow.collectAsState(
                initial = initialPrefs
            )
            val geckoProgress by appContainer.geckoProgressRepository.progressFlow.collectAsState(
                initial = GeckoProgressSnapshot()
            )
            val snakeHighScore by appContainer.highScoreRepository.highScoreFlow.collectAsState(
                initial = 0
            )
            val graphicsConfig = remember(devicePrefs.lowRamGraphicsMode) {
                GraphicsPerformanceConfig.forLowRamMode(devicePrefs.lowRamGraphicsMode)
            }

            LaunchedEffect(devicePrefs.musicEnabled, devicePrefs.soundEffectsEnabled, devicePrefs.vibrationEnabled) {
                soundManager.soundEnabled = devicePrefs.soundEffectsEnabled
                soundManager.vibrationEnabled = devicePrefs.vibrationEnabled
                mediaSoundManager.isMusicEnabled = devicePrefs.musicEnabled
                mediaSoundManager.isSoundEffectsEnabled = devicePrefs.soundEffectsEnabled
                if (devicePrefs.musicEnabled) {
                    mediaSoundManager.resumeBackgroundMusic()
                } else {
                    mediaSoundManager.pauseBackgroundMusic()
                }
            }

            CompositionLocalProvider(LocalGraphicsPerformanceConfig provides graphicsConfig) {
                StarterGameTheme(themeConfig = geckoOutThemeConfig()) {
                    StarterGameApp(
                        container = appContainer,
                        puzzleBoardContent = { _, _ -> },
                        customHomeScreen = { homeState, onPlayLevel, onPlayClassicSnake, onWatchRewardedAd, onOpenLeaderboard, onOpenAchievements, onOpenSettings ->
                            GeckoHomeScreen(
                                uiState = homeState,
                                progressSnapshot = geckoProgress,
                                snakeHighScore = snakeHighScore,
                                onPlayLevel = onPlayLevel,
                                onPlayClassicSnake = onPlayClassicSnake,
                                onWatchRewardedAd = onWatchRewardedAd,
                                onOpenLeaderboard = onOpenLeaderboard,
                                onOpenAchievements = onOpenAchievements,
                                onOpenSettings = onOpenSettings
                            )
                        },
                        customGameplayScreen = { gameplayState, onMove, onRequestHint, onStartLevel, onBackToHome ->
                            GeckoGameplayScreen(
                                uiState = gameplayState,
                                soundManager = soundManager,
                                onMove = onMove,
                                onRequestHint = onRequestHint,
                                onStartLevel = onStartLevel,
                                onBackToHome = onBackToHome,
                                onToggleLowRamMode = { enabled ->
                                    lifecycleScope.launch {
                                        appContainer.devicePreferencesRepository.setLowRamGraphicsMode(enabled)
                                    }
                                },
                                onLevelWonPersist = { levelId, moves, par ->
                                    lifecycleScope.launch {
                                        appContainer.geckoProgressRepository.recordLevelCompleted(
                                            levelId = levelId,
                                            moves = moves,
                                            par = par
                                        )
                                    }
                                }
                            )
                        }
                    )
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            if (::soundManager.isInitialized) {
                soundManager.trimMemory()
            }
            if (::mediaSoundManager.isInitialized) {
                mediaSoundManager.trimMemory()
            }
        }
    }

    override fun onDestroy() {
        if (::soundManager.isInitialized) {
            soundManager.release()
        }
        if (::mediaSoundManager.isInitialized) {
            mediaSoundManager.release()
        }
        appContainer.updateCurrentActivity(null)
        super.onDestroy()
    }
}
