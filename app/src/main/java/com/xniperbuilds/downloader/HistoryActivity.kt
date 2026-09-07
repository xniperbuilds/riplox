package com.xniperbuilds.downloader

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xniperbuilds.downloader.ui.theme.XniperDownloaderTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            XniperDownloaderTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { p ->
                    HistoryScreen(Modifier.padding(p))
                }
            }
        }
    }
}

@Composable
private fun HistoryScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var showTrash by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var chip by remember { mutableStateOf("All") }
    var selectMode by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<Long>()) }
    var confirmBulkDelete by remember { mutableStateOf(false) }
    val items = remember(showTrash, refresh) {
        if (showTrash) History.trash(context) else History.all(context)
    }

    // ---- Link-pack export/import (naye phone pe links le jao) ----
    var showExportDialog by remember { mutableStateOf(false) }
    var exportPass by remember { mutableStateOf("") }
    var pendingExport by remember { mutableStateOf("") }
    val exportSaver = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null && pendingExport.isNotBlank()) {
            try {
                context.contentResolver.openOutputStream(uri)?.use {
                    it.write(pendingExport.toByteArray())
                }
                Toast.makeText(context, "✓ Links file saved", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Save failed", Toast.LENGTH_SHORT).show()
            }
            pendingExport = ""
        }
    }
    var importText by remember { mutableStateOf<String?>(null) }
    var importPassAsk by remember { mutableStateOf(false) }
    var importPass by remember { mutableStateOf("") }
    var importLinks by remember { mutableStateOf<List<PackLink>?>(null) }
    var importSel by remember { mutableStateOf(setOf<Int>()) }
    fun tryImportParse(pass: String?) {
        val txt = importText ?: return
        try {
            val l = LinkPack.parse(txt, pass)
            importLinks = l
            importSel = l.indices.toSet()
            importPassAsk = false
        } catch (e: LinkPack.NeedPassword) {
            importPass = ""; importPassAsk = true
        } catch (e: LinkPack.BadPassword) {
            Toast.makeText(context, "Wrong password", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Not a valid Riplox links file", Toast.LENGTH_SHORT).show()
            importText = null
        }
    }
    val importPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                importText = context.contentResolver.openInputStream(uri)
                    ?.bufferedReader()?.use { it.readText() }
                tryImportParse(null)
            } catch (e: Exception) {
                Toast.makeText(context, "Couldn't read file", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val filtered = items.filter { rec ->
        val chipOk = when (chip) {
            "All" -> true
            "Audio" -> rec.isAudio
            "Photo" -> rec.isImage
            "Video" -> !rec.isAudio && !rec.isImage
            else -> rec.platform.equals(chip, ignoreCase = true)
        }
        val qOk = query.isBlank() ||
            rec.title.contains(query, true) ||
            rec.url.contains(query, true) ||
            rec.platform.contains(query, true)
        chipOk && qOk
    }

    if (confirmBulkDelete) {
        AlertDialog(
            onDismissRequest = { confirmBulkDelete = false },
            title = { Text(if (showTrash) "Permanently delete ${selected.size}?" else "Delete ${selected.size} download(s)?") },
            text = {
                Text(
                    if (showTrash) "These records will be removed forever."
                    else "Saved files will also be deleted from your phone. Links stay in Trash."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmBulkDelete = false
                    selected.forEach { id ->
                        if (showTrash) History.deleteForever(context, id)
                        else History.moveToTrash(context, id)
                    }
                    Toast.makeText(context, "✓ Deleted ${selected.size}", Toast.LENGTH_SHORT).show()
                    selected = emptySet(); selectMode = false; refresh++
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmBulkDelete = false }) { Text("Cancel") } }
        )
    }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        if (!selectMode) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (showTrash) "🗑 Trash" else "📜 History",
                    fontSize = 22.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                if (!showTrash) {
                    TextButton(onClick = { importPicker.launch(arrayOf("*/*")) }) { Text("⇪") }
                }
                TextButton(onClick = { selectMode = true; selected = emptySet() }) { Text("Select") }
                TextButton(onClick = { showTrash = !showTrash }) {
                    Text(if (showTrash) "History" else "Trash")
                }
            }
        } else {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { selectMode = false; selected = emptySet() }) { Text("✕") }
                Text(
                    "${selected.size} selected",
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = {
                    selected = if (selected.size == filtered.size) emptySet()
                    else filtered.map { it.id }.toSet()
                }) { Text(if (selected.size == filtered.size) "None" else "All") }
                TextButton(
                    onClick = { if (selected.isNotEmpty()) exportLinks(context, filtered.filter { selected.contains(it.id) }) }
                ) { Text("⧉") }
                TextButton(onClick = {
                    if (!showTrash) { exportPass = ""; showExportDialog = true }
                }) { Text("📤") }
                TextButton(
                    onClick = { if (selected.isNotEmpty()) confirmBulkDelete = true }
                ) { Text("🗑") }
            }
        }
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("🔍 Search (title / link / platform)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            listOf("All", "Audio", "Video", "Photo", "YouTube", "Instagram", "TikTok", "Facebook").forEach { c ->
                FilterChip(
                    selected = chip == c,
                    onClick = { chip = c },
                    label = { Text(c) },
                    modifier = Modifier.padding(end = 6.dp)
                )
            }
        }
        Spacer(Modifier.height(10.dp))

        if (filtered.isEmpty()) {
            Text(
                if (items.isEmpty()) {
                    if (showTrash) "Trash is empty" else "No downloads yet"
                } else "Nothing matches this filter/search",
                fontSize = 14.sp
            )
        } else {
            LazyColumn {
                items(filtered, key = { it.id }) { rec ->
                    RecordRow(
                        rec, showTrash,
                        selectMode = selectMode,
                        isSelected = selected.contains(rec.id),
                        onToggle = {
                            selected = if (selected.contains(rec.id)) selected - rec.id else selected + rec.id
                        }
                    ) { refresh++ }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }

    // ---- Export dialog (password optional) ----
    if (showExportDialog) {
        val toExport = if (selected.isNotEmpty()) filtered.filter { selected.contains(it.id) } else filtered
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("Export ${toExport.size} link(s)") },
            text = {
                Column {
                    Text(
                        "One file with these links — import it on any phone with Riplox. Password optional (locks the file).",
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = exportPass,
                        onValueChange = { exportPass = it },
                        label = { Text("Password (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (toExport.isEmpty()) {
                        Toast.makeText(context, "No links", Toast.LENGTH_SHORT).show()
                    } else {
                        pendingExport = LinkPack.export(
                            toExport.map { PackLink(it.url, it.title, it.isAudio) },
                            exportPass.ifBlank { null }
                        )
                        showExportDialog = false
                        exportSaver.launch("riplox_links.riplox")
                    }
                }) { Text("Save file") }
            },
            dismissButton = { TextButton(onClick = { showExportDialog = false }) { Text("Cancel") } }
        )
    }

    // ---- Import: password puchna ----
    if (importPassAsk) {
        AlertDialog(
            onDismissRequest = { importPassAsk = false; importText = null },
            title = { Text("File is locked") },
            text = {
                OutlinedTextField(
                    value = importPass,
                    onValueChange = { importPass = it },
                    label = { Text("Password") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = { tryImportParse(importPass) }) { Text("Unlock") }
            },
            dismissButton = {
                TextButton(onClick = { importPassAsk = false; importText = null }) { Text("Cancel") }
            }
        )
    }

    // ---- Import: links select + download ----
    val impList = importLinks
    if (impList != null) {
        AlertDialog(
            onDismissRequest = { importLinks = null; importText = null },
            title = { Text("Import ${impList.size} link(s)") },
            text = {
                Column {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("${importSel.size} selected", fontSize = 12.sp, modifier = Modifier.weight(1f))
                        TextButton(onClick = {
                            importSel = if (importSel.size == impList.size) emptySet() else impList.indices.toSet()
                        }) { Text(if (importSel.size == impList.size) "None" else "All") }
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        impList.forEachIndexed { i, l ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        importSel = if (importSel.contains(i)) importSel - i else importSel + i
                                    }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    if (importSel.contains(i)) "☑" else "☐",
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.height(0.dp))
                                Text(
                                    "  ${if (l.isAudio) "🎵" else "🎬"} ${l.title}",
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val chosen = impList.filterIndexed { i, _ -> importSel.contains(i) }
                    chosen.forEach { DownloadQueue.enqueue(context, it.url, it.isAudio) }
                    Toast.makeText(context, "⬇ ${chosen.size} download(s) queued", Toast.LENGTH_SHORT).show()
                    importLinks = null; importText = null
                }) { Text("⬇ Download (${importSel.size})") }
            },
            dismissButton = {
                TextButton(onClick = { importLinks = null; importText = null }) { Text("Cancel") }
            }
        )
    }
}

