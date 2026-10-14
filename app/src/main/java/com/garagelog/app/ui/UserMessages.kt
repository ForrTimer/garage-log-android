package com.garagelog.app.ui

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * The app's one channel for short confirmations and errors ("Backup exported.", "Couldn't join:
 * …"). Every ViewModel posts here and GarageLogApp shows them, so splitting the ViewModels didn't
 * mean a toast collector per screen.
 */
class UserMessages {
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    fun post(message: String) {
        _messages.tryEmit(message)
    }
}
