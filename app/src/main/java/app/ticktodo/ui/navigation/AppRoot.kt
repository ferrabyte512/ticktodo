package app.ticktodo.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.ticktodo.domain.focus.FocusPhase
import app.ticktodo.domain.focus.FocusState
import app.ticktodo.focus.FocusNotifications
import app.ticktodo.ui.theme.LocalExtraColors
import kotlinx.coroutines.delay
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import app.ticktodo.R
import app.ticktodo.data.auth.AuthState
import app.ticktodo.domain.model.TaskScope
import app.ticktodo.ui.MainViewModel
import app.ticktodo.ui.auth.SignInScreen
import app.ticktodo.ui.browse.BrowseScreen
import app.ticktodo.ui.appearance.AppearanceScreen
import app.ticktodo.ui.common.glassColor
import app.ticktodo.ui.editor.TaskEditorScreen
import app.ticktodo.ui.focus.FocusScreen
import app.ticktodo.ui.search.SearchScreen
import app.ticktodo.ui.settings.LicensesScreen
import app.ticktodo.ui.settings.SettingsScreen
import app.ticktodo.ui.tasks.TasksScreen
import kotlinx.serialization.Serializable

@Serializable data class ScopeRoute(val scopeKey: String)
@Serializable data class EditorRoute(val taskId: String)
@Serializable data object BrowseRoute
@Serializable data object SearchRoute
@Serializable data object SettingsRoute
@Serializable data object LicensesRoute
@Serializable data object AppearanceRoute
/** [taskId] is empty when opened for the running session. */
@Serializable data class FocusRoute(val taskId: String = "")

sealed interface PendingAction {
    data class OpenTask(val taskId: String) : PendingAction
    data object QuickAdd : PendingAction
    data object OpenFocus : PendingAction
}

private enum class TopLevel(val label: Int, val icon: ImageVector) {
    TODAY(R.string.scope_today, Icons.Outlined.Today),
    UPCOMING(R.string.scope_upcoming, Icons.Outlined.CalendarMonth),
    BROWSE(R.string.nav_browse, Icons.Outlined.ViewList),
}

@Composable
fun AppRoot(viewModel: MainViewModel, pending: PendingAction?, onPendingConsumed: () -> Unit) {
    val auth by viewModel.authState.collectAsStateWithLifecycle()
    when (auth) {
        AuthState.Loading -> Box(Modifier.fillMaxSize())
        AuthState.SignedOut -> SignInScreen()
        is AuthState.SignedIn -> MainNavigation(viewModel, pending, onPendingConsumed)
    }
}

