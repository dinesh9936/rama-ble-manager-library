package com.rama.ramablemanager

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.rama.ramablemanager.ui.screens.BleTestScreen
import com.rama.ramablemanager.ui.theme.RamaBleManagerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RamaBleManagerTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    // Launch the BLE test UI as the main content, apply scaffold padding
                    Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                        BleTestScreen()
                    }
                }
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    RamaBleManagerTheme {
        Greeting("Android")
    }
}