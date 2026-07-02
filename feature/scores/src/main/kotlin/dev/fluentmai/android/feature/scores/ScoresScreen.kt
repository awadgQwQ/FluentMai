package dev.fluentmai.android.feature.scores

import android.content.Context
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import dev.fluentmai.android.core.model.ChartRecord
import dev.fluentmai.android.core.model.ScoreRecord
import java.util.Locale
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
@Composable
fun ScoresScreen(
    scores: List<ScoreRecord>,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(text = "Scores", style = MaterialTheme.typography.headlineSmall)
        }
        if (scores.isEmpty()) {
            item {
                Text(text = "No scores imported yet.")
            }
        }
        items(scores, key = { it.id }) { score ->
            ScoreRow(score = score)
        }
    }
}

@Composable
private fun ScoreRow(score: ScoreRecord) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(text = score.title, style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(text = "${score.difficulty.name} ${score.level}")
                Text(text = String.format(Locale.US, "%.4f%%", score.achievement))
            }
            Text(text = "DX score: ${score.dxScore?.toString() ?: "not available"}")
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
                contentDescription = "$title jacket artwork",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                onSuccess = {
                    Log.d("FluentMaiJacket", "OK songId=$songId title=$title")
                },
                onError = { error ->
                    val throwable = error.result.throwable
                    Log.w("FluentMaiJacket", "FAIL songId=$songId title=$title url=$url error=${throwable?.javaClass?.simpleName}: ${throwable?.message}")
                },
            )
        } else {
            Text(
                text = "No jacket",
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

private fun jacketUrl(songId: Int): String =
    "https://assets2.lxns.net/maimai/jacket/$songId.png"

internal fun ChartRecord?.isNewRatingBucket(latestChartVersion: Int): Boolean =
    latestChartVersion > 0 && this?.chartVersion == latestChartVersion
