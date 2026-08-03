package com.phonas.backup.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemotePathTest {

    @Test
    fun `leaves a normal filename unchanged`() {
        assertEquals("IMG_1234.jpg", RemotePath.sanitizeComponent("IMG_1234.jpg"))
    }

    @Test
    fun `replaces illegal characters`() {
        val out = RemotePath.sanitizeComponent("a:b*c?\"d<e>f|g.jpg")
        listOf(':', '*', '?', '"', '<', '>', '|').forEach { assertFalse(out.contains(it)) }
        assertTrue(out.endsWith(".jpg"))
    }

    @Test
    fun `strips trailing dots and spaces`() {
        assertEquals("name", RemotePath.sanitizeComponent("name.  "))
        assertEquals("name", RemotePath.sanitizeComponent("name "))
    }

    @Test
    fun `escapes reserved device names`() {
        assertEquals("_CON", RemotePath.sanitizeComponent("CON"))
        assertEquals("_NUL.jpg", RemotePath.sanitizeComponent("NUL.jpg"))
    }

    @Test
    fun `caps length while preserving extension`() {
        val long = "a".repeat(300) + ".jpg"
        val out = RemotePath.sanitizeComponent(long)
        assertTrue(out.length <= 255)
        assertTrue(out.endsWith(".jpg"))
    }

    @Test
    fun `empty or all-illegal input becomes a safe placeholder`() {
        assertEquals("_", RemotePath.sanitizeComponent(""))
        assertEquals("_", RemotePath.sanitizeComponent("   "))
    }
}
