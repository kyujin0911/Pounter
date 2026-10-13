package io.github.kyujin0911.pounter.feature.count

import io.github.kyujin0911.pounter.core.model.Region

sealed interface CountAction {
    data object RetrySave : CountAction

    data object Analyze : CountAction

    data object CancelAnalysis : CountAction

    data object BeginManual : CountAction

    data class SetRegion(val region: Region) : CountAction

    data class Add(val normalizedX: Float, val normalizedY: Float) : CountAction

    data class Remove(val markerIds: Set<String>) : CountAction

    data object Undo : CountAction

    data object Confirm : CountAction

    data object Delete : CountAction

    data object ClearMessage : CountAction
}
