package com.example.pipecounter

import android.os.Bundle
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.core.content.FileProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.pipecounter.ui.*
import java.io.File
import java.util.UUID

class MainActivity: ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PipeTheme {
                val app=application as PipeApplication
                val factory=remember { viewModelFactory { initializer { CountViewModel(app.repository,createSavedStateHandle()) } } }
                val vm: CountViewModel=viewModel(factory=factory)
                val state by vm.uiState.collectAsStateWithLifecycle()
                var capturePath by rememberSaveable { mutableStateOf<String?>(null) }
                var inputError by remember { mutableStateOf<String?>(null) }
                val picker=rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
                    if(uri!=null) vm.dispatch(CountAction.Import(uri))
                }
                val camera=rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
                    val file=capturePath?.let(::File)
                    if(success && file!=null) {
                        vm.dispatch(CountAction.Import(FileProvider.getUriForFile(this,"$packageName.files",file)))
                    } else { file?.delete() }
                    capturePath=null
                }
                PipeApp(state,vm::dispatch,
                    onPick={ picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    onCamera={
                        try {
                            val folder=File(cacheDir,"captures").apply { mkdirs() }
                            val file=File(folder,"${UUID.randomUUID()}.jpg")
                            capturePath=file.path
                            camera.launch(FileProvider.getUriForFile(this,"$packageName.files",file))
                        } catch(e: Exception) { capturePath?.let(::File)?.delete(); capturePath=null; inputError="카메라를 열 수 없습니다. 사진 선택을 이용해주세요." }
                    },inputError=inputError,onClearInputError={inputError=null})
            }
        }
    }
}
