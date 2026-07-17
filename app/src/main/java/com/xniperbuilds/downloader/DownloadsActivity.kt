package com.xniperbuilds.downloader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.xniperbuilds.downloader.ui.theme.XniperDownloaderTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** ⬇ Downloads manager — Running / Queued / Failed / Completed ek jagah. */
class DownloadsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            XniperDownloaderTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { p ->
                    DownloadsScreen(Modifier.padding(p))
                }
            }
        }
    }
}

@Composable
private fun DownloadsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var infos by remember { mutableStateOf<List<WorkInfo>>(emptyList()) }
    var completed by remember { mutableStateOf<List<DownloadRecord>>(emptyList()) }
    var failed by remember { mutableStateOf<List<FailedItem>>(emptyList()) }

    // Live refresh — har second WorkManager ka state (simple + reliable)
    LaunchedEffect(Unit) {
        // Page khula = user download ka intezar kar raha — phansi ENQUEUED job ho to
        // escort se turant start karwao.
        EscortService.kickIfNeeded(context)
        while (true) {
            infos = withContext(Dispatchers.IO) {
                try {
                    WorkManager.getInstance(context).getWorkInfosByTag(DownloadQueue.TAG).get()
                } catch (e: Exception) {
                    emptyList()
                }
            }
            completed = withContext(Dispatchers.IO) {
                try { History.all(context) } catch (e: Exception) { emptyList() }
            }
            failed = withContext(Dispatchers.IO) {
                try { FailedStore.all(context) } catch (e: Exception) { emptyList() }
            }
            delay(1000)
        }
    }

    val running = infos.filter { it.state == WorkInfo.State.RUNNING }
    val queued = infos.filter { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.BLOCKED }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
    ) {
        Spacer(Modifier.height(8.dp))
        RiploxHeader(subtitle = "Downloads", tileSize = 36)
        Spacer(Modifier.height(14.dp))

        Section("RUNNING")
        if (running.isEmpty()) EmptyLine("Nothing downloading right now")
        else running.forEach { wi ->
            val title = wi.progress.getString("title") ?: "Downloading…"
            val pct = wi.progress.getInt("pct", -1)
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            title, fontSize = 13.sp, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = {
                            WorkManager.getInstance(context).cancelWorkById(wi.id)
                        }) { Text("Cancel") }
                    }
                    if (pct in 0..100) {
                        LinearProgressIndicator(
                            progress = { pct / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text("$pct%", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Section("QUEUED")
        if (queued.isEmpty()) EmptyLine("Queue is empty")
        else queued.forEach { wi ->
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("⏳ Waiting…", fontSize = 13.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        WorkManager.getInstance(context).cancelWorkById(wi.id)
                    }) { Text("Cancel") }
                }
            }
        }

        if (failed.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Section("FAILED")
                Spacer(Modifier.weight(1f))
                TextButton(onClick = {
                    FailedStore.clear(context)
                    WorkManager.getInstance(context).pruneWork()
                }) { Text("Clear", fontSize = 12.sp) }
            }
            failed.forEach { f ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    f.title, fontSize = 13.sp, maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "${if (f.isAudio) "🎵" else "🎬"} ${platformFolder(f.link)}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(onClick = {
                                FailedStore.remove(context, f.id)
                                DownloadQueue.enqueue(context, f.link, f.isAudio)
                            }) { Text("↻ Retry") }
                            TextButton(onClick = { FailedStore.remove(context, f.id) }) { Text("✕") }
                        }
                        if (f.error.isNotBlank()) {
                            Text(
                                f.error, fontSize = 10.5.sp, maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Section("COMPLETED")
        if (completed.isEmpty()) EmptyLine("No downloads yet")
        else completed.forEach { r ->
            val timeStr = remember(r.time) {
                SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(r.time))
            }
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable {
                        // tap = play
                        try {
                            context.startActivity(
                                android.content.Intent(context, PlayerActivity::class.java)
                                    .setData(android.net.Uri.parse(r.location))
                            )
                        } catch (e: Exception) {
                        }
                    }
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text(r.title, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${if (r.isAudio) "🎵" else "🎬"} ${r.platform} · $timeStr · tap to play",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Section(t: String) {
    Text(t, fontSize = 11.sp, letterSpacing = 2.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun EmptyLine(t: String) {
    Text(
        t, fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 6.dp)
    )
}
