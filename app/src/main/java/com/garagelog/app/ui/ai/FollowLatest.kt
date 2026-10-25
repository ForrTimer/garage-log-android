package com.garagelog.app.ui.ai

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow

/**
 * Whether a streaming answer should keep pulling the view down to its newest words. It does until
 * the reader drags the list themselves (to read back up), and starts again once they're back at
 * the bottom. Before this, every new word scrolled the view down, so scrolling up while Bob was
 * still talking just snapped back.
 */
@Composable
internal fun rememberFollowLatest(state: ScrollableState, interactions: InteractionSource): State<Boolean> {
    val follow = remember { mutableStateOf(true) }
    LaunchedEffect(state) {
        interactions.interactions.collect { if (it is DragInteraction.Start) follow.value = false }
    }
    LaunchedEffect(state) {
        snapshotFlow { state.canScrollForward }.collect { canScrollDown -> if (!canScrollDown) follow.value = true }
    }
    return follow
}
