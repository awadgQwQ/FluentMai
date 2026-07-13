package dev.fluentmai.android.feature.scores

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Pending
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.fluentmai.android.core.model.AchievementRank
import dev.fluentmai.android.core.model.ChartIdentity
import dev.fluentmai.android.core.model.ChartRecord
import dev.fluentmai.android.core.model.Difficulty
import dev.fluentmai.android.core.model.FullComboStatus
import dev.fluentmai.android.core.model.FullSyncStatus
import dev.fluentmai.android.core.model.MaimaiMajorVersion
import dev.fluentmai.android.core.model.PlateBlocker
import dev.fluentmai.android.core.model.PlateKind
import dev.fluentmai.android.core.model.PlateProgress
import dev.fluentmai.android.core.model.PlayedFilter
import dev.fluentmai.android.core.model.PlayerChartRecord
import dev.fluentmai.android.core.model.PlayerRecordSort
import dev.fluentmai.android.core.model.PlayerRecordStats
import dev.fluentmai.android.core.model.ScoreRecord
import dev.fluentmai.android.core.model.SongAliasCatalog
import dev.fluentmai.android.core.model.SongType
import dev.fluentmai.android.core.model.VersionAgeFilter
import dev.fluentmai.android.core.model.resolveCurrentMaimaiVersion
import java.util.Locale

@Composable
fun PlayerRecordsScreen(
    scores: List<ScoreRecord>,
    charts: List<ChartRecord>,
    majorVersions: List<MaimaiMajorVersion>,
    aliases: SongAliasCatalog = SongAliasCatalog.Empty,
    onChartSelected: (ChartIdentity) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val recordsViewModel: PlayerRecordsViewModel = viewModel()
    val state by recordsViewModel.uiState.collectAsState()
    val operatingVersionId = remember(majorVersions, charts) {
        resolveCurrentMaimaiVersion(majorVersions, charts)?.majorVersion?.id
    }
    SideEffect {
        recordsViewModel.submitCatalog(charts, scores, majorVersions, operatingVersionId, aliases)
    }

    when (state.section) {
        PlayerRecordsSection.RECORDS -> RecordsContent(
            state = state,
            viewModel = recordsViewModel,
            onChartSelected = onChartSelected,
            modifier = modifier,
        )
        PlayerRecordsSection.PLATES -> PlateContent(
            state = state,
            viewModel = recordsViewModel,
            onChartSelected = onChartSelected,
            modifier = modifier,
        )
    }
}

@Composable
private fun RecordsContent(
    state: PlayerRecordsUiState,
    viewModel: PlayerRecordsViewModel,
    onChartSelected: (ChartIdentity) -> Unit,
    modifier: Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(340.dp),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            RecordsHeader(state, viewModel)
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            state.stats?.let { StatsPanel(it, state.isWorking) }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            RecordFilters(state, viewModel)
        }
        if (state.records.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                EmptyRecords(if (state.isWorking) "正在计算玩家记录" else "没有匹配的谱面")
            }
        } else {
            items(state.records, key = { it.identity.saveableKey() }) { record ->
                PlayerRecordCard(record, onClick = { onChartSelected(record.identity) })
            }
        }
    }
}

@Composable
private fun RecordsHeader(state: PlayerRecordsUiState, viewModel: PlayerRecordsViewModel) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("玩家记录", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "统计来自当前有效曲库与本地最佳成绩，不完整映射会单独标记。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SectionSwitch(state.section, viewModel::updateSection)
        }
    }
}

@Composable
private fun SectionSwitch(
    section: PlayerRecordsSection,
    onChange: (PlayerRecordsSection) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = section == PlayerRecordsSection.RECORDS,
            onClick = { onChange(PlayerRecordsSection.RECORDS) },
            label = { Text("成绩与统计") },
        )
        FilterChip(
            selected = section == PlayerRecordsSection.PLATES,
            onClick = { onChange(PlayerRecordsSection.PLATES) },
            label = { Text("牌子进度") },
        )
    }
}

