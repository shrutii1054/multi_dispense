package com.retailone.pos.utils

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.LocaleList
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import com.retailone.pos.R
import java.util.Locale

/**
 * Helper for app multilingual support.
 *
 * Persists the user's selected language code in a dedicated SharedPreferences
 * file ("app_prefs", key "selected_language") and applies it to any Context
 * passed in. Defaults to English ("en") when nothing is saved.
 *
 * Supported codes (visible in UI): "en", "pt", "sw"
 * "fr" is intentionally NOT shown in any selector UI yet.
 *
 * This class is purely concerned with locale handling and does NOT touch any
 * existing business logic, network calls, or other SharedPreferences keys.
 */
object LanguageManager {

    const val PREFS_NAME = "app_prefs"
    const val KEY_SELECTED_LANGUAGE = "selected_language"
    const val DEFAULT_LANGUAGE = "en"

    /** Set on the activity intent before locale-driven recreation. */
    const val EXTRA_PENDING_LOCALE_TRANSITION = "pending_locale_transition"

    /** True while dashboard is recreating after in-app language change only. */
    @Volatile
    private var skipNextDashboardConfigRefresh = false

    /**
     * Read the saved language code from SharedPreferences. Defaults to "en"
     * when nothing has been saved yet.
     */
    fun getSavedLanguage(context: Context): String {
        // english only: force English regardless of saved preference or device
        // locale so this build never picks up Portuguese/Swahili/French strings.
        // BaseActivity.attachBaseContext, wrapContext, applyLanguage etc. all
        // funnel through this method, so this single short-circuit forces the
        // whole app into English. To restore multi-language behaviour, delete
        // this block and rely on the original SharedPreferences read below.
        return DEFAULT_LANGUAGE
        // english only: end
        /*
        val prefs: SharedPreferences =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_SELECTED_LANGUAGE, DEFAULT_LANGUAGE)
            ?: DEFAULT_LANGUAGE
        */
    }

    /**
     * Persist the chosen language code and apply it to the running app.
     *
     * Uses AppCompatDelegate.setApplicationLocales (AppCompat 1.6+, all API levels)
     * plus legacy Configuration update for non-AppCompat contexts (printers, etc.).
     *
     * Callers are expected to call activity.recreate() (or restart the
     * activity manually) AFTER this method so the new locale is fully
     * applied to all currently-inflated views.
     */
    fun applyLanguage(context: Context, languageCode: String) {
        // Persist first so wrapContext / future launches see the new value.
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SELECTED_LANGUAGE, languageCode)
            .apply()

        val locale = Locale(languageCode)
        Locale.setDefault(locale)

        // AppCompat 1.6+ backports per-app locales to API 24+ (MPOS terminals).
        AppCompatDelegate.setApplicationLocales(
            LocaleListCompat.forLanguageTags(languageCode)
        )

        // Legacy path for Application / printer / vendor SDK contexts.
        updateConfiguration(context, locale)

        logLocaleDebug(context, "applyLanguage($languageCode)")
    }

    /**
     * Wrap the given (base) Context so the returned Context resolves
     * resources using the saved language. Use this from
     * Activity.attachBaseContext / Application.attachBaseContext.
     */
    fun wrapContext(context: Context): Context {
        val languageCode = getSavedLanguage(context)
        val locale = Locale(languageCode)
        Locale.setDefault(locale)

        val config = Configuration(context.resources.configuration)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val localeList = LocaleList(locale)
            LocaleList.setDefault(localeList)
            config.setLocales(localeList)
            config.setLocale(locale)
            context.createConfigurationContext(config)
        } else {
            @Suppress("DEPRECATION")
            config.locale = locale
            @Suppress("DEPRECATION")
            context.resources.updateConfiguration(
                config,
                context.resources.displayMetrics
            )
            context.createConfigurationContext(config)
        }
    }

    /**
     * Apply the locale to the given context's resources directly. Used by
     * applyLanguage so any Activities already started see the new locale on
     * their next recreate().
     */
    @Suppress("DEPRECATION")
    private fun updateConfiguration(context: Context, locale: Locale) {
        val resources = context.resources
        val config = Configuration(resources.configuration)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val localeList = LocaleList(locale)
            LocaleList.setDefault(localeList)
            config.setLocales(localeList)
            config.setLocale(locale)
        } else {
            config.locale = locale
        }
        resources.updateConfiguration(config, resources.displayMetrics)
    }

    /**
     * Apply a crossfade and matching window background so recreate() does not flash black.
     * Call after [applyLanguage]. AppCompat 1.6+ recreates started activities when locales change.
     */
    fun prepareLocaleTransition(activity: Activity) {
        // Match the screen behind the transition (login is white; dashboard uses light gray).
        val bgRes = if (activity.javaClass.simpleName == "MPOSLoginActivity") {
            R.color.white
        } else {
            R.color.background_color
        }
        val bg = ContextCompat.getColor(activity, bgRes)
        activity.window?.apply {
            setBackgroundDrawable(ColorDrawable(bg))
            decorView.setBackgroundColor(bg)
        }
        activity.intent.putExtra(EXTRA_PENDING_LOCALE_TRANSITION, true)
        val anim = R.anim.language_crossfade_in
        activity.overridePendingTransition(anim, anim)
    }

    fun applyLocaleTransitionOnCreate(activity: Activity) {
        if (!activity.intent.getBooleanExtra(EXTRA_PENDING_LOCALE_TRANSITION, false)) return
        activity.intent.removeExtra(EXTRA_PENDING_LOCALE_TRANSITION)
        val anim = R.anim.language_crossfade_in
        activity.overridePendingTransition(anim, anim)
    }

    /**
     * Completes a locale change on the current activity with a smooth crossfade.
     */
    fun restartActivityForLocale(activity: Activity, languageCode: String) {
        // Locale change does not alter org/localization API payloads — skip redundant fetches on recreate.
        skipNextDashboardConfigRefresh = true
        applyLanguage(activity, languageCode)
        prepareLocaleTransition(activity)
        // AppCompatDelegate.setApplicationLocales() recreates AppCompat activities.
        // Fallback for plain Activity subclasses only (e.g. legacy hardware screens).
        if (activity !is androidx.appcompat.app.AppCompatActivity) {
            activity.window?.decorView?.post { activity.recreate() }
        }
    }

    /**
     * Consumed once by [com.retailone.pos.ui.Activity.MPOSDashboardActivity] after a locale-only recreate.
     * @return true when dashboard should not re-call localization/organisation APIs.
     */
    fun consumeSkipDashboardConfigRefresh(): Boolean {
        if (!skipNextDashboardConfigRefresh) return false
        skipNextDashboardConfigRefresh = false
        return true
    }

    /** Temporary diagnostic — filter logcat with LANG_DEBUG. */
    fun logLocaleDebug(context: Context, source: String) {
        val configLocale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.resources.configuration.locales[0].toLanguageTag()
        } else {
            @Suppress("DEPRECATION")
            context.resources.configuration.locale?.toLanguageTag() ?: "?"
        }
        val appCompatLocale = AppCompatDelegate.getApplicationLocales()
            .get(0)?.toLanguageTag() ?: "none"
        val saved = getSavedLanguage(context)
        Log.d(
            "LANG_DEBUG",
            "$source | saved=$saved | default=${Locale.getDefault().toLanguageTag()} | " +
                "config=$configLocale | appCompat=$appCompatLocale | ctx=${context.javaClass.simpleName}"
        )
    }
}
