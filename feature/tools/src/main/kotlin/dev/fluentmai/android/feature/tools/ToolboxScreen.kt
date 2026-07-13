package dev.fluentmai.android.feature.tools

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.fluentmai.android.core.model.KaleidScopeCatalog
import dev.fluentmai.android.core.model.MaimaiAchievementCalculation
import dev.fluentmai.android.core.model.MaimaiJudgement
import dev.fluentmai.android.core.model.MaimaiMajorVersion
import dev.fluentmai.android.core.model.MaimaiNoteCounts
import dev.fluentmai.android.core.model.MaimaiNoteKind
import dev.fluentmai.android.core.model.RatingHistoryEntry
import dev.fluentmai.android.core.model.SingleSongRatingCalculation
import dev.fluentmai.android.core.model.buildMaimaiVersionReferences
import dev.fluentmai.android.core.model.calculateMaimaiAchievement
import dev.fluentmai.android.core.model.calculateSingleSongRating
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

@Composable
fun ToolboxScreen(
    majorVersions: List<MaimaiMajorVersion>,
    ratingHistory: List<RatingHistoryEntry>,
    onAddManualRating: (Long, Int, String?) -> Unit,
    onUpdateManualRating: (String, Long, Int, String?) -> Unit,
    onDeleteManualRating: (String) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    kaleidScopeRepository: KaleidScopeRepository = ReviewedKaleidScopeRepository,
) {
    var sectionName by rememberSaveable { mutableStateOf(ToolSection.RATING.name) }
    val section = ToolSection.entries.firstOrNull { it.name == sectionName } ?: ToolSection.RATING

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(
            modifier = Modifier.widthIn(max = 1000.dp).fillMaxSize(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("工具箱", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "公式、版本资料与本地 Rating 时间轴",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = "应用设置")
                }
            }
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ToolSection.entries, key = ToolSection::name) { item ->
                    FilterChip(
                        selected = section == item,
                        onClick = { sectionName = item.name },
                        label = { Text(item.label) },
                    )
                }
            }
        }
            item {
                when (section) {
                ToolSection.RATING -> RatingCalculator()
                ToolSection.ACHIEVEMENT -> AchievementCalculator()
                ToolSection.VERSIONS -> VersionReference(majorVersions)
                ToolSection.KALEID -> KaleidScopeStatus(kaleidScopeRepository.currentCatalog())
                ToolSection.TREND -> RatingTrend(
                    history = ratingHistory,
                    onAdd = onAddManualRating,
                    onUpdate = onUpdateManualRating,
                    onDelete = onDeleteManualRating,
                )
                }
            }
        }
    }
}

