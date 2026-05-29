package com.retailone.pos

import android.app.Application
import android.content.Context
import android.util.Log
import com.retailone.pos.localstorage.DataStore.LoginSession
import com.retailone.pos.utils.FeatureManager
import com.retailone.pos.utils.LanguageManager
import com.retailone.pos.workers.SyncWorker
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MyApplication : Application() {

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LanguageManager.wrapContext(base))
    }

    override fun onCreate() {
        super.onCreate()

        // Apply saved locale before any activity starts (critical on MPOS API 24–28).
        val savedLang = LanguageManager.getSavedLanguage(this)
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(savedLang))
        LanguageManager.applyLanguage(this, savedLang)
        LanguageManager.logLocaleDebug(this, "MyApplication.onCreate")

        Log.d("MyApplication", "========================================")
        Log.d("MyApplication", "APP STARTED - onCreate() called")
        Log.d("MyApplication", "========================================")

        // Initialize WorkManager & schedule sync
        try {
            val workManager = WorkManager.getInstance(this)
            Log.d("MyApplication", "✅ WorkManager initialized successfully")

            SyncWorker.scheduleSync(this)
            Log.d("MyApplication", "✅ Sync scheduled (instant + every 15 min)")

        } catch (e: Exception) {
            Log.e("MyApplication", "❌ WorkManager Error: ${e.message}")
            e.printStackTrace()
        }

        // Initialize FeatureManager with modules from LoginSession
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val modules = LoginSession.getInstance(this@MyApplication).getModules().first()
                FeatureManager.init(modules)
                Log.d("MyApplication", "✅ FeatureManager initialized successfully")
            } catch (e: Exception) {
                Log.e("MyApplication", "❌ FeatureManager Error: ${e.message}")
                e.printStackTrace()
            }
        }
    }
}