package io.github.kyujin0911.pounter.preview

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import io.github.kyujin0911.pounter.core.model.CountTask
import io.github.kyujin0911.pounter.core.model.Marker
import io.github.kyujin0911.pounter.core.ui.components.NoticeCard
import io.github.kyujin0911.pounter.core.ui.components.NoticeTone
import io.github.kyujin0911.pounter.core.ui.theme.PipeTheme
import io.github.kyujin0911.pounter.feature.count.CountUiState
import io.github.kyujin0911.pounter.feature.count.CountWorkScreen
import io.github.kyujin0911.pounter.feature.count.Operation
import io.github.kyujin0911.pounter.feature.home.HomeScreen
import io.github.kyujin0911.pounter.feature.home.HomeUiState
import io.github.kyujin0911.pounter.feature.record.RecordScreen
import io.github.kyujin0911.pounter.feature.record.RecordUiState
import io.github.kyujin0911.pounter.feature.shared.CountSummary

// Debug-only synthetic fixtures: no repository, ViewModel, file, or network access.
private fun previewTask(count: Int = 0, analyzed: Boolean = false, confirmed: Boolean = false) =
    CountTask(
        id = "preview-$count-$analyzed-$confirmed",
        photoName = "synthetic.png",
        width = 900,
        height = 650,
        createdAt = 0L,
        markers =
            List(count) { index ->
                Marker(
                    "marker-$index",
                    .05f + (index % 20) * .9f / 19,
                    .05f + (index / 20) * .9f / 14,
                    .01f,
                    manual = true,
                )
            },
        analyzed = analyzed,
        confirmedAt = if (confirmed) 1000L else null,
    )

@Composable
private fun previewBitmap(): Bitmap {
    val bitmap = remember {
        createBitmap(900, 650).apply { eraseColor(android.graphics.Color.GRAY) }
    }
    DisposableEffect(bitmap) { onDispose { bitmap.recycle() } }
    return bitmap
}

@Preview(name = "Home light", widthDp = 360, heightDp = 800)
@Composable
private fun HomeLight() {
    HomePreview(false)
}

@Preview(name = "Home dark large", widthDp = 360, heightDp = 800, fontScale = 2f)
@Composable
private fun HomeDarkLarge() {
    HomePreview(true)
}

@Composable
private fun HomePreview(dark: Boolean) {
    PipeTheme(dark) {
        HomeScreen(
            HomeUiState(
                listOf(previewTask(), previewTask(analyzed = true), previewTask(300, true, true)),
                false,
            ),
            {},
            {},
            {},
        )
    }
}

@Preview(name = "Review 300 light", widthDp = 360, heightDp = 800)
@Composable
private fun ReviewLight() {
    WorkPreview(false, false)
}

@Preview(name = "Review 300 dark large", widthDp = 360, heightDp = 800, fontScale = 2f)
@Composable
private fun ReviewDarkLarge() {
    WorkPreview(true, false)
}

@Preview(name = "Recovery light large", widthDp = 360, heightDp = 800, fontScale = 2f)
@Composable
private fun RecoveryLightLarge() {
    WorkPreview(false, true)
}

@Composable
private fun WorkPreview(dark: Boolean, failed: Boolean) {
    val bitmap = previewBitmap()
    PipeTheme(dark) {
        CountWorkScreen(
            CountUiState(
                task = previewTask(300, true),
                preview = bitmap,
                operation = Operation.NONE,
                unsavedChanges = failed,
                message = if (failed) "저장하지 못했습니다" else null,
            ),
            {},
            {},
            {},
        )
    }
}

@Preview(name = "Confirmed light", widthDp = 360, heightDp = 800)
@Composable
private fun ConfirmedLight() {
    RecordPreview(false)
}

@Preview(name = "Confirmed dark large", widthDp = 360, heightDp = 800, fontScale = 2f)
@Composable
private fun ConfirmedDarkLarge() {
    RecordPreview(true)
}

@Composable
private fun RecordPreview(dark: Boolean) {
    val bitmap = previewBitmap()
    PipeTheme(dark) {
        RecordScreen(
            RecordUiState(task = previewTask(300, true, true), preview = bitmap, isLoading = false),
            {},
            {},
            {},
        )
    }
}

@Preview(name = "Status and notice slots", widthDp = 360, fontScale = 2f)
@Composable
private fun StatusAndNotice() {
    PipeTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            CountSummary(previewTask())
            CountSummary(previewTask(analyzed = true))
            CountSummary(previewTask(300, true, true))
            NoticeCard(
                NoticeTone.ERROR,
                title = "저장 실패",
                actions = { TextButton(onClick = {}) { Text("저장 재시도") } },
            ) {
                Text("보정 내용은 유지됩니다")
            }
        }
    }
}
