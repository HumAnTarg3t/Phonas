package com.phonas.backup.backup

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaTypesTest {

    @Test
    fun `recognises common image and video extensions`() {
        listOf("IMG.jpg", "photo.heic", "clip.mp4", "movie.mkv", "anim.gif", "cam.insv", "pic.avif")
            .forEach { assertTrue(it, MediaTypes.isSupported(it)) }
    }

    @Test
    fun `is case insensitive`() {
        assertTrue(MediaTypes.isSupported("IMG.JPG"))
        assertTrue(MediaTypes.isSupported("Video.MP4"))
    }

    @Test
    fun `rejects non-media and extensionless files`() {
        listOf("notes.txt", "archive.zip", "app.apk", "README", "song.mp3")
            .forEach { assertFalse(it, MediaTypes.isSupported(it)) }
    }

    @Test
    fun `mime detection covers image and video`() {
        assertTrue(MediaTypes.isMediaMime("image/jpeg"))
        assertTrue(MediaTypes.isMediaMime("video/mp4"))
        assertFalse(MediaTypes.isMediaMime("application/pdf"))
        assertFalse(MediaTypes.isMediaMime(null))
    }
}
