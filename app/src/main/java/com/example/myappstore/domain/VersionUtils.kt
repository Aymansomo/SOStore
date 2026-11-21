package com.example.myappstore.domain

/**
 * Version comparison for Android `versionName` values.
 *
 * Handles:
 *  - an optional leading `v` (`v1.2.0`)
 *  - numeric segments of any length (`1.2.0` vs `1.1.9`, `1` == `1.0.0`)
 *  - build metadata (`1.2.0+42`, ignored)
 *  - pre-releases (`1.2.0-beta.2` < `1.2.0`)
 */
object VersionUtils {

    /** Tag prefixes used by the app repositories, e.g. `android-v1.0.0` or `mobile-v0.1.9`. */
    private val TAG_PREFIX = Regex("^(?:(?:android|mobile|store)\\s*-\\s*)?[vV]")

    /** Strips an optional tag prefix (`v`, `android-v`, `mobile-v`) and surrounding whitespace. */
    fun normalize(raw: String?): String =
        raw.orEmpty().trim().replace(TAG_PREFIX, "").trim()

    /** Returns a positive number if [left] is newer, negative if older, 0 if equivalent. */
    fun compare(left: String?, right: String?): Int {
        val a = parse(left)
        val b = parse(right)

        val size = maxOf(a.numbers.size, b.numbers.size)
        for (index in 0 until size) {
            val x = a.numbers.getOrElse(index) { 0L }
            val y = b.numbers.getOrElse(index) { 0L }
            if (x != y) return if (x > y) 1 else -1
        }

        val preA = a.preRelease
        val preB = b.preRelease
        return when {
            preA == null && preB == null -> 0
            preA == null -> 1
            preB == null -> -1
            else -> comparePreRelease(preA, preB)
        }
    }

    fun isNewer(candidate: String?, installed: String?): Boolean =
        compare(candidate, installed) > 0

    private data class ParsedVersion(
        val numbers: List<Long>,
        val preRelease: String?
    )

    private fun parse(raw: String?): ParsedVersion {
        val normalized = normalize(raw)
        if (normalized.isEmpty()) return ParsedVersion(listOf(0L), null)

        val withoutBuild = normalized.substringBefore('+')
        val main = withoutBuild.substringBefore('-')
        val preRelease = withoutBuild.substringAfter('-', "").ifBlank { null }

        val numbers = main
            .split('.', '_')
            .map { part -> part.takeWhile { it.isDigit() }.toLongOrNull() ?: 0L }
            .ifEmpty { listOf(0L) }

        return ParsedVersion(numbers, preRelease)
    }

    private fun comparePreRelease(left: String, right: String): Int {
        val a = left.split('.')
        val b = right.split('.')
        val size = maxOf(a.size, b.size)

        for (index in 0 until size) {
            val x = a.getOrElse(index) { "" }
            val y = b.getOrElse(index) { "" }
            if (x == y) continue
            if (x.isEmpty()) return -1
            if (y.isEmpty()) return 1

            val xNumber = x.toLongOrNull()
            val yNumber = y.toLongOrNull()
            if (xNumber != null && yNumber != null) return if (xNumber > yNumber) 1 else -1
            if (xNumber != null) return -1
            if (yNumber != null) return 1

            return if (x.compareTo(y, ignoreCase = true) > 0) 1 else -1
        }
        return 0
    }
}
