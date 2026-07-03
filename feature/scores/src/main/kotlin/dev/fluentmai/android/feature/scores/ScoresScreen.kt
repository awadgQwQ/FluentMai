package dev.fluentmai.android.feature.scores

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import coil.request.CachePolicy
import coil.request.ImageRequest
import dev.fluentmai.android.core.model.ChartRecord
import dev.fluentmai.android.core.model.Difficulty
import dev.fluentmai.android.core.model.ScoreRecord
import dev.fluentmai.android.core.model.SongType
import java.text.Normalizer
import java.util.Locale
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor

@Composable
fun ScoresScreen(
    scores: List<ScoreRecord>,
    charts: List<ChartRecord>,
    modifier: Modifier = Modifier,
) {
    val enrichedScores = remember(scores, charts) { enrichScores(scores, charts) }
    val bestSet = remember(enrichedScores, charts) { buildBestSet(enrichedScores, charts) }
    var mode by remember { mutableStateOf(ScoreMode.Best50) }
    var searchQuery by remember { mutableStateOf("") }

    val visibleScores = remember(enrichedScores, mode, searchQuery, bestSet) {
        val source = when (mode) {
            ScoreMode.Best50 -> bestSet.all
            ScoreMode.All -> enrichedScores.sortedWith(scoreSort)
        }
        source.filter { it.matches(searchQuery) }
    }

    LazyVerticalGrid(
        modifier = modifier.fillMaxSize(),
        columns = GridCells.Adaptive(320.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            ScoreSummaryHeader(
                scoreCount = scores.size,
                chartCount = charts.size,
                bestRating = bestSet.rating,
            )
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            ScoreControls(
                mode = mode,
                onModeChanged = { mode = it },
                searchQuery = searchQuery,
                onSearchQueryChanged = { searchQuery = it },
            )
        }

        if (scores.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                EmptyState(text = "还没有导入成绩")
            }
        } else if (visibleScores.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                EmptyState(text = "没有匹配的成绩")
            }
        } else if (mode == ScoreMode.Best50 && searchQuery.isBlank()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionTitle("旧版本 Best 35", bestSet.oldBest.size)
            }
            items(bestSet.oldBest, key = { "old-${it.score.id}" }) { item ->
                ScoreCard(item = item, rank = bestSet.oldBest.indexOf(item) + 1)
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionTitle("当前版本 Best 15", bestSet.newBest.size)
            }
            items(bestSet.newBest, key = { "new-${it.score.id}" }) { item ->
                ScoreCard(item = item, rank = bestSet.newBest.indexOf(item) + 1)
            }
        } else {
            items(visibleScores, key = { it.score.id }) { item ->
                ScoreCard(item = item, rank = visibleScores.indexOf(item) + 1)
            }
        }
    }
}

@Composable
fun ChartQueryScreen(
    charts: List<ChartRecord>,
    scores: List<ScoreRecord>,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scoreIndex = remember(scores) { ScoreIndex.from(scores) }
    var searchQuery by remember { mutableStateOf("") }
    var levelQuery by remember { mutableStateOf("") }
    var selectedDifficulty by remember { mutableStateOf<Difficulty?>(null) }
    var genreFilter by remember { mutableStateOf(ChartGenreFilter.All) }
    var versionFilter by remember { mutableStateOf(ChartVersionFilter.All) }
    var statusFilter by remember { mutableStateOf(ChartStatusFilter.All) }
    var sortMode by remember { mutableStateOf(ChartSort.ConstantDesc) }
    val latestVersion = remember(charts) { charts.maxOfOrNull { it.songVersion } ?: 0 }

    val visibleCharts = remember(
        charts,
        scores,
        searchQuery,
        levelQuery,
        selectedDifficulty,
        genreFilter,
        versionFilter,
        statusFilter,
        sortMode,
        latestVersion,
    ) {
        charts
            .asSequence()
            .filter { selectedDifficulty == null || it.difficulty == selectedDifficulty }
            .filter { genreFilter.matches(it) }
            .filter { versionFilter.matches(it, latestVersion) }
            .filter { it.matchesLevel(levelQuery) }
            .filter { it.matchesQuery(searchQuery) }
            .filter { chart ->
                val score = scoreIndex.scoreFor(chart)
                statusFilter.matches(score)
            }
            .sortedWith(sortMode.comparator(scoreIndex))
            .take(500)
            .toList()
    }

    LazyVerticalGrid(
        modifier = modifier.fillMaxSize(),
        columns = GridCells.Adaptive(340.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            ChartHeader(
                chartCount = charts.size,
                visibleCount = visibleCharts.size,
                isLoading = isLoading,
                onRefresh = onRefresh,
            )
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            ChartFilters(
                searchQuery = searchQuery,
                onSearchQueryChanged = { searchQuery = it },
                levelQuery = levelQuery,
                onLevelQueryChanged = { levelQuery = it },
                selectedDifficulty = selectedDifficulty,
                onDifficultyChanged = { selectedDifficulty = it },
                genreFilter = genreFilter,
                onGenreFilterChanged = { genreFilter = it },
                versionFilter = versionFilter,
                onVersionFilterChanged = { versionFilter = it },
                statusFilter = statusFilter,
                onStatusFilterChanged = { statusFilter = it },
                sortMode = sortMode,
                onSortModeChanged = { sortMode = it },
            )
        }

        if (charts.isEmpty() && !isLoading) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                EmptyState(text = "还没有曲库数据")
            }
        } else if (visibleCharts.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                EmptyState(text = if (isLoading) "正在同步曲库" else "没有匹配的谱面")
            }
        } else {
            items(visibleCharts, key = { "${it.songId}-${it.songType}-${it.levelIndex}" }) { chart ->
                ChartCard(chart = chart, score = scoreIndex.scoreFor(chart))
            }
        }
    }
}

