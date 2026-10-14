package com.garagelog.app.ui

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import com.garagelog.app.GarageLogApplication
import com.garagelog.app.di.ServiceLocator

/**
 * The app's dependencies, for a ViewModel factory's `initializer { }` — the pattern Android's
 * ViewModel guide gives for constructor dependencies without a DI framework. Each ViewModel has
 * its own `Factory`, and a screen asks for it with `viewModel(factory = X.Factory)`; they're all
 * scoped to the activity, so every screen asking for the same one gets the same instance.
 */
internal fun CreationExtras.locator(): ServiceLocator = (this[APPLICATION_KEY] as GarageLogApplication).serviceLocator
