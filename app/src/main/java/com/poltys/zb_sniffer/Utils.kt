package com.poltys.zb_sniffer

import java.time.ZoneId
import java.time.format.DateTimeFormatter

val timeHourMinutesFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())

fun Float.format(digits: Int) = "%.${digits}f".format(this)