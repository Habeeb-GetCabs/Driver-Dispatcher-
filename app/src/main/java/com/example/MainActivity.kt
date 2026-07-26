package com.example

import android.os.Bundle
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
