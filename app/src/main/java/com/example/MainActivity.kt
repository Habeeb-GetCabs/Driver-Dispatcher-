package com.example

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.example.ui.navigation.GetTaxiNavGraph
import com.example.ui.theme.GetTaxiTheme
import com.example.viewmodel.DispatchViewModel
import com.example.viewmodel.MeterViewModel
import com.example.viewmodel.SettingsViewModel

class MainActivity : ComponentActivity() {

    private val settingsViewModel: SettingsViewModel by viewModels()
    private val meterViewModel: MeterViewModel by viewModels()
    private val dispatchViewModel: DispatchViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Enable Activity to show over lockscreen and wake up phone screen for incoming full-screen dispatch popup
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }

        setContent {
            GetTaxiTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    GetTaxiNavGraph(
                        meterViewModel = meterViewModel,
                        settingsViewModel = settingsViewModel,
                        dispatchViewModel = dispatchViewModel
                    )
                }
            }
        }
    }
}
