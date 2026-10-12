package io.github.kyujin0911.pounter.data.repository

import android.net.Uri
import io.github.kyujin0911.pounter.core.model.CountTask
import io.github.kyujin0911.pounter.data.detector.Detection
import kotlinx.coroutines.flow.Flow

interface CountRepository {
    val tasks: Flow<List<CountTask>>

    suspend fun get(id: String): CountTask?

    suspend fun import(uri: Uri): CountTask

    suspend fun save(task: CountTask): CountTask

    suspend fun delete(task: CountTask)

    suspend fun preview(task: CountTask): android.graphics.Bitmap?

    suspend fun detect(task: CountTask): Detection
}