private fun exportLinks(context: Context, records: List<DownloadRecord>) {
    if (records.isEmpty()) {
        Toast.makeText(context, "No links", Toast.LENGTH_SHORT).show()
        return
    }
    val text = records.joinToString("\n") { it.url }
    val cm = context.getSystemService(ClipboardManager::class.java)
    cm?.setPrimaryClip(ClipData.newPlainText("links", text))
    Toast.makeText(context, "✓ Copied ${records.size} link(s)", Toast.LENGTH_SHORT).show()
}

@Composable
private fun RecordRow(
    rec: DownloadRecord,
    inTrash: Boolean,
    selectMode: Boolean = false,
    isSelected: Boolean = false,
    onToggle: () -> Unit = {},
    onChanged: () -> Unit
) {
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmForever by remember { mutableStateOf(false) }
    val timeStr = remember(rec.time) {
        SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(rec.time))
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this download?") },
            text = { Text("The saved file will also be deleted from your phone. The link stays in Trash for re-download.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    History.moveToTrash(context, rec.id)
                    onChanged()
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
    if (confirmForever) {
        AlertDialog(
            onDismissRequest = { confirmForever = false },
            title = { Text("Permanently delete?") },
            text = { Text("This record will be removed forever.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmForever = false
                    History.deleteForever(context, rec.id)
                    onChanged()
                }) { Text("Delete forever") }
            },
            dismissButton = { TextButton(onClick = { confirmForever = false }) { Text("Cancel") } }
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                when {
                    selectMode -> onToggle()
                    !inTrash -> playFile(context, rec) // tap = seedha play
                }
            }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selectMode) {
                Text(
                    if (isSelected) "☑" else "☐",
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 10.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    rec.title,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${if (rec.isImage) "🖼" else if (rec.isAudio) "🎵" else "🎬"} ${rec.platform} • $timeStr",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!selectMode) {
            TextButton(onClick = { menu = true }) { Text("⋮", fontSize = 20.sp) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                if (!inTrash) {
                    DropdownMenuItem(text = { Text("▶ Play") }, onClick = {
                        menu = false; playFile(context, rec)
                    })
                    DropdownMenuItem(text = { Text("📤 Share") }, onClick = {
                        menu = false; shareVideo(context, rec)
                    })
                    DropdownMenuItem(text = { Text("🔗 Copy link") }, onClick = {
                        menu = false; copyLink(context, rec.url)
                    })
                    DropdownMenuItem(text = { Text("⤓ Re-download") }, onClick = {
                        menu = false; reDownload(context, rec)
                    })
                    if (Prefs.vaultEnabled(context)) {
                        DropdownMenuItem(text = { Text("🔒 Move to vault") }, onClick = {
                            menu = false
                            if (Vault.moveIn(context, rec)) {
                                Toast.makeText(context, "✓ Moved to vault (tap the home logo to open)", Toast.LENGTH_SHORT).show()
                                onChanged()
                            } else {
                                Toast.makeText(context, "Move failed — file missing?", Toast.LENGTH_SHORT).show()
                            }
                        })
                    }
                    DropdownMenuItem(text = { Text("🗑 Delete") }, onClick = {
                        menu = false; confirmDelete = true
                    })
                } else {
                    DropdownMenuItem(text = { Text("⤓ Re-download") }, onClick = {
                        menu = false; reDownload(context, rec)
                    })
                    DropdownMenuItem(text = { Text("🔗 Copy link") }, onClick = {
                        menu = false; copyLink(context, rec.url)
                    })
                    DropdownMenuItem(text = { Text("↩ Restore") }, onClick = {
                        menu = false; History.restore(context, rec.id); onChanged()
                    })
                    DropdownMenuItem(text = { Text("❌ Permanently delete") }, onClick = {
                        menu = false; confirmForever = true
                    })
                }
            }
            } // !selectMode
        }
    }
}

