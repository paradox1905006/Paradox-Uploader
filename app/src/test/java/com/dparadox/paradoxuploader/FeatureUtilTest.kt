package com.dparadox.paradoxuploader

import com.dparadox.paradoxuploader.domain.model.UploadItem
import com.dparadox.paradoxuploader.domain.model.UploadStatus
import com.dparadox.paradoxuploader.domain.util.DuplicateChecker
import com.dparadox.paradoxuploader.domain.util.LinkExport
import com.dparadox.paradoxuploader.domain.util.Licenses
import com.dparadox.paradoxuploader.domain.util.UploadStatsCalculator
import com.dparadox.paradoxuploader.domain.util.VersionCompare
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

private val ZONE: ZoneId = ZoneOffset.UTC

private fun ms(year: Int, month: Int, day: Int, hour: Int = 12): Long =
    LocalDateTime.of(year, month, day, hour, 0).atZone(ZONE).toInstant().toEpochMilli()

private fun item(
    name: String = "photo.jpg",
    size: Long = 1000,
    status: UploadStatus = UploadStatus.READY,
    createdAt: Long = ms(2026, 10, 1),
    folderId: Long? = null,
    id: Long = 1
) = UploadItem(
    id = id,
    identifier = "paradox-abcd1234",
    fileName = name,
    sizeBytes = size,
    mimeType = "image/jpeg",
    createdAt = createdAt,
    status = status,
    directUrl = if (status == UploadStatus.READY) "https://archive.org/download/paradox-abcd1234/$name" else null,
    lastCheckedAt = null,
    errorMessage = null,
    folderId = folderId
)

class DuplicateCheckerTest {
    @Test fun sameNameAndSizeIsADuplicate() {
        assertNotNull(DuplicateChecker.find(listOf(item()), "photo.jpg", 1000))
    }

    @Test fun differentSizeOrNameIsNot() {
        assertNull(DuplicateChecker.find(listOf(item()), "photo.jpg", 1001))
        assertNull(DuplicateChecker.find(listOf(item()), "other.jpg", 1000))
    }

    @Test fun nameMatchIgnoresCaseAndUnsafeCharacters() {
        assertNotNull(DuplicateChecker.find(listOf(item(name = "My_file.txt", size = 5)), "My/file.TXT", 5))
    }

    @Test fun failedUploadsDoNotCount() {
        assertNull(DuplicateChecker.find(listOf(item(status = UploadStatus.FAILED)), "photo.jpg", 1000))
    }

    @Test fun queuedAndProcessingUploadsCount() {
        assertNotNull(DuplicateChecker.find(listOf(item(status = UploadStatus.UPLOADING)), "photo.jpg", 1000))
        assertNotNull(DuplicateChecker.find(listOf(item(status = UploadStatus.PROCESSING)), "photo.jpg", 1000))
    }
}

class LinkExportTest {
    @Test fun textHasOneLinkPerReadyFile() {
        val items = listOf(item("a.jpg", id = 1), item("b.jpg", id = 2), item("c.jpg", status = UploadStatus.PROCESSING, id = 3))
        assertEquals(
            "https://archive.org/download/paradox-abcd1234/a.jpg\nhttps://archive.org/download/paradox-abcd1234/b.jpg",
            LinkExport.text(items)
        )
    }

    @Test fun textIsEmptyWhenNothingIsReady() {
        assertEquals("", LinkExport.text(listOf(item(status = UploadStatus.FAILED))))
    }

    @Test fun csvHasHeaderRowsAndFolderNames() {
        val csv = LinkExport.csv(listOf(item("a.jpg", size = 42, folderId = 3)), mapOf(3L to "Trip"))
        val lines = csv.trimEnd().split("\r\n")
        assertEquals(LinkExport.CSV_HEADER, lines[0])
        assertEquals(2, lines.size)
        assertTrue(lines[1].startsWith("a.jpg,https://archive.org/download/paradox-abcd1234/a.jpg,"))
        assertTrue(lines[1].contains(",Trip,42,"))
    }

