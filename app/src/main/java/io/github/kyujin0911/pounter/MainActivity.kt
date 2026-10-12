package io.github.kyujin0911.pounter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.kyujin0911.pounter.ui.PipeNavigation
import io.github.kyujin0911.pounter.ui.PipeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { PipeTheme { PipeNavigation((application as PipeApplication).repository) } }
    }
}
