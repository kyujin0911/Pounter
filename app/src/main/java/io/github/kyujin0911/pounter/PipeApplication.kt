package io.github.kyujin0911.pounter

import android.app.Application
import android.util.Log
import androidx.room.Room
import io.github.kyujin0911.pounter.data.detector.ValidationRequiredDetector
import io.github.kyujin0911.pounter.data.local.database.CountDatabase
import io.github.kyujin0911.pounter.data.local.photo.PhotoStore
import io.github.kyujin0911.pounter.data.repository.LocalCountRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PipeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                repository.finishDeletes()
            } catch (e: Exception) {
                Log.w("Pounter", "Pending deletion will be retried", e)
            }
        }
    }

    val photos by lazy { PhotoStore(this) }
    val repository by lazy {
        val db = Room.databaseBuilder(this, CountDatabase::class.java, "counts.db").build()
        LocalCountRepository(db.tasks(), photos, ValidationRequiredDetector())
    }
}