@Composable
private fun StatsPanel(stats: PlayerRecordStats, isWorking: Boolean) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("当前条件统计", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (isWorking) Text("计算中", color = MaterialTheme.colorScheme.primary)
            }
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatChip("谱面", stats.totalCharts)
                StatChip("已游玩", stats.playedCharts)
                StatChip("未游玩", stats.unplayedCharts)
                AchievementRank.entries.forEach {
                    StatChip(it.displayName, stats.rankCounts[it] ?: 0)
                }
                FullComboStatus.entries.filter { it != FullComboStatus.UNKNOWN }.forEach {
                    StatChip(it.displayName, stats.fullComboCounts[it] ?: 0)
                }
                FullSyncStatus.entries.filter { it != FullSyncStatus.UNKNOWN }.forEach {
                    StatChip(it.displayName, stats.fullSyncCounts[it] ?: 0)
                }
                if (stats.unmatchedScores > 0) StatChip("未映射成绩", stats.unmatchedScores)
            }
        }
    }
}

@Composable
private fun StatChip(label: String, value: Int) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(value.toString(), fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun RecordFilters(state: PlayerRecordsUiState, viewModel: PlayerRecordsViewModel) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("筛选", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                TextButton(onClick = viewModel::resetFilters) {
                    Icon(Icons.Filled.Clear, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("重置")
                }
            }
            OutlinedTextField(
                value = state.filters.query,
                onValueChange = viewModel::updateQuery,
                label = { Text("曲名、曲师、谱师、类别或歌曲 ID") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.constantMinText,
                    onValueChange = viewModel::updateConstantMin,
                    label = { Text("最低定数") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = state.constantMaxText,
                    onValueChange = viewModel::updateConstantMax,
                    label = { Text("最高定数") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = state.filters.displayLevel,
                    onValueChange = viewModel::updateDisplayLevel,
                    label = { Text("等级") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Text("游玩状态", style = MaterialTheme.typography.labelLarge)
            ChoiceRow {
                PlayedFilter.entries.forEach { value ->
                    FilterChip(
                        selected = state.filters.played == value,
                        onClick = { viewModel.updatePlayed(value) },
                        label = { Text(value.displayName()) },
                    )
                }
                VersionAgeFilter.entries.forEach { value ->
                    FilterChip(
                        selected = state.filters.versionAge == value,
                        onClick = { viewModel.updateVersionAge(value) },
                        label = { Text(value.displayName()) },
                    )
                }
            }
            Text("难度与类型", style = MaterialTheme.typography.labelLarge)
            ChoiceRow {
                FilterChip(
                    selected = state.filters.difficulty == null,
                    onClick = { viewModel.updateDifficulty(null) },
                    label = { Text("全部难度") },
                )
                Difficulty.entries.forEach { difficulty ->
                    FilterChip(
                        selected = state.filters.difficulty == difficulty,
                        onClick = { viewModel.updateDifficulty(difficulty) },
                        label = { Text(difficulty.shortName()) },
                    )
                }
                listOf<SongType?>(null, SongType.STANDARD, SongType.DX).forEach { type ->
                    FilterChip(
                        selected = state.filters.songType == type,
                        onClick = { viewModel.updateSongType(type) },
                        label = { Text(type?.divingFishName ?: "SD/DX") },
                    )
                }
            }
            Text("版本", style = MaterialTheme.typography.labelLarge)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = state.filters.versionId == null,
                        onClick = { viewModel.updateRecordVersion(null) },
                        label = { Text("全部版本") },
                    )
                }
                items(state.availableVersions, key = { it.id }) { version ->
                    FilterChip(
                        selected = state.filters.versionId == version.id,
                        onClick = { viewModel.updateRecordVersion(version.id) },
                        label = { Text(version.name) },
                    )
                }
            }
            ChoiceRow {
                FilterMenu(
                    label = state.filters.genre ?: "全部类别",
                    values = listOf(null to "全部类别") + state.availableGenres.map { it to it },
                    onSelected = viewModel::updateGenre,
                )
                FilterMenu(
                    label = state.filters.rank?.displayName ?: "全部成绩等级",
                    values = listOf(null to "全部成绩等级") + AchievementRank.entries.map { it to it.displayName },
                    onSelected = viewModel::updateRank,
                )
                FilterMenu(
                    label = state.filters.fullCombo?.displayName ?: "全部 FC 状态",
                    values = listOf(null to "全部 FC 状态") + FullComboStatus.entries.map { it to it.displayName },
                    onSelected = viewModel::updateFullCombo,
                )
                FilterMenu(
                    label = state.filters.fullSync?.displayName ?: "全部 FS 状态",
                    values = listOf(null to "全部 FS 状态") + FullSyncStatus.entries.map { it to it.displayName },
                    onSelected = viewModel::updateFullSync,
                )
                FilterMenu(
                    label = state.filters.plateBlockerFor?.let { "${it.displayName}牌阻塞" } ?: "全部牌子条件",
                    values = listOf(null to "全部牌子条件") + PlateKind.entries.map { it to "${it.displayName}牌阻塞" },
                    onSelected = viewModel::updatePlateBlocker,
                )
                FilterMenu(
                    label = state.filters.sort.displayName(),
                    values = PlayerRecordSort.entries.map { it to it.displayName() },
                    onSelected = viewModel::updateSort,
                )
            }
        }
    }
}

@Composable
private fun ChoiceRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
private fun <T> FilterMenu(
    label: String,
    values: List<Pair<T, String>>,
    onSelected: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(Icons.Filled.ExpandMore, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            values.forEach { (value, text) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = {
                        expanded = false
                        onSelected(value)
                    },
                )
            }
        }
    }
}

