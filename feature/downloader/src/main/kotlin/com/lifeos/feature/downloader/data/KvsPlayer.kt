package com.lifeos.feature.downloader.data

/**
 * kt_player ("KVS") link decoder. ThisVid and a long tail of tube sites hand
 * out `function/0/https://.../<scrambled>` URLs plus a `license_code`; the
 * player unscrambles one path segment client-side before playing.
 *
 * Reimplemented here so those pages resolve fully on-device, with no external
 * extractor binary involved.
 */
internal object KvsPlayer {

    /** Turns a license code into the digit sequence the shuffle is driven by. */
    fun licenseToken(license: String): List<Int> {
        val modified = license.replace("$", "").replace("0", "1")
        if (modified.length < 2) return emptyList()
        val center = modified.length / 2
        val front = modified.substring(0, center + 1).toLongOrNull() ?: return emptyList()
        val back = modified.substring(center).toLongOrNull() ?: return emptyList()
        val shuffled = (4 * kotlin.math.abs(front - back)).toString()
        val trimmed = shuffled.take(center + 1)
        return trimmed.map { char -> char.digitToIntOrNull() ?: 0 }
    }

    /**
     * @param url a `function/0/<real url>` style link from the page's flashvars.
     * @return the playable URL, or null when the input is not a KVS link.
     */
    fun decode(url: String, licenseCode: String): String? {
        if (!url.contains("/get_file/") && !url.startsWith("function/0/")) return null
        val real = url.removePrefix("function/0/")
        if (licenseCode.isBlank()) return real
        val parts = real.split('/').toMutableList()
        // The scrambled segment is the one after the /get_file/ marker.
        val index = parts.indexOf("get_file").let { if (it >= 0) it + 1 else 5 }
        val segment = parts.getOrNull(index) ?: return real
        if (segment.length < 32) return real
        val token = licenseToken(licenseCode)
        if (token.isEmpty()) return real

        var magic = segment.take(32)
        for (o in 31 downTo 0) {
            val sum = token.drop(o).sum()
            val l = (o + sum) % 32
            val builder = StringBuilder(32)
            for (i in 0 until 32) {
                builder.append(
                    when (i) {
                        o -> magic[l]
                        l -> magic[o]
                        else -> magic[i]
                    },
                )
            }
            magic = builder.toString()
        }
        parts[index] = magic + segment.substring(32)
        return parts.joinToString("/")
    }
}
