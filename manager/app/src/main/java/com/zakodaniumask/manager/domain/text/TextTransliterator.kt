package com.zakodaniumask.manager.domain.text

fun interface TextTransliterator {
    fun transliterate(value: String): String
}
