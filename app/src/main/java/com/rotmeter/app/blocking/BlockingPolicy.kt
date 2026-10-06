package com.rotmeter.app.blocking

/** A missing limit, an unselected app, or the master switch being off always allows access. */
object BlockingPolicy {
    fun shouldBlock(enabled: Boolean, selected: Boolean, limitMinutes: Int?, usageMinutes: Long): Boolean =
        enabled && selected && limitMinutes != null && limitMinutes > 0 && usageMinutes >= limitMinutes
}
