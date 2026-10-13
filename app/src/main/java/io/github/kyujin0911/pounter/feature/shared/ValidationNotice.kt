package io.github.kyujin0911.pounter.feature.shared

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.kyujin0911.pounter.core.ui.components.NoticeCard
import io.github.kyujin0911.pounter.core.ui.components.NoticeTone

@Composable
internal fun ValidationNotice(modifier: Modifier = Modifier) {
    NoticeCard(NoticeTone.INFORMATION, modifier, title = "자동 계수 · 검증 대기") {
        Text("추가 정면 사진으로 정확도를 검증한 뒤 제공합니다. 현재는 사진에 수동으로 표시하고 기록을 남길 수 있습니다.")
    }
}
