package com.ssafy.culture.ui.navigation

import android.net.Uri
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.ssafy.culture.ui.component.RestoreSystemBarsEffect
import com.ssafy.culture.ui.motion.CultureMotion
import com.ssafy.culture.ui.screen.camera.QuestCameraRoute
import com.ssafy.culture.ui.screen.course.CourseOrderRoute
import com.ssafy.culture.ui.screen.course.CourseSelectRoute
import com.ssafy.culture.ui.screen.detail.DetailRoute
import com.ssafy.culture.ui.screen.home.HomeRoute
import com.ssafy.culture.ui.screen.login.LoginRoute
import com.ssafy.culture.ui.screen.map.MapRoute
import com.ssafy.culture.ui.screen.people.PeopleCountRoute
import com.ssafy.culture.ui.screen.permission.PermissionOnboardingRoute
import com.ssafy.culture.ui.screen.profile.EditProfileRoute
import com.ssafy.culture.ui.screen.profile.ProfileRoute
import com.ssafy.culture.ui.screen.profile.SettingsRoute
import com.ssafy.culture.ui.screen.quest.QuestDetailRoute
import com.ssafy.culture.ui.screen.quest.QuestRoute
import com.ssafy.culture.ui.screen.result.ResultCompleteRoute
import com.ssafy.culture.ui.screen.signup.SignupRoute
import com.ssafy.culture.ui.screen.story.GalleryRoute
import com.ssafy.culture.ui.screen.story.StoryBookFullModeRoute
import com.ssafy.culture.ui.screen.story.StoryBookViewerRoute
import com.ssafy.culture.ui.screen.story.StoryBookViewerViewModel
import com.ssafy.culture.ui.screen.survey.PersonalitySurveyRoute

private object Destinations {
    const val Login = "login"
    const val PermissionOnboarding = "permissionOnboarding"
    const val Signup = "signup"
    const val Home = "home"
    const val Map = "map"
    const val Story = "story"
    const val PeopleCount = "peopleCount"
    const val PersonalitySurvey = "personalitySurvey"
    const val CourseSelect = "courseSelect"
    const val CourseOrder = "courseOrder"
    const val CourseId = "courseId"
    const val CourseOrderRoute = "$CourseOrder/{$CourseId}"
    const val QuestRoute = "questRoute"
    const val QuestDetail = "questDetail"
    const val StoryId = "storyId"
    const val ChapterId = "chapterId"
    const val QuestCamera = "questCamera"
    const val QuestCameraRoute = "$QuestCamera/{$StoryId}/{$ChapterId}"
    const val QuestCameraValidation = "questCameraValidation"
    const val ResultComplete = "resultComplete"
    const val ResultCompleteRoute = "$ResultComplete/{$StoryId}"
    const val StoryBookViewer = "storyBookViewer"
    const val StoryBookFullMode = "storyBookViewerFullMode"
    const val EbookId = "ebookId"
    const val PageIndex = "pageIndex"
    const val StoryBookViewerRoute = "$StoryBookViewer/{$EbookId}"
    const val StoryBookFullModeRoute = "$StoryBookFullMode/{$EbookId}/{$PageIndex}"
    const val Profile = "profile"
    const val EditProfile = "editProfile"
    const val Settings = "settings"
    const val Detail = "detail"
    const val ItemId = "itemId"
    const val DetailRoute = "$Detail/{$ItemId}"

    fun detail(id: Int): String = "$Detail/$id"
    fun courseOrder(courseId: Long): String = "$CourseOrder/$courseId"
    fun questCamera(storyId: Long, chapterId: Long): String = "$QuestCamera/$storyId/$chapterId"
    fun resultComplete(storyId: Long): String = "$ResultComplete/$storyId"
    fun storyBookViewer(ebookId: String): String =
        "$StoryBookViewer/${Uri.encode(ebookId.ifBlank { "latest" })}"
    fun storyBookFullMode(ebookId: String, pageIndex: Int): String =
        "$StoryBookFullMode/${Uri.encode(ebookId.ifBlank { "latest" })}/${pageIndex.coerceAtLeast(0)}"
}

