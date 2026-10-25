package com.homeworkoutcoach.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.homeworkoutcoach.engine.Library

// Placeholder until Phase 3 builds the real screens.
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val exerciseCount = Library.default.exercises.size
        setContent {
            MaterialTheme {
                Scaffold { padding ->
                    Box(Modifier.fillMaxSize().padding(padding)) {
                        Text("${stringResource(R.string.app_name)}: $exerciseCount exercises loaded")
                    }
                }
            }
        }
    }
}
