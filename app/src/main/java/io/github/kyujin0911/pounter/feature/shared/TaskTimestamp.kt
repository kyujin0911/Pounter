package io.github.kyujin0911.pounter.feature.shared

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatTaskTimestamp(timeMillis: Long): String =
    SimpleDateFormat("MM.dd HH:mm", Locale.KOREA).format(Date(timeMillis))
