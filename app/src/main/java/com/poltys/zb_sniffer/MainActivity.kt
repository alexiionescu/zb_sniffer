package com.poltys.zb_sniffer

import android.net.Uri
import android.os.Bundle
import android.util.Log
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.text.isDigitsOnly
import com.poltys.protos.zb_stats.StatsReportGrpcKt
import com.poltys.protos.zb_stats.statsReply
import com.poltys.protos.zb_stats.statsRequest
import com.poltys.zb_sniffer.ui.theme.TextError
import com.poltys.zb_sniffer.ui.theme.TextWarning
import com.poltys.zb_sniffer.ui.theme.Zb_snifferTheme
import io.grpc.ManagedChannelBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.io.Closeable
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds


class MainActivity : ComponentActivity() {
    private val uri by lazy { Uri.parse(resources.getString(R.string.server_url)) }
    private val reporterService by lazy { StatsRCP(uri) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Zb_snifferTheme {
                Surface {
                    Reporter(reporterService)
                }
            }
        }
    }
}

class StatsRCP(uri: Uri) : Closeable {
    val responseState = mutableStateOf("")
    var responseError = mutableStateOf(false)
    val responseValues = mutableStateOf(statsReply { })

    private var tickerJob: Job? = null
    var lqi: Float = UserStore.DEFAULT_LQI
    var lastMinutes: Int = UserStore.DEFAULT_MIN
    var channel802154: Int = UserStore.DEFAULT_CHANNEL

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

    private suspend fun sendRequest(lqi: Float, lastMinutes: Int) {
        try {
            val request = statsRequest {
                this.channel = channel802154
                this.name = "Stats from Android App"
                this.lqi = lqi
                this.timestamp = (Instant.now().epochSecond - lastMinutes * 60) * 1000000
            }
            val response = reporter.getStats(request)
            responseState.value = "Received: ${response.statsCount} items."
            responseValues.value = response
            responseError.value = false
        } catch (e: Exception) {
            responseState.value = e.message ?: "Unknown Error"
            responseError.value = true
            Log.e(TAG, "Error Request", e)
        }
    }


    fun sendRequestRepeat(
        scope: CoroutineScope, period: Duration
    ) {
        if (this.tickerJob == null) {
            this.tickerJob = tickerFlow(period).onEach {
                this.sendRequest(this.lqi, this.lastMinutes)
            }.launchIn(scope)
        }
    }

    fun cancelRequestRepeat() {
        tickerJob?.cancel()
        tickerJob = null
    }


    private fun tickerFlow(period: Duration, initialDelay: Duration = Duration.ZERO) = flow {
        delay(initialDelay)
        while (true) {
            emit(Unit)
            delay(period)
        }
    }

    override fun close() {
        channel.shutdownNow()
    }

    companion object {
        const val TAG = "ZB_SNIFFER"
    }
}


