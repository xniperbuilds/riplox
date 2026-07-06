package com.xniperbuilds.downloader

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xniperbuilds.downloader.ui.theme.XniperDownloaderTheme

/** Ek service/product ka entry — NAYA link add karna ho to bas is list me ek line. */
private data class Service(val title: String, val desc: String, val url: String)

private val SERVICES = listOf(
    Service("🛠 XniperTools", "Free online tools — PDF, images, text, calculators & more", "https://xnipertools.com"),
    Service("🎮 XniperArcade", "Free browser games — play instantly, nothing to install", "https://xniperarcade.me"),
    Service("💻 GitHub", "All XniperBuilds projects & code", "https://github.com/xniperbuilds"),
    Service("📸 Instagram", "@xniperr — new tools, games & app updates", "https://instagram.com/xniperr"),
    // Future apps/websites yahan add hongi — bas ek Service(...) line
)

/** "More from XniperBuilds" — hamari sab websites/apps ek jagah. */
class ServicesActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            XniperDownloaderTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { p ->
                    ServicesScreen(Modifier.padding(p))
                }
            }
        }
    }
}

@Composable
private fun ServicesScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        Text("🧩 More from XniperBuilds", fontSize = 24.sp, color = MaterialTheme.colorScheme.primary)
        Text(
            "Our other free tools, games and apps — new ones keep coming.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))

        SERVICES.forEach { s ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .clickable {
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(s.url)))
                        } catch (_: Exception) {
                        }
                    }
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(s.title, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(s.desc, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        s.url.removePrefix("https://"),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}
