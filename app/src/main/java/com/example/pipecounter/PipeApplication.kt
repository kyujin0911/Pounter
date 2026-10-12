package com.example.pipecounter

import android.app.Application
import androidx.room.Room
import kotlinx.coroutines.*
import android.util.Log
import com.example.pipecounter.data.*

class PipeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CoroutineScope(SupervisorJob()+Dispatchers.IO).launch {
            try {repository.finishDeletes()} catch(e: Exception) {Log.w("PipeCounter","Pending deletion will be retried",e)}
        }
    }
    val photos by lazy { PhotoStore(this) }
    val repository by lazy {
        val db=Room.databaseBuilder(this,CountDatabase::class.java,"counts.db").build()
        LocalCountRepository(db.tasks(),photos,ValidationRequiredDetector())
    }
}
