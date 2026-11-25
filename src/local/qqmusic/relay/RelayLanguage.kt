package local.qqmusic.relay

import android.app.LocaleManager
import android.os.LocaleList
import androidx.annotation.StringRes
import java.util.Locale

internal enum class RelayLanguage(tag: String, @get:StringRes val label: Int) {
    EnglishUS("en-US", R.string.language_english),
    EnglishUK("en-GB", R.string.language_english_uk),
    ChineseSimplified("zh-Hans", R.string.language_chinese),
    ChineseTraditional("zh-Hant", R.string.language_chinese_traditional),
    Japanese("ja", R.string.language_japanese),
    Korean("ko", R.string.language_korean),
    German("de", R.string.language_german),
    ;

    private val locale = Locale.forLanguageTag(tag)

    fun select(manager: LocaleManager) {
        val locales = LocaleList(locale)
        if (manager.applicationLocales != locales) manager.applicationLocales = locales
    }

    companion object {
        private val traditionalChineseRegions = setOf("TW", "HK", "MO")

        fun current(manager: LocaleManager): RelayLanguage {
            val chosen = manager.applicationLocales
            return bestMatch(if (chosen.isEmpty) manager.systemLocales else chosen)
        }

        fun ensureSelection(manager: LocaleManager): RelayLanguage =
            current(manager).also { it.select(manager) }

        fun displayLocale(locales: LocaleList): Locale = bestMatch(locales).locale

        private fun bestMatch(locales: LocaleList): RelayLanguage {
            for (index in 0 until locales.size()) {
                matching(locales[index])?.let { return it }
            }
            return EnglishUS
        }

        private fun matching(locale: Locale): RelayLanguage? = when (locale.language) {
            "en" -> if (locale.country == "GB") EnglishUK else EnglishUS
            "zh" -> if (locale.script == "Hant" ||
                (locale.script.isEmpty() && locale.country in traditionalChineseRegions)) {
                ChineseTraditional
            } else {
                ChineseSimplified
            }
            "ja" -> Japanese
            "ko" -> Korean
            "de" -> German
            else -> null
        }
    }
}
