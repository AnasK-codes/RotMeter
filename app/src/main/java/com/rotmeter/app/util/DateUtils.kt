package com.rotmeter.app.util

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

object DateUtils {
    val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US)

    fun todayString(): String = LocalDate.now().format(formatter)

    fun daysAgo(n: Int): String = LocalDate.now().minusDays(n.toLong()).format(formatter)
}
