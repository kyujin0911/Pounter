package io.github.kyujin0911.pounter.navigation

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

internal data class PhotoInput(
    val onPick: () -> Unit,
    val onCamera: () -> Unit,
    val error: String?,
    val clearError: () -> Unit,
)

/** Launchers belong to their originating entry: cancellation leaves its route and task intact. */
@Composable
internal fun rememberPhotoInput(onImport: (android.net.Uri) -> Unit): PhotoInput {
    val context = LocalContext.current
    val currentOnImport by rememberUpdatedState(onImport)
    var capturePath by rememberSaveable { mutableStateOf<String?>(null) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) currentOnImport(uri)
        }
    val camera =
        rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            val file = capturePath?.let(::File)
            if (success && file != null) {
                currentOnImport(
                    FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                )
            } else file?.delete()
            capturePath = null
        }
    return PhotoInput(
        onPick = {
            try {
                picker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            } catch (e: Exception) {
                error = "사진 선택을 열 수 없습니다. 다시 시도해주세요."
            }
        },
        onCamera = {
            try {
                val folder = File(context.cacheDir, "captures").apply { mkdirs() }
                val file = File(folder, "${UUID.randomUUID()}.jpg")
                capturePath = file.path
                camera.launch(
                    FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                )
            } catch (e: Exception) {
                capturePath?.let(::File)?.delete()
                capturePath = null
                error = "카메라를 열 수 없습니다. 사진 선택을 이용해주세요."
            }
        },
        error = error,
        clearError = { error = null },
    )
}
