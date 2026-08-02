package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.crash.CrashHandler
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.view.MetroPlayerApp

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    CrashHandler.init(this)
    enableEdgeToEdge()

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
        != PackageManager.PERMISSION_GRANTED) {
        ActivityCompat.requestPermissions(
          this,
          arrayOf(Manifest.permission.POST_NOTIFICATIONS),
          101
        )
      }
    }

    setContent {
      MyApplicationTheme(dynamicColor = false) {
        Surface(
          modifier = Modifier.fillMaxSize(),
          color = Color.Transparent
        ) {
          MetroPlayerApp()
        }
      }
    }
  }
}

