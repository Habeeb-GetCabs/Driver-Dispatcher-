#!/bin/bash
sed -i 's/import androidx.compose.ui.Modifier/import androidx.compose.ui.Modifier\nimport androidx.compose.runtime.getValue\nimport androidx.lifecycle.compose.collectAsStateWithLifecycle\nimport com.example.ui.components.UpdateDialog\nimport com.example.updater.AppUpdater/' app/src/main/java/com/example/MainActivity.kt
sed -i 's/enableEdgeToEdge()/enableEdgeToEdge()\n        AppUpdater.checkForUpdates()/' app/src/main/java/com/example/MainActivity.kt
sed -i 's/setContent {/setContent {\n            val updateInfo by AppUpdater.updateInfo.collectAsStateWithLifecycle()/' app/src/main/java/com/example/MainActivity.kt
sed -i 's/dispatchViewModel = dispatchViewModel\n                    )/dispatchViewModel = dispatchViewModel\n                    )\n\n                    if (updateInfo.updateAvailable) {\n                        UpdateDialog(updateInfo = updateInfo)\n                    }/' app/src/main/java/com/example/MainActivity.kt
