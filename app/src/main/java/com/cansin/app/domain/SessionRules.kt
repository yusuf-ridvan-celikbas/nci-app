package com.cansin.app.domain

object SessionRules {
    const val DEFAULT_INACTIVITY_MS = 6 * 60 * 60 * 1000L

    fun isStale(lastActivityAt: Long, now: Long, timeoutMs: Long = DEFAULT_INACTIVITY_MS): Boolean =
        now - lastActivityAt >= timeoutMs
}
