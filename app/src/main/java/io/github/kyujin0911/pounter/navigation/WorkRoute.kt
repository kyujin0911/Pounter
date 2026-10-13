package io.github.kyujin0911.pounter.navigation

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Serialized name is retained for pre-refactor saved back stacks.
@Serializable
@SerialName("io.github.kyujin0911.pounter.ui.WorkRoute")
data class WorkRoute(val taskId: String)
