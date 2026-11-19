package com.morad.housekeepermini

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.morad.housekeepermini.ui.HousekeeperApp
import com.morad.housekeepermini.ui.theme.HousekeeperMiniTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HousekeeperMiniTheme {
                HousekeeperApp()
            }
        }
    }
}
