package com.sagisawa.gbfaccelerator.update

object Versioning {

    fun isNewer(remote: String, current: String): Boolean = compare(remote, current) > 0

    fun stripPrefix(value: String): String {
        val trimmed = value.trim()
        return if (trimmed.startsWith("v", ignoreCase = true)) trimmed.substring(1) else trimmed
    }

    fun compare(a: String, b: String): Int {
        val left = parse(a)
        val right = parse(b)
        val length = maxOf(left.numbers.size, right.numbers.size)
        for (index in 0 until length) {
            val leftPart = left.numbers.getOrElse(index) { 0 }
            val rightPart = right.numbers.getOrElse(index) { 0 }
            if (leftPart != rightPart) {
                return leftPart.compareTo(rightPart)
            }
        }
        return comparePreRelease(left.preRelease, right.preRelease)
    }

    private fun parse(value: String): ParsedVersion {
        var cleaned = stripPrefix(value)
        val buildIndex = cleaned.indexOf('+')
        if (buildIndex >= 0) {
            cleaned = cleaned.substring(0, buildIndex)
        }
        val preReleaseIndex = cleaned.indexOf('-')
        val core = if (preReleaseIndex >= 0) cleaned.substring(0, preReleaseIndex) else cleaned
        val preRelease = if (preReleaseIndex >= 0) cleaned.substring(preReleaseIndex + 1).lowercase() else ""
        val numbers = core.split('.').map { part ->
            part.takeWhile { it.isDigit() }.toIntOrNull() ?: 0
        }
        return ParsedVersion(numbers.ifEmpty { listOf(0) }, preRelease)
    }

    private fun comparePreRelease(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return 1
        if (b.isEmpty()) return -1

        val left = a.split('.')
        val right = b.split('.')
        val length = minOf(left.size, right.size)
        for (index in 0 until length) {
            val leftNumber = left[index].toIntOrNull()
            val rightNumber = right[index].toIntOrNull()
            val result = when {
                leftNumber != null && rightNumber != null -> leftNumber.compareTo(rightNumber)
                leftNumber != null -> -1
                rightNumber != null -> 1
                else -> left[index].compareTo(right[index])
            }
            if (result != 0) {
                return if (result > 0) 1 else -1
            }
        }
        return left.size.compareTo(right.size)
    }

    private data class ParsedVersion(
        val numbers: List<Int>,
        val preRelease: String
    )
}
