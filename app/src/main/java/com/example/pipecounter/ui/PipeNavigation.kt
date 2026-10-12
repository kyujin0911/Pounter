package com.example.pipecounter.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import com.example.pipecounter.data.CountRepository
import kotlinx.serialization.Serializable
import java.io.File
import java.util.UUID

@Serializable data object HomeRoute
@Serializable data class WorkRoute(val taskId: String)
@Serializable data class RecordRoute(val taskId: String)

/** Navigation owns the selected destination; each entry owns its task ViewModel. */
@Composable
fun PipeNavigation(repository: CountRepository, navController: NavHostController = rememberNavController()) {
    fun home() { navController.popBackStack(HomeRoute, inclusive=false) }
    fun work(id: String) {
        navController.navigate(WorkRoute(id)) {
            popUpTo<HomeRoute>()
            launchSingleTop=true
        }
    }
    fun record(id: String) {
        navController.navigate(RecordRoute(id)) {
            popUpTo<HomeRoute>()
            launchSingleTop=true
        }
    }
    NavHost(navController=navController,startDestination=HomeRoute) {
        composable<HomeRoute> { entry ->
            val vm=entryViewModel(repository)
            val state by vm.uiState.collectAsStateWithLifecycle()
            val input=rememberPhotoInput(vm::dispatch)
            LaunchedEffect(state.importedTaskId) {
                state.importedTaskId?.let { work(it); vm.dispatch(CountAction.ImportHandled) }
            }
            HomeScreen(state,vm::dispatch,input.onPick,input.onCamera,input.error,input.clearError) { task ->
                if(entry.lifecycle.currentState==Lifecycle.State.RESUMED) {
                    if(task.confirmed) record(task.id) else work(task.id)
                }
            }
        }
        composable<WorkRoute> {
            val vm=entryViewModel(repository)
            val state by vm.uiState.collectAsStateWithLifecycle()
            val input=rememberPhotoInput(vm::dispatch)
            LaunchedEffect(state.importedTaskId,state.task?.confirmed,state.deleted) {
                when {
                    state.deleted -> home()
                    state.importedTaskId!=null -> {
                        work(state.importedTaskId!!); vm.dispatch(CountAction.ImportHandled)
                    }
                    state.task?.confirmed==true -> record(state.task!!.id)
                }
            }
            CountWorkScreen(state,vm::dispatch,input.onPick,input.onCamera,input.error,input.clearError,::home)
        }
        composable<RecordRoute> {
            val vm=entryViewModel(repository)
            val state by vm.uiState.collectAsStateWithLifecycle()
            val input=rememberPhotoInput(vm::dispatch)
            LaunchedEffect(state.importedTaskId,state.task?.confirmed,state.deleted) {
                when {
                    state.deleted -> home()
                    state.importedTaskId!=null -> {
                        work(state.importedTaskId!!); vm.dispatch(CountAction.ImportHandled)
                    }
                    state.task?.confirmed==false -> work(state.task!!.id)
                }
            }
            RecordScreen(state,vm::dispatch,input.onPick,input.onCamera,input.error,input.clearError,::home)
        }
    }
}

@Composable
private fun entryViewModel(repository: CountRepository): CountViewModel {
    val factory=remember(repository) {
        viewModelFactory { initializer { CountViewModel(repository,createSavedStateHandle()) } }
    }
    return viewModel(factory=factory)
}

private data class PhotoInput(val onPick: ()->Unit,val onCamera: ()->Unit,val error: String?,val clearError: ()->Unit)

/** Launchers belong to their originating entry: cancellation leaves its route and task intact. */
@Composable
private fun rememberPhotoInput(dispatch: (CountAction)->Unit): PhotoInput {
    val context=LocalContext.current
    val currentDispatch by rememberUpdatedState(dispatch)
    var capturePath by rememberSaveable { mutableStateOf<String?>(null) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if(uri!=null) currentDispatch(CountAction.Import(uri))
    }
    val camera=rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val file=capturePath?.let(::File)
        if(success && file!=null) {
            currentDispatch(CountAction.Import(FileProvider.getUriForFile(context,"${context.packageName}.files",file)))
        } else file?.delete()
        capturePath=null
    }
    return PhotoInput(
        onPick={
            try { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
            catch(e: Exception) { error="사진 선택을 열 수 없습니다. 다시 시도해주세요." }
        },
        onCamera={
            try {
                val folder=File(context.cacheDir,"captures").apply { mkdirs() }
                val file=File(folder,"${UUID.randomUUID()}.jpg")
                capturePath=file.path
                camera.launch(FileProvider.getUriForFile(context,"${context.packageName}.files",file))
            } catch(e: Exception) {
                capturePath?.let(::File)?.delete(); capturePath=null
                error="카메라를 열 수 없습니다. 사진 선택을 이용해주세요."
            }
        },error=error,clearError={error=null}
    )
}
