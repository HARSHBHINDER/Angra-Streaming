package dev.livepipe.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.livepipe.Destination
import dev.livepipe.Prefs
import dev.livepipe.StreamService
import dev.livepipe.SuperChatCard
import dev.livepipe.canvasAspect
import kotlin.math.min

private val TIER_LABELS = listOf("$1", "$2", "$5", "$10", "$20", "$50", "$100")

/**
 * Super Chat alerts: pick the look on a live preview of the stream frame, set timing and placement, and connect
 * YouTube. The preview card is the exact bitmap the stream uses. Settings apply immediately.
 */
@Composable
fun SuperChatScreen(
    dest: Destination?, prefs: Prefs, onPrefs: (Prefs) -> Unit, onDest: (Destination) -> Unit,
    onPlace: () -> Unit, onClose: () -> Unit, onPlaceChat: () -> Unit = {},
) {
    val state by StreamService.state.collectAsState()
    val status by StreamService.superChatStatus.collectAsState()
    val live = state == StreamService.State.LIVE
    var tier by remember { mutableIntStateOf(4) }
    var source by remember(dest?.id) { mutableStateOf(dest?.ytSource.orEmpty()) }
    val d = dest ?: Destination("", "", "", "")
    val preview = remember(prefs.alertStyle, prefs.alertShowTotal, tier) {
        SuperChatCard.render(SuperChatCard.sample(tier), prefs.alertStyle, prefs.alertShowTotal).asImageBitmap()
    }
    // Replays the entrance whenever the look changes, the way it arrives on stream.
    val enter = remember { Animatable(0f) }
    LaunchedEffect(prefs.alertStyle, prefs.alertShowTotal, tier) {
        enter.snapTo(0f); enter.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = 260f))
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(44.dp))
            Text("Chat & Super Chats", fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            Text("Done", color = Accent, fontWeight = FontWeight.SemiBold, modifier = Modifier.iosTap { onClose() })
        }

        // ---- Stage: the stream frame, with the card where viewers will see it ----
        val aspect = d.canvasAspect()
        BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
            val h = min((maxWidth / aspect).value, 420f).dp
            val w = h * aspect
            Box(
                Modifier.size(w, h).clip(RoundedCornerShape(14.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF1B2A4A), Color(0xFF3B1F5C), Color(0xFF7A2A3A))))
                    .border(0.5.dp, Ios.separator, RoundedCornerShape(14.dp)),
            ) {
                val cw = d.alertW
                val ch = cw * aspect * preview.height / preview.width
                Image(
                    preview, "Super Chat preview", contentScale = ContentScale.FillBounds,
                    modifier = Modifier
                        .offset(x = w * (d.alertX - cw / 2), y = h * (d.alertY - ch / 2) + 14.dp * (1 - enter.value))
                        .size(w * cw, h * ch)
                        .graphicsLayer {
                            alpha = enter.value.coerceIn(0f, 1f)
                            val s = 0.9f + 0.1f * enter.value; scaleX = s; scaleY = s
                        },
                )
                // Preview pill, top-left, so the frame reads as a stream.
                Row(Modifier.padding(10.dp).background(Color(0xCC000000), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).background(AppleRed, CircleShape))
                    Text("  PREVIEW", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                }
            }
        }

        SectionHeader("Preview amount")
        Chips((1..7).toList(), tier, { TIER_LABELS[it - 1] }) { tier = it }
        Footer("Colours follow YouTube's Super Chat tiers, so viewers recognise them.")

        // ---- Styles: each thumbnail is the real render ----
        SectionHeader("Style")
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SuperChatCard.STYLES.forEachIndexed { i, name ->
                val thumb = remember(i, tier) { SuperChatCard.render(SuperChatCard.sample(tier), i, false).asImageBitmap() }
                val on = i == prefs.alertStyle
                Column(Modifier.width(156.dp).iosTap { onPrefs(prefs.copy(alertStyle = i)) }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.fillMaxWidth().height(100.dp).clip(RoundedCornerShape(16.dp))
                            .background(Brush.linearGradient(listOf(Color(0xFF16182A), Color(0xFF2A1630))))
                            .border(if (on) 2.dp else 0.5.dp, if (on) Accent else Ios.separator, RoundedCornerShape(16.dp))
                            .padding(8.dp),
                        contentAlignment = Alignment.Center,
                    ) { Image(thumb, name, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()) }
                    Text(name, fontSize = 14.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (on) Accent else MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }

        SectionHeader("On stream")
        InsetCard {
            RowItem("Show Super Chats") { IosSwitch(prefs.superChat, { onPrefs(prefs.copy(superChat = it)); StreamService.refreshChat() }) }
            Hairline()
            RowItem("Show supporter's total") { IosSwitch(prefs.alertShowTotal, { onPrefs(prefs.copy(alertShowTotal = it)) }) }
            Hairline()
            RowItem("Position & size") {
                Text(if (dest != null) "Arrange" else "Add a channel first", color = if (dest != null) Accent else Ios.secondaryLabel,
                    modifier = Modifier.iosTap(dest != null) { onPlace() })
            }
            Hairline()
            RowItem("Test on stream") {
                Text(if (live) "Send test" else "Go live first", color = if (live) Accent else Ios.secondaryLabel,
                    modifier = Modifier.iosTap(live) { StreamService.testSuperChat(tier) })
            }
        }
        SectionHeader("Live chat on stream")
        InsetCard {
            RowItem("Show live chat") { IosSwitch(prefs.showChat, { onPrefs(prefs.copy(showChat = it)); StreamService.refreshChat() }) }
            Hairline()
            RowItem("Position & size") {
                Text(if (dest != null) "Arrange" else "Add a channel first", color = if (dest != null) Accent else Ios.secondaryLabel,
                    modifier = Modifier.iosTap(dest != null) { onPlaceChat() })
            }
            Hairline()
            RowItem("Test on stream") {
                Text(if (live) "Send test" else "Go live first", color = if (live) Accent else Ios.secondaryLabel,
                    modifier = Modifier.iosTap(live) { StreamService.testChat() })
            }
        }
        Footer("Your viewers' messages in a box on stream, newest at the bottom, names in YouTube's colours for members, " +
            "moderators and you. Use it with Super Chats, on its own, or turn both off. Changes apply live.")

        SectionHeader("On screen for")
        Chips(listOf(5, 8, 12, 20), prefs.alertSeconds, { "$it s" }) { onPrefs(prefs.copy(alertSeconds = it)) }
        Footer("Several at once queue up and play one after another.")

        SectionHeader("YouTube")
        InsetCard {
            if (dest != null) FieldRow("Channel", source, { source = it; onDest(dest.copy(ytSource = it.trim())) }, "@handle or live link")
            else RowItem("Channel") { Text("Add a channel first", color = Ios.secondaryLabel) }
            Hairline()
            FieldRow("API key", prefs.ytApiKey, { onPrefs(prefs.copy(ytApiKey = it.trim())) }, "YouTube Data API key", secure = true)
        }
        if (live && status.isNotBlank()) {
            Row(Modifier.padding(horizontal = 32.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(if (status.startsWith("Connected")) AppleGreen else Color(0xFFFF9500), CircleShape))
                Text("  $status", fontSize = 13.sp, color = Ios.secondaryLabel)
            }
        }
        Footer("Super Chats and Super Stickers are read from your live chat with the official YouTube Data API, using your " +
            "own free key: in Google Cloud Console create a project, enable \"YouTube Data API v3\", then Credentials → " +
            "Create credentials → API key. The key stays on this phone. A key's free daily quota covers about 2–4 hours " +
            "of live chat. Channel applies to \"${dest?.name ?: "this channel"}\".")
        Spacer(Modifier.height(32.dp))
    }
}
