package com.garagelog.app.ui.theme

import androidx.compose.ui.graphics.Color

// Redesign palette. Warm graphite (dark) / warm paper (light).
// Replaces the navy set ported from the PWA. Rust is reserved for alarm states only —
// primary actions are bone-on-dark / ink-on-light, never the alarm color.

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
val DarkAlarm = Color(0xFFE4572E) // rust: open counts, overdue, active-tab marker
val DarkAlarmText = Color(0xFFF07A54) // rust legible as small text on panel
val DarkWarn = Color(0xFFE8A33D)
val DarkOk = Color(0xFF7BC47F)
val DarkInverse = Color(0xFFF2EEEB) // primary button fill
val DarkOnInverse = Color(0xFF17110F)

// ---- Light ---------------------------------------------------------------
val LightBg = Color(0xFFEDEAE6)
val LightPanel = Color(0xFFF8F6F3)
val LightRule = Color(0xFFDCD6CE)
val LightEdge = Color(0xFFC6BFB6)
val LightChrome = Color(0xFFCCC2B4)
val LightChromeEdge = Color(0xFFA79B8B)
val LightText = Color(0xFF191715)
val LightTextDim = Color(0xFF4F4842)
val LightTextMuted = Color(0xFF635C55)
val LightAlarm = Color(0xFFC63F17)
val LightAlarmText = Color(0xFFA8330F)
val LightWarn = Color(0xFF8A5A08)
val LightOk = Color(0xFF316B3B)
val LightInverse = Color(0xFF191715)
val LightOnInverse = Color(0xFFF8F6F3)

// Status-pill tint. Light mode needs a weaker tint or the pill reads as a filled button.
const val PillTintAlphaDark = 0.16f
const val PillTintAlphaLight = 0.10f
