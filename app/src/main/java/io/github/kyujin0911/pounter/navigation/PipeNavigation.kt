package io.github.kyujin0911.pounter.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.kyujin0911.pounter.data.repository.CountRepository
import io.github.kyujin0911.pounter.feature.count.CountViewModel
import io.github.kyujin0911.pounter.feature.count.CountWorkScreen
import io.github.kyujin0911.pounter.feature.home.HomeScreen
import io.github.kyujin0911.pounter.feature.home.HomeViewModel
import io.github.kyujin0911.pounter.feature.record.RecordScreen
import io.github.kyujin0911.pounter.feature.record.RecordViewModel
import io.github.kyujin0911.pounter.feature.shared.PhotoImportAction

/** Each destination owns its own VM and import handler, never a global selected task. */
@Composable
fun PipeNavigation(
    repository: CountRepository,
    navController: NavHostController = rememberNavController(),
) {
    fun home() {
        navController.popBackStack(HomeRoute, inclusive = false)
    }
    fun work(taskId: String) {
        navController.navigate(WorkRoute(taskId)) {
            popUpTo<HomeRoute>()
            launchSingleTop = true
        }
    }
    fun record(taskId: String) {
        navController.navigate(RecordRoute(taskId)) {
            popUpTo<HomeRoute>()
            launchSingleTop = true
        }
    }
    NavHost(navController = navController, startDestination = HomeRoute) {
        composable<HomeRoute> { entry ->
            val viewModel =
                entryViewModel<HomeViewModel>(repository) { saved ->
                    HomeViewModel(repository, saved)
                }
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val importState by viewModel.photoImport.uiState.collectAsStateWithLifecycle()
            val input = rememberPhotoInput {
                viewModel.photoImport.dispatch(PhotoImportAction.Import(it))
            }
            LaunchedEffect(importState.importedTaskId) {
                importState.importedTaskId?.let { taskId ->
                    work(taskId)
                    viewModel.photoImport.dispatch(PhotoImportAction.Handled)
                }
            }
            HomeScreen(
                state,
                viewModel::dispatch,
                input.onPick,
                input.onCamera,
                input.error,
                onClearInputError = {
                    input.clearError()
                    viewModel.photoImport.dispatch(PhotoImportAction.ClearMessage)
                },
                photoImportState = importState,
                onOpen = { task ->
                    if (entry.lifecycle.currentState == Lifecycle.State.RESUMED) {
                        if (task.confirmed) record(task.id) else work(task.id)
                    }
                },
            )
        }
        composable<WorkRoute> {
            val viewModel =
                entryViewModel<CountViewModel>(repository) { saved ->
                    CountViewModel(repository, saved)
                }
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val importState by viewModel.photoImport.uiState.collectAsStateWithLifecycle()
            val input = rememberPhotoInput {
                viewModel.photoImport.dispatch(PhotoImportAction.Import(it))
            }
            LaunchedEffect(importState.importedTaskId, state.task?.confirmed, state.deleted) {
                when {
                    state.deleted -> home()
                    importState.importedTaskId != null -> {
                        work(importState.importedTaskId!!)
                        viewModel.photoImport.dispatch(PhotoImportAction.Handled)
                    }
                    state.task?.confirmed == true -> record(state.task!!.id)
                }
            }
            CountWorkScreen(
                state,
                viewModel::dispatch,
                input.onPick,
                input.onCamera,
                input.error,
                onClearInputError = {
                    input.clearError()
                    viewModel.photoImport.dispatch(PhotoImportAction.ClearMessage)
                },
                onBack = ::home,
                photoImportState = importState,
            )
        }
        composable<RecordRoute> {
            val viewModel =
                entryViewModel<RecordViewModel>(repository) { saved ->
                    RecordViewModel(repository, saved)
                }
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val importState by viewModel.photoImport.uiState.collectAsStateWithLifecycle()
            val input = rememberPhotoInput {
                viewModel.photoImport.dispatch(PhotoImportAction.Import(it))
            }
            LaunchedEffect(importState.importedTaskId, state.task?.confirmed, state.deleted) {
                when {
                    state.deleted -> home()
                    importState.importedTaskId != null -> {
                        work(importState.importedTaskId!!)
                        viewModel.photoImport.dispatch(PhotoImportAction.Handled)
                    }
                    state.task?.confirmed == false -> work(state.task!!.id)
                }
            }
            RecordScreen(
                state,
                viewModel::dispatch,
                input.onPick,
                input.onCamera,
                input.error,
                onClearInputError = {
                    input.clearError()
                    viewModel.photoImport.dispatch(PhotoImportAction.ClearMessage)
                },
                onBack = ::home,
                photoImportState = importState,
            )
        }
    }
}

// Explicit old key preserves SavedStateHandle bundles across the package/class split.
// Entries have distinct ViewModelStores: this does not share one VM across destinations.
private const val LEGACY_ENTRY_VIEW_MODEL_KEY =
    "androidx.lifecycle.ViewModelProvider.DefaultKey:io.github.kyujin0911.pounter.ui.CountViewModel"

@Composable
private inline fun <reified T : ViewModel> entryViewModel(
    repository: CountRepository,
    crossinline create: (SavedStateHandle) -> T,
): T {
    val factory =
        remember(repository) {
            viewModelFactory { initializer { create(createSavedStateHandle()) } }
        }
    return viewModel(key = LEGACY_ENTRY_VIEW_MODEL_KEY, factory = factory)
}
