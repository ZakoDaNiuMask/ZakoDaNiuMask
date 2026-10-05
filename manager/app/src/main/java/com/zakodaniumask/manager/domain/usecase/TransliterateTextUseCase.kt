package com.zakodaniumask.manager.domain.usecase

import com.zakodaniumask.manager.domain.text.TextTransliterator

class TransliterateTextUseCase(private val transliterator: TextTransliterator) {
    operator fun invoke(value: String): String = transliterator.transliterate(value)
}
