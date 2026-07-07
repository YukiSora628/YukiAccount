package com.yukisora.yukiaccount

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.yukisora.yukiaccount.ui.YukiAccountApp
import com.yukisora.yukiaccount.ui.theme.YukiAccountTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            YukiAccountTheme {
                YukiAccountApp()
            }
        }
    }
}