    @Test fun csvQuotesCommasAndQuotes() {
        assertEquals("plain", LinkExport.escape("plain"))
        assertEquals("\"a,b\"", LinkExport.escape("a,b"))
        assertEquals("\"say \"\"hi\"\"\"", LinkExport.escape("say \"hi\""))
    }

    @Test fun csvSkipsFilesWithoutALink() {
        val csv = LinkExport.csv(listOf(item(status = UploadStatus.PROCESSING)))
        assertEquals(LinkExport.CSV_HEADER + "\r\n", csv)
    }
}

class UploadStatsCalculatorTest {
    private val now = ms(2026, 10, 23)

    @Test fun countsOnlyFilesThatReachedArchive() {
        val items = listOf(
            item(size = 100, status = UploadStatus.READY, id = 1),
            item(size = 200, status = UploadStatus.PROCESSING, id = 2),
            item(size = 300, status = UploadStatus.DELAYED, id = 3),
            item(size = 400, status = UploadStatus.UPLOADING, id = 4),
            item(size = 500, status = UploadStatus.FAILED, id = 5)
        )
        val stats = UploadStatsCalculator.from(items, now, ZONE)
        assertEquals(3, stats.totalFiles)
        assertEquals(600L, stats.totalBytes)
        assertEquals(1, stats.failedFiles)
    }

    @Test fun splitsTodayAndThisMonth() {
        val items = listOf(
            item(size = 10, createdAt = ms(2026, 10, 23, 8), id = 1),
            item(size = 20, createdAt = ms(2026, 10, 2), id = 2),
            item(size = 40, createdAt = ms(2026, 9, 30), id = 3),
            item(size = 80, createdAt = ms(2025, 10, 23), id = 4)
        )
        val stats = UploadStatsCalculator.from(items, now, ZONE)
        assertEquals(4, stats.totalFiles)
        assertEquals(150L, stats.totalBytes)
        assertEquals(2, stats.monthFiles)
        assertEquals(30L, stats.monthBytes)
        assertEquals(1, stats.todayFiles)
        assertEquals(10L, stats.todayBytes)
    }

    @Test fun emptyHistoryGivesZeros() {
        val stats = UploadStatsCalculator.from(emptyList(), now, ZONE)
        assertEquals(0, stats.totalFiles)
        assertEquals(0L, stats.totalBytes)
    }
}

class VersionCompareTest {
    @Test fun newerVersionsAreDetected() {
        assertTrue(VersionCompare.isNewer("1.3.0", "1.2.0"))
        assertTrue(VersionCompare.isNewer("v1.10.0", "1.9.9"))
        assertTrue(VersionCompare.isNewer("2.0", "1.99.99"))
        assertTrue(VersionCompare.isNewer("1.2.1", "1.2"))
    }

    @Test fun sameOrOlderIsNotNewer() {
        assertFalse(VersionCompare.isNewer("1.2.0", "1.2.0"))
        assertFalse(VersionCompare.isNewer("v1.2", "1.2.0"))
        assertFalse(VersionCompare.isNewer("1.1.9", "1.2.0"))
    }

    @Test fun prefixesAndSuffixesAreIgnored() {
        assertEquals(listOf(1, 4, 0), VersionCompare.parse("V1.4.0-beta+5"))
        assertEquals(0, VersionCompare.compare("1.4.0-rc1", "1.4.0"))
    }

    @Test fun garbageParsesAsZero() {
        assertEquals(listOf(0), VersionCompare.parse("abc"))
        assertFalse(VersionCompare.isNewer("abc", "1.0.0"))
    }
}

class LicensesTest {
    @Test fun knownUrlsHaveLabels() {
        assertEquals("CC BY 4.0", Licenses.labelFor("https://creativecommons.org/licenses/by/4.0/"))
        assertNull(Licenses.labelFor("https://example.com/other"))
        assertNull(Licenses.labelFor(null))
    }

    @Test fun allLicensesAreCreativeCommonsUrls() {
        assertTrue(Licenses.ALL.all { it.url.startsWith("https://creativecommons.org/") && it.url.endsWith("/") })
    }
}
