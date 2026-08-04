package com.android.car.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.android.car.settings.ui.theme.CarSettingTheme
import timber.log.Timber

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Timber.d("onCreate()")
        setContent {
            CarSettingTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    CarSettingScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        Timber.d("onStart()")
    }

    override fun onRestart() {
        super.onRestart()
        Timber.d("onRestart()")
    }

    override fun onResume() {
        super.onResume()
        Timber.d("onResume()")
    }

    override fun onPause() {
        Timber.d("onPause()")
        super.onPause()
    }

    override fun onStop() {
        Timber.d("onStop()")
        super.onStop()
    }

    override fun onDestroy() {
        Timber.d("onDestroy()")
        super.onDestroy()
    }
}

@Composable
fun CarSettingScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = "CarSetting")
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    CarSettingTheme {
        CarSettingScreen()
    }
}
