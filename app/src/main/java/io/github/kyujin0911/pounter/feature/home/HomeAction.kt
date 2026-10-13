package io.github.kyujin0911.pounter.feature.home
sealed interface HomeAction {
    data object ClearMessage : HomeAction
}
