package io.github.kyujin0911.pounter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.kyujin0911.pounter.core.ui.theme.PipeTheme
import io.github.kyujin0911.pounter.navigation.PipeNavigation

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { PipeTheme { PipeNavigation() } }
    }
}
