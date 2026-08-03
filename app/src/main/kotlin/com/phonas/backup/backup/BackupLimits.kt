package com.phonas.backup.backup

/** Single source of truth for size thresholds used by dedup and verification. */
object BackupLimits {

    private const val MB = 1024L * 1024L
    private const val GB = 1024L * MB

    /**
     * During duplicate detection (file not in DB, but present on the NAS with matching size),
     * compare SHA-256 only up to this size — beyond it, both a local and a remote full read are
     * too expensive to justify, and a size match is accepted.
     */
    const val DEDUP_HASH_MAX_BYTES = 500L * MB

    /**
     * After upload the full local hash is already computed for free, so verify by re-reading the
     * remote and comparing hashes up to this (larger) size; above it, fall back to size-only.
     */
    const val VERIFY_FULL_HASH_MAX_BYTES = 2L * GB
}