@Composable
private fun ScoreSummaryHeader(
    scoreCount: Int,
    chartCount: Int,
    bestRating: Int,
) {
    OutlinedCard(
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(text = "成绩", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                RatingPlate(rating = bestRating)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricChip(label = "本地成绩", value = scoreCount.toString())
                MetricChip(label = "曲库谱面", value = if (chartCount == 0) "未同步" else chartCount.toString())
            }
        }
    }
}

@Composable
private fun ChartHeader(
    chartCount: Int,
    visibleCount: Int,
    isLoading: Boolean,
    onRefresh: () -> Unit,
) {
    OutlinedCard(
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "谱面查询", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricChip(label = "曲库", value = if (isLoading) "同步中" else chartCount.toString())
                    MetricChip(label = "结果", value = visibleCount.toString())
                }
            }
            IconButton(onClick = onRefresh, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Filled.Refresh, contentDescription = "刷新曲库")
            }
        }
    }
}

@Composable
private fun ScoreControls(
    mode: ScoreMode,
    onModeChanged: (ScoreMode) -> Unit,
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(ScoreMode.entries.size) { index ->
                val item = ScoreMode.entries[index]
                FilterChip(
                    selected = mode == item,
                    onClick = { onModeChanged(item) },
                    label = { Text(item.label) },
                )
            }
        }
        SearchField(
            value = searchQuery,
            onValueChanged = onSearchQueryChanged,
            label = "搜索曲名 / 难度 / 等级",
        )
    }
}

