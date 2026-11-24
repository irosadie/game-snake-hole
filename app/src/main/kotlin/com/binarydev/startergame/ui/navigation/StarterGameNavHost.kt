package com.binarydev.startergame.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.binarydev.startergame.di.AppContainer
import com.binarydev.startergame.ui.screens.AchievementsScreen
import com.binarydev.startergame.ui.screens.GameplayScreen
import com.binarydev.startergame.ui.screens.HomeScreen
import com.binarydev.startergame.ui.screens.LeaderboardScreen
import com.binarydev.startergame.ui.screens.PersistentBottomAdMobBanner
import com.binarydev.startergame.ui.screens.ResultScreen
import com.binarydev.startergame.ui.screens.SettingsScreen
import com.binarydev.startergame.ui.screens.SimulatedRewardedVideoAdOverlay
import com.binarydev.startergame.ui.screens.SplashScreen
import com.binarydev.startergame.ui.snake.SnakeGameScreen
import com.binarydev.startergame.ui.viewmodel.AchievementsViewModel
import com.binarydev.startergame.ui.viewmodel.GameplayUiState
import com.binarydev.startergame.ui.viewmodel.GameplayViewModel
import com.binarydev.startergame.ui.viewmodel.HomeUiState
import com.binarydev.startergame.ui.viewmodel.HomeViewModel
import com.binarydev.startergame.ui.viewmodel.LeaderboardViewModel
import com.binarydev.startergame.ui.viewmodel.SettingsViewModel
import com.binarydev.startergame.ui.viewmodel.SnakeGameViewModel

object StarterDestinations {
    const val SPLASH = "splash"
    const val HOME = "home"
    const val GAMEPLAY = "gameplay"
    const val SNAKE = "snake"
    const val RESULT = "result"
    const val LEADERBOARD = "leaderboard"
    const val ACHIEVEMENTS = "achievements"
    const val SETTINGS = "settings"
}

/**
 * Navigation host connecting all screens with scoped per-route StateFlow collection so high-frequency
 * gameplay moves never trigger whole-tree recompositions of unrelated screens or the NavHost wrapper.
 */