object AppStartDestination {
    const val Login: String = Destinations.Login
    const val PermissionOnboarding: String = Destinations.PermissionOnboarding
}

@Composable
fun CultureNavGraph(
    navController: NavHostController,
    startDestination: String,
    darkModeEnabled: Boolean,
    onDarkModeChange: (Boolean) -> Unit,
) {
    val backStackEntry: NavBackStackEntry? by navController.currentBackStackEntryAsState()
    val isStoryBookFullMode: Boolean =
        backStackEntry?.destination?.route == Destinations.StoryBookFullModeRoute
    var wasStoryBookFullMode: Boolean by remember { mutableStateOf(false) }
    var restoreSystemBarsKey: Long? by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(isStoryBookFullMode) {
        if (wasStoryBookFullMode && !isStoryBookFullMode) {
            restoreSystemBarsKey = (restoreSystemBarsKey ?: 0L) + 1L
        }
        wasStoryBookFullMode = isStoryBookFullMode
    }
    RestoreSystemBarsEffect(restoreKey = restoreSystemBarsKey)

    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = { cultureEnterTransition() },
        exitTransition = { cultureExitTransition() },
        popEnterTransition = { culturePopEnterTransition() },
        popExitTransition = { culturePopExitTransition() },
    ) {
        composable(route = Destinations.Login) {
            LoginRoute(
                onLoginSuccess = {
                    navController.navigate(Destinations.PermissionOnboarding) {
                        popUpTo(Destinations.Login) {
                            inclusive = true
                        }
                    }
                },
                onOpenSignup = {
                    navController.navigate(Destinations.Signup)
                },
            )
        }

        composable(route = Destinations.PermissionOnboarding) {
            PermissionOnboardingRoute(
                onComplete = {
                    navController.navigate(Destinations.Home) {
                        popUpTo(Destinations.PermissionOnboarding) {
                            inclusive = true
                        }
                        launchSingleTop = true
                    }
                },
            )
        }

        composable(route = Destinations.Signup) {
            SignupRoute(
                onBack = navController::popBackStack,
                onSignupComplete = {
                    navController.navigate(Destinations.Login) {
                        popUpTo(Destinations.Signup) {
                            inclusive = true
                        }
                        launchSingleTop = true
                    }
                },
            )
        }

        composable(route = Destinations.Home) {
            HomeRoute(
                onOpenCourseSelect = {
                    navController.navigate(Destinations.PeopleCount)
                },
                onOpenCurrentQuest = {
                    navController.navigate(Destinations.QuestDetail)
                },
                onOpenMap = {
                    navController.navigateSingleTop(Destinations.Map)
                },
                onOpenStory = {
                    navController.navigateSingleTop(Destinations.Story)
                },
                onOpenProfile = {
                    navController.navigateSingleTop(Destinations.Profile)
                },
                onOpenResultDetail = { ebookId ->
                    navController.navigate(Destinations.storyBookViewer(ebookId))
                },
            )
        }

        composable(route = Destinations.Map) {
            MapRoute(
                onOpenHome = {
                    navController.navigateSingleTop(Destinations.Home)
                },
                onOpenStory = {
                    navController.navigateSingleTop(Destinations.Story)
                },
                onOpenProfile = {
                    navController.navigateSingleTop(Destinations.Profile)
                },
            )
        }

        composable(route = Destinations.Story) {
            GalleryRoute(
                onOpenHome = {
                    navController.navigateSingleTop(Destinations.Home)
                },
                onOpenMap = {
                    navController.navigateSingleTop(Destinations.Map)
                },
                onOpenProfile = {
                    navController.navigateSingleTop(Destinations.Profile)
                },
                onOpenCurrentStory = {
                    navController.navigate(Destinations.QuestRoute)
                },
                onOpenResultDetail = {
                    navController.navigate(Destinations.storyBookViewer(it))
                },
            )
        }

        composable(route = Destinations.PeopleCount) {
            PeopleCountRoute(
                onBack = navController::popBackStack,
                onNext = {
                    navController.navigate(Destinations.PersonalitySurvey)
                },
            )
        }

        composable(route = Destinations.PersonalitySurvey) {
            PersonalitySurveyRoute(
                onBack = navController::popBackStack,
                onNext = {
                    navController.navigate(Destinations.CourseSelect)
                },
            )
        }

        composable(route = Destinations.CourseSelect) {
            CourseSelectRoute(
                onBack = navController::popBackStack,
                onNext = { courseId ->
                    navController.navigate(Destinations.courseOrder(courseId))
                },
            )
        }

        composable(
            route = Destinations.CourseOrderRoute,
            arguments = listOf(
                navArgument(Destinations.CourseId) {
                    type = NavType.LongType
                },
            ),
        ) {
            CourseOrderRoute(
                onBack = navController::popBackStack,
                onComplete = {
                    navController.navigate(Destinations.QuestRoute) {
                        popUpTo(Destinations.Home) {
                            inclusive = false
                        }
                        launchSingleTop = true
                    }
                },
            )
        }

        composable(route = Destinations.QuestRoute) {
            QuestRoute(
                onBack = {
                    val returnedToStory = navController.popBackStack(
                        route = Destinations.Story,
                        inclusive = false,
                    )
                    if (!returnedToStory) {
                        navController.navigateSingleTop(Destinations.Story)
                    }
                },
                onOpenHome = {
                    navController.navigateSingleTop(Destinations.Home)
                },
                onOpenMap = {
                    navController.navigateSingleTop(Destinations.Map)
                },
                onOpenProfile = {
                    navController.navigateSingleTop(Destinations.Profile)
                },
                onOpenQuestDetail = {
                    navController.navigate(Destinations.QuestDetail)
                },
            )
        }

        composable(route = Destinations.QuestDetail) {
            QuestDetailRoute(
                onBack = navController::popBackStack,
                onOpenCamera = { storyId, chapterId ->
                    navController.navigate(Destinations.questCamera(storyId, chapterId))
                },
                onSubmit = { storyId ->
                    navController.navigate(Destinations.resultComplete(storyId))
                },
            )
        }

        composable(
            route = Destinations.QuestCameraRoute,
            arguments = listOf(
                navArgument(Destinations.StoryId) {
                    type = NavType.LongType
                },
                navArgument(Destinations.ChapterId) {
                    type = NavType.LongType
                },
            ),
        ) { backStackEntry ->
            val storyId: Long = backStackEntry.arguments?.getLong(Destinations.StoryId) ?: 0L
            val chapterId: Long = backStackEntry.arguments?.getLong(Destinations.ChapterId) ?: 0L
            QuestCameraRoute(
                storyId = storyId,
                chapterId = chapterId,
                onBack = navController::popBackStack,
                onSubmitted = { isAllSubmitted ->
                    if (isAllSubmitted) {
                        navController.navigate(Destinations.resultComplete(storyId)) {
                            popUpTo(Destinations.QuestDetail) {
                                inclusive = true
                            }
                        }
                    } else {
                        navController.navigate(Destinations.QuestDetail) {
                            popUpTo(Destinations.QuestDetail) {
                                inclusive = true
                            }
                            launchSingleTop = true
                        }
                    }
                },
            )
        }

        composable(route = Destinations.QuestCameraValidation) {
            QuestCameraRoute(
                storyId = 0L,
                chapterId = 0L,
                validationMode = true,
                onBack = navController::popBackStack,
                onSubmitted = {},
            )
        }

        composable(route = Destinations.ResultComplete) {
            ResultCompleteRoute(
                storyId = null,
                onBack = {
                    navController.navigateMainTab(Destinations.Story)
                },
                onOpenHome = {
                    navController.navigateMainTab(Destinations.Home)
                },
                onOpenMap = {
                    navController.navigateMainTab(Destinations.Map)
                },
                onOpenStory = {
                    navController.navigateMainTab(Destinations.Story)
                },
                onOpenProfile = {
                    navController.navigateMainTab(Destinations.Profile)
                },
                onReadNow = {
                    navController.navigate(Destinations.storyBookViewer(it))
                },
            )
        }

        composable(
            route = Destinations.ResultCompleteRoute,
            arguments = listOf(
                navArgument(Destinations.StoryId) {
                    type = NavType.LongType
                },
            ),
        ) { backStackEntry ->
            val storyId: Long = backStackEntry.arguments?.getLong(Destinations.StoryId) ?: 0L
            ResultCompleteRoute(
                storyId = storyId.takeIf { it > 0L },
                onBack = {
                    navController.navigateMainTab(Destinations.Story)
                },
                onOpenHome = {
                    navController.navigateMainTab(Destinations.Home)
                },
                onOpenMap = {
                    navController.navigateMainTab(Destinations.Map)
                },
                onOpenStory = {
                    navController.navigateMainTab(Destinations.Story)
                },
                onOpenProfile = {
                    navController.navigateMainTab(Destinations.Profile)
                },
                onReadNow = {
                    navController.navigate(Destinations.storyBookViewer(it))
                },
            )
        }

        composable(
            route = Destinations.StoryBookViewerRoute,
            arguments = listOf(
                navArgument(Destinations.EbookId) {
                    type = NavType.StringType
                },
            ),
        ) { backStackEntry ->
            val ebookId: String = backStackEntry.arguments
                ?.getString(Destinations.EbookId)
                ?.ifBlank { "latest" }
                ?: "latest"
            StoryBookViewerRoute(
                onBack = navController::popBackStack,
                onOpenFullMode = { pageIndex ->
                    navController.navigate(Destinations.storyBookFullMode(ebookId, pageIndex))
                },
            )
        }

        composable(
            route = Destinations.StoryBookFullModeRoute,
            arguments = listOf(
                navArgument(Destinations.EbookId) {
                    type = NavType.StringType
                },
                navArgument(Destinations.PageIndex) {
                    type = NavType.IntType
                },
            ),
        ) { backStackEntry ->
            val initialPage: Int = backStackEntry.arguments
                ?.getInt(Destinations.PageIndex)
                ?: 0
            val viewerBackStackEntry: NavBackStackEntry? = remember(backStackEntry) {
                navController.previousBackStackEntry
                    ?.takeIf { entry -> entry.destination.route == Destinations.StoryBookViewerRoute }
            }
            val viewModel: StoryBookViewerViewModel = if (viewerBackStackEntry != null) {
                hiltViewModel(viewModelStoreOwner = viewerBackStackEntry)
            } else {
                hiltViewModel()
            }
            StoryBookFullModeRoute(
                initialPage = initialPage,
                onBack = navController::popBackStack,
                viewModel = viewModel,
            )
        }

        composable(route = Destinations.Profile) {
            ProfileRoute(
                onOpenHome = {
                    navController.navigateSingleTop(Destinations.Home)
                },
                onOpenMap = {
                    navController.navigateSingleTop(Destinations.Map)
                },
                onOpenStory = {
                    navController.navigateSingleTop(Destinations.Story)
                },
                onLogout = {
                    navController.navigate(Destinations.Login) {
                        popUpTo(navController.graph.id) {
                            inclusive = true
                        }
                        launchSingleTop = true
                    }
                },
                onOpenEditProfile = {
                    navController.navigate(Destinations.EditProfile)
                },
                onOpenSettings = {
                    navController.navigate(Destinations.Settings)
                },
            )
        }

        composable(route = Destinations.EditProfile) {
            EditProfileRoute(
                onBack = navController::popBackStack,
            )
        }

        composable(route = Destinations.Settings) {
            SettingsRoute(
                darkModeEnabled = darkModeEnabled,
                onDarkModeChange = onDarkModeChange,
                onBack = {
                    val returnedToProfile = navController.popBackStack(
                        route = Destinations.Profile,
                        inclusive = false,
                    )
                    if (!returnedToProfile) {
                        navController.navigate(Destinations.Profile) {
                            launchSingleTop = true
                        }
                    }
                },
                onAccountDeleted = {
                    navController.navigate(Destinations.Login) {
                        popUpTo(navController.graph.id) {
                            inclusive = true
                        }
                        launchSingleTop = true
                    }
                },
            )
        }

        composable(
            route = Destinations.DetailRoute,
            arguments = listOf(
                navArgument(Destinations.ItemId) {
                    type = NavType.IntType
                },
            ),
        ) {
            DetailRoute(
                onBack = navController::popBackStack,
            )
        }
    }
}

