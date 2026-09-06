package com.phonas.backup.backup

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaExclusionsTest {

    @Test
    fun `excludes the classic Pictures layout`() {
        assertTrue(MediaExclusions.isExcluded("Pictures/WhatsApp/WhatsApp Images/Sent"))
    }

    @Test
    fun `excludes the legacy root install`() {
        assertTrue(MediaExclusions.isExcluded("WhatsApp/Media/WhatsApp Video/Sent"))
    }

    @Test
    fun `excludes the scoped storage layout`() {
        assertTrue(
            MediaExclusions.isExcluded(
                "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Images/Sent"
            )
        )
    }

    @Test
    fun `excludes WhatsApp Business`() {
        assertTrue(
            MediaExclusions.isExcluded(
                "Android/media/com.whatsapp.w4b/WhatsApp Business/Media/WhatsApp Images/Sent"
            )
        )
    }

    @Test
    fun `excludes folders nested below Sent`() {
        assertTrue(MediaExclusions.isExcluded("Pictures/WhatsApp/WhatsApp Images/Sent/Private"))
    }

    @Test
    fun `excludes case variants`() {
        assertTrue(MediaExclusions.isExcluded("pictures/whatsapp/whatsapp images/sent"))
        assertTrue(MediaExclusions.isExcluded("WHATSAPP/MEDIA/WHATSAPP VIDEO/SENT"))
    }

    @Test
    fun `excludes the other WhatsApp media types`() {
        assertTrue(MediaExclusions.isExcluded("WhatsApp/Media/WhatsApp Documents/Sent"))
        assertTrue(MediaExclusions.isExcluded("WhatsApp/Media/WhatsApp Animated Gifs/Sent"))
    }

    // Received media is the only copy the user has; it must survive.
    @Test
    fun `keeps the received WhatsApp folder itself`() {
        assertFalse(MediaExclusions.isExcluded("Pictures/WhatsApp/WhatsApp Images"))
        assertFalse(MediaExclusions.isExcluded("WhatsApp/Media/WhatsApp Video"))
    }

    @Test
    fun `keeps ordinary media folders`() {
        assertFalse(MediaExclusions.isExcluded("DCIM/Camera"))
        assertFalse(MediaExclusions.isExcluded("Pictures/Screenshots"))
        assertFalse(MediaExclusions.isExcluded("Download"))
    }

    @Test
    fun `keeps a Sent folder whose parent is not a WhatsApp media folder`() {
        assertFalse(MediaExclusions.isExcluded("Documents/Sent"))
        assertFalse(MediaExclusions.isExcluded("Pictures/Telegram/Sent"))
    }

    // "WhatsAppImages" has no space, so startsWith("WhatsApp ") must not fire.
    @Test
    fun `keeps a lookalike folder with no space`() {
        assertFalse(MediaExclusions.isExcluded("Pictures/WhatsAppImages/Sent"))
    }

    @Test
    fun `handles edge shaped paths`() {
        assertFalse(MediaExclusions.isExcluded(""))
        // A single segment has no parent to test against.
        assertFalse(MediaExclusions.isExcluded("Sent"))
        assertTrue(MediaExclusions.isExcluded("/Pictures/WhatsApp/WhatsApp Images/Sent/"))
    }

    // FileScanner and MediaStoreScanner both emit '/', but be tolerant.
    @Test
    fun `handles backslash separators`() {
        assertTrue(MediaExclusions.isExcluded("Pictures\\WhatsApp\\WhatsApp Images\\Sent"))
    }
}
