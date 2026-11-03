package com.dparadox.paradoxuploader

import com.dparadox.paradoxuploader.domain.model.UploadStatus
import com.dparadox.paradoxuploader.domain.util.ArchiveUrls
import com.dparadox.paradoxuploader.domain.util.Formatters
import com.dparadox.paradoxuploader.domain.util.IdentifierError
import com.dparadox.paradoxuploader.domain.util.IdentifierGenerator
import com.dparadox.paradoxuploader.domain.util.IdentifierValidator
import com.dparadox.paradoxuploader.domain.util.MetaHeaders
import com.dparadox.paradoxuploader.domain.util.PollingSchedule
import com.dparadox.paradoxuploader.domain.util.RetryPolicy
import com.dparadox.paradoxuploader.domain.util.StatusMachine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class IdentifierValidatorTest {
    @Test fun acceptsLowercaseDigitsDashUnderscore() {
        assertNull(IdentifierValidator.validate("my-item_01"))
        assertTrue(IdentifierValidator.isValid("paradox-abc12345"))
    }

    @Test fun rejectsUppercaseSpacesAndSymbols() {
        assertEquals(IdentifierError.INVALID_CHARS, IdentifierValidator.validate("MyItem"))
        assertEquals(IdentifierError.INVALID_CHARS, IdentifierValidator.validate("my item"))
        assertEquals(IdentifierError.INVALID_CHARS, IdentifierValidator.validate("my.item"))
        assertEquals(IdentifierError.INVALID_CHARS, IdentifierValidator.validate("café"))
    }

    @Test fun rejectsEmptyShortLongAndBadStart() {
        assertEquals(IdentifierError.EMPTY, IdentifierValidator.validate(""))
        assertEquals(IdentifierError.TOO_SHORT, IdentifierValidator.validate("ab"))
        assertEquals(IdentifierError.TOO_LONG, IdentifierValidator.validate("a".repeat(101)))
        assertEquals(IdentifierError.INVALID_START, IdentifierValidator.validate("-abc"))
        assertEquals(IdentifierError.INVALID_START, IdentifierValidator.validate("_abc"))
    }

    @Test fun generatedIdentifiersAreValidAndUnique() {
        val ids = (1..50).map { IdentifierGenerator.newItem() }
        assertTrue(ids.all { it.startsWith("paradox-") && it.length == 16 && IdentifierValidator.isValid(it) })
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(IdentifierGenerator.testItem().startsWith("paradox-test-"))
    }
}

class ArchiveUrlsTest {
    @Test fun buildsPlainUrls() {
        assertEquals("https://archive.org/download/paradox-abc12345/file.txt", ArchiveUrls.downloadUrl("paradox-abc12345", "file.txt"))
        assertEquals("https://archive.org/details/paradox-abc12345", ArchiveUrls.detailsUrl("paradox-abc12345"))
        assertEquals("https://archive.org/metadata/paradox-abc12345", ArchiveUrls.metadataUrl("paradox-abc12345").toString())
        assertEquals("https://s3.us.archive.org/paradox-abc12345/file.txt", ArchiveUrls.uploadUrl("paradox-abc12345", "file.txt").toString())
    }

    @Test fun encodesSpaces() {
        assertEquals("https://archive.org/download/id1/my%20file.txt", ArchiveUrls.downloadUrl("id1", "my file.txt"))
    }

    @Test fun encodesUnicodeAsUtf8() {
        assertEquals("https://archive.org/download/id1/caf%C3%A9.png", ArchiveUrls.downloadUrl("id1", "café.png"))
    }

    @Test fun encodesSpecialCharacters() {
        assertEquals("https://archive.org/download/id1/a%23b%3Fc.txt", ArchiveUrls.downloadUrl("id1", "a#b?c.txt"))
        assertEquals("https://archive.org/download/id1/100%25.txt", ArchiveUrls.downloadUrl("id1", "100%.txt"))
    }

    @Test fun sanitizesPathSeparatorsAndDots() {
        assertEquals("a_b.txt", ArchiveUrls.sanitizeFileName("a/b.txt"))
        assertEquals("a_b.txt", ArchiveUrls.sanitizeFileName("a\\b.txt"))
        assertEquals("file", ArchiveUrls.sanitizeFileName(".."))
        assertEquals("file", ArchiveUrls.sanitizeFileName("   "))
        assertEquals("keep me.txt", ArchiveUrls.sanitizeFileName("  keep me.txt "))
    }

    @Test fun slashInFileNameNeverCreatesExtraSegments() {
        val url = ArchiveUrls.uploadUrl("id1", ArchiveUrls.sanitizeFileName("dir/evil.txt"))
        assertEquals(listOf("id1", "dir_evil.txt"), url.pathSegments)
    }
}