/** Video/audio/photo SHARE — file ke sath Riplox ka intro-text + link bhi jata hai. */
private fun shareVideo(context: Context, rec: DownloadRecord) {
    try {
        val uri = Uri.parse(rec.location)
        val i = Intent(Intent.ACTION_SEND).apply {
            type = rec.viewMime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(
                Intent.EXTRA_TEXT,
                "Downloaded with Riplox — free video & audio downloader.\nhttps://xniperbuilds.com"
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(i, "Share"))
    } catch (e: Exception) {
        Toast.makeText(context, "Can't share (file missing?)", Toast.LENGTH_SHORT).show()
    }
}

private fun playFile(context: Context, rec: DownloadRecord) {
    // Photo → gallery viewer, video/audio → apna player. Faisla openRecord ke andar hai.
    if (!openRecord(context, rec)) {
        Toast.makeText(context, "Can't open (file may have been deleted)", Toast.LENGTH_SHORT).show()
    }
}

private fun copyLink(context: Context, url: String) {
    val cm = context.getSystemService(ClipboardManager::class.java)
    cm?.setPrimaryClip(ClipData.newPlainText("link", url))
    Toast.makeText(context, "Link copied", Toast.LENGTH_SHORT).show()
}

private fun reDownload(context: Context, rec: DownloadRecord) {
    DownloadQueue.enqueue(context, rec.url, rec.isAudio)
    Toast.makeText(context, "⬇ Re-download started (check notification)", Toast.LENGTH_SHORT).show()
}