@Composable
fun Reporter(statsRCP: StatsRCP) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val store = UserStore(context)
    val channelStore by store.getChannel.collectAsState(initial = UserStore.DEFAULT_CHANNEL)
    val lqiStore by store.getMinLQI.collectAsState(initial = UserStore.DEFAULT_LQI)
    val minutesStore by store.getMinutes.collectAsState(initial = UserStore.DEFAULT_MIN)
    var lqiText by rememberSaveable { mutableStateOf(UserStore.DEFAULT_LQI.format(0)) }
    var lastMinutes by rememberSaveable { mutableStateOf(UserStore.DEFAULT_MIN.toString()) }
    var channel802154 by rememberSaveable { mutableStateOf(UserStore.DEFAULT_CHANNEL.toString()) }
    if (statsRCP.lqi != lqiStore) {
        statsRCP.lqi = lqiStore
        lqiText = lqiStore.format(0)
    }
    if (statsRCP.lastMinutes != minutesStore) {
        statsRCP.lastMinutes = minutesStore
        lastMinutes = minutesStore.toString()
    }
    if (statsRCP.channel802154 != channelStore) {
        statsRCP.channel802154 = channelStore
        channel802154 = channelStore.toString()
    }
    var isSending by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxWidth()
            .fillMaxHeight(), Arrangement.Top, Alignment.CenterHorizontally
    ) {
        val focusManager = LocalFocusManager.current
        Spacer(modifier = Modifier.height(32.dp))
        if (statsRCP.responseState.value.isNotEmpty()) {
            if (statsRCP.responseError.value) {
                Text(stringResource(R.string.server_response))
                Text(statsRCP.responseState.value)
            } else {
                val gridItems = statsRCP.responseValues.value.statsMap.toList()
                    .filter { (it.second.seqCnt > 1 || it.second.zbdccTotal > 0) && it.second.lqi != 255F }
                    .sortedBy { it.first }
                LazyVerticalGrid(columns = GridCells.Adaptive(140.dp),

                    // content padding
                    contentPadding = PaddingValues(
                        start = 12.dp, top = 16.dp, end = 12.dp, bottom = 16.dp
                    ), modifier = Modifier.padding(top = 20.dp), content = {
                        items(gridItems.size) { index ->
                            val key = gridItems[index].first
                            val value = gridItems[index].second
                            val seqLostDupes = value.seqLost + value.seqDuplicates
                            var lostTextColor = Color.Unspecified
                            var lostText = if (value.zbdccTotal > 0) {
                                when (value.zbdccLost.toFloat() / value.zbdccTotal.toFloat()) {
                                    in 0.01..<0.2 -> lostTextColor = TextWarning
                                    in 0.2..1.0 -> lostTextColor = TextError
                                }
                                "A: ${value.zbdccLost} / ${value.zbdccTotal}   "
                            } else {
                                ""
                            }
                            if (value.seqCnt > 1) {
                                lostText += "PKT: $seqLostDupes / ${value.seqCnt}"
                                when (seqLostDupes.toFloat() / value.seqCnt.toFloat()) {
                                    in 0.3..<0.5 -> lostTextColor = TextWarning
                                    in 0.5..1.0 -> lostTextColor = TextError
                                }
                            }
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
                                    text = lostText,
                                    fontWeight = FontWeight.Normal,
                                    fontSize = 16.sp,
                                    color = lostTextColor,
                                    textAlign = TextAlign.Left,
                                    modifier = Modifier.padding(2.dp)
                                )
                                if (value.rssi != 0F && value.lqi != 0F) {
                                    Text(
                                        text = "LQI: ${value.lqi.format(0)} [${value.rssi.format(0)} dBm]",
                                        fontWeight = FontWeight.Normal,
                                        fontSize = 16.sp,
                                        color = if (value.lqi > 0F && value.lqi < 20) TextWarning else Color.Unspecified,
                                        textAlign = TextAlign.Left,
                                        modifier = Modifier.padding(2.dp)
                                    )
                                }
                                Text(
                                    text = timeHourMinutesFormatter.format(
                                        Instant.ofEpochSecond(
                                            value.timestamp / 1000000
                                        )
                                    ),
                                    fontWeight = FontWeight.Normal,
                                    fontSize = 16.sp,
                                    color = Color.Unspecified,
                                    textAlign = TextAlign.Left,
                                    modifier = Modifier.padding(2.dp)
                                )
                            }
                        }
                    })
            }
        }

        Button(
            {
                if (isSending) {
                    statsRCP.cancelRequestRepeat()
                    isSending = false
                } else {
                    statsRCP.sendRequestRepeat(
                        scope, 30.seconds
                    )
                    isSending = true
                }

            }, Modifier.padding(10.dp)
        ) {
            if (isSending) Text(stringResource(R.string.cancel_requests))
            else Text(stringResource(R.string.send_request).format(30))
        }
        Row {
            TextField(
                value = channel802154,
                onValueChange = {
                    channel802154 = it
                    if (it.isNotEmpty() && it.isDigitsOnly()) {
                        val newCh = it.toInt()
                        if (newCh in 11..26) {
                            CoroutineScope(Dispatchers.IO).launch {
                                store.saveChannel(newCh)
                            }
                        }
                    }
                },
                label = { Text(stringResource(R.string.capture_channel)) },
                modifier = Modifier.widthIn(min = 40.dp, max = 80.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                keyboardActions = KeyboardActions(onDone = {
                    val newCh = channel802154.toInt()
                    if (newCh in 11..26) {
                        focusManager.clearFocus()
                    } else {
                        channel802154 = statsRCP.channel802154.toString()
                    }
                })
            )
            TextField(
                value = lqiText,
                onValueChange = {
                    lqiText = it
                    if (it.isNotEmpty() && it.isDigitsOnly()) {
                        CoroutineScope(Dispatchers.IO).launch {
                            store.saveMinLQI(it.toFloat())
                        }
                    }
                },
                label = { Text(stringResource(R.string.min_lqi)) },
                modifier = Modifier.widthIn(min = 40.dp, max = 80.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
            )
            TextField(
                value = lastMinutes,
                onValueChange = {
                    lastMinutes = it
                    if (it.isNotEmpty() && it.isDigitsOnly()) {
                        CoroutineScope(Dispatchers.IO).launch {
                            store.saveMinutes(it.toInt())
                        }
                    }
                },
                label = { Text(stringResource(R.string.last_minutes)) },
                modifier = Modifier.width(150.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
            )
        }

    }
}