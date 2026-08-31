package com.garagelog.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as GarageLogApplication
        setContent {
            GarageLogRoot(app)
        }
    }
}

@Composable
private fun GarageLogRoot(app: GarageLogApplication) {
    val viewModel: GarageLogViewModel = viewModel(factory = GarageLogViewModelFactory(app.serviceLocator))
    GarageLogTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            GarageLogApp(viewModel = viewModel)
        }
    }
}
