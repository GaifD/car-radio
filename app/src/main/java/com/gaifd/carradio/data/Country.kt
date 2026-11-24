package com.gaifd.carradio.data

import android.content.Context
import android.telephony.TelephonyManager
import com.gaifd.carradio.L
import java.util.Locale

/** Страна для поиска станций: выбранная пользователем или определённая по SIM-карте / региону телефона. */
object Country {
    /** Сверху списка — страны, откуда чаще всего будут пользователи. */
    val PINNED = listOf("UA", "PL", "DE", "CZ", "SK", "RO", "MD", "LT", "LV", "EE", "GB", "US", "CA", "IT", "ES", "FR")

    fun detect(ctx: Context): String {
        val tm = ctx.getSystemService(TelephonyManager::class.java)
        val fromSim = try {
            tm?.simCountryIso?.takeIf { it.length == 2 } ?: tm?.networkCountryIso?.takeIf { it.length == 2 }
        } catch (_: Exception) {
            null
        }
        val code = fromSim ?: ctx.resources.configuration.locales[0]?.country?.takeIf { it.length == 2 } ?: "UA"
        return code.uppercase()
    }

    fun current(ctx: Context): String = Prefs.settings.value.country.ifBlank { detect(ctx) }

    fun name(code: String): String =
        Locale("", code).getDisplayCountry(L.locale()).ifBlank { code }.replaceFirstChar { it.titlecase(L.locale()) }

    /** Все страны: сначала популярные, потом по алфавиту на языке приложения. */
    fun all(): List<String> {
        val rest = Locale.getISOCountries().filter { it !in PINNED }.sortedBy { name(it) }
        return PINNED + rest
    }
}
