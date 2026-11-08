package com.coral.printagent

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.safeDrawingPadding
import com.coral.printagent.ui.theme.CoralTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.coral.printagent.ui.PrinterScreen
import com.coral.printagent.ui.PrinterViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        setContent {
            CoralTheme {
                Surface {
                    val vm: PrinterViewModel = viewModel()
                    PrinterScreen(vm, Modifier.safeDrawingPadding())
                }
            }
        }
    }
}
