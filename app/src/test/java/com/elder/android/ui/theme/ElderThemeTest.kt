package com.elder.android.ui.theme

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.elder.android.data.db.FontScale
import com.elder.android.di.ServiceLocator
import com.elder.android.testing.ElderRobolectricTestRunner
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(ElderRobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class ElderThemeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var app: Application

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        ServiceLocator.init(app)
    }

    @Test
    fun xlargeFontScale_isAppliedToComposition() {
        var observedFontScale = 1f
        composeRule.setContent {
            ElderTheme(fontScale = FontScale.XLARGE) {
                observedFontScale = LocalDensity.current.fontScale
            }
        }

        composeRule.waitForIdle()
        assertTrue(
            "特大字体设置必须作用于 CompositionLocal fontScale，实际为 $observedFontScale",
            observedFontScale > 1.2f,
        )
    }
}