@Composable
fun <LevelData, State, Move> StarterGameApp(
    container: AppContainer<LevelData, State, Move>,
    puzzleBoardContent: @Composable (State, (Move) -> Unit) -> Unit,
    customHomeScreen: (
        @Composable (
            uiState: HomeUiState,
            onPlayLevel: (String) -> Unit,
            onPlayClassicSnake: () -> Unit,
            onWatchRewardedAd: () -> Unit,
            onOpenLeaderboard: () -> Unit,
            onOpenAchievements: () -> Unit,
            onOpenSettings: () -> Unit
        ) -> Unit
    )? = null,
    customGameplayScreen: (
        @Composable (
            uiState: GameplayUiState<State>,
            onMove: (Move) -> Unit,
            onRequestHint: () -> Unit,
            onStartLevel: (String) -> Unit,
            onBackToHome: () -> Unit
        ) -> Unit
    )? = null
) {
    val navController = rememberNavController()

    val homeViewModel = remember(container) { HomeViewModel(container) }
    val gameplayViewModel = remember(container) { GameplayViewModel(container) }
    val leaderboardViewModel = remember(container) { LeaderboardViewModel(container) }
    val achievementsViewModel = remember(container) { AchievementsViewModel(container) }
    val settingsViewModel = remember(container) { SettingsViewModel(container) }

    val activeAdSession by container.simulatedAdAdapter.activeSession.collectAsState()
    val currentAdSession = activeAdSession
    if (currentAdSession != null) {
        SimulatedRewardedVideoAdOverlay(session = currentAdSession)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = StarterDestinations.SPLASH,
            modifier = Modifier.weight(1f)
        ) {
            composable(StarterDestinations.SPLASH) {
                SplashScreen(
                    gameTitle = container.ruleEngine.metadata.title,
                    onSplashFinished = {
                        navController.navigate(StarterDestinations.HOME) {
                            popUpTo(StarterDestinations.SPLASH) { inclusive = true }
                        }
                    }
                )
            }

            composable(StarterDestinations.HOME) {
                val homeState by homeViewModel.uiState.collectAsState()
                val playAction: (String) -> Unit = { levelId ->
                    gameplayViewModel.startLevel(levelId)
                    navController.navigate(StarterDestinations.GAMEPLAY)
                }
                if (customHomeScreen != null) {
                    customHomeScreen(
                        homeState,
                        playAction,
                        { navController.navigate(StarterDestinations.SNAKE) },
                        { homeViewModel.watchRewardedAdForBonus() },
                        {
                            leaderboardViewModel.refresh()
                            navController.navigate(StarterDestinations.LEADERBOARD)
                        },
                        { navController.navigate(StarterDestinations.ACHIEVEMENTS) },
                        { navController.navigate(StarterDestinations.SETTINGS) }
                    )
                } else {
                    HomeScreen(
                        uiState = homeState,
                        onPlayLevel = playAction,
                        onWatchRewardedAd = { homeViewModel.watchRewardedAdForBonus() },
                        onOpenLeaderboard = {
                            leaderboardViewModel.refresh()
                            navController.navigate(StarterDestinations.LEADERBOARD)
                        },
                        onOpenAchievements = { navController.navigate(StarterDestinations.ACHIEVEMENTS) },
                        onOpenSettings = { navController.navigate(StarterDestinations.SETTINGS) }
                    )
                }
            }

            composable(StarterDestinations.SNAKE) {
                val context = LocalContext.current
                val snakeVm: SnakeGameViewModel = viewModel(
                    factory = SnakeGameViewModel.provideFactory(
                        context = context,
                        highScoreRepository = container.highScoreRepository
                    )
                )
                SnakeGameScreen(
                    viewModel = snakeVm,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(StarterDestinations.GAMEPLAY) {
                val gameplayState by gameplayViewModel.uiState.collectAsState()
                if (customGameplayScreen != null) {
                    customGameplayScreen(
                        gameplayState,
                        { move -> gameplayViewModel.onMove(move) },
                        { gameplayViewModel.onRequestHint() },
                        { levelId -> gameplayViewModel.startLevel(levelId) },
                        { navController.popBackStack(StarterDestinations.HOME, inclusive = false) }
                    )
                } else {
                    GameplayScreen(
                        uiState = gameplayState,
                        onMove = { move -> gameplayViewModel.onMove(move) },
                        onRequestHint = { gameplayViewModel.onRequestHint() },
                        onRecoverLifeViaAd = { gameplayViewModel.recoverLivesViaRewardedAd() },
                        onNavigateToResult = { navController.navigate(StarterDestinations.RESULT) },
                        onBackToHome = { navController.popBackStack(StarterDestinations.HOME, inclusive = false) },
                        puzzleBoardContent = puzzleBoardContent
                    )
                }
            }

            composable(StarterDestinations.RESULT) {
                val gameplayState by gameplayViewModel.uiState.collectAsState()
                ResultScreen(
                    result = gameplayState.latestResult,
                    livesEnabled = gameplayState.livesEnabled,
                    remainingLives = gameplayState.balance.lives,
                    onReplayLevel = { levelId ->
                        gameplayViewModel.startLevel(levelId)
                        navController.popBackStack(StarterDestinations.GAMEPLAY, inclusive = false)
                    },
                    onPlayNextLevel = { nextLevelId ->
                        gameplayViewModel.startLevel(nextLevelId)
                        navController.popBackStack(StarterDestinations.GAMEPLAY, inclusive = false)
                    },
                    onRecoverLifeViaAd = { gameplayViewModel.recoverLivesViaRewardedAd() },
                    onBackToHome = { navController.popBackStack(StarterDestinations.HOME, inclusive = false) }
                )
            }

            composable(StarterDestinations.LEADERBOARD) {
                val leaderboardState by leaderboardViewModel.state.collectAsState()
                val leaderboardRawJson by leaderboardViewModel.lastRawJson.collectAsState()
                val leaderboardSimOffline by leaderboardViewModel.isSimulatingOffline.collectAsState()
                LeaderboardScreen(
                    state = leaderboardState,
                    rawJsonPayload = leaderboardRawJson,
                    isSimulatingOffline = leaderboardSimOffline,
                    onRetry = { leaderboardViewModel.refresh() },
                    onToggleSimulateOffline = { leaderboardViewModel.toggleSimulateBackendOffline() },
                    onBack = { navController.popBackStack() }
                )
            }

            composable(StarterDestinations.ACHIEVEMENTS) {
                val achievementsState by achievementsViewModel.achievements.collectAsState()
                val achievementsUiState by achievementsViewModel.uiState.collectAsState()
                AchievementsScreen(
                    achievements = achievementsState,
                    uiState = achievementsUiState,
                    onRefreshFromApi = { achievementsViewModel.refreshFromApi() },
                    onToggleSimulateOffline = { achievementsViewModel.toggleSimulateBackendOffline() },
                    onBack = { navController.popBackStack() }
                )
            }

            composable(StarterDestinations.SETTINGS) {
                val settingsState by settingsViewModel.uiState.collectAsState()
                SettingsScreen(
                    uiState = settingsState,
                    onToggleMusic = { settingsViewModel.toggleMusic(it) },
                    onToggleSoundEffects = { settingsViewModel.toggleSoundEffects(it) },
                    onToggleVibration = { settingsViewModel.toggleVibration(it) },
                    onToggleLowRamGraphicsMode = { settingsViewModel.toggleLowRamGraphicsMode(it) },
                    onSignInGoogle = { settingsViewModel.signInWithGoogle() },
                    onSignOut = { settingsViewModel.signOut() },
                    onTriggerSimulatedConfig = { hints, lives, ads, failApi ->
                        settingsViewModel.triggerSimulatedConfigChange(hints, lives, ads, failApi)
                    },
                    onTriggerFullBackendAdControl = { hints, lives, banner, rewarded, interstitial, failApi ->
                        settingsViewModel.triggerSimulatedConfigChange(
                            hintsEnabled = hints,
                            livesEnabled = lives,
                            rewardedAdsEnabled = rewarded,
                            simulateApiFailure = failApi,
                            bannerAdsEnabled = banner,
                            interstitialAdsEnabled = interstitial
                        )
                    },
                    onSetSimulatedAdMode = { settingsViewModel.setSimulatedAdMode(it) },
                    onToggleRealAdMobTestAds = { settingsViewModel.setUseRealAdMobTestAds(it) },
                    onSubscribeRemoveAdsGooglePlay = { settingsViewModel.subscribeRemoveAdsGooglePlay() },
                    onRestoreRemoveAdsSubscription = { settingsViewModel.restoreRemoveAdsSubscription() },
                    onToggleSimulateSandboxSubscription = { settingsViewModel.toggleSimulateSandboxSubscription() },
                    onBack = { navController.popBackStack() }
                )
            }
        }

        // Scoped banner observer so banner visibility changes don't recompose active game routes
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route
        val settingsState by settingsViewModel.uiState.collectAsState()
        val shouldShowBottomBanner = currentRoute != StarterDestinations.SPLASH &&
            settingsState.configState.config.bannerAd.enabled &&
            !settingsState.subscriptionEntitlement.isAdFreeSubscribed
        AnimatedVisibility(visible = shouldShowBottomBanner) {
            PersistentBottomAdMobBanner()
        }
    }
}
