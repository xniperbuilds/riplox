package com.xniperbuilds.downloader

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import com.xniperbuilds.downloader.ui.theme.XniperDownloaderTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 🔒 Secret Vault — home-logo tap se khulta hai (koi visible button nahi).
 * Lock = phone ka fingerprint/PIN (BiometricPrompt) → password bhoolne ka masla hi nahi.
 * Files app-private folder me — gallery/file managers me invisible.
 */
class VaultActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val prompt = BiometricPrompt(
            this, ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    showVault()
                }

                override fun onAuthenticationError(code: Int, msg: CharSequence) {
                    Toast.makeText(this@VaultActivity, "Locked", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        )
        try {
            prompt.authenticate(
                BiometricPrompt.PromptInfo.Builder()
                    .setTitle("Secret Vault")
                    .setSubtitle("Unlock with fingerprint or screen lock")
                    .setAllowedAuthenticators(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
                    .build()
            )
        } catch (e: Exception) {
            // Device pe koi lock set hi nahi → seedha kholo (user ki marzi)
            showVault()
        }
    }

    private fun showVault() {
        setContent {
            XniperDownloaderTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { p ->
                    VaultScreen(Modifier.padding(p))
                }
            }
        }
    }
}

@Composable
private fun VaultScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    val items = remember(refresh) { Vault.items(context) }
    val frameLoader = remember {
        ImageLoader.Builder(context).components { add(VideoFrameDecoder.Factory()) }.build()
    }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Spacer(Modifier.height(8.dp))
        RiploxHeader(subtitle = "Secret Vault", tileSize = 36)
        Spacer(Modifier.height(4.dp))
        Text(
            "Only here. Not in gallery, not in backups.",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))

        if (items.isEmpty()) {
            Text(
                "Vault is empty.\nHistory → ⋮ → Move to vault.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyColumn {
                items(items, key = { it.id }) { v ->
                    VaultRow(v, frameLoader) { refresh++ }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun VaultRow(v: VaultItem, frameLoader: ImageLoader, onChanged: () -> Unit) {
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val timeStr = remember(v.time) {
        SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(v.time))
    }
    val file = remember(v.id) { Vault.fileOf(context, v) }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete from vault?") },
            text = { Text("This file exists ONLY in the vault — deleting it here removes it from the phone permanently.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    Vault.delete(context, v.id)
                    onChanged()
                }) { Text("Delete forever") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }

    fun play() {
        try {
            context.startActivity(
                Intent(context, PlayerActivity::class.java).setData(Uri.fromFile(file))
            )
        } catch (e: Exception) {
            Toast.makeText(context, "Can't play this file", Toast.LENGTH_SHORT).show()
        }
    }

    Card(modifier = Modifier.fillMaxWidth().clickable { play() }) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (v.isAudio) {
                    Text("🎵", fontSize = 20.sp)
                } else {
                    AsyncImage(
                        model = file,
                        imageLoader = frameLoader,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(v.title, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${v.platform} · $timeStr",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = { menu = true }) { Text("⋮", fontSize = 20.sp) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("▶ Play") }, onClick = { menu = false; play() })
                DropdownMenuItem(text = { Text("↩ Restore to gallery") }, onClick = {
                    menu = false
                    if (Vault.restore(context, v.id)) {
                        Toast.makeText(context, "✓ Restored to gallery", Toast.LENGTH_SHORT).show()
                        onChanged()
                    } else {
                        Toast.makeText(context, "Restore failed", Toast.LENGTH_SHORT).show()
                    }
                })
                DropdownMenuItem(text = { Text("🗑 Delete forever") }, onClick = {
                    menu = false; confirmDelete = true
                })
            }
        }
    }
}
