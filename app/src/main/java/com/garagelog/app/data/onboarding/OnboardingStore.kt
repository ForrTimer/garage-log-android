package com.garagelog.app.data.onboarding

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The screens that each show a one-time tip the first time they're opened. */
enum class Tip { Home, Log, Issues, Trends, Maintenance, Vehicles, AskBob, Diagnosis }

/**
 * [ready] stays false until the first launch has decided whether this is a fresh install, so an
 * existing install never flashes the intro while that check runs.
 */
data class OnboardingState(
    val ready: Boolean = false,
    val showIntro: Boolean = false,
    val seenTips: Set<Tip> = emptySet(),
)

/**
 * The startup guide's state: whether the intro cards are due, and which screens' tips have been
 * dismissed. Per phone, in plain prefs (it's not a record, so it neither syncs nor backs up).
 * Only a fresh install sees the guide on its own; Settings → Getting started reopens either part.
 */
class OnboardingStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(read())
    val state: StateFlow<OnboardingState> = _state.asStateFlow()

    val isInitialized: Boolean get() = prefs.getBoolean(KEY_INITIALIZED, false)

    /**
     * Called once, on the first launch of a build that has the guide. An install that already had
     * data (an update) is treated as having seen all of it.
     */
    fun initialize(freshInstall: Boolean) {
        prefs.edit {
            putBoolean(KEY_INITIALIZED, true)
            putBoolean(KEY_INTRO_SEEN, !freshInstall)
            putStringSet(KEY_TIPS_SEEN, if (freshInstall) emptySet() else Tip.entries.map { it.name }.toSet())
        }
        _state.value = read()
    }

    fun markIntroSeen() = update { putBoolean(KEY_INTRO_SEEN, true) }
    fun showIntroAgain() = update { putBoolean(KEY_INTRO_SEEN, false) }
    fun markTipSeen(tip: Tip) = update { putStringSet(KEY_TIPS_SEEN, _state.value.seenTips.map { it.name }.toSet() + tip.name) }
    fun resetTips() = update { putStringSet(KEY_TIPS_SEEN, emptySet()) }

    private fun update(block: android.content.SharedPreferences.Editor.() -> Unit) {
        prefs.edit(action = block)
        _state.value = read()
    }

    private fun read(): OnboardingState {
        if (!isInitialized) return OnboardingState()
        val seen = prefs.getStringSet(KEY_TIPS_SEEN, null).orEmpty()
        return OnboardingState(
            ready = true,
            showIntro = !prefs.getBoolean(KEY_INTRO_SEEN, true),
            seenTips = Tip.entries.filter { it.name in seen }.toSet(),
        )
    }

    private companion object {
        const val PREFS_NAME = "garage_log_onboarding"
        const val KEY_INITIALIZED = "initialized"
        const val KEY_INTRO_SEEN = "intro_seen"
        const val KEY_TIPS_SEEN = "tips_seen"
    }
}
