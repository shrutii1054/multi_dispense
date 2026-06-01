package com.retailone.pos.localstorage.SharedPreference

import android.content.Context
import android.content.SharedPreferences

/**
 * Tracks the LAST store_id that successfully logged in ONLINE on this device.
 *
 * This drives the "same store only" offline policy:
 *   1. When an online login succeeds:
 *        - If the new store_id differs from the saved one → we wipe all offline
 *          data for the previous store (see MPOSLoginActivity.resetAllOfflineDataForStoreSwitch)
 *          and save the new store_id here.
 *        - If the new store_id matches → we keep everything.
 *   2. When the user tries to log in OFFLINE:
 *        - The UserEntity they match against must have storeId == saved store_id.
 *        - Otherwise, we refuse the login and tell them to "Login in online mode first".
 *
 * This file is SEPARATE from MyPrefs (which is cleared on logout) and
 * InvoiceIdPrefs, so the "last online store" survives logout and crashes.
 */
class ActiveStoreHelper(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(
            PREF_NAME,
            Context.MODE_PRIVATE
        )

    companion object {
        private const val PREF_NAME = "ActiveStorePrefs"
        private const val KEY_LAST_STORE_ID = "last_online_store_id"
        private const val KEY_LAST_USER_EMAIL = "last_online_user_email"
    }

    /** Call ONLY after a successful ONLINE login. */
    fun saveActiveStore(storeId: String?, userEmail: String?) {
        if (storeId.isNullOrBlank()) return
        prefs.edit()
            .putString(KEY_LAST_STORE_ID, storeId)
            .putString(KEY_LAST_USER_EMAIL, userEmail ?: "")
            .apply()
    }

    /** @return the store_id of the last successful online login, or null. */
    fun getActiveStoreId(): String? {
        val v = prefs.getString(KEY_LAST_STORE_ID, null)
        return if (v.isNullOrBlank()) null else v
    }

    /** @return true if there is NO saved active store yet (first-ever login on device). */
    fun isEmpty(): Boolean = getActiveStoreId().isNullOrBlank()

    /**
     * Compare the incoming store_id with the saved one.
     * @return true if the stores are the same (or we have nothing saved yet).
     */
    fun isSameStore(incomingStoreId: String?): Boolean {
        val saved = getActiveStoreId() ?: return true // first login — treat as "same"
        return !incomingStoreId.isNullOrBlank() && saved == incomingStoreId
    }

    /**
     * NEVER called during logout (we deliberately want this to survive).
     * Only call this if the user wants to fully reset the device/app.
     */
    fun clear() {
        prefs.edit().clear().apply()
    }
}
