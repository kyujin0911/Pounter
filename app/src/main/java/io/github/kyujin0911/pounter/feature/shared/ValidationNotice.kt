package io.github.kyujin0911.pounter.feature.shared

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.kyujin0911.pounter.core.ui.components.NoticeCard
import io.github.kyujin0911.pounter.core.ui.components.NoticeTone

@Composable
internal fun ValidationNotice(modifier: Modifier = Modifier) {
    NoticeCard(NoticeTone.INFORMATION, modifier, title = "자동 계수 · 검토 필요") {
        Text("자동 표시는 누락되거나 중복될 수 있습니다. 사진과 대조해 추가·삭제한 뒤 개수를 확인하고 저장해주세요.")
    }
}
