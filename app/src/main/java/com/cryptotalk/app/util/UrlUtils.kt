package com.cryptotalk.app.util

import java.util.regex.Pattern

/**
 * UrlUtils finds website links (URLs) hidden inside text messages
 * so the app can make them clickable.
 */
object UrlUtils {
    private val URL_PATTERN = Pattern.compile(
        "(https?://(?:www\\.|(?!www))[a-zA-Z0-9][a-zA-Z0-9-]+[a-zA-Z0-9]\\.[^\\s]{2,}|www\\.[a-zA-Z0-9][a-zA-Z0-9-]+[a-zA-Z0-9]\\.[^\\s]{2,}|https?://(?:www\\.|(?!www))[a-zA-Z0-9]+\\.[^\\s]{2,}|www\\.[a-zA-Z0-9]+\\.[^\\s]{2,})"
    )

    fun extractUrl(text: String): String? {
        val matcher = URL_PATTERN.matcher(text)
        return if (matcher.find()) {
            matcher.group(0)
        } else {
            null
        }
    }
}
