package com.phonas.backup.backup

/** SMB/NTFS-safe path handling, isolated here so it can be unit-tested without the engine. */
object RemotePath {

    private val ILLEGAL_CHARS = charArrayOf('\\', '/', ':', '*', '?', '"', '<', '>', '|').toSet()
    private val RESERVED_NAMES = buildSet {
        addAll(listOf("CON", "PRN", "AUX", "NUL"))
        for (i in 1..9) { add("COM$i"); add("LPT$i") }
    }
    private const val MAX_COMPONENT_LEN = 255

    /**
     * Make one path segment safe: replace illegal/control chars, strip trailing dots/spaces,
     * escape reserved device names, and cap length while preserving the extension.
     */
    fun sanitizeComponent(raw: String): String {
        val cleaned = buildString {
            for (c in raw) append(if (c.code < 0x20 || c in ILLEGAL_CHARS) '_' else c)
        }.trimEnd(' ', '.')

        val safe = cleaned.ifEmpty { "_" }
        val base = safe.substringBeforeLast('.', safe)
        if (base.uppercase() in RESERVED_NAMES) return "_$safe"

        if (safe.length <= MAX_COMPONENT_LEN) return safe
        val ext = safe.substringAfterLast('.', "")
        return if (ext.isNotEmpty() && ext.length < MAX_COMPONENT_LEN - 1) {
            safe.substring(0, MAX_COMPONENT_LEN - ext.length - 1) + "." + ext
        } else {
            safe.substring(0, MAX_COMPONENT_LEN)
        }
    }
}