@Composable
private fun ChartFilters(
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit,
    levelQuery: String,
    onLevelQueryChanged: (String) -> Unit,
    selectedDifficulty: Difficulty?,
    onDifficultyChanged: (Difficulty?) -> Unit,
    genreFilter: ChartGenreFilter,
    onGenreFilterChanged: (ChartGenreFilter) -> Unit,
    versionFilter: ChartVersionFilter,
    onVersionFilterChanged: (ChartVersionFilter) -> Unit,
    statusFilter: ChartStatusFilter,
    onStatusFilterChanged: (ChartStatusFilter) -> Unit,
    sortMode: ChartSort,
    onSortModeChanged: (ChartSort) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SearchField(
            value = searchQuery,
            onValueChanged = onSearchQueryChanged,
            label = "曲名 / 谱师 / 艺术家 / 版本",
        )
        OutlinedTextField(
            value = levelQuery,
            onValueChange = onLevelQueryChanged,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("等级或内部定数：13、13+、13.3") },
            trailingIcon = {
                if (levelQuery.isNotBlank()) {
                    IconButton(onClick = { onLevelQueryChanged("") }) {
                        Icon(Icons.Filled.Clear, contentDescription = "清除定数")
                    }
                }
            },
        )
        ChipRow {
            FilterChip(
                selected = selectedDifficulty == null,
                onClick = { onDifficultyChanged(null) },
                label = { Text("全部难度") },
            )
            Difficulty.entries.forEach { difficulty ->
                FilterChip(
                    selected = selectedDifficulty == difficulty,
                    onClick = { onDifficultyChanged(difficulty) },
                    label = { Text(difficulty.displayName()) },
                )
            }
        }
        ChipRow {
            ChartGenreFilter.entries.forEach { item ->
                FilterChip(
                    selected = genreFilter == item,
                    onClick = { onGenreFilterChanged(item) },
                    label = { Text(item.label) },
                )
            }
        }
        ChipRow {
            ChartVersionFilter.entries.forEach { item ->
                FilterChip(
                    selected = versionFilter == item,
                    onClick = { onVersionFilterChanged(item) },
                    label = { Text(item.label) },
                )
            }
        }
        ChipRow {
            ChartStatusFilter.entries.forEach { item ->
                FilterChip(
                    selected = statusFilter == item,
                    onClick = { onStatusFilterChanged(item) },
                    label = { Text(item.label) },
                )
            }
        }
        ChipRow {
            FilterChip(
                selected = sortMode == ChartSort.ConstantDesc || sortMode == ChartSort.ConstantAsc,
                onClick = {
                    onSortModeChanged(if (sortMode == ChartSort.ConstantDesc) ChartSort.ConstantAsc else ChartSort.ConstantDesc)
                },
                label = { Text(if (sortMode == ChartSort.ConstantAsc) ChartSort.ConstantAsc.label else ChartSort.ConstantDesc.label) },
            )
            FilterChip(
                selected = sortMode == ChartSort.VersionDesc || sortMode == ChartSort.VersionAsc,
                onClick = {
                    onSortModeChanged(if (sortMode == ChartSort.VersionDesc) ChartSort.VersionAsc else ChartSort.VersionDesc)
                },
                label = { Text(if (sortMode == ChartSort.VersionAsc) ChartSort.VersionAsc.label else ChartSort.VersionDesc.label) },
            )
            FilterChip(
                selected = sortMode == ChartSort.AchievementAsc || sortMode == ChartSort.AchievementDesc,
                onClick = {
                    onSortModeChanged(if (sortMode == ChartSort.AchievementAsc) ChartSort.AchievementDesc else ChartSort.AchievementAsc)
                },
                label = { Text(if (sortMode == ChartSort.AchievementDesc) ChartSort.AchievementDesc.label else ChartSort.AchievementAsc.label) },
            )
            FilterChip(
                selected = sortMode == ChartSort.TitleAsc || sortMode == ChartSort.TitleDesc,
                onClick = {
                    onSortModeChanged(if (sortMode == ChartSort.TitleAsc) ChartSort.TitleDesc else ChartSort.TitleAsc)
                },
                label = { Text(if (sortMode == ChartSort.TitleDesc) ChartSort.TitleDesc.label else ChartSort.TitleAsc.label) },
            )
        }
    }
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun SearchField(
    value: String,
    onValueChanged: (String) -> Unit,
    label: String,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChanged,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text(label) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (value.isNotBlank()) {
                IconButton(onClick = { onValueChanged("") }) {
                    Icon(Icons.Filled.Clear, contentDescription = "清除搜索")
                }
            }
        },
    )
}

