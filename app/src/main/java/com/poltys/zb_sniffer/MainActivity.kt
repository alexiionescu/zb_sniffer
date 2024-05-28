package com.poltys.zb_sniffer

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.poltys.protos.zb_stats.StatsReportGrpcKt
import com.poltys.protos.zb_stats.statsReply
import com.poltys.protos.zb_stats.statsRequest
import com.poltys.zb_sniffer.ui.theme.Zb_snifferTheme
import io.grpc.ManagedChannelBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.launch
import java.io.Closeable
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter


class MainActivity : ComponentActivity() {
    private val uri by lazy { Uri.parse(resources.getString(R.string.server_url)) }
    private val reporterService by lazy { StatsRCP(uri) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Zb_snifferTheme {
                Surface() {
                    Reporter(reporterService)
                }
            }
        }
    }
}

class StatsRCP(uri: Uri) : Closeable {
    val responseState = mutableStateOf("")
    val responseValues = mutableStateOf(statsReply {  })

    private val channel = let {
        val builder = ManagedChannelBuilder.forAddress(uri.host, uri.port)
        if (uri.scheme == "https") {
            builder.useTransportSecurity()
        } else {
            builder.usePlaintext()
        }

        builder.executor(Dispatchers.IO.asExecutor()).build()
    }

    private val reporter = StatsReportGrpcKt.StatsReportCoroutineStub(channel)

    suspend fun sendRequest(lqi: Float = 80F, lastMinutes: Int = 10) {
        try {
            val request = statsRequest {
                this.name = "Stats from Android App"
                this.lqi = lqi
                this.timestamp = (Instant.now().epochSecond - lastMinutes * 60) * 1000000
            }
            val response = reporter.getStats(request)
            responseState.value = "Received: ${response.statsCount} items."
            responseValues.value = response
        } catch (e: Exception) {
            responseState.value = e.message ?: "Unknown Error"
            e.printStackTrace()
        }
    }

    override fun close() {
        channel.shutdownNow()
    }
}

var timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    .withZone(ZoneId.systemDefault())

fun Float.format(digits: Int) = "%.${digits}f".format(this)
@Composable
fun Reporter(statsRCP: StatsRCP) {
    val scope = rememberCoroutineScope()
    var lqiText by rememberSaveable { mutableStateOf("20") }
    var lastMinutes by rememberSaveable { mutableStateOf("10") }

    Column(
        Modifier
            .fillMaxWidth()
            .fillMaxHeight(), Arrangement.Top, Alignment.CenterHorizontally) {
        val focusManager = LocalFocusManager.current
        if (statsRCP.responseState.value.isNotEmpty()) {
            if (statsRCP.responseValues.value.statsCount == 0){
                Text(stringResource(R.string.server_response), modifier = Modifier.padding(top = 20.dp))
                Text(statsRCP.responseState.value)
            }
            val gridItems = statsRCP.responseValues.value.statsMap.toList()

            LazyVerticalGrid(
                columns = GridCells.Adaptive(128.dp),

                // content padding
                contentPadding = PaddingValues(
                    start = 12.dp,
                    top = 16.dp,
                    end = 12.dp,
                    bottom = 16.dp
                ),
                modifier = Modifier.padding(top = 20.dp),
                content = {
                    items(gridItems.size) { index ->
                        val key = gridItems[index].first
                        val value = gridItems[index].second
                        val seqLostDupes = value.seqLost + value.seqDuplicates
                        Card {
                            Text(
                                text = key,
                                fontWeight = FontWeight.Bold,
                                fontSize = 24.sp,
                                color = Color.Unspecified,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(4.dp)
                            )
                            Text(
                                text = "LQI: ${value.lqi.format(0)}",
                                fontWeight = FontWeight.Normal,
                                fontSize = 16.sp,
                                color = if (value.lqi < 60) Color.Red else Color.Unspecified,
                                textAlign = TextAlign.Left,
                                modifier = Modifier.padding(2.dp)
                            )
                            Text(
                                text = "LOST: $seqLostDupes / ${value.seqCnt}",
                                fontWeight = FontWeight.Normal,
                                fontSize = 16.sp,
                                color = if (seqLostDupes > 5) Color.Red else if (seqLostDupes > 2) Color.Yellow else Color.Unspecified,
                                textAlign = TextAlign.Left,
                                modifier = Modifier.padding(2.dp)
                            )
                            Text(
                                text = timeFormatter.format(Instant.ofEpochSecond(value.timestamp / 1000000)),
                                fontWeight = FontWeight.Normal,
                                fontSize = 16.sp,
                                color = Color.Unspecified,
                                textAlign = TextAlign.Left,
                                modifier = Modifier.padding(2.dp)
                            )
                        }
                    }
                }
            )
        } else {
            Spacer(modifier = Modifier.height(32.dp))
        }

        Button(
            { scope.launch {
                statsRCP.sendRequest(
                    lqiText.toFloat(),
                    lastMinutes.toInt())
                }
            },
            Modifier.padding(10.dp)
        ) {
            Text(stringResource(R.string.send_request))
        }
        Row {
            TextField(
                value = lqiText,
                onValueChange = {
                    lqiText = it
                },
                label = { Text(stringResource(R.string.min_lqi)) },
                modifier = Modifier.widthIn(min = 40.dp, max = 80.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                keyboardActions = KeyboardActions(
                    onDone = {focusManager.clearFocus()}
                )
            )
            TextField(
                value = lastMinutes,
                onValueChange = {
                    lastMinutes = it
                },
                label = { Text(stringResource(R.string.last_minutes)) },
                modifier = Modifier.width(150.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                keyboardActions = KeyboardActions(
                    onDone = {focusManager.clearFocus()}
                )
            )
        }

    }
}