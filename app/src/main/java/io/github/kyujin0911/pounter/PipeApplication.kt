package io.github.kyujin0911.pounter

import android.app.Application
import android.util.Log
import io.github.kyujin0911.pounter.data.repository.LocalCountRepository
import io.github.kyujin0911.pounter.di.appModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class PipeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val koin =
            startKoin {
                    androidContext(this@PipeApplication)
                    modules(appModule())
                }
                .koin
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                koin.get<LocalCountRepository>().finishDeletes()
            } catch (e: Exception) {
                Log.w("Pounter", "Pending deletion will be retried", e)
            }
        }
    }
}