@Composable
private fun PlayerRecordCard(record: PlayerChartRecord, onClick: () -> Unit) {
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    record.chart.title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = record.chart.difficulty.color().copy(alpha = 0.16f),
                ) {
                    Text(
                        record.chart.difficulty.shortName(),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = record.chart.difficulty.color(),
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Text(
                "Song ID ${record.identity.songId} · ${record.identity.songType.divingFishName} · " +
                    "${record.chart.level} / ${record.chart.levelValue?.formatConstant() ?: "--"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                record.chart.chartVersionName ?: "版本 ${record.chart.chartVersion}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            HorizontalDivider()
            val score = record.score
            if (score == null) {
                Text("未游玩", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text("%.4f%%".format(Locale.US, score.achievement), fontWeight = FontWeight.Bold)
                        Text(record.rank?.displayName.orEmpty(), style = MaterialTheme.typography.labelMedium)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Rating ${record.rating ?: "--"}", fontWeight = FontWeight.Bold)
                        Text(
                            listOfNotNull(record.fullComboStatus?.displayName, record.fullSyncStatus?.displayName)
                                .joinToString(" · ")
                                .ifBlank { "无 FC / FS 状态" },
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlateContent(
    state: PlayerRecordsUiState,
    viewModel: PlayerRecordsViewModel,
    onChartSelected: (ChartIdentity) -> Unit,
    modifier: Modifier,
) {
    val progress = state.plateProgress
    val blockers = remember(progress) { progress?.blockers?.associateBy { it.record.identity }.orEmpty() }
    val displayed = remember(
        progress,
        blockers,
        state.selectedPlateDifficulty,
        state.plateIncompleteOnly,
        state.plateSort,
    ) {
        progress?.eligibleRecords.orEmpty()
            .asSequence()
            .filter { state.selectedPlateDifficulty == null || it.chart.difficulty == state.selectedPlateDifficulty }
            .filter { !state.plateIncompleteOnly || it.identity in blockers }
            .sortedWith(state.plateSort.comparator())
            .toList()
    }
    val grouped = remember(displayed, state.plateSort) {
        if (state.plateSort == PlateListSort.LEVEL_DESC) displayed.groupBy { it.chart.level }
        else linkedMapOf("谱面" to displayed)
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { PlateHeader(state, viewModel) }
        item { PlateControls(state, viewModel) }
        item { progress?.let { PlateSummary(it) } ?: EmptyRecords("正在计算牌子进度") }
        if (displayed.isEmpty() && progress != null) {
            item {
                EmptyRecords(if (progress.isComplete) "当前条件已全部完成" else "当前查看条件没有谱面")
            }
        }
        grouped.forEach { (group, records) ->
            if (records.isNotEmpty()) {
                item(key = "group-$group") {
                    Text(group, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                items(records, key = { it.identity.saveableKey() }) { record ->
                    PlateRecordCard(
                        record = record,
                        blocker = blockers[record.identity],
                        onClick = { onChartSelected(record.identity) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PlateHeader(state: PlayerRecordsUiState, viewModel: PlayerRecordsViewModel) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("牌子进度", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "规则来自 SEGA 官方公告；缺少可核验数据时不会宣布完成。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SectionSwitch(state.section, viewModel::updateSection)
        }
    }
}

@Composable
private fun PlateControls(state: PlayerRecordsUiState, viewModel: PlayerRecordsViewModel) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("牌子类型", style = MaterialTheme.typography.labelLarge)
            ChoiceRow {
                PlateKind.entries.forEach { kind ->
                    FilterChip(
                        selected = state.selectedPlate == kind,
                        onClick = { viewModel.updatePlateKind(kind) },
                        label = { Text(kind.displayName) },
                    )
                }
            }
            if (state.selectedPlate != PlateKind.CONQUEROR) {
                Text("版本分类", style = MaterialTheme.typography.labelLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.availableVersions, key = { it.id }) { version ->
                        FilterChip(
                            selected = state.selectedPlateVersionId == version.id,
                            onClick = { viewModel.updatePlateVersion(version.id) },
                            label = { Text(version.name) },
                        )
                    }
                }
            }
            Text("查看条件", style = MaterialTheme.typography.labelLarge)
            ChoiceRow {
                FilterChip(
                    selected = state.plateIncompleteOnly,
                    onClick = { viewModel.updatePlateIncompleteOnly(!state.plateIncompleteOnly) },
                    label = { Text(if (state.plateIncompleteOnly) "只看未完成" else "显示全部") },
                )
                FilterChip(
                    selected = state.selectedPlateDifficulty == null,
                    onClick = { viewModel.updatePlateDifficulty(null) },
                    label = { Text("全部难度") },
                )
                Difficulty.entries.forEach { difficulty ->
                    FilterChip(
                        selected = state.selectedPlateDifficulty == difficulty,
                        onClick = { viewModel.updatePlateDifficulty(difficulty) },
                        label = { Text(difficulty.shortName()) },
                    )
                }
                FilterMenu(
                    label = state.plateSort.displayName(),
                    values = PlateListSort.entries.map { it to it.displayName() },
                    onSelected = viewModel::updatePlateSort,
                )
            }
        }
    }
}

@Composable
private fun PlateSummary(progress: PlateProgress) {
    val accent = if (progress.isComplete) Color(0xFF2B8A3E) else MaterialTheme.colorScheme.primary
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = when {
                        !progress.dataSufficient -> Icons.Filled.Warning
                        progress.isComplete -> Icons.Filled.CheckCircle
                        else -> Icons.Filled.Pending
                    },
                    contentDescription = null,
                    tint = if (progress.dataSufficient) accent else MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (progress.kind == PlateKind.CONQUEROR) "霸者 · 全标准谱面" else "${progress.versionName}${progress.kind.displayName}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (!progress.dataSufficient) {
                Text("数据不足：${progress.dataMessage}", color = MaterialTheme.colorScheme.error)
            } else {
                LinearProgressIndicator(
                    progress = { progress.completionFraction.toFloat() },
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    color = accent,
                    trackColor = ProgressIndicatorDefaults.linearTrackColor,
                )
                Text(
                    "${progress.completedCount} / ${progress.requiredCount} · " +
                        "剩余 ${progress.remainingCount} · " +
                        "%.1f%%".format(Locale.US, progress.completionFraction * 100),
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    if (progress.isComplete) "已满足当前曲库可核验的全部条件" else "下方列出阻塞完成的具体谱面与差距",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PlateRecordCard(
    record: PlayerChartRecord,
    blocker: PlateBlocker?,
    onClick: () -> Unit,
) {
    ElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(12.dp),
                color = record.chart.difficulty.color().copy(alpha = 0.16f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(record.chart.level, fontWeight = FontWeight.Bold, color = record.chart.difficulty.color())
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(record.chart.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${record.chart.difficulty.shortName()} · ${record.chart.songType.divingFishName} · ID ${record.chart.songId}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    blocker?.let { "${it.currentValue} · ${it.requirementGap}" } ?: "已完成",
                    color = if (blocker == null) Color(0xFF2B8A3E) else MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun EmptyRecords(text: String) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text,
            modifier = Modifier.padding(24.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun PlayedFilter.displayName(): String = when (this) {
    PlayedFilter.ALL -> "全部"
    PlayedFilter.PLAYED -> "已游玩"
    PlayedFilter.UNPLAYED -> "未游玩"
}

private fun VersionAgeFilter.displayName(): String = when (this) {
    VersionAgeFilter.ALL -> "新旧版本"
    VersionAgeFilter.CURRENT -> "当前版本"
    VersionAgeFilter.OLD -> "旧版本"
}

private fun PlayerRecordSort.displayName(): String = when (this) {
    PlayerRecordSort.RATING_DESC -> "Rating 贡献"
    PlayerRecordSort.ACHIEVEMENT_DESC -> "达成率降序"
    PlayerRecordSort.CONSTANT_DESC -> "定数降序"
    PlayerRecordSort.CONSTANT_ASC -> "定数升序"
    PlayerRecordSort.LEVEL_DESC -> "等级降序"
    PlayerRecordSort.TITLE_ASC -> "曲名"
    PlayerRecordSort.VERSION_DESC -> "版本新到旧"
    PlayerRecordSort.SONG_ID_ASC -> "歌曲 ID"
}

private fun PlateListSort.displayName(): String = when (this) {
    PlateListSort.LEVEL_DESC -> "按等级分组"
    PlateListSort.TITLE_ASC -> "按曲名"
    PlateListSort.SONG_ID_ASC -> "按歌曲 ID"
}

private fun PlateListSort.comparator(): Comparator<PlayerChartRecord> = when (this) {
    PlateListSort.LEVEL_DESC -> compareByDescending<PlayerChartRecord> { it.chart.levelValue ?: -1.0 }
        .thenBy { it.chart.title }
    PlateListSort.TITLE_ASC -> compareBy<PlayerChartRecord> { it.chart.title }.thenByDescending { it.chart.levelValue ?: -1.0 }
    PlateListSort.SONG_ID_ASC -> compareBy<PlayerChartRecord> { it.chart.songId }.thenBy { it.chart.levelIndex }
}

private fun Difficulty.shortName(): String = when (this) {
    Difficulty.BASIC -> "BASIC"
    Difficulty.ADVANCED -> "ADV"
    Difficulty.EXPERT -> "EXP"
    Difficulty.MASTER -> "MAS"
    Difficulty.RE_MASTER -> "Re:MAS"
}

private fun Difficulty.color(): Color = when (this) {
    Difficulty.BASIC -> Color(0xFF2F9E44)
    Difficulty.ADVANCED -> Color(0xFFF08C00)
    Difficulty.EXPERT -> Color(0xFFE03131)
    Difficulty.MASTER -> Color(0xFF7048E8)
    Difficulty.RE_MASTER -> Color(0xFF8E44AD)
}

private fun Double.formatConstant(): String = "%.1f".format(Locale.US, this)

private fun ChartIdentity.saveableKey(): String = "$songId-${songType.name}-${difficulty.name}"
