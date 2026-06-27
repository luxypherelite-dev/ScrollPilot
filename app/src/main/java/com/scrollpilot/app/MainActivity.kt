package com.scrollpilot.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.scrollpilot.app.ui.navigation.AppNavigation
import com.scrollpilot.app.ui.theme.ScrollPilotTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ScrollPilotTheme {
                AppNavigation()
            }
        }
    }
}