@Composable
private fun SectionTitle(title: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(text = "$count 张", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ScoreCard(item: EnrichedScore, rank: Int) {
    val difficultyColor = item.score.difficulty.color()
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                JacketArt(
                    songId = item.chart?.songId ?: item.score.songId,
                    title = item.score.title,
                    modifier = Modifier.size(86.dp),
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        RankLabel(rank = rank)
                        CopyableText(
                            text = item.score.title,
                            copyLabel = "曲名",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    val scoreSubtitle = listOfNotNull(
                        item.chart?.artist?.takeIf { it.isNotBlank() },
                        item.chart?.displayVersionName()?.takeIf { it != "--" },
                    ).joinToString(" · ").ifBlank { item.score.songType.displayName() }
                    CopyableText(
                        text = scoreSubtitle,
                        copyText = item.chart?.artist?.takeIf { it.isNotBlank() },
                        copyLabel = "曲师",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    DifficultyPill(
                        text = "${item.score.difficulty.displayName()} ${item.score.level}",
                        color = difficultyColor,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = String.format(Locale.US, "%.4f%%", item.score.achievement),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = item.score.rankName(),
                        style = MaterialTheme.typography.labelMedium,
                        color = item.score.rankColor(),
                        fontWeight = FontWeight.Bold,
                    )
                }
                InfoStack(label = "定数", value = item.chart?.levelValue?.formatConst() ?: item.score.level)
                InfoStack(label = "Rating", value = item.rating?.toString() ?: "--")
                InfoStack(label = "DX", value = item.score.dxScore?.toString() ?: "--")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item.score.fc?.takeIf { it.isNotBlank() }?.let { SmallTag(it.uppercase(), Color(0xFF2F9E44)) }
                item.score.fs?.takeIf { it.isNotBlank() }?.let { SmallTag(it.uppercase(), Color(0xFF1C7ED6)) }
                SmallTag(item.score.songType.displayName(), MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ChartCard(
    chart: ChartRecord,
    score: ScoreRecord?,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (score == null) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                JacketArt(
                    songId = chart.songId,
                    title = chart.title,
                    modifier = Modifier.size(82.dp),
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    CopyableText(
                        text = chart.title,
                        copyLabel = "曲名",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    CopyableText(
                        text = chart.artist.ifBlank { chart.genre },
                        copyLabel = "曲师",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    DifficultyPill(
                        text = "${chart.difficulty.displayName()} ${chart.level}",
                        color = chart.difficulty.color(),
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                InfoStack(label = "定数", value = chart.levelValue?.formatConst() ?: chart.level)
                InfoStack(label = "BPM", value = chart.bpm?.toString() ?: "--")
                InfoStack(label = "版本", value = chart.displayVersionName())
                InfoStack(label = "物量", value = chart.notes?.total?.toString() ?: "--")
            }
            CopyableText(
                text = "谱师 ${chart.noteDesigner.ifBlank { "--" }}",
                copyText = chart.noteDesigner.takeIf { it.isNotBlank() },
                copyLabel = "谱师",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                SmallTag(chart.songType.displayName(), MaterialTheme.colorScheme.onSurfaceVariant)
                if (score == null) {
                    SmallTag("未游玩", Color(0xFFB42318))
                } else {
                    SmallTag(String.format(Locale.US, "%.4f%%", score.achievement), score.rankColor())
                    score.fc?.takeIf { it.isNotBlank() }?.let { SmallTag(it.uppercase(), Color(0xFF2F9E44)) }
                    score.fs?.takeIf { it.isNotBlank() }?.let { SmallTag(it.uppercase(), Color(0xFF1C7ED6)) }
                }
            }
        }
    }
}

@Composable
private fun JacketArt(
    songId: Int?,
    title: String,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (songId != null) {
            val url = jacketUrl(songId)
            val context = LocalContext.current
            val imageLoader = remember {
                createCoilImageLoader(context.applicationContext)
            }

            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(url)
                    .crossfade(300)
                    .build(),
                imageLoader = imageLoader,
                contentDescription = "$title 曲绘",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                onSuccess = {
                    Log.d("FluentMaiJacket", "OK  songId=$songId title=$title")
                },
                onError = { error ->
                    val throwable = error.result.throwable
                    Log.w("FluentMaiJacket", "FAIL songId=$songId title=$title url=$url error=${throwable?.javaClass?.simpleName}: ${throwable?.message}")
                },
            )
        } else {
            Text(
                text = "无曲绘",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun createCoilImageLoader(context: Context): ImageLoader {
    val loggingInterceptor = HttpLoggingInterceptor { message ->
        Log.d("FluentMaiJacket", message)
    }.apply { level = HttpLoggingInterceptor.Level.BASIC }

    val okHttpClient = OkHttpClient.Builder()
        .proxy(java.net.Proxy.NO_PROXY)
        .addNetworkInterceptor(loggingInterceptor)
        .build()

    return ImageLoader.Builder(context)
        .callFactory(okHttpClient)
        .diskCachePolicy(CachePolicy.ENABLED)
        .memoryCachePolicy(CachePolicy.ENABLED)
        .build()
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CopyableText(
    text: String,
    copyLabel: String,
    modifier: Modifier = Modifier,
    copyText: String? = text,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    val context = LocalContext.current
    Text(
        text = text,
        modifier = modifier.combinedClickable(
            onClick = {},
            onLongClick = {
                copyText?.takeIf { it.isNotBlank() }?.let { value ->
                    copyToClipboard(context, copyLabel, value)
                }
            },
        ),
        style = style,
        color = color,
        fontWeight = fontWeight,
        maxLines = maxLines,
        overflow = overflow,
    )
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, "已复制$label", Toast.LENGTH_SHORT).show()
}

@Composable
private fun DifficultyPill(text: String, color: Color) {
    Surface(
        color = color,
        contentColor = Color.White,
        shape = RoundedCornerShape(8.dp),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
private fun InfoStack(label: String, value: String) {
    Column(horizontalAlignment = Alignment.Start) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun RatingPlate(rating: Int) {
    val palette = ratingFramePalette(rating)
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(Brush.horizontalGradient(palette.frameColors))
            .padding(2.dp),
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF121318))
                .padding(3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(5.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFFFFFFFF), Color(0xFFBFEFFF), Color(0xFF2F8CFF))))
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "DX",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFFFA726),
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        text = "RATING",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF0B56A8),
                        fontWeight = FontWeight.Black,
                    )
                }
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(5.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFF2B2D33), Color(0xFF050506))))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = rating.takeIf { it > 0 }?.toString() ?: "-----",
                    style = MaterialTheme.typography.titleMedium,
                    color = palette.textColor,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun RankLabel(rank: Int) {
    Text(
        text = "#$rank",
        modifier = Modifier.padding(top = 3.dp),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
        fontWeight = FontWeight.Bold,
        maxLines = 1,
    )
}

private data class RatingFramePalette(
    val frameColors: List<Color>,
    val textColor: Color,
)

private fun ratingFramePalette(rating: Int): RatingFramePalette =
    when {
        rating >= 16000 -> RatingFramePalette(
            frameColors = listOf(Color(0xFFFFD166), Color(0xFFFF7A59), Color(0xFF1DD3B0), Color(0xFF4CC9F0), Color(0xFFFFF7B8)),
            textColor = Color(0xFFFFF3C4),
        )
        rating >= 15000 -> RatingFramePalette(
            frameColors = listOf(Color(0xFFFF4D6D), Color(0xFFFFD166), Color(0xFF06D6A0), Color(0xFF4D96FF), Color(0xFFB15CFF), Color(0xFFFFFFFF)),
            textColor = Color(0xFFFFFFFF),
        )
        rating >= 14000 -> RatingFramePalette(
            frameColors = listOf(Color(0xFFFFFFFF), Color(0xFFF6E7A8), Color(0xFFC9A64C), Color(0xFFFFFFFF)),
            textColor = Color(0xFFFFD84D),
        )
        rating >= 13000 -> RatingFramePalette(
            frameColors = listOf(Color(0xFFD7B35C), Color(0xFFFFE8A3), Color(0xFFA06A22)),
            textColor = Color(0xFFFFE8A3),
        )
        else -> RatingFramePalette(
            frameColors = listOf(Color(0xFF64748B), Color(0xFFCBD5E1), Color(0xFF64748B)),
            textColor = Color(0xFFE5E7EB),
        )
    }

@Composable
private fun MetricChip(label: String, value: String) {
    AssistChip(
        onClick = {},
        label = {
            Text("$label $value", style = MaterialTheme.typography.labelLarge)
        },
    )
}

@Composable
private fun SmallTag(text: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.12f),
        contentColor = color,
        shape = RoundedCornerShape(6.dp),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
private fun EmptyState(text: String) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(20.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private enum class ScoreMode(val label: String) {
    Best50("B50"),
    All("全部成绩"),
}

private enum class ChartStatusFilter(val label: String) {
    All("全部"),
    Played("已游玩"),
    Missing("未游玩");

    fun matches(score: ScoreRecord?): Boolean =
        when (this) {
            All -> true
            Played -> score != null
            Missing -> score == null
        }
}

private enum class ChartGenreFilter(val label: String) {
    All("全部分区"),
    Maimai("舞萌区"),
    OngekiChunithm("中二/音击区"),
    VocaloidNiconico("VOCALOID & NICONICO"),
    Touhou("东方区"),
    GameVariety("GAME & VARIETY"),
    PopsAnime("POPS & ANIME"),
    Utage("宴会场");

    fun matches(chart: ChartRecord): Boolean {
        val genre = normalizeQuery(chart.genre)
        return when (this) {
            All -> true
            Maimai -> genre == "maimai"
            OngekiChunithm -> genre.contains("オンゲキ") || genre.contains("chunithm") || genre.contains("中二") || genre.contains("音击")
            VocaloidNiconico -> genre.contains("vocaloid") || genre.contains("niconico") || genre.contains("ボーカロイド")
            Touhou -> genre.contains("東方") || genre.contains("东方")
            GameVariety -> genre.contains("ゲーム") || genre.contains("バラエティ") || genre.contains("game") || genre.contains("variety")
            PopsAnime -> genre.contains("pops") || genre.contains("アニメ") || genre.contains("anime")
            Utage -> genre.contains("宴会") || genre.contains("utage")
        }
    }
}

private enum class ChartVersionFilter(val label: String) {
    All("全部版本"),
    Current("当前版本"),
    Dx2025("DX 2025"),
    Dx2024("DX 2024"),
    Dx2023("DX 2023"),
    Finale("FiNALE"),
    Classic("旧框");

    fun matches(chart: ChartRecord, latestVersion: Int): Boolean =
        when (this) {
            All -> true
            Current -> latestVersion > 0 && chart.songVersion == latestVersion
            Dx2025 -> chart.songVersion in 25000 until 25500
            Dx2024 -> chart.songVersion in 24000 until 25000
            Dx2023 -> chart.songVersion in 23000 until 24000
            Finale -> chart.songVersion in 19900 until 20000 || chart.chartVersion in 19900 until 20000
            Classic -> chart.songVersion in 1 until 20000
        }
}

private enum class ChartSort(val label: String) {
    ConstantDesc("定数降序"),
    ConstantAsc("定数升序"),
    VersionDesc("上线新到旧"),
    VersionAsc("上线旧到新"),
    AchievementAsc("成绩升序"),
    AchievementDesc("成绩降序"),
    TitleAsc("曲名升序"),
    TitleDesc("曲名降序");

    fun comparator(scoreIndex: ScoreIndex): Comparator<ChartRecord> =
        when (this) {
            ConstantDesc -> compareByDescending<ChartRecord> { it.levelValue ?: -1.0 }
                .thenByDescending { it.levelIndex }
                .thenBy { it.title }
            ConstantAsc -> compareBy<ChartRecord> { it.levelValue ?: 999.0 }
                .thenBy { it.levelIndex }
                .thenBy { it.title }
            VersionDesc -> compareByDescending<ChartRecord> { it.chartVersion }
                .thenByDescending { it.songVersion }
                .thenByDescending { it.levelValue ?: -1.0 }
            VersionAsc -> compareBy<ChartRecord> { it.chartVersion }
                .thenBy { it.songVersion }
                .thenBy { it.levelValue ?: 999.0 }
            AchievementAsc -> compareBy<ChartRecord> { scoreIndex.scoreFor(it)?.achievement ?: 999.0 }
                .thenByDescending { it.levelValue ?: -1.0 }
            AchievementDesc -> compareByDescending<ChartRecord> { scoreIndex.scoreFor(it)?.achievement ?: -1.0 }
                .thenByDescending { it.levelValue ?: -1.0 }
            TitleAsc -> compareBy<ChartRecord> { normalizeQuery(it.title) }
                .thenByDescending { it.levelValue ?: -1.0 }
            TitleDesc -> compareByDescending<ChartRecord> { normalizeQuery(it.title) }
                .thenByDescending { it.levelValue ?: -1.0 }
        }
}

private data class EnrichedScore(
    val score: ScoreRecord,
    val chart: ChartRecord?,
    val rating: Int?,
) {
    fun matches(query: String): Boolean {
        if (query.isBlank()) return true
        val normalized = normalizeQuery(query)
        return listOf(
            score.title,
            score.difficulty.displayName(),
            score.level,
            score.songType.displayName(),
            chart?.artist.orEmpty(),
            chart?.noteDesigner.orEmpty(),
            chart?.chartVersionName.orEmpty(),
        ).any { normalizeQuery(it).contains(normalized) }
    }
}

private data class BestSet(
    val newBest: List<EnrichedScore>,
    val oldBest: List<EnrichedScore>,
) {
    val all: List<EnrichedScore> = oldBest + newBest
    val rating: Int = all.sumOf { it.rating ?: 0 }
}

private data class ScoreKey(
    val title: String,
    val songType: SongType,
    val levelIndex: Int,
) {
    companion object {
        fun fromScore(score: ScoreRecord): ScoreKey =
            ScoreKey(normalizeQuery(score.title), score.songType, score.levelIndex)

        fun fromChart(chart: ChartRecord): ScoreKey =
            ScoreKey(normalizeQuery(chart.title), chart.songType, chart.levelIndex)
    }
}

private data class ScoreSongIdKey(
    val songId: Int,
    val songType: SongType,
    val levelIndex: Int,
)

private data class ScoreTitleDifficultyKey(
    val title: String,
    val songType: SongType,
    val levelIndex: Int,
)

private data class ScoreIndex(
    private val exact: Map<ScoreKey, ScoreRecord>,
    private val bySongId: Map<ScoreSongIdKey, ScoreRecord>,
    private val byTitleDifficulty: Map<ScoreTitleDifficultyKey, ScoreRecord>,
) {
    fun scoreFor(chart: ChartRecord): ScoreRecord? =
        exact[ScoreKey.fromChart(chart)]
            ?: bySongId[ScoreSongIdKey(chart.songId, chart.songType, chart.levelIndex)]
            ?: byTitleDifficulty[ScoreTitleDifficultyKey(normalizeQuery(chart.title), chart.songType, chart.levelIndex)]

    companion object {
        fun from(scores: List<ScoreRecord>): ScoreIndex =
            ScoreIndex(
                exact = scores.bestBy { ScoreKey.fromScore(it) },
                bySongId = scores
                    .mapNotNull { score -> score.songId?.let { ScoreSongIdKey(it, score.songType, score.levelIndex) to score } }
                    .bestPairs(),
                byTitleDifficulty = scores.bestBy {
                    ScoreTitleDifficultyKey(normalizeQuery(it.title), it.songType, it.levelIndex)
                },
            )

        private fun <K> List<ScoreRecord>.bestBy(keySelector: (ScoreRecord) -> K): Map<K, ScoreRecord> =
            groupBy(keySelector).mapValues { (_, records) -> records.bestScore() }

        private fun <K> List<Pair<K, ScoreRecord>>.bestPairs(): Map<K, ScoreRecord> =
            groupBy({ it.first }, { it.second }).mapValues { (_, records) -> records.bestScore() }

        private fun List<ScoreRecord>.bestScore(): ScoreRecord =
            maxWithOrNull(
                compareBy<ScoreRecord> { it.achievement }
                    .thenBy { it.dxScore ?: -1 },
            ) ?: first()
    }
}

internal fun scoreForChartForTest(scores: List<ScoreRecord>, chart: ChartRecord): ScoreRecord? =
    ScoreIndex.from(scores).scoreFor(chart)

private fun enrichScores(scores: List<ScoreRecord>, charts: List<ChartRecord>): List<EnrichedScore> {
    val chartMap = charts.associateBy { ScoreKey.fromChart(it) }
    return scores.map { score ->
        val chart = chartMap[ScoreKey.fromScore(score)]
        EnrichedScore(
            score = score,
            chart = chart,
            rating = chart?.levelValue?.let { calculateDxRating(it, score.achievement, score.fc) },
        )
    }
}

private fun buildBestSet(scores: List<EnrichedScore>, charts: List<ChartRecord>): BestSet {
    val latestVersion = charts.maxOfOrNull { it.chartVersion } ?: 0
    val ratedScores = scores.filter { it.rating != null }
    val newBest = ratedScores
        .filter { it.chart.isNewRatingBucket(latestVersion) }
        .sortedWith(scoreSort)
        .take(15)
    val oldBest = ratedScores
        .filterNot { it.chart.isNewRatingBucket(latestVersion) }
        .sortedWith(scoreSort)
        .take(35)
    return BestSet(newBest = newBest, oldBest = oldBest)
}

internal fun ChartRecord?.isNewRatingBucket(latestChartVersion: Int): Boolean =
    latestChartVersion > 0 && this?.chartVersion == latestChartVersion

private val scoreSort: Comparator<EnrichedScore> =
    compareByDescending<EnrichedScore> { it.rating ?: -1 }
        .thenByDescending { it.score.achievement }
        .thenByDescending { it.chart?.levelValue ?: -1.0 }
        .thenBy { it.score.title }

private fun ChartRecord.matchesLevel(query: String): Boolean {
    val trimmed = query.trim()
    if (trimmed.isBlank()) return true
    val numeric = trimmed.toDoubleOrNull()
    if (numeric != null && trimmed.contains(".")) {
        return levelValue?.let { kotlin.math.abs(it - numeric) < 0.0001 } == true
    }

    val normalized = normalizeQuery(trimmed)
    val isPlusLevel = normalized.endsWith("+")
    val baseLevel = normalized.removeSuffix("+").toIntOrNull()
    if (baseLevel != null) {
        if (level.equals(trimmed, ignoreCase = true)) return true
        val value = levelValue ?: return false
        return if (isPlusLevel) {
            value >= baseLevel + 0.6 - 0.0001 && value <= baseLevel + 0.9 + 0.0001
        } else {
            value >= baseLevel.toDouble() - 0.0001 && value <= baseLevel + 0.5 + 0.0001
        }
    }

    return level.equals(trimmed, ignoreCase = true)
}

private fun ChartRecord.matchesQuery(query: String): Boolean {
    if (query.isBlank()) return true
    val normalized = normalizeQuery(query)
    val designerAliases = designerAliasesFor(normalized)
    val fields = listOf(
        title,
        artist,
        genre,
        noteDesigner,
        songVersionName.orEmpty(),
        chartVersionName.orEmpty(),
        bpm?.toString().orEmpty(),
    ).map(::normalizeQuery)
    return fields.any { it.contains(normalized) } ||
        designerAliases.any { alias -> fields.any { it.contains(alias) } }
}

private fun designerAliasesFor(query: String): Set<String> =
    when {
        query.contains("沙发太") || query.contains("沙發太") ->
            setOf("サファ太").map(::normalizeQuery).toSet()
        query.contains("哈皮") ->
            setOf("はっぴー").map(::normalizeQuery).toSet()
        query.contains("7.3") ||
            query.contains("7_3") ||
            query.contains("shichimi") ||
            query.contains("シチミ") ||
            query.contains("七味") ->
            setOf(
                "7.3ghz",
                "シチミッピー",
                "シチミヘルツ",
                "しちみへるつ",
                "超七味星人",
            ).map(::normalizeQuery).toSet()
        else -> emptySet()
    }

private fun ScoreRecord.rankName(): String =
    when {
        achievement >= 100.5 -> "SSS+"
        achievement >= 100.0 -> "SSS"
        achievement >= 99.5 -> "SS+"
        achievement >= 99.0 -> "SS"
        achievement >= 98.0 -> "S+"
        achievement >= 97.0 -> "S"
        achievement >= 94.0 -> "AAA"
        achievement >= 90.0 -> "AA"
        achievement >= 80.0 -> "A"
        achievement >= 75.0 -> "BBB"
        achievement >= 70.0 -> "BB"
        achievement >= 60.0 -> "B"
        achievement >= 50.0 -> "C"
        else -> "D"
    }

private fun ScoreRecord.rankColor(): Color =
    when {
        achievement >= 100.5 -> Color(0xFFE03131)
        achievement >= 100.0 -> Color(0xFF1971C2)
        achievement >= 97.0 -> Color(0xFF5F3DC4)
        else -> Color(0xFF475569)
    }

private fun Difficulty.displayName(): String =
    when (this) {
        Difficulty.BASIC -> "Basic"
        Difficulty.ADVANCED -> "Advanced"
        Difficulty.EXPERT -> "Expert"
        Difficulty.MASTER -> "Master"
        Difficulty.RE_MASTER -> "Re:MASTER"
    }

private fun Difficulty.color(): Color =
    when (this) {
        Difficulty.BASIC -> Color(0xFF2F9E44)
        Difficulty.ADVANCED -> Color(0xFFD9480F)
        Difficulty.EXPERT -> Color(0xFFE03131)
        Difficulty.MASTER -> Color(0xFF8E44D6)
        Difficulty.RE_MASTER -> Color(0xFFB15CFF)
    }

private fun SongType.displayName(): String =
    when (this) {
        SongType.STANDARD -> "SD"
        SongType.DX -> "DX"
    }

private fun ChartRecord.displayVersionName(): String =
    chartVersionName
        ?: songVersionName
        ?: versionNameFor(chartVersion)
        ?: versionNameFor(songVersion)
        ?: "--"

private fun versionNameFor(version: Int): String? =
    when {
        version >= 25500 -> "舞萌DX 2026"
        version >= 25000 -> "舞萌DX 2025"
        version >= 24000 -> "舞萌DX 2024"
        version >= 23000 -> "舞萌DX 2023"
        version >= 22000 -> "舞萌DX 2022"
        version >= 21000 -> "舞萌DX 2021"
        version >= 20000 -> "舞萌DX"
        version >= 19900 -> "FiNALE"
        version >= 19500 -> "MiLK PLUS"
        version >= 19000 -> "MiLK"
        version >= 18500 -> "MURASAKi PLUS"
        version >= 18000 -> "MURASAKi"
        version >= 17000 -> "PiNK PLUS"
        version >= 16000 -> "PiNK"
        version >= 15000 -> "ORANGE PLUS"
        version >= 14000 -> "ORANGE"
        version >= 13000 -> "GreeN PLUS"
        version >= 12000 -> "GreeN"
        version >= 11000 -> "maimai PLUS"
        version >= 10000 -> "maimai"
        else -> null
    }

private fun Double.formatConst(): String =
    String.format(Locale.US, "%.1f", this)

private fun jacketUrl(songId: Int): String =
    "https://assets2.lxns.net/maimai/jacket/$songId.png"

private fun normalizeQuery(value: String): String =
    Normalizer.normalize(value.trim(), Normalizer.Form.NFKC).lowercase(Locale.ROOT)
