package com.garagelog.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.garagelog.app.ui.GarageLogViewModel
import com.garagelog.app.ui.GarageLogViewModelFactory
import com.garagelog.app.ui.GarageLogApp
import com.garagelog.app.ui.theme.GarageLogTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Must be called before super.onCreate() / setContent.
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)

        // Hold the splash only while the first Room read is in flight, so the app
        // never sits on a branded screen longer than it needs to.
        var ready = false
        splash.setKeepOnScreenCondition { !ready }

        enableEdgeToEdge()
        val app = application as GarageLogApplication
        setContent {
            GarageLogRoot(app, onReady = { ready = true })
        }
    }
}

@Composable
private fun GarageLogRoot(app: GarageLogApplication, onReady: () -> Unit) {
    val viewModel: GarageLogViewModel = viewModel(factory = GarageLogViewModelFactory(app.serviceLocator))
    // GarageLogTheme now reads isSystemInDarkTheme() by default — no argument needed.
    GarageLogTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            GarageLogApp(viewModel = viewModel)
            onReady()
        }
    }
}
