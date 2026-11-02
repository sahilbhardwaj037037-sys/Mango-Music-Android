package com.mangomusic

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mangomusic.ui.MangoScreen
import com.mangomusic.ui.SplashScreen

class MainActivity: ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: com.mangomusic.ui.MusicViewModel = viewModel()
            androidx.compose.runtime.LaunchedEffect(intent) {
                if (intent?.getBooleanExtra("action_show_update", false) == true) {
                    vm.showUpdateDialog()
                }
            }
            var showSplash by rememberSaveable { mutableStateOf(true) }
            Box(Modifier.fillMaxSize()) {
                MangoScreen(vm)
                if (showSplash) {
                    SplashScreen(onSplashFinished = { showSplash = false })
                }
            }
        }
    }
}
