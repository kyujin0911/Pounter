package io.github.kyujin0911.pounter.di

import androidx.room.Room
import io.github.kyujin0911.pounter.data.detector.PipeDetector
import io.github.kyujin0911.pounter.data.detector.ValidationRequiredDetector
import io.github.kyujin0911.pounter.data.local.database.CountDatabase
import io.github.kyujin0911.pounter.data.local.photo.PhotoStore
import io.github.kyujin0911.pounter.data.repository.CountRepository
import io.github.kyujin0911.pounter.data.repository.LocalCountRepository
import io.github.kyujin0911.pounter.feature.count.CountViewModel
import io.github.kyujin0911.pounter.feature.home.HomeViewModel
import io.github.kyujin0911.pounter.feature.record.RecordViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module

fun appModule() = module {
    single {
        Room.databaseBuilder(androidContext(), CountDatabase::class.java, "counts.db").build()
    }
    single { get<CountDatabase>().tasks() }
    single { PhotoStore(androidContext()) }
    single<PipeDetector> { ValidationRequiredDetector() }
    single { LocalCountRepository(get(), get(), get()) } bind CountRepository::class

    viewModelOf(::HomeViewModel)
    viewModelOf(::CountViewModel)
    viewModelOf(::RecordViewModel)
}
