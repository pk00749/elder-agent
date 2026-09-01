// PR 4 入口：Compose 全屏 Surface + AppNavGraph
package com.elder.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.elder.android.design.tokens.BrandColor
import com.elder.android.nav.AppNavGraph

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
                AppNavGraph()
            }
        }
    }
}
