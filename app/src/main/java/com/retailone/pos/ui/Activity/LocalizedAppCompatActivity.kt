package com.retailone.pos.ui.Activity

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.retailone.pos.utils.LanguageManager

/**
 * Ensures every screen resolves strings using the app-selected locale.
 * Required on MPOS terminals (API 24–28) where per-app locales are not applied
 * unless [LanguageManager.wrapContext] is used in [attachBaseContext].
 */
open class LocalizedAppCompatActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageManager.wrapContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        LanguageManager.applyLocaleTransitionOnCreate(this)
        super.onCreate(savedInstanceState)
        LanguageManager.logLocaleDebug(this, "${javaClass.simpleName}.onCreate")
    }
}
