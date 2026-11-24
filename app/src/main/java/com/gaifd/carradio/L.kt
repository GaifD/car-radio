package com.gaifd.carradio

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import java.util.Locale

/**
 * Тексты на выбранном языке. Работает везде: на экранах, в плеере, уведомлениях, виджете.
 * Язык выбирается в приложении (или берётся системный) — см. Настройки → «Язык».
 */
object L {
    private lateinit var app: Context
    private var cachedTag: String? = null
    private var cached: Context? = null

    fun init(ctx: Context) {
        app = ctx.applicationContext
    }

    /** Выбранный в приложении язык или пустая строка — «как в системе». */
    fun appLanguage(): String = AppCompatDelegate.getApplicationLocales().toLanguageTags().substringBefore(',')

    fun setAppLanguage(tag: String) {
        AppCompatDelegate.setApplicationLocales(androidx.core.os.LocaleListCompat.forLanguageTags(tag))
    }

    fun locale(): Locale {
        val tag = appLanguage()
        if (tag.isNotBlank()) return Locale.forLanguageTag(tag)
        return app.resources.configuration.locales[0] ?: Locale.getDefault()
    }

    @Synchronized
    fun ctx(): Context {
        val tag = appLanguage()
        if (tag.isBlank()) return app
        if (tag != cachedTag || cached == null) {
            val conf = Configuration(app.resources.configuration)
            conf.setLocale(Locale.forLanguageTag(tag))
            cached = app.createConfigurationContext(conf)
            cachedTag = tag
        }
        return cached!!
    }

    fun s(@StringRes id: Int): String = ctx().getString(id)

    fun s(@StringRes id: Int, vararg args: Any?): String = ctx().getString(id, *args.map { it ?: "" }.toTypedArray())
}
