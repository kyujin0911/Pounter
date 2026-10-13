package io.github.kyujin0911.pounter.feature.shared

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kyujin0911.pounter.core.model.CountTask
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PhotoPanel(
    task: CountTask,
    bitmap: android.graphics.Bitmap?,
    mode: EditMode,
    showNumbers: Boolean,
    canEdit: Boolean,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
    viewportHeight: Dp = 340.dp,
    onAddMarker: (Float, Float) -> Unit = { _, _ -> },
    onRemoveMarkers: (Set<String>) -> Unit = {},
) {
    var zoom by remember(task.id) { mutableFloatStateOf(1f) }
    var panPx by remember(task.id) { mutableStateOf(Offset.Zero) }
    var viewportSizePx by remember { mutableStateOf(IntSize(1, 1)) }
    var normalizedCursor by
        remember(task.id, task.region) {
            mutableStateOf(
                Offset(
                    (task.region.left + task.region.right) / 2,
                    (task.region.top + task.region.bottom) / 2,
                )
            )
        }
    var overlappingMarkerIds by remember(task.id) { mutableStateOf<List<String>>(emptyList()) }
    var isMarkerListOpen by remember(task.id) { mutableStateOf(false) }
    val geometry =
        PhotoGeometry(
            task.width,
            task.height,
            viewportSizePx.width.toFloat(),
            viewportSizePx.height.toFloat(),
            zoom,
            panPx.x,
            panPx.y,
        )
    val currentGeometry by rememberUpdatedState(geometry)
    val currentTask by rememberUpdatedState(task)
    val currentOnAddMarker by rememberUpdatedState(onAddMarker)
    val currentOnRemoveMarkers by rememberUpdatedState(onRemoveMarkers)
    val raster = remember(bitmap) { bitmap?.asImageBitmap() }
    val numberPaint = remember {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textAlign = android.graphics.Paint.Align.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(shape = MaterialTheme.shapes.large, color = Color(0xFF111827)) {
            Canvas(
                Modifier.fillMaxWidth()
                    .height(viewportHeight)
                    .testTag("photo-viewport")
                    .onSizeChanged { size ->
                        val resizedGeometry =
                            PhotoGeometry(
                                task.width,
                                task.height,
                                size.width.toFloat().coerceAtLeast(1f),
                                size.height.toFloat().coerceAtLeast(1f),
                                zoom,
                            )
                        val clampedPan = resizedGeometry.clampPan(panPx.x, panPx.y)
                        viewportSizePx = size
                        panPx = Offset(clampedPan.first, clampedPan.second)
                    }
                    .semantics {
                        stateDescription = "확대 ${zoom}배"
                        contentDescription =
                            "계수 사진. ${task.count}개 표시. 아래 확대와 표시 목록, 위치 조절 버튼으로도 사용할 수 있습니다."
                    }
                    .pointerInput(task.id) {
                        detectTransformGestures { _, panDeltaPx, zoomFactor, _ ->
                            val newZoom = (zoom * zoomFactor).coerceIn(1f, 6f)
                            val photoGeometry =
                                PhotoGeometry(
                                    task.width,
                                    task.height,
                                    viewportSizePx.width.toFloat(),
                                    viewportSizePx.height.toFloat(),
                                    newZoom,
                                )
                            val clamped =
                                photoGeometry.clampPan(
                                    panPx.x + panDeltaPx.x,
                                    panPx.y + panDeltaPx.y,
                                )
                            zoom = newZoom
                            panPx = Offset(clamped.first, clamped.second)
                        }
                    }
                    .pointerInput(task.id, mode, canEdit) {
                        detectTapGestures { tapPx ->
                            if (!canEdit) return@detectTapGestures
                            val photoGeometry = currentGeometry
                            val normalizedPoint =
                                photoGeometry.imagePoint(tapPx.x, tapPx.y)
                                    ?: return@detectTapGestures
                            if (
                                mode == EditMode.ADD &&
                                    currentTask.region.contains(
                                        normalizedPoint.first,
                                        normalizedPoint.second,
                                    )
                            ) {
                                normalizedCursor =
                                    Offset(normalizedPoint.first, normalizedPoint.second)
                                currentOnAddMarker(normalizedPoint.first, normalizedPoint.second)
                            } else if (mode == EditMode.DELETE) {
                                val hitMarkers =
                                    currentTask.markers.filter { marker ->
                                        val clampedPanPx =
                                            photoGeometry.screenPoint(marker.x, marker.y)
                                        hypot(
                                            clampedPanPx.first - tapPx.x,
                                            clampedPanPx.second - tapPx.y,
                                        ) <=
                                            max(
                                                24.dp.toPx(),
                                                marker.radius * photoGeometry.drawnWidthPx,
                                            )
                                    }
                                if (hitMarkers.size == 1)
                                    currentOnRemoveMarkers(setOf(hitMarkers.first().id))
                                else if (hitMarkers.size > 1)
                                    overlappingMarkerIds = hitMarkers.map { it.id }
                            }
                        }
                    }
            ) {
                val photoGeometry = geometry
                if (raster != null)
                    drawImage(
                        raster,
                        dstOffset =
                            IntOffset(
                                photoGeometry.leftPx.roundToInt(),
                                photoGeometry.topPx.roundToInt(),
                            ),
                        dstSize =
                            IntSize(
                                photoGeometry.drawnWidthPx.roundToInt().coerceAtLeast(1),
                                photoGeometry.drawnHeightPx.roundToInt().coerceAtLeast(1),
                            ),
                    )
                val region = task.region
                val regionTopLeftPx = photoGeometry.screenPoint(region.left, region.top)
                val regionBottomRightPx = photoGeometry.screenPoint(region.right, region.bottom)
                drawRect(
                    Color(0xFFFFD166),
                    Offset(regionTopLeftPx.first, regionTopLeftPx.second),
                    Size(
                        regionBottomRightPx.first - regionTopLeftPx.first,
                        regionBottomRightPx.second - regionTopLeftPx.second,
                    ),
                    style = Stroke(2.dp.toPx()),
                )
                task.markers.forEachIndexed { markerIndex, marker ->
                    val clampedPanPx = photoGeometry.screenPoint(marker.x, marker.y)
                    val center = Offset(clampedPanPx.first, clampedPanPx.second)
                    val radius =
                        max(
                            10.dp.toPx(),
                            min(marker.radius * photoGeometry.drawnWidthPx, 28.dp.toPx()),
                        )
                    val color = if (marker.manual) Color(0xFF60A5FA) else Color(0xFFFFD166)
                    if (marker.manual) {
                        drawRect(
                            Color.Black,
                            center - Offset(radius, radius),
                            Size(radius * 2, radius * 2),
                            style = Stroke(5.dp.toPx()),
                        )
                        drawRect(
                            color,
                            center - Offset(radius, radius),
                            Size(radius * 2, radius * 2),
                            style = Stroke(2.dp.toPx()),
                        )
                    } else {
                        drawCircle(Color.Black, radius, center, style = Stroke(5.dp.toPx()))
                        drawCircle(color, radius, center, style = Stroke(2.dp.toPx()))
                    }
                    if (showNumbers) {
                        drawCircle(Color(0xD9000000), 10.dp.toPx(), center)
                        numberPaint.textSize = 11.sp.toPx()
                        drawContext.canvas.nativeCanvas.drawText(
                            "${markerIndex + 1}",
                            center.x,
                            center.y - (numberPaint.ascent() + numberPaint.descent()) / 2,
                            numberPaint,
                        )
                    }
                }
                if (mode == EditMode.ADD && canEdit) {
                    val clampedPanPx =
                        photoGeometry.screenPoint(normalizedCursor.x, normalizedCursor.y)
                    val cursorCenterPx = Offset(clampedPanPx.first, clampedPanPx.second)
                    val crosshairLengthPx = 12.dp.toPx()
                    val crosshairGapPx = 5.dp.toPx()
                    drawLine(
                        Color.White,
                        cursorCenterPx - Offset(crosshairLengthPx, 0f),
                        cursorCenterPx - Offset(crosshairGapPx, 0f),
                        3.dp.toPx(),
                    )
                    drawLine(
                        Color.White,
                        cursorCenterPx + Offset(crosshairGapPx, 0f),
                        cursorCenterPx + Offset(crosshairLengthPx, 0f),
                        3.dp.toPx(),
                    )
                    drawLine(
                        Color.White,
                        cursorCenterPx - Offset(0f, crosshairLengthPx),
                        cursorCenterPx - Offset(0f, crosshairGapPx),
                        3.dp.toPx(),
                    )
                    drawLine(
                        Color.White,
                        cursorCenterPx + Offset(0f, crosshairGapPx),
                        cursorCenterPx + Offset(0f, crosshairLengthPx),
                        3.dp.toPx(),
                    )
                }
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            OutlinedButton(onClick = { zoom = (zoom * 1.5f).coerceAtMost(6f) }) { Text("확대") }
            OutlinedButton(
                onClick = {
                    zoom = (zoom / 1.5f).coerceAtLeast(1f)
                    val clampedPanPx = geometry.copy(zoom = zoom).clampPan(panPx.x, panPx.y)
                    panPx = Offset(clampedPanPx.first, clampedPanPx.second)
                }
            ) {
                Text("축소")
            }
            TextButton(
                onClick = {
                    zoom = 1f
                    panPx = Offset.Zero
                }
            ) {
                Text("전체 보기")
            }
            if (task.markers.isNotEmpty())
                TextButton(onClick = { isMarkerListOpen = true }) { Text("표시 목록 ${task.count}개") }
        }
        if (bitmap == null && !isLoading)
            Text("사진을 표시할 수 없습니다. 기록 목록에서 다시 열어주세요.", color = MaterialTheme.colorScheme.error)
        if (mode == EditMode.ADD && canEdit) {
            Text(
                "추가 위치: 가로 ${(normalizedCursor.x * 100).roundToInt()}%, 세로 ${(normalizedCursor.y * 100).roundToInt()}%",
                style = MaterialTheme.typography.bodyMedium,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                TextButton(
                    onClick = {
                        normalizedCursor =
                            normalizedCursor.copy(
                                x = (normalizedCursor.x - .01f).coerceAtLeast(task.region.left)
                            )
                    }
                ) {
                    Text("위치 왼쪽")
                }
                TextButton(
                    onClick = {
                        normalizedCursor =
                            normalizedCursor.copy(
                                x = (normalizedCursor.x + .01f).coerceAtMost(task.region.right)
                            )
                    }
                ) {
                    Text("위치 오른쪽")
                }
                TextButton(
                    onClick = {
                        normalizedCursor =
                            normalizedCursor.copy(
                                y = (normalizedCursor.y - .01f).coerceAtLeast(task.region.top)
                            )
                    }
                ) {
                    Text("위치 위")
                }
                TextButton(
                    onClick = {
                        normalizedCursor =
                            normalizedCursor.copy(
                                y = (normalizedCursor.y + .01f).coerceAtMost(task.region.bottom)
                            )
                    }
                ) {
                    Text("위치 아래")
                }
                OutlinedButton(onClick = { onAddMarker(normalizedCursor.x, normalizedCursor.y) }) {
                    Text("이 위치에 추가")
                }
            }
        }
    }
    if (isMarkerListOpen || overlappingMarkerIds.isNotEmpty()) {
        val visibleMarkers =
            if (overlappingMarkerIds.isNotEmpty())
                task.markers.filter { it.id in overlappingMarkerIds }
            else task.markers
        AlertDialog(
            onDismissRequest = {
                isMarkerListOpen = false
                overlappingMarkerIds = emptyList()
            },
            title = { Text(if (overlappingMarkerIds.isNotEmpty()) "겹친 표시 선택" else "표시 목록") },
            text = {
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    itemsIndexed(visibleMarkers, key = { _, marker -> marker.id }) { _, marker ->
                        val number = task.markers.indexOfFirst { it.id == marker.id } + 1
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        ) {
                            Text(
                                "$number · ${if (marker.manual) "수동" else "자동"}\n가로 ${(marker.x * 100).roundToInt()}%, 세로 ${(marker.y * 100).roundToInt()}%",
                                Modifier.weight(1f),
                            )
                            TextButton(
                                onClick = {
                                    zoom = 3f
                                    val photoGeometry = geometry.copy(zoom = 3f)
                                    val clampedPanPx =
                                        photoGeometry.clampPan(
                                            (.5f - marker.x) * photoGeometry.drawnWidthPx,
                                            (.5f - marker.y) * photoGeometry.drawnHeightPx,
                                        )
                                    panPx = Offset(clampedPanPx.first, clampedPanPx.second)
                                    isMarkerListOpen = false
                                    overlappingMarkerIds = emptyList()
                                }
                            ) {
                                Text("보기")
                            }
                            if (canEdit)
                                TextButton(
                                    onClick = {
                                        onRemoveMarkers(setOf(marker.id))
                                        isMarkerListOpen = false
                                        overlappingMarkerIds = emptyList()
                                    }
                                ) {
                                    Text("삭제")
                                }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        isMarkerListOpen = false
                        overlappingMarkerIds = emptyList()
                    }
                ) {
                    Text("닫기")
                }
            },
        )
    }
}
