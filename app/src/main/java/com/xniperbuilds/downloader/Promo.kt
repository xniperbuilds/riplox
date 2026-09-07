package com.xniperbuilds.downloader

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.xniperbuilds.downloader.ui.theme.RiploxInk
import com.xniperbuilds.downloader.ui.theme.RiploxSteel
import com.xniperbuilds.downloader.ui.theme.RiploxSteelDeep
import com.xniperbuilds.downloader.ui.theme.SpaceGrotesk

/**
 * The in-app banner strip — home screen and the share/download sheet.
 *
 * WHY THIS IS A HOUSE BANNER AND NOT ADMOB. Riplox is distributed from GitHub Releases and is
 * on no app store (YouTube support means it can never be on Play). AdMob's own rule is that
 * "all Android apps must be publicly available in a supported store in order to link to
 * AdMob" — Play, Amazon, Samsung, Xiaomi GetApps, OPPO, VIVO
 * (https://support.google.com/admob/answer/10564477). An app that cannot be linked is never
 * reviewed, and an unreviewed app gets **limited ad serving**: the requests go out, almost
 * nothing comes back. So an AdMob banner here would be a mostly-empty rectangle, and it would
 * also attach a YouTube downloader to the same AdMob account that Riplox TT and JFF earn from.
 *
 * What this does instead is send Riplox's own users to the products that CAN earn — the Play
 * app in particular. No SDK, no network call, no tracking, nothing to consent to, and nothing
 * that can be throttled or disabled by someone else's policy.
 *
 * # lazy: one static rotating list. If Riplox ever gets listed on Amazon Appstore (the one
 *   realistic route to a supported store) this whole file is the seam: keep `PromoBanner()`
 *   where it is called from and swap the body for a real AdView + UMP consent flow. Nothing
 *   outside this file knows what fills the strip.
 */

/** Ek promo entry — naya product add karna ho to bas ek line. */
private data class Promo(
    val emoji: String,
    val title: String,
    val line: String,
    val cta: String,
    val url: String
)

// ⚠️ Riplox khud is list me NAHI — user pehle se yahi chala raha hai.
// ⚠️ Riplox IG bhi nahi: wo abhi public Play listing par nahi hai, aur mara hua link
//    ishtihar se bura hai. Public jagah pe TT ke liye lafz "early access" hai —
//    "open testing" kabhi nahi.
private val PROMOS = listOf(
    Promo(
        "🎬", "Riplox TT",
        "TikTok downloader — no watermark, on Google Play",
        "Get",
        "https://play.google.com/store/apps/details?id=com.xniperbuilds.riploxtt"
    ),
    Promo(
        "🔐", "Cipherly",
        "Encrypt & hide your files — free, on Google Play",
        "Get",
        "https://play.google.com/store/apps/details?id=com.xniperbuilds.cipherly"
    ),
    Promo(
        "💻", "Riplox for Windows",
        "The full desktop version — playlists, 4K, batch",
        "Download",
        "https://github.com/xniperbuilds/riplox-desktop/releases/latest"
    ),
    Promo(
        "🛠", "XniperTools",
        "Free online tools — PDF, images, text, calculators",
        "Open",
        "https://xnipertools.com"
    ),
    Promo(
        "🎮", "XniperArcade",
        "Free browser games — play instantly, no install",
        "Play",
        "https://xniperarcade.me"
    )
)

private const val ROTATE_MS = 12_000L

/**
 * @param modifier caller decides the spacing around it.
 *
 * ⚠️ Har rotation pe naya entry — magar tap ka target wahi rehta hai jo us waqt DIKH raha
 * hai (Crossfade ke andar ka apna clickable). Rotation ke lamhe pe tap se ghalat cheez
 * khulne wala masla is liye nahi banta.
 */
@Composable
fun PromoBanner(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    // Har baar wahi pehla card na dikhe — session ke shuru me jagah random.
    var index by remember { mutableStateOf(PROMOS.indices.random()) }

    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(ROTATE_MS)
            index = (index + 1) % PROMOS.size
        }
    }

    Column(modifier) {
        Text(
            "From XniperBuilds",
            fontSize = 11.sp,
            color = Color(0xFF7C8DA6),
            modifier = Modifier.padding(bottom = 6.dp)
        )
        Crossfade(targetState = index, label = "promo") { i ->
            val p = PROMOS[i]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(68.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        // Flat patti "sasti" lagti hai — halka gradient + hairline border
                        // usay app ka hissa banata hai, chipka hua ishtihar nahi.
                        Brush.horizontalGradient(
                            listOf(Color(0xFF16202F), RiploxSteelDeep.copy(alpha = 0.55f))
                        )
                    )
                    .border(1.dp, Color(0xFF1E2938), RoundedCornerShape(14.dp))
                    .clickable {
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(p.url)))
                        } catch (_: Exception) {
                        }
                    }
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF223046)),
                    contentAlignment = Alignment.Center
                ) { Text(p.emoji, fontSize = 18.sp) }

                Spacer(Modifier.width(12.dp))

                Column(Modifier.weight(1f)) {
                    Text(
                        p.title,
                        color = Color(0xFFE8EEF5),
                        fontSize = 14.sp,
                        fontFamily = SpaceGrotesk,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        p.line,
                        color = Color(0xFF7C8DA6),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.width(10.dp))

                // ⚠️ INK on steel = 7.14:1. WHITE on steel = 2.66:1 aur FAIL hota hai —
                //    ye style ka faisla nahi, naapa hua contrast hai.
                Box(
                    Modifier
                        .height(32.dp)
                        .clip(RoundedCornerShape(50))
                        .background(RiploxSteel)
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(p.cta, color = RiploxInk, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
