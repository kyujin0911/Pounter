package io.github.kyujin0911.pounter

import androidx.lifecycle.SavedStateHandle
import io.github.kyujin0911.pounter.feature.shared.PhotoImportAction
import io.github.kyujin0911.pounter.feature.shared.PhotoImportHandler
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class PhotoImportHandlerTest {
    @Test
    fun oldSavedImportResultIsRestoredAndAcknowledgementIsEntryLocal() = runTest {
        val repository = ScreenTestRepository()
        val originSavedState = SavedStateHandle(mapOf("importedTaskId" to "new-task"))
        val anotherSavedState = SavedStateHandle(mapOf("importedTaskId" to "another-task"))
        val origin = PhotoImportHandler(repository, originSavedState, backgroundScope) { true }
        val another = PhotoImportHandler(repository, anotherSavedState, backgroundScope) { true }
        assertEquals("new-task", origin.uiState.value.importedTaskId)
        assertFalse(origin.uiState.value.isImporting)
        origin.dispatch(PhotoImportAction.Handled)
        assertNull(originSavedState.get<String>("importedTaskId"))
        assertNull(origin.uiState.value.importedTaskId)
        assertEquals("another-task", another.uiState.value.importedTaskId)
    }
}
