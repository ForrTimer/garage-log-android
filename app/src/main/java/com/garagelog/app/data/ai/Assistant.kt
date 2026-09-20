package com.garagelog.app.data.ai

/**
 * What the assistant is called everywhere the owner can see it.
 *
 * Kept as one constant so the name can't drift across a dozen screens. Everything below the UI
 * keeps its Claude/Anthropic naming, because that's the API this actually talks to — the persona
 * and the vendor are deliberately different things.
 */
const val ASSISTANT_NAME = "Bob"