@Composable
private fun MainNavigation(viewModel: MainViewModel, pending: PendingAction?, onPendingConsumed: () -> Unit) {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    var quickAddRequested by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { msg ->
            val text = resources.getString(msg.text, *msg.args.toTypedArray())
            val result = snackbar.showSnackbar(
                message = text,
                actionLabel = msg.actionLabel?.let(resources::getString),
                withDismissAction = msg.actionLabel == null,
                duration = if (msg.actionLabel != null) SnackbarDuration.Long else SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) msg.onAction?.invoke()
        }
    }

    LaunchedEffect(pending) {
        when (pending) {
            is PendingAction.OpenTask -> nav.navigate(EditorRoute(pending.taskId))
            PendingAction.OpenFocus -> nav.navigate(FocusRoute()) { launchSingleTop = true }
            PendingAction.QuickAdd -> {
                nav.navigateTopLevel(TopLevel.TODAY)
                quickAddRequested = true
            }
            null -> Unit
        }
        if (pending != null) onPendingConsumed()
    }

    val backStack by nav.currentBackStackEntryAsState()
    val destination = backStack?.destination
    val current: TopLevel? = when {
        destination == null -> TopLevel.TODAY
        destination.hasRoute<BrowseRoute>() -> TopLevel.BROWSE
        destination.hasRoute<ScopeRoute>() -> when (backStack?.toRoute<ScopeRoute>()?.scopeKey) {
            "today" -> TopLevel.TODAY
            "upcoming" -> TopLevel.UPCOMING
            else -> null
        }
        else -> null
    }
    val wide = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() } >= 600.dp
    val focusState by viewModel.focusState.collectAsStateWithLifecycle()
    val onFocusScreen = destination?.hasRoute<FocusRoute>() == true

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (!wide && current != null) {
                NavigationBar(containerColor = glassColor(MaterialTheme.colorScheme.surfaceContainer)) {
                    TopLevel.entries.forEach { item ->
                        NavigationBarItem(
                            selected = current == item,
                            onClick = { nav.navigateTopLevel(item) },
                            icon = { Icon(item.icon, null) },
                            label = { Text(stringResource(item.label)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Row(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            if (wide && current != null) {
                NavigationRail(containerColor = glassColor(MaterialTheme.colorScheme.surfaceContainer)) {
                    TopLevel.entries.forEach { item ->
                        NavigationRailItem(
                            selected = current == item,
                            onClick = { nav.navigateTopLevel(item) },
                            icon = { Icon(item.icon, null) },
                            label = { Text(stringResource(item.label)) },
                        )
                    }
                }
            }
            Column(Modifier.weight(1f)) {
            val showBanner = focusState != null && !onFocusScreen
            if (showBanner) FocusBanner(focusState!!) { nav.navigate(FocusRoute()) { launchSingleTop = true } }
            NavHost(
                nav,
                startDestination = ScopeRoute("today"),
                // The banner already sits under the status bar; screens below must not pad for it again.
                modifier = Modifier.weight(1f).then(if (showBanner) Modifier.consumeWindowInsets(WindowInsets.statusBars) else Modifier),
            ) {
                composable<ScopeRoute> { entry ->
                    val route = entry.toRoute<ScopeRoute>()
                    val topLevel = route.scopeKey == "today" || route.scopeKey == "upcoming"
                    TasksScreen(
                        onOpenTask = { nav.navigate(EditorRoute(it)) },
                        onOpenSearch = { nav.navigate(SearchRoute) },
                        onOpenSettings = { nav.navigate(SettingsRoute) },
                        onOpenFocus = { nav.navigate(FocusRoute()) { launchSingleTop = true } },
                        onBack = if (topLevel) null else ({ nav.popBackStack() }),
                        openQuickAdd = quickAddRequested && route.scopeKey == "today",
                        onQuickAddConsumed = { quickAddRequested = false },
                    )
                }
                composable<BrowseRoute> {
                    BrowseScreen(
                        onOpenScope = { scope: TaskScope -> nav.navigate(ScopeRoute(scope.key())) },
                        onOpenSearch = { nav.navigate(SearchRoute) },
                        onOpenSettings = { nav.navigate(SettingsRoute) },
                    )
                }
                composable<EditorRoute> {
                    TaskEditorScreen(
                        onClose = { nav.popBackStack() },
                        onOpenTask = { nav.navigate(EditorRoute(it)) },
                        onStartFocus = { nav.navigate(FocusRoute(it)) },
                    )
                }
                composable<SearchRoute> {
                    SearchScreen(onBack = { nav.popBackStack() }, onOpenTask = { nav.navigate(EditorRoute(it)) })
                }
                composable<SettingsRoute> {
                    SettingsScreen(
                        onBack = { nav.popBackStack() },
                        onOpenLicenses = { nav.navigate(LicensesRoute) },
                        onOpenAppearance = { nav.navigate(AppearanceRoute) },
                    )
                }
                composable<LicensesRoute> {
                    LicensesScreen(onBack = { nav.popBackStack() })
                }
                composable<AppearanceRoute> {
                    AppearanceScreen(onBack = { nav.popBackStack() })
                }
                composable<FocusRoute> {
                    FocusScreen(onBack = { nav.popBackStack() })
                }
            }
            }
        }
    }
}

private fun NavHostController.navigateTopLevel(item: TopLevel) {
    val route: Any = when (item) {
        TopLevel.TODAY -> ScopeRoute("today")
        TopLevel.UPCOMING -> ScopeRoute("upcoming")
        TopLevel.BROWSE -> BrowseRoute
    }
    val currentScopeKey = currentBackStackEntry?.takeIf { it.destination.hasRoute<ScopeRoute>() }
        ?.toRoute<ScopeRoute>()?.scopeKey
    val targetScopeKey = (route as? ScopeRoute)?.scopeKey
    val isSameDestination = if (targetScopeKey != null) {
        currentScopeKey == targetScopeKey
    } else {
        currentBackStackEntry?.destination?.hasRoute<BrowseRoute>() == true
    }

    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = isSameDestination
        restoreState = true
    }
}

/** Slim bar shown on every screen while a focus session runs; tap to open the timer. */
@Composable
private fun FocusBanner(state: FocusState, onClick: () -> Unit) {
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state.running, state.phaseEndsAt) {
        while (state.running) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
        now = System.currentTimeMillis()
    }
    val extra = LocalExtraColors.current
    val color = if (state.phase == FocusPhase.FOCUS) extra.focus else extra.breakTime
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        modifier = Modifier.fillMaxWidth().statusBarsPadding().clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Timer, null, tint = color)
            Text(
                FocusNotifications.phaseLabel(context, state.phase) + " · " + state.taskTitle,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            )
            Text(
                if (state.running) FocusNotifications.formatMs(state.remainingMs(now)) else stringResource(R.string.focus_paused),
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}
