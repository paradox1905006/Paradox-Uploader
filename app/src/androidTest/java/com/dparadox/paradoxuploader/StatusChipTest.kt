package com.dparadox.paradoxuploader

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dparadox.paradoxuploader.domain.model.AppSettings
import com.dparadox.paradoxuploader.domain.model.UploadStatus
import com.dparadox.paradoxuploader.ui.components.StatusChip
import com.dparadox.paradoxuploader.ui.theme.ParadoxTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StatusChipTest {
    @get:Rule val rule = createComposeRule()

    @Test fun everyStatusHasAVisibleLabel() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        rule.setContent {
            ParadoxTheme(AppSettings(dynamicColor = false)) {
                Column { UploadStatus.entries.forEach { StatusChip(it) } }
            }
        }
        listOf(
            R.string.status_uploading, R.string.status_processing, R.string.status_ready,
            R.string.status_delayed, R.string.status_failed
        ).forEach { rule.onNodeWithText(context.getString(it)).assertIsDisplayed() }
    }
}
