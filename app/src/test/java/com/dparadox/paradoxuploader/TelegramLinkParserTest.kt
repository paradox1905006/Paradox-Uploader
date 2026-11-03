package com.dparadox.paradoxuploader

import com.dparadox.paradoxuploader.domain.telegram.TelegramException
import com.dparadox.paradoxuploader.domain.telegram.TelegramLinkParser
import com.dparadox.paradoxuploader.domain.telegram.TgError
import com.dparadox.paradoxuploader.domain.telegram.TgLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class TelegramLinkParserTest {

    @Test fun publicLink() {
        val l = TelegramLinkParser.parse("https://t.me/some_channel/123") as TgLink.Public
        assertEquals("some_channel", l.username)
        assertEquals(123L, l.messageId)
    }

    @Test fun privateLink() {
        val l = TelegramLinkParser.parse("https://t.me/c/1234567890/42") as TgLink.Private
        assertEquals(1234567890L, l.channelId)
        assertEquals(42L, l.messageId)
    }

    @Test fun topicLinkUsesLastSegmentAsMessage() {
        val l = TelegramLinkParser.parse("t.me/c/1234567890/7/42?single") as TgLink.Private
        assertEquals(42L, l.messageId)
    }

    @Test fun alternateHostsAndTgScheme() {
        assertTrue(TelegramLinkParser.parse("http://telegram.me/abcde/9") is TgLink.Public)
        assertTrue(TelegramLinkParser.parse("tg://resolve?domain=abcde&post=9") is TgLink.Public)
        assertTrue(TelegramLinkParser.parse("tg://privatepost?channel=123&post=9") is TgLink.Private)
    }

    @Test fun rejectsBadInput() {
        assertError("", TgError.EmptyLink)
        assertError("   ", TgError.EmptyLink)
        assertError("https://example.com/abcde/1", TgError.InvalidLink)
        assertError("https://t.me/abcde", TgError.InvalidLink)
        assertError("https://t.me/abcde/notanumber", TgError.InvalidLink)
        assertError("https://t.me/+AbCdEf123", TgError.InviteLink)
        assertError("https://t.me/joinchat/AbCdEf", TgError.InviteLink)
    }

    private fun assertError(input: String, expected: TgError) {
        try {
            TelegramLinkParser.parse(input)
            fail("Expected $expected for '$input'")
        } catch (e: TelegramException) {
            assertEquals(expected, e.error)
        }
    }
}
