package dev.fluentmai.android

import java.net.CookieHandler
import java.net.CookieManager
import java.net.CookiePolicy

object WahlapCookieStore {
    private val manager = CookieManager(null, CookiePolicy.ACCEPT_ALL)

    fun clear() {
        synchronized(manager) {
            manager.cookieStore.removeAll()
        }
    }

    fun <T> withCookieHandler(block: () -> T): T =
        synchronized(manager) {
            val previous = CookieHandler.getDefault()
            CookieHandler.setDefault(manager)
            try {
                block()
            } finally {
                CookieHandler.setDefault(previous)
            }
        }

    fun safeSummary(): String =
        synchronized(manager) {
            val cookies = manager.cookieStore.cookies
            if (cookies.isEmpty()) {
                "count=0"
            } else {
                val names = cookies
                    .map { cookie -> "${cookie.domain.orEmpty()}:${cookie.name}" }
                    .sorted()
                    .take(MAX_SUMMARY_COOKIES)
                val suffix = if (cookies.size > names.size) ",..." else ""
                "count=${cookies.size} names=${names.joinToString("|")}$suffix"
            }
        }

    private const val MAX_SUMMARY_COOKIES = 12
}
