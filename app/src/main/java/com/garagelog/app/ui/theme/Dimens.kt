package com.garagelog.app.ui.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp

/**
 * Shared spacing tokens — named references for values that were previously repeated as
 * identical dp literals across multiple screen files, so a spacing change only has to happen
 * in one place and the intent (why this number, not just what it is) is documented once.
 */
object GarageDimens {
    val screenHorizontal = 16.dp
    val screenTop = 14.dp
    val screenBottom = 24.dp

    // The FAB is a SmallFloatingActionButton docked bottom-end by Scaffold; this clears its
    // height plus margin so a list's last row never sits underneath it.
    val fabClearance = 88.dp

    /** Tab screens with no FAB (Dashboard, Settings). */
    val listContentPadding = PaddingValues(screenHorizontal, screenTop, screenHorizontal, screenBottom)

    /** Tab screens with a FAB (Log, Issues, Build). */
    val listContentPaddingWithFab = PaddingValues(screenHorizontal, screenTop, screenHorizontal, fabClearance)

    /** Drill-down screens with their own back-button header instead of a tab (Schedule, Cost trend). */
    val subScreenContentPadding = PaddingValues(screenHorizontal, 4.dp, screenHorizontal, screenBottom)

    // Bottom-sheet form chrome (LogFormSheet, IssueFormSheet, ScheduleFormSheet, PhaseFormSheet,
    // StepFormSheet, VehicleFormSheet, and FormSheetScaffold which now hosts all of them).
    val sheetHorizontalPadding = 18.dp
    val sheetButtonRowTop = 18.dp
    val sheetButtonRowBottom = 24.dp
    val sheetButtonSpacing = 10.dp
}
