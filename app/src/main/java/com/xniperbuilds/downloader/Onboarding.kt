package com.xniperbuilds.downloader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xniperbuilds.downloader.ui.theme.SpaceGrotesk

/** Pehli baar ka user guide — short, visual, "Don't show again" ke sath. */
@Composable
fun OnboardingScreen(modifier: Modifier = Modifier, onDone: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    val steps = listOf(
        // ⚠️ "no ads" nahi likhna — app apne hi doosre products ki ek patti dikhati hai. Koi
        //    ad network aur koi tracker phir bhi nahi; jumla wahi kehta hai jo waqai sach hai.
        Triple("👋", "Welcome to Riplox", "Video & audio from any site — free, no ad networks, no watermarks."),
        Triple("🔗", "Paste → Download", "Paste a link, tap Download, pick video or audio in the popup. Your choices become the defaults."),
        Triple("⚡", "Even faster: Share", "In any app tap Share → ⚡ Instant Download. Zero taps, runs in the background."),
        Triple("🔒", "Private videos?", "Instagram & others block guests now. Connect the site once — you log in on the REAL site, your password never touches this app.")
    )
    val (emoji, title, body) = steps[step]

    Column(
        modifier = modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        RiploxTile(56)
        Spacer(Modifier.height(28.dp))
        Text(emoji, fontSize = 44.sp)
        Spacer(Modifier.height(12.dp))
        Text(
            title,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = SpaceGrotesk,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(10.dp))
        Text(
            body,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(34.dp))
        Text(
            "${step + 1} / ${steps.size}",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        GradientButton(text = if (step < steps.lastIndex) "Next" else "Start using Riplox") {
            if (step < steps.lastIndex) step++ else onDone()
        }
        TextButton(onClick = onDone) {
            Text("Don't show again", fontSize = 13.sp)
        }
    }
}
