// 对应 prd.md §3.1.5 老人端主屏
// PR 1：单入口，仅渲染主屏空态（不接后端，不调 SDK）
package com.elder.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.elder.android.screen.HomeScreen
import com.elder.android.design.tokens.BrandColor
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .background(BrandColor.CardWhite),
                color = BrandColor.CardWhite,
            ) {
                HomeScreen()
            }
        }
    }
}
