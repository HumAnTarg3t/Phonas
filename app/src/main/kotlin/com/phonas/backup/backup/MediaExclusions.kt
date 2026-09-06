package com.phonas.backup.backup

/**
 * Folders whose contents are derivative copies of media already on the device, and so are never
 * worth backing up.
 *
 * This exists because content hashing cannot catch them. WhatsApp *recompresses* a photo when you
 * send it, so the copy under "WhatsApp Images/Sent" is byte-different from the DCIM original that
 * produced it — same picture, different SHA-256. Only a path rule can suppress it.
 *
 * Received media is deliberately NOT excluded: for a photo someone sent you, the WhatsApp copy is
 * usually the only one you have.
 */
object MediaExclusions {

    private const val SENT = "Sent"
    private const val WHATSAPP_PREFIX = "WhatsApp "

    /**
     * True when [relativePath] is, or sits beneath, a WhatsApp "Sent" folder.
     *
     * Matched as an adjacent segment pair — a segment named "Sent" whose parent starts with
     * "WhatsApp " — rather than by enumerating path prefixes. That one rule covers every install
     * layout without knowing any of them:
     *
     *  - `Pictures/WhatsApp/WhatsApp Images/Sent`
     *  - `WhatsApp/Media/WhatsApp Video/Sent`                          (legacy root install)
     *  - `Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Images/Sent`   (scoped storage)
     *  - `Android/media/com.whatsapp.w4b/WhatsApp Business/Media/WhatsApp Images/Sent`
     *
     * and picks up WhatsApp Documents/Audio/Animated Gifs "Sent" folders for free. Being
     * prefix-independent is also what makes it behave identically for all three scanners, whose
     * relativePath values are rooted differently — MediaStore and the filesystem walk are relative
     * to the volume, while FileScanner's are relative to the granted SAF tree.
     *
     * Every adjacent pair is checked, not just the last two, so `.../WhatsApp Images/Sent/Private`
     * is excluded as well.
     */
    fun isExcluded(relativePath: String): Boolean {
        val segments = relativePath.split('/', '\\').filter { it.isNotEmpty() }
        for (i in 1 until segments.size) {
            if (segments[i].equals(SENT, ignoreCase = true) &&
                segments[i - 1].startsWith(WHATSAPP_PREFIX, ignoreCase = true)
            ) {
                return true
            }
        }
        return false
    }
}
