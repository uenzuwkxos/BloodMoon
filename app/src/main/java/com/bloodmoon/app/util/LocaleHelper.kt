package com.bloodmoon.app.util

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import java.util.Locale

/**
 * In-app language switching, independent of the phone's system language.
 *
 * The standard, reliable way to do this on Android is to wrap the Context returned to the
 * Activity in attachBaseContext() with a Configuration carrying the chosen Locale - simply
 * calling createConfigurationContext() and discarding the result (as the old code did) changes
 * nothing, because the Activity keeps using its original (unwrapped) resources.
 *
 * attachBaseContext() runs before onCreate() / before the ViewModel or DataStore are available,
 * so the persisted language code is mirrored into a tiny synchronous SharedPreferences file
 * (see persist/readPersisted below) purely so it can be read instantly at that point - the
 * DataStore-backed AppSettings remains the single source of truth for everything else.
 */
object LocaleHelper {

    private const val PREFS_NAME = "blood_moon_locale_prefs"
    private const val KEY_LANGUAGE_CODE = "language_code"
    const val SYSTEM_DEFAULT = "system"

    fun wrap(context: Context, languageCode: String): Context {
        if (languageCode == SYSTEM_DEFAULT || languageCode.isBlank()) {
            return context
        }

        val locale = localeFor(languageCode)
        val config = Configuration(context.resources.configuration)

        // No SDK_INT check: setLocales/LocaleList exists since API 24 (N), below this app's minSdk
        // 28 - always available. The fallback branch with Configuration.locale (deprecated since
        // API 24) would never be reached anyway.
        config.setLocales(LocaleList(locale))

        return context.createConfigurationContext(config)
    }

    private fun localeFor(languageCode: String): Locale {
        return when (languageCode) {
            "pt" -> Locale("pt", "BR")
            // Simplified Chinese: the resource folder is values-zh.
            "zh" -> Locale.SIMPLIFIED_CHINESE
            // Indonesian: Android uses the legacy code "in" (folder values-in); Locale("id") converts it.
            else -> Locale(languageCode)
        }
    }

    fun persist(context: Context, languageCode: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE_CODE, languageCode)
            .apply()
    }

    fun readPersisted(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE_CODE, SYSTEM_DEFAULT) ?: SYSTEM_DEFAULT
    }
}
