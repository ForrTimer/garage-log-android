package com.garagelog.app.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.garagelog.app.data.ai.ASSISTANT_NAME
import com.garagelog.app.data.onboarding.Tip
import com.garagelog.app.ui.components.ConfirmDialog
import com.garagelog.app.ui.components.GarageCard
import com.garagelog.app.ui.theme.GarageDimens
import com.garagelog.app.ui.theme.garageColors
import kotlinx.coroutines.launch

private data class IntroCard(val icon: ImageVector, val title: String, val body: String)

private val introCards = listOf(
    IntroCard(
        Icons.Filled.DirectionsCar,
        "Welcome to Garage Log",
        "Every vehicle's maintenance, fuel, repairs and problems in one place.\n\n" +
            "Two sample vehicles are loaded so you can look around first. When you're ready, " +
            "remove them from the banner on Home and add your own.",
    ),
    IntroCard(
        Icons.Filled.Home,
        "Home: each vehicle at a glance",
        "Each card shows the mileage, what's due or overdue, open issues, and the last 12 months " +
            "of spending.\n\nTap \"Update mileage\" after a drive, \"Add fueling\" at the pump, " +
            "and any due item to jump straight to it.",
    ),
    IntroCard(
        Icons.AutoMirrored.Filled.MenuBook,
        "Log: everything you do to it",
        "Fill-ups, mileage readings, routine service, repairs and upgrades, with costs, parts and " +
            "photos.\n\nLink a service to a maintenance item and its next due date resets itself. " +
            "Full-tank fill-ups work out your mpg.",
    ),
    IntroCard(
        Icons.Filled.Build,
        "Maintenance: stay ahead of it",
        "Open Maintenance from the menu at the top left. Give each job an interval in miles, " +
            "months or both, and the app counts down to it.\n\nEach item has a how-to video button, " +
            "and Settings can remind you to log your mileage.",
    ),
    IntroCard(
        Icons.Filled.AutoAwesome,
        "Issues, and $ASSISTANT_NAME",
        "Note problems as they show up, and mark the safety-critical ones so they stand out.\n\n" +
            "$ASSISTANT_NAME is the built-in assistant: he can diagnose an issue or answer questions " +
            "using your records. He needs an Anthropic API key, added in Settings.",
    ),
    IntroCard(
        Icons.AutoMirrored.Filled.ShowChart,
        "Trends, reports and backups",
        "Trends charts running costs, mileage and mpg. Export a vehicle report as a PDF to " +
            "hand to a buyer.\n\nIn Settings: export a backup, sync through Google Drive, or share " +
            "a garage with family. You can open this guide again there too.",
    ),
)

/**
 * The intro cards: a full-screen swipeable set shown once on a fresh install, and again from
 * Settings → Getting started. Skipping, finishing or Back all count as seen.
 */
@Composable
fun IntroCards(onDone: () -> Unit) {
    Dialog(
        onDismissRequest = onDone,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false, decorFitsSystemWindows = false),
    ) {
        BackHandler(onBack = onDone)
        val pagerState = rememberPagerState { introCards.size }
        val scope = rememberCoroutineScope()
        val last = pagerState.currentPage == introCards.lastIndex
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.End) {
                    // Hidden rather than removed on the last card, so the page doesn't jump up.
                    TextButton(onClick = onDone, enabled = !last, modifier = Modifier.alpha(if (last) 0f else 1f)) { Text("Skip") }
                }
                HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
                    IntroPage(introCards[page])
                }
                PageDots(count = introCards.size, current = pagerState.currentPage)
                Button(
                    onClick = { if (last) onDone() else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp),
                ) { Text(if (last) "Get started" else "Next") }
            }
        }
    }
}

@Composable
private fun IntroPage(card: IntroCard) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(112.dp).background(garageColors.chrome),
        ) {
            Icon(card.icon, contentDescription = null, tint = garageColors.info, modifier = Modifier.size(56.dp))
        }
        Spacer(Modifier.height(32.dp))
        Text(card.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Text(
            card.body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PageDots(count: Int, current: Int) {
    Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
        repeat(count) { index ->
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .size(width = if (index == current) 20.dp else 8.dp, height = 8.dp)
                    .background(if (index == current) garageColors.info else garageColors.chromeEdge),
            )
        }
    }
}

/** What each screen's first-time tip says. Short: it sits above the screen's own content. */
fun Tip.text(): String = when (this) {
    Tip.Home -> "Tap a vehicle's picture to add a photo. \"Show vehicle details\" holds its specs, Edit and the PDF report. Swipe left and right to move between tabs."
    Tip.Log -> "Tap + to log a fill-up, mileage reading, service, repair or upgrade. Search, or pick a vehicle at the top to narrow the list. Swipe an entry left to delete it."
    Tip.Issues -> "Tap + to note a problem. Mark it safety-critical so it stands out on Home. The sparkle icon on an issue has $ASSISTANT_NAME diagnose it."
    Tip.Trends -> "Charts fill in as you log fill-ups and services. Pick a vehicle at the top for its own charts and the Export report button."
    Tip.Maintenance -> "\"Mark done today\" resets an item's countdown. Logging the service and linking it to the item does the same, and keeps the cost. The video icon finds a how-to for that job."
    Tip.Vehicles -> "Add, edit and reorder vehicles here. Deleting one removes its log, issues and schedule too."
    Tip.AskBob -> "Ask about this vehicle: what's due, what a noise might be, what oil it takes. $ASSISTANT_NAME reads its records and answers with a short TL;DR first."
    Tip.Diagnosis -> "$ASSISTANT_NAME reads the issue and the vehicle's history, then researches likely causes. More detail in the issue gives a better answer."
}

/** A screen's one-time tip, above its content until "Got it". */
@Composable
fun FirstTimeTip(tip: Tip, onDismiss: () -> Unit) {
    GarageCard(
        accentColor = garageColors.info,
        modifier = Modifier.padding(start = GarageDimens.screenHorizontal, end = GarageDimens.screenHorizontal, top = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(Icons.Filled.Lightbulb, contentDescription = null, tint = garageColors.info, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(tip.text(), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        }
        TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Got it") }
    }
}

/** On Home while the sample vehicles are loaded: what they are, and the way to clear them. */
@Composable
fun SampleDataBanner(onRemove: () -> Unit, modifier: Modifier = Modifier) {
    var confirm by remember { mutableStateOf(false) }
    GarageCard(accentColor = garageColors.warn, modifier = modifier) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(Icons.Filled.Science, contentDescription = null, tint = garageColors.warn, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                "Sample truck and Sample car are made-up examples. Look around, then remove them and add your own.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
        }
        OutlinedButton(onClick = { confirm = true }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
            Text("Remove sample data")
        }
    }
    if (confirm) {
        ConfirmDialog(
            title = "Remove sample data?",
            message = "Deletes the two sample vehicles and everything logged on them. Your own vehicles aren't touched. You can add the samples back in Settings.",
            confirmLabel = "Remove",
            onConfirm = onRemove,
            onDismiss = { confirm = false },
        )
    }
}
