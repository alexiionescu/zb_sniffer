package com.poltys.zb_sniffer

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.poltys.zb_sniffer.ui.theme.Zb_snifferTheme

import io.grpc.ManagedChannelBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import java.io.Closeable

import com.poltys.protos.zb_stats.*
import kotlinx.coroutines.launch

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

    private val channel = let {
        println("Connecting to ${uri.host}:${uri.port}")

        val builder = ManagedChannelBuilder.forAddress(uri.host, uri.port)
        if (uri.scheme == "https") {
            builder.useTransportSecurity()
        } else {
            builder.usePlaintext()
        }

        builder.executor(Dispatchers.IO.asExecutor()).build()
    }

    private val reporter = StatsReportGrpcKt.StatsReportCoroutineStub(channel)

    suspend fun sendRequest(name: String) {
        try {
            val request = statsRequest {
                this.name = name
                this.lqi = 100F
            }
            val response = reporter.getStats(request)
            responseState.value = response.toString()
        } catch (e: Exception) {
            responseState.value = e.message ?: "Unknown Error"
            e.printStackTrace()
        }
    }

    override fun close() {
        channel.shutdownNow()
    }
}

@Composable
fun Reporter(statsRCP: StatsRCP) {
    val scope = rememberCoroutineScope()
    val nameState = remember { mutableStateOf(TextFieldValue()) }
    Column(Modifier.fillMaxWidth().fillMaxHeight(), Arrangement.Top, Alignment.CenterHorizontally) {
        Text(stringResource(R.string.name_hint), modifier = Modifier.padding(top = 10.dp))
        OutlinedTextField(nameState.value, { nameState.value = it })

        Button({ scope.launch { statsRCP.sendRequest(nameState.value.text) } }, Modifier.padding(10.dp)) {
            Text(stringResource(R.string.send_request))
        }

        if (statsRCP.responseState.value.isNotEmpty()) {
            Text(stringResource(R.string.server_response), modifier = Modifier.padding(top = 10.dp))
            Text(statsRCP.responseState.value)
        }
    }
}