@Composable
private fun RatingCalculator() {
    var constantText by rememberSaveable { mutableStateOf("") }
    var achievementText by rememberSaveable { mutableStateOf("") }
    var result by remember { mutableStateOf<SingleSongRatingCalculation?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    ToolCard("单曲 Rating 计算", "公式输入只有谱面定数与达成率；达成率按 100.5% 封顶参与 Rating。") {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DecimalField(
                value = constantText,
                onValueChange = { constantText = it },
                label = "谱面定数",
                modifier = Modifier.weight(1f),
            )
            DecimalField(
                value = achievementText,
                onValueChange = { achievementText = it },
                label = "达成率 %",
                modifier = Modifier.weight(1f),
            )
        }
        Button(
            onClick = {
                result = null
                error = runCatching {
                    val constant = constantText.toDoubleOrNull() ?: kotlin.error("请输入有效谱面定数")
                    val achievement = achievementText.toDoubleOrNull() ?: kotlin.error("请输入有效达成率")
                    result = calculateSingleSongRating(constant, achievement)
                }.exceptionOrNull()?.message
            },
        ) { Text("计算") }
        error?.let { ErrorText(it) }
        result?.let { calculation ->
            ResultSurface {
                Text("单曲 Rating ${calculation.rating}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("成绩等级 ${calculation.rank.displayName} · 系数 ${calculation.coefficient.format(1)}")
                Text(
                    "floor(${calculation.levelValue.format(1)} × ${calculation.cappedAchievement.format(4)}% ÷ 100 × " +
                        "${calculation.coefficient.format(1)})",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (calculation.achievement > calculation.cappedAchievement) {
                    Text("原始达成率高于 100.5%，Rating 计算部分已按规则封顶。", color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        SourceNote(
            "Rating 阶段系数与边界由 core:model 回归测试锁定；结果向下取整，FC/AP 不提供额外 Rating。",
        )
    }
}

@Composable
private fun AchievementCalculator() {
    var tap by rememberSaveable { mutableStateOf("") }
    var hold by rememberSaveable { mutableStateOf("") }
    var slide by rememberSaveable { mutableStateOf("") }
    var touch by rememberSaveable { mutableStateOf("") }
    var breakCount by rememberSaveable { mutableStateOf("") }
    var occurrenceText by rememberSaveable { mutableStateOf("1") }
    var targetText by rememberSaveable { mutableStateOf("100.5000") }
    var kindName by rememberSaveable { mutableStateOf(MaimaiNoteKind.TAP.name) }
    var judgementName by rememberSaveable { mutableStateOf(MaimaiJudgement.GREAT.name) }
    var result by remember { mutableStateOf<MaimaiAchievementCalculation?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val kind = MaimaiNoteKind.entries.first { it.name == kindName }
    val judgements = MaimaiJudgement.entries.filter { kind == MaimaiNoteKind.BREAK || it != MaimaiJudgement.PERFECT_HIGH }
    val judgement = MaimaiJudgement.entries.firstOrNull { it.name == judgementName }
        ?.takeIf { it in judgements }
        ?: MaimaiJudgement.GREAT

    ToolCard("谱面失分与达成率", "输入谱面物量，查看单个判定失分、指定数量后的达成率与目标容错。") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IntegerField(tap, { tap = it }, "Tap", Modifier.weight(1f))
            IntegerField(hold, { hold = it }, "Hold", Modifier.weight(1f))
            IntegerField(slide, { slide = it }, "Slide", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IntegerField(touch, { touch = it }, "Touch", Modifier.weight(1f))
            IntegerField(breakCount, { breakCount = it }, "Break", Modifier.weight(1f))
        }
        Text("判定对象", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(MaimaiNoteKind.entries, key = MaimaiNoteKind::name) { item ->
                FilterChip(
                    selected = kind == item,
                    onClick = {
                        kindName = item.name
                        if (item != MaimaiNoteKind.BREAK && judgementName == MaimaiJudgement.PERFECT_HIGH.name) {
                            judgementName = MaimaiJudgement.PERFECT.name
                        }
                    },
                    label = { Text(item.displayName) },
                )
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(judgements, key = MaimaiJudgement::name) { item ->
                FilterChip(
                    selected = judgement == item,
                    onClick = { judgementName = item.name },
                    label = { Text(item.displayLabel(kind)) },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IntegerField(occurrenceText, { occurrenceText = it }, "出现次数", Modifier.weight(1f))
            DecimalField(targetText, { targetText = it }, "目标达成率 %", Modifier.weight(1f))
        }
        Button(
            onClick = {
                result = null
                error = runCatching {
                    val notes = MaimaiNoteCounts(
                        tap = tap.toIntOrNull() ?: kotlin.error("请填写 Tap 物量"),
                        hold = hold.toIntOrNull() ?: kotlin.error("请填写 Hold 物量"),
                        slide = slide.toIntOrNull() ?: kotlin.error("请填写 Slide 物量"),
                        touch = touch.toIntOrNull() ?: kotlin.error("请填写 Touch 物量"),
                        breakCount = breakCount.toIntOrNull() ?: kotlin.error("请填写 Break 物量"),
                    )
                    result = calculateMaimaiAchievement(
                        notes = notes,
                        noteKind = kind,
                        judgement = judgement,
                        occurrences = occurrenceText.toIntOrNull() ?: kotlin.error("请输入有效判定次数"),
                        targetAchievement = targetText.toDoubleOrNull() ?: kotlin.error("请输入有效目标达成率"),
                    )
                }.exceptionOrNull()?.message
            },
        ) { Text("计算失分") }
        error?.let { ErrorText(it) }
        result?.let { calculation ->
            ResultSurface {
                Text(
                    "结果 ${calculation.resultingAchievement.format(6)}%",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text("单个该判定失分 ${calculation.lossPerJudgement.format(6)}%")
                Text(
                    "以 ${calculation.targetAchievement.format(4)}% 为目标，最多容许 " +
                        "${calculation.toleratedOccurrences} 个同类判定。",
                )
                Text("全 Critical Perfect 理论值 ${calculation.maximumAchievement.format(4)}%")
            }
        }
        SourceNote(
            "基础权重 Tap/Touch=1、Hold=2、Slide=3、Break=5；Great/Good/Miss 基础倍率为 0.8/0.5/0。" +
                "BREAK 额外段按 Critical Perfect=1、2550 Perfect=0.75、2500 Perfect=0.5、Great=0.4、Good=0.3。",
        )
    }
}

@Composable
private fun VersionReference(majorVersions: List<MaimaiMajorVersion>) {
    val versions = remember(majorVersions) { buildMaimaiVersionReferences(majorVersions) }
    ToolCard("版本名称对照", "优先显示当前曲库版本表；静态回退数据集中维护在 core:model，不在 UI 散落大表。") {
        versions.forEachIndexed { index, version ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(version.officialName, fontWeight = FontWeight.Bold)
                    Text(version.generation.displayName, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                AssistChip(onClick = {}, label = { Text(version.versionId.toString()) })
            }
            if (index != versions.lastIndex) HorizontalDivider()
        }
        SourceNote("版本 ID 与正式名称来自 LXNS 公开曲库版本表；牌子简称没有稳定结构化来源，本版本不猜测补全。")
    }
}

@Composable
private fun KaleidScopeStatus(catalog: KaleidScopeCatalog) {
    ToolCard("Kaleid×Scope", "门曲与解锁条件只接受可审查、可更新的数据源。") {
        when (catalog) {
            is KaleidScopeCatalog.Available -> {
                if (catalog.gates.isEmpty()) {
                    ErrorText("数据源返回空目录，未展示任何虚构门曲。")
                } else {
                    catalog.gates.forEach { gate ->
                        ResultSurface {
                            Text(gate.name, fontWeight = FontWeight.Bold)
                            Text(gate.songs.joinToString(" · "))
                            Text(gate.unlockCondition, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            is KaleidScopeCatalog.Unavailable -> {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("数据源待接入", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(catalog.reason)
                    }
                }
                Text("已审查来源", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                catalog.reviewedSources.forEach { source ->
                    Text(source, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun RatingTrend(
    history: List<RatingHistoryEntry>,
    onAdd: (Long, Int, String?) -> Unit,
    onUpdate: (String, Long, Int, String?) -> Unit,
    onDelete: (String) -> Unit,
) {
    var rangeName by rememberSaveable { mutableStateOf(TrendRange.MONTH.name) }
    var editorEntry by remember { mutableStateOf<RatingHistoryEntry?>(null) }
    var showAddEditor by remember { mutableStateOf(false) }
    var deleteEntry by remember { mutableStateOf<RatingHistoryEntry?>(null) }
    val range = TrendRange.entries.firstOrNull { it.name == rangeName } ?: TrendRange.MONTH
    val now = System.currentTimeMillis()
    val visible = remember(history, range, now / DAY_MILLIS) {
        val cutoff = range.durationDays?.let { now - it * DAY_MILLIS }
        history.filter { cutoff == null || it.recordedAtEpochMillis >= cutoff }
            .sortedBy { it.recordedAtEpochMillis }
    }

    ToolCard("Rating Trend", "只绘制真实本地记录；不会根据现有分数反推或伪造过去时间点。") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(TrendRange.entries, key = TrendRange::name) { item ->
                    FilterChip(
                        selected = range == item,
                        onClick = { rangeName = item.name },
                        label = { Text(item.label) },
                    )
                }
            }
            IconButton(onClick = { showAddEditor = true }) {
                Icon(Icons.Filled.Add, contentDescription = "手动补录")
            }
        }
        if (visible.isEmpty()) {
            ResultSurface {
                Text("当前范围没有 Rating 记录", fontWeight = FontWeight.Bold)
                Text("成功导入后的 Rating 变化会自动记录；也可以手动补录真实历史。")
            }
        } else {
            val latest = visible.maxBy { it.recordedAtEpochMillis }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("当前", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(latest.rating.toString(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("范围变化", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val delta = latest.rating - visible.first().rating
                    Text(if (delta >= 0) "+$delta" else delta.toString(), fontWeight = FontWeight.Bold)
                }
            }
            RatingTrendChart(visible)
        }
        Text("记录", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        history.sortedByDescending { it.recordedAtEpochMillis }.forEach { entry ->
            RatingHistoryRow(
                entry = entry,
                onEdit = { editorEntry = entry },
                onDelete = { deleteEntry = entry },
            )
        }
    }

    if (showAddEditor || editorEntry != null) {
        ManualRatingDialog(
            entry = editorEntry,
            onDismiss = {
                showAddEditor = false
                editorEntry = null
            },
            onSave = { recordedAt, rating, note ->
                editorEntry?.let { onUpdate(it.id, recordedAt, rating, note) }
                    ?: onAdd(recordedAt, rating, note)
                showAddEditor = false
                editorEntry = null
            },
        )
    }
    deleteEntry?.let { entry ->
        AlertDialog(
            onDismissRequest = { deleteEntry = null },
            title = { Text("删除手动记录？") },
            text = { Text("将删除 ${entry.recordedAtEpochMillis.formatDateTime()} 的 Rating ${entry.rating}。自动记录不会出现在此操作中。") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(entry.id)
                    deleteEntry = null
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { deleteEntry = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun RatingTrendChart(entries: List<RatingHistoryEntry>) {
    val sorted = entries.sortedBy { it.recordedAtEpochMillis }
    val primary = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val minRating = sorted.minOf { it.rating }
    val maxRating = sorted.maxOf { it.rating }
    val minTime = sorted.first().recordedAtEpochMillis
    val maxTime = sorted.last().recordedAtEpochMillis
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Rating", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("$minRating – $maxRating", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
            ) {
                val width = size.width
                val height = size.height
                repeat(4) { index ->
                    val y = height * index / 3f
                    drawLine(grid, Offset(0f, y), Offset(width, y), strokeWidth = 1f)
                }
                val ratingSpan = (maxRating - minRating).coerceAtLeast(1)
                val timeSpan = (maxTime - minTime).coerceAtLeast(1L)
                val points = sorted.mapIndexed { index, entry ->
                    val x = if (sorted.size == 1) width / 2f else {
                        width * ((entry.recordedAtEpochMillis - minTime).toDouble() / timeSpan).toFloat()
                    }
                    val y = height - height * ((entry.rating - minRating).toFloat() / ratingSpan)
                    if (sorted.size == 1) Offset(x, height / 2f) else Offset(x, y)
                }
                val path = Path()
                points.forEachIndexed { index, point ->
                    if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
                }
                if (points.size > 1) drawPath(path, primary, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 5f))
                points.forEach { point -> drawCircle(primary, radius = 7f, center = point) }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(minTime.formatDate(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(maxTime.formatDate(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun RatingHistoryRow(
    entry: RatingHistoryEntry,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(entry.rating.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    AssistChip(onClick = {}, label = { Text(entry.source.displayName) })
                }
                Text(entry.recordedAtEpochMillis.formatDateTime(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                entry.note?.let { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            }
            if (entry.isManual) {
                IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = "编辑手动记录") }
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "删除手动记录") }
            }
        }
    }
}

@Composable
private fun ManualRatingDialog(
    entry: RatingHistoryEntry?,
    onDismiss: () -> Unit,
    onSave: (Long, Int, String?) -> Unit,
) {
    val initial = remember(entry?.id) {
        entry?.recordedAtEpochMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDateTime() }
            ?: LocalDateTime.now()
    }
    var dateText by rememberSaveable(entry?.id) { mutableStateOf(initial.toLocalDate().format(DATE_FORMAT)) }
    var timeText by rememberSaveable(entry?.id) { mutableStateOf(initial.toLocalTime().format(TIME_FORMAT)) }
    var ratingText by rememberSaveable(entry?.id) { mutableStateOf(entry?.rating?.toString().orEmpty()) }
    var noteText by rememberSaveable(entry?.id) { mutableStateOf(entry?.note.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (entry == null) "手动补录 Rating" else "编辑手动记录") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("手动记录会明确标记；请只填写真实发生过的时间与 Rating。")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = dateText,
                        onValueChange = { dateText = it },
                        label = { Text("日期 yyyy-MM-dd") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = timeText,
                        onValueChange = { timeText = it },
                        label = { Text("时间 HH:mm") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                IntegerField(ratingText, { ratingText = it }, "Rating", Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { if (it.length <= 200) noteText = it },
                    label = { Text("备注（可选，最多 200 字）") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3,
                )
                error?.let { ErrorText(it) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                error = runCatching {
                    val recordedAt = LocalDateTime.of(
                        LocalDate.parse(dateText, DATE_FORMAT),
                        LocalTime.parse(timeText, TIME_FORMAT),
                    ).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    val rating = ratingText.toIntOrNull() ?: kotlin.error("请输入有效 Rating")
                    require(rating in 0..30_000) { "Rating 必须在 0 到 30000 之间" }
                    onSave(recordedAt, rating, noteText.trim().takeIf(String::isNotEmpty))
                }.exceptionOrNull()?.let { throwable ->
                    if (throwable is DateTimeParseException) "日期或时间格式无效" else throwable.message ?: "输入无效"
                }
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun ToolCard(
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}

@Composable
private fun ResultSurface(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { content() }
    }
}

@Composable
private fun SourceNote(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun ErrorText(text: String) {
    Text(text, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun DecimalField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}

@Composable
private fun IntegerField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { next -> if (next.all(Char::isDigit)) onValueChange(next) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

private fun MaimaiJudgement.displayLabel(kind: MaimaiNoteKind): String =
    if (kind != MaimaiNoteKind.BREAK && this == MaimaiJudgement.PERFECT) "Perfect" else displayName

private fun Double.format(decimals: Int): String = String.format(Locale.US, "%.${decimals}f", this)

private fun Long.formatDateTime(): String =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).format(DATE_TIME_FORMAT)

private fun Long.formatDate(): String =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).format(DATE_FORMAT)

private enum class ToolSection(val label: String) {
    RATING("Rating"),
    ACHIEVEMENT("失分 / 容错"),
    VERSIONS("版本"),
    KALEID("Kaleid×Scope"),
    TREND("趋势"),
}

private enum class TrendRange(val label: String, val durationDays: Long?) {
    MONTH("近 1 月", 30),
    QUARTER("近 3 月", 90),
    ALL("全部", null),
}

private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val DATE_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
private const val DAY_MILLIS = 24L * 60 * 60 * 1000
