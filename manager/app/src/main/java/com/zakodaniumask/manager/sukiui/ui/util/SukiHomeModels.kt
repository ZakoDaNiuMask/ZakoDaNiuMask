package com.zakodaniumask.manager.sukiui.ui.util

enum class HomeLayout(val value: String) {
    CLASSIC("classic"),
    IMMERSIVE("immersive"),
    ;

    companion object {
        fun fromValue(value: String?): HomeLayout =
            entries.firstOrNull { it.value == value } ?: CLASSIC
    }
}

enum class HomeCardImageStyle(val value: String) {
    BANNER("banner"),
    OFF("off"),
    ;

    companion object {
        fun fromValue(value: String?): HomeCardImageStyle =
            if (value == OFF.value) OFF else BANNER
    }
}

enum class HomeStatusAlignment(val value: String) {
    START("start"),
    END("end"),
    ;

    companion object {
        fun fromValue(value: String?): HomeStatusAlignment =
            if (value == END.value) END else START
    }
}

data class HomeCardSticker(
    val path: String = "",
)

data class ZygiskImplementationInfo(
    val name: String = "",
    val version: String = "",
    val state: String = "",
)

fun getSuSFSStatus(): String = "false"
fun getSuSFSVersion(): String = ""
