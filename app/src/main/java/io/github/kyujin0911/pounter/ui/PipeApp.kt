package io.github.kyujin0911.pounter.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import io.github.kyujin0911.pounter.model.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun timestamp(time: Long) = SimpleDateFormat("MM.dd HH:mm",Locale.KOREA).format(Date(time))
enum class EditMode { VIEW, ADD, DELETE }

@Composable
fun HomeScreen(state: CountUiState,dispatch: (CountAction)->Unit,onPick: ()->Unit,onCamera: ()->Unit,
               inputError: String?=null,onClearInputError: ()->Unit={},onOpen: (CountTask)->Unit={}) {
    PipeScreen(state,dispatch,onPick,onCamera,inputError,onClearInputError,onOpen=onOpen)
}

@Composable
fun CountWorkScreen(state: CountUiState,dispatch: (CountAction)->Unit,onPick: ()->Unit,onCamera: ()->Unit,
                    inputError: String?=null,onClearInputError: ()->Unit={},onBack: ()->Unit={}) {
    PipeScreen(state,dispatch,onPick,onCamera,inputError,onClearInputError,detail=true,onBack=onBack)
}

@Composable
fun RecordScreen(state: CountUiState,dispatch: (CountAction)->Unit,onPick: ()->Unit,onCamera: ()->Unit,
                 inputError: String?=null,onClearInputError: ()->Unit={},onBack: ()->Unit={}) {
    PipeScreen(state,dispatch,onPick,onCamera,inputError,onClearInputError,detail=true,readOnly=true,onBack=onBack)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PipeScreen(state: CountUiState, dispatch: (CountAction)->Unit, onPick: ()->Unit, onCamera: ()->Unit,
            inputError: String?,onClearInputError: ()->Unit,detail: Boolean=false,readOnly: Boolean=false,
            onOpen: (CountTask)->Unit={},onBack: ()->Unit={}) {
    var dialog by rememberSaveable { mutableStateOf<String?>(null) }
    var mode by rememberSaveable { mutableStateOf(EditMode.VIEW) }
    var showNumbers by rememberSaveable { mutableStateOf(true) }
    val task=state.task
    val editable=!state.busy && !state.unsavedChanges
    fun back() {
        when {
            state.operation==Operation.ANALYZING -> dispatch(CountAction.CancelAnalysis)
            state.busy -> Unit
            state.unsavedChanges -> dialog="unsaved"
            else -> onBack()
        }
    }
    // NavHost owns ordinary Back. Intercept only operations that must not lose work.
    BackHandler(state.busy || state.unsavedChanges) { back() }
    Scaffold(containerColor=MaterialTheme.colorScheme.background) { insets ->
        LazyColumn(Modifier.fillMaxSize().padding(insets).imePadding().testTag("content"),
            contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            item {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if(!detail) "Pounter" else if(task==null) "작업 불러오기" else if(task.confirmed) "검수 기록" else "사진 확인",fontSize=22.sp,fontWeight=FontWeight.Bold)
                        Text(if(!detail) "사진으로 확인하고, 기록으로 남기세요" else task?.let { "${timestamp(it.createdAt)} · 기기 내부 저장" }.orEmpty(),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if(detail) TextButton(onClick={back()},enabled=!state.busy || state.operation==Operation.ANALYZING) { Text("기록 목록") }
                }
            }
            if(state.busy) item {
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(Modifier.size(24.dp),strokeWidth=2.dp)
                        Text(when(state.operation) { Operation.ANALYZING->"분석 중"; Operation.SAVING->"변경 사항 저장 중"; else->"사진과 기록을 불러오는 중" },Modifier.weight(1f))
                        if(state.operation==Operation.ANALYZING) TextButton(onClick={dispatch(CountAction.CancelAnalysis)}) { Text("취소") }
                    }
                }
            }
            if(state.unsavedChanges && !state.busy) item {
                Text("저장되지 않은 변경이 있습니다. 저장을 재시도한 뒤 이동해주세요.")
                Button(onClick={dispatch(CountAction.RetrySave)}) { Text("저장 재시도") }
            }
            if(state.message!=null || inputError!=null) item {
                Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.errorContainer)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(inputError ?: state.message.orEmpty(),color=MaterialTheme.colorScheme.onErrorContainer)
                        TextButton(onClick={dispatch(CountAction.ClearMessage);onClearInputError()}) { Text("닫기") }
                    }
                }
            }
            if(detail && task==null) {
                if(!state.busy) item {
                    Text(state.message ?: "작업을 표시할 수 없습니다.")
                    Button(onClick=onBack) { Text("목록으로 돌아가기") }
                }
            } else if(!detail) {
                item {
                    Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.primaryContainer)) {
                        Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                            Text("새 물량 확인",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
                            Text("파이프 단면 전체가 보이도록 정면에서 촬영해주세요. 한 묶음, 비슷한 지름, 가림 없는 사진이 기준입니다.")
                            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                Button(onClick=onCamera,enabled=editable,modifier=Modifier.heightIn(min=56.dp)) { Text("사진 촬영") }
                                OutlinedButton(onClick=onPick,enabled=editable,modifier=Modifier.heightIn(min=56.dp)) { Text("사진 선택") }
                            }
                        }
                    }
                }
                item { ValidationNotice() }
                item {
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        Text("내 기록",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f))
                        Text("${state.records.size}건",color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if(state.records.isEmpty() && !state.busy) item {
                    Column(Modifier.fillMaxWidth().padding(vertical=24.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        Text("아직 저장된 기록이 없습니다",style=MaterialTheme.typography.titleMedium)
                        Text("사진을 선택하거나 촬영해 시작하세요",color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                items(state.records,key={it.id}) { record ->
                    Card(onClick={if(!state.busy)onOpen(record)},modifier=Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                            Surface(color=MaterialTheme.colorScheme.primaryContainer,shape=MaterialTheme.shapes.medium) {
                                Text(if(record.analyzed) "${record.count}" else "—",Modifier.padding(16.dp),fontSize=28.sp,fontWeight=FontWeight.Bold)
                            }
                            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                                Text(if(record.confirmed) "확정됨 · ${record.count}개" else "진행 중 · 이어서 확인",fontWeight=FontWeight.Bold)
                                Text(timestamp(record.updatedAt),color=MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(if(record.analysisMillis==null) "수동 확인 / 자동 계수 미검증" else "자동 분석 후 확인",style=MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
                item { Text("기록은 이 기기에만 보관됩니다. 앱 데이터 삭제 또는 앱 제거 시 사라집니다.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) }
            } else if(task!=null) {
                item {
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                        Text("${task.count}",fontSize=40.sp,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.primary)
                        Column {
                            Text("현재 개수",fontWeight=FontWeight.Bold)
                            Text(if(task.confirmed) "확정됨 · ${timestamp(task.confirmedAt!!)}" else "확인 전 · 표시와 단면을 대조해주세요",style=MaterialTheme.typography.bodyMedium)
                            Text(if(task.analysisMillis==null) "수동 표시" else "자동 분석 ${task.analysisMillis}ms",style=MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                item {
                    PhotoPanel(state,if(readOnly) EditMode.VIEW else mode,showNumbers,dispatch,readOnly)
                }
                item {
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        Text("번호 표시",Modifier.weight(1f))
                        Switch(checked=showNumbers,onCheckedChange={showNumbers=it})
                    }
                }
                if(!readOnly && !task.confirmed) {
                    item {
                        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick={dialog="region"},enabled=editable) { Text("계수 영역") }
                            OutlinedButton(onClick={if(task.markers.isNotEmpty()) dialog="reanalyze" else dispatch(CountAction.Analyze)},enabled=editable) { Text("자동 계수 상태") }
                            if(!task.analyzed) Button(onClick={dispatch(CountAction.BeginManual);mode=EditMode.ADD},enabled=editable) { Text("수동 표시 시작") }
                        }
                    }
                    item { ValidationNotice() }
                    if(task.analyzed) {
                        item {
                            Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                Text("표시 보정",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
                                FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                    FilterChip(selected=mode==EditMode.VIEW,onClick={mode=EditMode.VIEW},label={Text("이동")},enabled=editable)
                                    FilterChip(selected=mode==EditMode.ADD,onClick={mode=EditMode.ADD},label={Text("추가")},enabled=editable)
                                    FilterChip(selected=mode==EditMode.DELETE,onClick={mode=EditMode.DELETE},label={Text("삭제")},enabled=editable)
                                    OutlinedButton(onClick={dispatch(CountAction.Undo)},enabled=editable && task.undo!=null) { Text("되돌리기") }
                                }
                                Text(when(mode) { EditMode.ADD->"단면 중심을 눌러 표시를 추가하세요. 원형은 자동, 사각형은 수동 표시입니다."; EditMode.DELETE->"삭제할 표시를 누르세요. 겹친 표시는 목록에서 선택할 수 있습니다."; else->"두 손가락으로 확대하고 사진을 이동할 수 있습니다." },style=MaterialTheme.typography.bodySmall)
                            }
                        }
                        item {
                            Button(onClick={dialog="confirm"},enabled=editable,modifier=Modifier.fillMaxWidth().heightIn(min=56.dp)) { Text("${task.count}개 확인하고 확정") }
                        }
                    }
                }
                item {
                    FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick=onPick,enabled=editable) { Text("다른 사진 선택") }
                        OutlinedButton(onClick=onCamera,enabled=editable) { Text("새 사진 촬영") }
                        TextButton(onClick={dialog="delete"},enabled=editable) { Text("이 기록 삭제",color=MaterialTheme.colorScheme.error) }
                    }
                    Text("새 사진은 별도 작업으로 저장되며 이 기록은 유지됩니다.",style=MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    if(dialog=="unsaved") {
        ConfirmDialog("저장되지 않은 변경", "현재 변경을 유지하고 있습니다. 저장을 재시도한 뒤 목록으로 이동해주세요.","저장 재시도",{dialog=null}) {
            dialog=null; dispatch(CountAction.RetrySave)
        }
    }
    if(task!=null && dialog!=null) {
        when(dialog) {
            "region" -> RegionDialog(task,onDismiss={dialog=null},onApply={r -> dispatch(CountAction.SetRegion(r)); mode=EditMode.VIEW;dialog=null})
            "confirm" -> ConfirmDialog("개수 확정", "사진의 모든 단면과 표시를 확인했나요? ${task.count}개로 저장하며 확정된 기록은 수정할 수 없습니다.","확정 저장",{dialog=null}) {dispatch(CountAction.Confirm);mode=EditMode.VIEW;dialog=null}
            "delete" -> ConfirmDialog("기록 삭제", "앱 내부 사진과 표시가 삭제됩니다. 원본 갤러리 사진은 유지되며 이 삭제는 되돌릴 수 없습니다.","삭제",{dialog=null}) {dispatch(CountAction.Delete);dialog=null}
            "reanalyze" -> ConfirmDialog("자동 계수 상태", "자동 계수는 정면 사진 검증 대기 중입니다. 현재 수동 표시는 유지됩니다.","확인",{dialog=null}) {dispatch(CountAction.Analyze);dialog=null}
        }
    }
}

@Composable private fun ValidationNotice() {
    Surface(color=MaterialTheme.colorScheme.surfaceContainer,shape=MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Text("자동 계수 · 검증 대기",fontWeight=FontWeight.Bold)
            Text("추가 정면 사진으로 정확도를 검증한 뒤 제공합니다. 현재는 사진에 수동으로 표시하고 기록을 남길 수 있습니다.",style=MaterialTheme.typography.bodyMedium)
        }
    }
}
@Composable private fun ConfirmDialog(title: String,body: String,confirm: String,onDismiss: ()->Unit,onConfirm: ()->Unit) {
    AlertDialog(onDismissRequest=onDismiss,title={Text(title)},text={Text(body)},confirmButton={TextButton(onClick=onConfirm){Text(confirm)}},dismissButton={TextButton(onClick=onDismiss){Text("취소")}})
}
@Composable private fun RegionDialog(task: CountTask,onDismiss: ()->Unit,onApply: (Region)->Unit) {
    var r by remember { mutableStateOf(task.region) }
    AlertDialog(onDismissRequest=onDismiss,title={Text("계수 영역")},text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("사진 전체를 0~100%로 표시합니다. 영역을 바꾸면 기존 표시와 보정이 초기화됩니다.")
            RegionSlider("왼쪽",r.left,0f..(r.right-.05f)) {r=r.copy(left=it)}
            RegionSlider("위쪽",r.top,0f..(r.bottom-.05f)) {r=r.copy(top=it)}
            RegionSlider("오른쪽",r.right,(r.left+.05f)..1f) {r=r.copy(right=it)}
            RegionSlider("아래쪽",r.bottom,(r.top+.05f)..1f) {r=r.copy(bottom=it)}
            TextButton(onClick={r=Region()}) {Text("사진 전체로 초기화")}
        }
    },confirmButton={TextButton(onClick={onApply(r)}){Text(if(task.markers.isEmpty()) "영역 적용" else "표시 초기화하고 적용")}},dismissButton={TextButton(onClick=onDismiss){Text("취소")}})
}
@Composable private fun RegionSlider(label: String,value: Float,range: ClosedFloatingPointRange<Float>,onChange: (Float)->Unit) {
    Text("$label ${kotlin.math.round(value*100).toInt()}%")
    Slider(value=value.coerceIn(range),onValueChange=onChange,valueRange=range,modifier=Modifier.semantics { contentDescription="계수 영역 $label" })
}

@Preview(showBackground=true)
@Composable private fun HomePreview() { PipeTheme { HomeScreen(CountUiState(operation=Operation.NONE),{}, {}, {}) } }
