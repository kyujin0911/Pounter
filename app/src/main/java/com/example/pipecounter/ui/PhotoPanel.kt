package com.example.pipecounter.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.*
import com.example.pipecounter.model.*
import kotlin.math.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PhotoPanel(state: CountUiState,mode: EditMode,showNumbers: Boolean,dispatch: (CountAction)->Unit) {
    val task=state.task ?: return
    val bitmap=state.preview
    var zoom by remember(task.id) { mutableFloatStateOf(1f) }
    var pan by remember(task.id) { mutableStateOf(Offset.Zero) }
    var viewport by remember { mutableStateOf(IntSize(1,1)) }
    var cursor by remember(task.id,task.region) { mutableStateOf(Offset((task.region.left+task.region.right)/2,(task.region.top+task.region.bottom)/2)) }
    var candidates by remember(task.id) { mutableStateOf<List<String>>(emptyList()) }
    var listOpen by remember(task.id) { mutableStateOf(false) }
    val geometry=PhotoGeometry(task.width,task.height,viewport.width.toFloat(),viewport.height.toFloat(),zoom,pan.x,pan.y)
    val currentGeometry by rememberUpdatedState(geometry)
    val currentTask by rememberUpdatedState(task)
    val currentDispatch by rememberUpdatedState(dispatch)
    val enabled=!state.busy && !task.confirmed && task.analyzed
    val raster=remember(bitmap) { bitmap?.asImageBitmap() }
    val numberPaint=remember { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color=android.graphics.Color.WHITE; textAlign=android.graphics.Paint.Align.CENTER; typeface=android.graphics.Typeface.DEFAULT_BOLD
    } }
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Surface(shape=MaterialTheme.shapes.large,color=Color(0xFF111827)) {
            Canvas(Modifier.fillMaxWidth().height(340.dp).onSizeChanged {viewport=it}
                .semantics { contentDescription="계수 사진. ${task.count}개 표시. 아래 확대와 표시 목록, 위치 조절 버튼으로도 사용할 수 있습니다." }
                .pointerInput(task.id) {
                    detectTransformGestures { _,delta,factor,_ ->
                        val newZoom=(zoom*factor).coerceIn(1f,6f)
                        val g=PhotoGeometry(task.width,task.height,viewport.width.toFloat(),viewport.height.toFloat(),newZoom)
                        val clamped=g.clampPan(pan.x+delta.x,pan.y+delta.y)
                        zoom=newZoom; pan=Offset(clamped.first,clamped.second)
                    }
                }
                .pointerInput(task.id,mode,enabled) {
                    detectTapGestures { tap ->
                        if(!enabled) return@detectTapGestures
                        val g=currentGeometry
                        val point=g.imagePoint(tap.x,tap.y) ?: return@detectTapGestures
                        if(mode==EditMode.ADD && currentTask.region.contains(point.first,point.second)) {
                            cursor=Offset(point.first,point.second)
                            currentDispatch(CountAction.Add(point.first,point.second))
                        } else if(mode==EditMode.DELETE) {
                            val found=currentTask.markers.filter { m ->
                                val p=g.screenPoint(m.x,m.y)
                                hypot(p.first-tap.x,p.second-tap.y)<=max(24.dp.toPx(),m.radius*g.drawnWidth)
                            }
                            if(found.size==1) currentDispatch(CountAction.Remove(setOf(found.first().id)))
                            else if(found.size>1) candidates=found.map {it.id}
                        }
                    }
                }) {
                val g=geometry
                if(raster!=null) drawImage(raster,dstOffset=IntOffset(g.left.roundToInt(),g.top.roundToInt()),dstSize=IntSize(g.drawnWidth.roundToInt().coerceAtLeast(1),g.drawnHeight.roundToInt().coerceAtLeast(1)))
                val r=task.region
                val a=g.screenPoint(r.left,r.top); val b=g.screenPoint(r.right,r.bottom)
                drawRect(Color(0xFFFFD166),Offset(a.first,a.second),Size(b.first-a.first,b.second-a.second),style=Stroke(2.dp.toPx()))
                task.markers.forEachIndexed { i,m ->
                    val p=g.screenPoint(m.x,m.y); val center=Offset(p.first,p.second)
                    val radius=max(10.dp.toPx(),min(m.radius*g.drawnWidth,28.dp.toPx()))
                    val color=if(m.manual)Color(0xFF60A5FA) else Color(0xFFFFD166)
                    if(m.manual) {
                        drawRect(Color.Black,center-Offset(radius,radius),Size(radius*2,radius*2),style=Stroke(5.dp.toPx()))
                        drawRect(color,center-Offset(radius,radius),Size(radius*2,radius*2),style=Stroke(2.dp.toPx()))
                    } else {
                        drawCircle(Color.Black,radius,center,style=Stroke(5.dp.toPx()))
                        drawCircle(color,radius,center,style=Stroke(2.dp.toPx()))
                    }
                    if(showNumbers) {
                        drawCircle(Color(0xD9000000),10.dp.toPx(),center)
                        numberPaint.textSize=11.sp.toPx()
                        drawContext.canvas.nativeCanvas.drawText("${i+1}",center.x,center.y-(numberPaint.ascent()+numberPaint.descent())/2,numberPaint)
                    }
                }
                if(mode==EditMode.ADD && enabled) {
                    val p=g.screenPoint(cursor.x,cursor.y); val c=Offset(p.first,p.second); val d=12.dp.toPx()
                    val gap=5.dp.toPx()
                    drawLine(Color.White,c-Offset(d,0f),c-Offset(gap,0f),3.dp.toPx())
                    drawLine(Color.White,c+Offset(gap,0f),c+Offset(d,0f),3.dp.toPx())
                    drawLine(Color.White,c-Offset(0f,d),c-Offset(0f,gap),3.dp.toPx())
                    drawLine(Color.White,c+Offset(0f,gap),c+Offset(0f,d),3.dp.toPx())
                }
            }
        }
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            OutlinedButton(onClick={zoom=(zoom*1.5f).coerceAtMost(6f)}) {Text("확대")}
            OutlinedButton(onClick={zoom=(zoom/1.5f).coerceAtLeast(1f);val p=geometry.copy(zoom=zoom).clampPan(pan.x,pan.y);pan=Offset(p.first,p.second)}) {Text("축소")}
            TextButton(onClick={zoom=1f;pan=Offset.Zero}) {Text("전체 보기")}
            if(task.markers.isNotEmpty()) TextButton(onClick={listOpen=true}) {Text("표시 목록 ${task.count}개")}
        }
        if(bitmap==null && !state.busy) Text("사진을 표시할 수 없습니다. 기록 목록에서 다시 열어주세요.",color=MaterialTheme.colorScheme.error)
        if(mode==EditMode.ADD && enabled) {
            Text("추가 위치: 가로 ${(cursor.x*100).roundToInt()}%, 세로 ${(cursor.y*100).roundToInt()}%",style=MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(4.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                TextButton(onClick={cursor=cursor.copy(x=(cursor.x-.01f).coerceAtLeast(task.region.left))}){Text("위치 왼쪽")}
                TextButton(onClick={cursor=cursor.copy(x=(cursor.x+.01f).coerceAtMost(task.region.right))}){Text("위치 오른쪽")}
                TextButton(onClick={cursor=cursor.copy(y=(cursor.y-.01f).coerceAtLeast(task.region.top))}){Text("위치 위")}
                TextButton(onClick={cursor=cursor.copy(y=(cursor.y+.01f).coerceAtMost(task.region.bottom))}){Text("위치 아래")}
                OutlinedButton(onClick={dispatch(CountAction.Add(cursor.x,cursor.y))}) {Text("이 위치에 추가")}
            }
        }
    }
    if(listOpen || candidates.isNotEmpty()) {
        val shown=if(candidates.isNotEmpty()) task.markers.filter {it.id in candidates} else task.markers
        AlertDialog(onDismissRequest={listOpen=false;candidates=emptyList()},title={Text(if(candidates.isNotEmpty()) "겹친 표시 선택" else "표시 목록")},text={
            LazyColumn(Modifier.heightIn(max=360.dp)) {
                itemsIndexed(shown,key={_,m->m.id}) { _,m ->
                    val number=task.markers.indexOfFirst {it.id==m.id}+1
                    Row(Modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                        Text("$number · ${if(m.manual) "수동" else "자동"}\n가로 ${(m.x*100).roundToInt()}%, 세로 ${(m.y*100).roundToInt()}%",Modifier.weight(1f))
                        TextButton(onClick={
                            zoom=3f
                            val g=geometry.copy(zoom=3f)
                            val p=g.clampPan((.5f-m.x)*g.drawnWidth,(.5f-m.y)*g.drawnHeight)
                            pan=Offset(p.first,p.second);listOpen=false;candidates=emptyList()
                        }){Text("보기")}
                        if(enabled) TextButton(onClick={dispatch(CountAction.Remove(setOf(m.id)));listOpen=false;candidates=emptyList()}) {Text("삭제")}
                    }
                }
            }
        },confirmButton={TextButton(onClick={listOpen=false;candidates=emptyList()}){Text("닫기")}})
    }
}
