package com.garagelog.app.ui.theme

import androidx.compose.ui.graphics.Color

// Warm graphite (dark) / workshop concrete (light, since 2026-10-15: Ethan's pick of "garage" option B).
// Replaces the navy set ported from the PWA. Rust is reserved for alarm states only —
// primary actions use the brand accent blue (matching the launcher icon's wrench), never the
// alarm color, so red stays a genuine, uncompeted-for signal wherever it appears.

// ---- Dark ----------------------------------------------------------------
val DarkBg = Color(0xFF161413) // content ground
val DarkPanel = Color(0xFF1C1A18) // panel / row surface
val DarkRule = Color(0xFF2E2A28) // hairline between rows
val DarkEdge = Color(0xFF3A3532) // outlined-button / chip border
val DarkChrome = Color(0xFF332E2A) // status strip, header, bottom nav
val DarkChromeEdge = Color(0xFF59514B) // 2dp rule under header / above nav
val DarkText = Color(0xFFF2EEEB)
val DarkTextDim = Color(0xFFA9A19C) // supporting body copy
val DarkTextMuted = Color(0xFF8A827D) // metadata, eyebrow labels
val DarkAlarm = Color(0xFFE43D2E) // rust: open counts, overdue, active-tab marker
val DarkAlarmText = Color(0xFFF05E54) // rust legible as small text on panel
val DarkWarn = Color(0xFFE8A33D)
val DarkOk = Color(0xFF7BC47F)
// Brand accent — the exact blue of the launcher icon's wrench (ic_launcher_foreground.xml),
// used as primary/info everywhere so there's one consistent "this app's color" instead of the
// old bone-fill primary and a separately-tuned steel blue for "info".
val DarkAccent = Color(0xFF4DA3FF)
val DarkOnAccent = Color(0xFF0B1E33)

// ---- Light ---------------------------------------------------------------
// Cool "shop concrete" grays with a deep workshop blue. Every text/background pair here was
// checked against WCAG AA (4.5:1); the lowest is accent text on the header chrome at 5.8:1.
// Red stays the only warm color on screen, so Overdue is never mistaken for a button.
val LightBg = Color(0xFFE4E6E8)
val LightPanel = Color(0xFFF7F8F8)
val LightRule = Color(0xFFD3D7DA)
val LightEdge = Color(0xFFB6BCC1)
val LightChrome = Color(0xFFD8DCDF)
val LightChromeEdge = Color(0xFF9EA6AD)
val LightText = Color(0xFF15181B)
val LightTextDim = Color(0xFF41474D)
val LightTextMuted = Color(0xFF575E65)
val LightAlarm = Color(0xFFB42318)
val LightAlarmText = Color(0xFF9F1C12)
val LightWarn = Color(0xFF8A5A08)
val LightOk = Color(0xFF2E6B37)
// The launcher icon's blue family, deep enough to read as text on light gray (7.5:1 on a panel).
val LightAccent = Color(0xFF1B4F9C)
val LightOnAccent = Color(0xFFFFFFFF)

// ---- Material 3 container roles ------------------------------------------
// Sheets, dialogs, menus and tonal buttons draw from these. Left unset, Material fills them with
// its own lavender defaults, which tinted every sheet and dialog in both themes.
val DarkContainerLow = Color(0xFF1C1A18)
val DarkContainer = Color(0xFF211F1D)
val DarkContainerHigh = Color(0xFF272422)
val DarkContainerHighest = Color(0xFF2E2A28)
val DarkAccentContainer = Color(0xFF16324F)
val DarkOnAccentContainer = Color(0xFFD6E8FF)
val LightContainerLow = Color(0xFFF7F8F8)
val LightContainer = Color(0xFFEFF1F2)
val LightContainerHigh = Color(0xFFE9ECEE)
val LightContainerHighest = Color(0xFFE2E5E8)
val LightAccentContainer = Color(0xFFDCE6F4)
val LightOnAccentContainer = Color(0xFF0E2E5C)

// Status-pill tint. Light mode needs a weaker tint or the pill reads as a filled button.
const val PillTintAlphaDark = 0.16f
const val PillTintAlphaLight = 0.10f
