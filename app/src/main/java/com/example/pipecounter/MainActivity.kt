package com.example.pipecounter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.pipecounter.ui.PipeNavigation
import com.example.pipecounter.ui.PipeTheme

class MainActivity: ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PipeTheme { PipeNavigation((application as PipeApplication).repository) }
        }
    }
}
