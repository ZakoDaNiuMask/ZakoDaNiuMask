package com.zakodaniumask.manager.data.detection

/** Severity of a detector's result, ordered from worst to best via [severityRank]. */
enum class DetectorStatus { DANGER, ERROR, SUPPORT, INFO, CLEAR, UNKNOWN }

fun DetectorStatus.severityRank(): Int = when (this) {
    DetectorStatus.DANGER -> 0
    DetectorStatus.ERROR -> 1
    DetectorStatus.SUPPORT -> 2
    DetectorStatus.INFO -> 3
    DetectorStatus.UNKNOWN -> 4
    DetectorStatus.CLEAR -> 5
}