private fun NavHostController.navigateSingleTop(route: String) {
    navigate(route) {
        launchSingleTop = true
    }
}

private fun NavHostController.navigateMainTab(route: String) {
    navigate(route) {
        popUpTo(Destinations.Home) {
            inclusive = false
        }
        launchSingleTop = true
    }
}

private fun cultureEnterTransition(): EnterTransition =
    fadeIn(
        animationSpec = tween(
            durationMillis = CultureMotion.ScreenTransitionMillis,
            easing = FastOutSlowInEasing,
        ),
    ) + slideInHorizontally(
        animationSpec = tween(
            durationMillis = CultureMotion.ScreenTransitionMillis,
            easing = FastOutSlowInEasing,
        ),
        initialOffsetX = { fullWidth -> fullWidth / 14 },
    ) + scaleIn(
        animationSpec = tween(
            durationMillis = CultureMotion.ScreenTransitionMillis,
            easing = FastOutSlowInEasing,
        ),
        initialScale = 0.985f,
    )

private fun cultureExitTransition(): ExitTransition =
    fadeOut(
        animationSpec = tween(
            durationMillis = CultureMotion.QuickTransitionMillis,
            easing = FastOutSlowInEasing,
        ),
    ) + slideOutHorizontally(
        animationSpec = tween(
            durationMillis = CultureMotion.QuickTransitionMillis,
            easing = FastOutSlowInEasing,
        ),
        targetOffsetX = { fullWidth -> -fullWidth / 18 },
    ) + scaleOut(
        animationSpec = tween(
            durationMillis = CultureMotion.QuickTransitionMillis,
            easing = FastOutSlowInEasing,
        ),
        targetScale = 0.995f,
    )

