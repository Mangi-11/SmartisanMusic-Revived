package com.smartisan.music.platform.text

import android.icu.text.Transliterator
import android.os.Build
import androidx.annotation.RequiresApi

internal object HanLatinTransliterator {
    fun transliterate(value: String): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Api29.transliterate(value)
        } else {
            value
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private object Api29 {
        private val hanToLatin = runCatching {
            Transliterator.getInstance("Han-Latin; Latin-ASCII")
        }.getOrNull()

        fun transliterate(value: String): String {
            return hanToLatin?.transliterate(value) ?: value
        }
    }
}