class PollingScheduleTest {
    @Test fun everyTenSecondsInFirstMinute() {
        assertEquals(10_000L, PollingSchedule.nextDelayMs(0))
        assertEquals(10_000L, PollingSchedule.nextDelayMs(59_999))
    }

    @Test fun everyThirtySecondsUpToTenMinutes() {
        assertEquals(30_000L, PollingSchedule.nextDelayMs(60_000))
        assertEquals(30_000L, PollingSchedule.nextDelayMs(599_999))
    }

    @Test fun everyTwoMinutesUpToOneHour() {
        assertEquals(120_000L, PollingSchedule.nextDelayMs(600_000))
        assertEquals(120_000L, PollingSchedule.nextDelayMs(3_599_999))
    }

    @Test fun givesUpAfterOneHour() {
        assertNull(PollingSchedule.nextDelayMs(3_600_000))
        assertNull(PollingSchedule.nextDelayMs(10_000_000))
    }
}

class RetryPolicyTest {
    @Test fun doublesAndCaps() {
        assertEquals(2_000L, RetryPolicy.delayMs(0))
        assertEquals(4_000L, RetryPolicy.delayMs(1))
        assertEquals(8_000L, RetryPolicy.delayMs(2))
        assertEquals(60_000L, RetryPolicy.delayMs(10))
        assertEquals(60_000L, RetryPolicy.delayMs(1000))
    }
}

class StatusMachineTest {
    @Test fun allowsTheDocumentedFlow() {
        assertTrue(StatusMachine.canTransition(UploadStatus.UPLOADING, UploadStatus.PROCESSING))
        assertTrue(StatusMachine.canTransition(UploadStatus.UPLOADING, UploadStatus.FAILED))
        assertTrue(StatusMachine.canTransition(UploadStatus.PROCESSING, UploadStatus.READY))
        assertTrue(StatusMachine.canTransition(UploadStatus.PROCESSING, UploadStatus.DELAYED))
        assertTrue(StatusMachine.canTransition(UploadStatus.DELAYED, UploadStatus.READY))
        assertTrue(StatusMachine.canTransition(UploadStatus.FAILED, UploadStatus.UPLOADING))
    }

    @Test fun rejectsEverythingElse() {
        assertFalse(StatusMachine.canTransition(UploadStatus.UPLOADING, UploadStatus.READY))
        assertFalse(StatusMachine.canTransition(UploadStatus.PROCESSING, UploadStatus.UPLOADING))
        assertFalse(StatusMachine.canTransition(UploadStatus.READY, UploadStatus.PROCESSING))
        assertFalse(StatusMachine.canTransition(UploadStatus.READY, UploadStatus.FAILED))
        assertFalse(StatusMachine.canTransition(UploadStatus.PROCESSING, UploadStatus.PROCESSING))
    }

    @Test fun transitionThrowsOnIllegalMove() {
        assertEquals(UploadStatus.READY, StatusMachine.transition(UploadStatus.PROCESSING, UploadStatus.READY))
        assertThrows(IllegalStateException::class.java) {
            StatusMachine.transition(UploadStatus.READY, UploadStatus.UPLOADING)
        }
    }
}

class FormattersTest {
    @Test fun formatsSizes() {
        assertEquals("0 B", Formatters.size(0, Locale.US))
        assertEquals("512 B", Formatters.size(512, Locale.US))
        assertEquals("1.0 KB", Formatters.size(1024, Locale.US))
        assertEquals("12.4 MB", Formatters.size(13_000_000, Locale.US))
        assertEquals("2.0 GB", Formatters.size(2L * 1024 * 1024 * 1024, Locale.US))
    }

    @Test fun formatsEta() {
        assertEquals("45s", Formatters.eta(45))
        assertEquals("2m 05s", Formatters.eta(125))
        assertEquals("1h 02m", Formatters.eta(3_720))
        assertEquals("—", Formatters.eta(-1))
    }

    @Test fun formatsSpeed() {
        assertEquals("1.5 MB/s", Formatters.speed((1.5 * 1024 * 1024).toLong(), Locale.US))
    }
}

class MetaHeadersTest {
    @Test fun keepsPlainAscii() {
        assertEquals("My holiday photos", MetaHeaders.encode("My holiday photos"))
    }

    @Test fun uriEncodesNonAsciiAndNewlines() {
        assertEquals("uri(caf%C3%A9%20menu)", MetaHeaders.encode("café menu"))
        assertEquals("uri(a%0Ab)", MetaHeaders.encode("a\nb"))
    }
}
