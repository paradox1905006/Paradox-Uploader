package com.dparadox.paradoxuploader

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dparadox.paradoxuploader.domain.model.AppSettings
import com.dparadox.paradoxuploader.domain.model.UploadItem
import com.dparadox.paradoxuploader.domain.model.UploadStatus
import com.dparadox.paradoxuploader.ui.components.FileRow
import com.dparadox.paradoxuploader.ui.components.FileRowActions
import com.dparadox.paradoxuploader.ui.theme.ParadoxTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FileRowTest {
    @get:Rule val rule = createComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun item(status: UploadStatus, url: String? = null) = UploadItem(
        id = 1,
        identifier = "paradox-abcd1234",
        fileName = "holiday photo.jpg",
        sizeBytes = 13_000_000,
        mimeType = "image/jpeg",
        createdAt = 1_700_000_000_000,
        status = status,
        directUrl = url,
        lastCheckedAt = null,
        errorMessage = null
    )

    @Test fun processingFileShowsPreparingAndCheckNow() {
        rule.setContent {
            ParadoxTheme(AppSettings(dynamicColor = false)) {
                FileRow(item(UploadStatus.PROCESSING), selected = false, selecting = false, actions = FileRowActions())
            }
        }
        rule.onNodeWithText("holiday photo.jpg").assertIsDisplayed()
        rule.onNodeWithText(context.getString(R.string.link_preparing)).assertIsDisplayed()
        rule.onNodeWithText(context.getString(R.string.status_processing)).assertIsDisplayed()
        rule.onNodeWithText(context.getString(R.string.check_now)).assertIsDisplayed()
    }

    @Test fun readyFileShowsTheDirectLinkAndSize() {
        val url = "https://archive.org/download/paradox-abcd1234/holiday%20photo.jpg"
        rule.setContent {
            ParadoxTheme(AppSettings(dynamicColor = false)) {
                FileRow(item(UploadStatus.READY, url), selected = false, selecting = false, actions = FileRowActions())
            }
        }
        rule.onNodeWithText(url).assertIsDisplayed()
        rule.onNodeWithText(context.getString(R.string.status_ready)).assertIsDisplayed()
    }

    @Test fun clickAndLongClickAreForwarded() {
        var clicks = 0
        var longClicks = 0
        rule.setContent {
            ParadoxTheme(AppSettings(dynamicColor = false)) {
                FileRow(
                    item(UploadStatus.READY, "https://archive.org/download/x/y"),
                    selected = false,
                    selecting = false,
                    actions = FileRowActions(onClick = { clicks++ }, onLongClick = { longClicks++ })
                )
            }
        }
        rule.onNodeWithText("holiday photo.jpg").performClick()
        rule.onNodeWithText("holiday photo.jpg").performTouchInput { longClick() }
        assertEquals(1, clicks)
        assertEquals(1, longClicks)
    }
}
