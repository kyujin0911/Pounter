package io.github.kyujin0911.pounter.feature.record
sealed interface RecordAction {
    data object Delete : RecordAction

    data object ClearMessage : RecordAction
}
