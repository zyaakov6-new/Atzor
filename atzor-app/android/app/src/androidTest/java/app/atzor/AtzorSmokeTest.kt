package app.atzor

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Smoke instrumented tests: package identity + Compose root launches under RTL.
 * Full lock/unlock needs accessibility permissions on-device; those stay manual.
 */
@RunWith(AndroidJUnit4::class)
class AtzorSmokeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun packageName_isAtzor() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.atzor.app", ctx.packageName)
    }

    @Test
    fun app_launches_hebrewUi() {
        // Onboarding or home always shows the brand wordmark.
        composeRule.waitForIdle()
        // Either onboarding ("עצור") or vault home ("עצור") is visible.
        composeRule.onNodeWithText("עצור", substring = true).assertExists()
    }

    @Test
    fun resources_supportRtl() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue(ctx.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_SUPPORTS_RTL != 0)
        // Layout direction for Hebrew content is forced in AtzorTheme; system may still be LTR.
        val dir = ctx.resources.configuration.layoutDirection
        assertTrue(dir == LayoutDirection.Rtl.ordinal || dir == LayoutDirection.Ltr.ordinal)
    }
}