private fun culturePopEnterTransition(): EnterTransition =
    fadeIn(
        animationSpec = tween(
            durationMillis = CultureMotion.ScreenTransitionMillis,
            easing = FastOutSlowInEasing,
        ),
    ) + slideInHorizontally(
        animationSpec = tween(
            durationMillis = CultureMotion.ScreenTransitionMillis,
            easing = FastOutSlowInEasing,
        ),
        initialOffsetX = { fullWidth -> -fullWidth / 14 },
    ) + scaleIn(
        animationSpec = tween(
            durationMillis = CultureMotion.ScreenTransitionMillis,
            easing = FastOutSlowInEasing,
        ),
        initialScale = 0.985f,
    )

private fun culturePopExitTransition(): ExitTransition =
    fadeOut(
        animationSpec = tween(
            durationMillis = CultureMotion.QuickTransitionMillis,
            easing = FastOutSlowInEasing,
        ),
    ) + slideOutHorizontally(
        animationSpec = tween(
            durationMillis = CultureMotion.QuickTransitionMillis,
            easing = FastOutSlowInEasing,
        ),
        targetOffsetX = { fullWidth -> fullWidth / 18 },
    ) + scaleOut(
        animationSpec = tween(
            durationMillis = CultureMotion.QuickTransitionMillis,
            easing = FastOutSlowInEasing,
        ),
        targetScale = 0.995f,
    )
