package com.retailone.pos.localstorage.SharedPreference

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.retailone.pos.models.CommonModel.StockRequsition.SearchResData
import kotlin.math.roundToInt

class SharedPrefHelper(context: Context) {

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)

    // ✅ Separate prefs file for invoice IDs — survives logout so offline
    //    invoice numbering continues correctly for each user.
    private val invoicePrefs: SharedPreferences =
        context.getSharedPreferences("InvoiceIdPrefs", Context.MODE_PRIVATE)

    private val gson = Gson()
    private val KEY_FINAL_EXPOSURE = "final_exposure_value"

    fun setFinalExposure(value: Double) {
        sharedPreferences.edit().putInt(KEY_FINAL_EXPOSURE, value.roundToInt()).apply()
    }

    fun getFinalExposure(): Int {
        return sharedPreferences.getInt(KEY_FINAL_EXPOSURE, 0)
    }


    // Save the list to SharedPreferences
    fun saveSearchResultsList(searchResultsList: List<SearchResData>) {
        val json = gson.toJson(searchResultsList)
        sharedPreferences.edit().putString("searchResultsList", json).apply()
    }

    // Retrieve the list from SharedPreferences
    fun getSearchResultsList(): List<SearchResData> {
        val json = sharedPreferences.getString("searchResultsList", "")
        return if (json.isNullOrEmpty()) {
            emptyList()
        } else {
            gson.fromJson(json, object : TypeToken<List<SearchResData>>() {}.type)
        }
    }

    //✅ Step 1: Add Method in `SharedPrefHelper.kt`
/*
    fun getTotalCartValue(): Double {
        val itemList = getSearchResultsList()
        var total = 0.0
        for (item in itemList) {
            val price = item.price?.toDoubleOrNull() ?: 0.0
            val qty = item.cart_quantity?.toIntOrNull() ?: 0
            total += price * qty
        }
        return total
    }*/
    fun getTotalCartValue(): Double {
        val itemList = getSearchResultsList()
        var total = 0.0
        for (item in itemList) {
            val price = item.price ?: 0.0
            val qty = item.cart_quantity.toIntOrNull() ?: 0
            val pack = item.no_of_packs

            if (qty > 0 && pack > 0) {
                total += price * qty * pack
            }
            //total += price * qty
        }
        return total
    }




    fun saveSearchItem(newItem: SearchResData) {
        val existingList = getSearchResultsList().toMutableList()

        // Check if the item with the same product_id and distribution_pack_id already exists
        val itemExists = existingList.any {
            it.product_id == newItem.product_id &&
                    it.distribution_pack_id == newItem.distribution_pack_id
        }

        if (!itemExists) {
            // If the item doesn't exist, add it to the list
            existingList.add(newItem)
            val json = gson.toJson(existingList)
            sharedPreferences.edit().putString("searchResultsList", json).apply()
        }
    }

    // Update the quantity of an item based on product_id and distribution_pack_id
    fun updateQuantity(product_id: String, distribution_pack_id: Int, cartQuantity: String) {
        val existingList = getSearchResultsList().toMutableList()

        // Find the item with the specified product_id and distribution_pack_id
        val existingItemIndex = existingList.indexOfFirst {
            it.product_id == product_id &&
                    it.distribution_pack_id == distribution_pack_id
        }

        if (existingItemIndex != -1) {
            // If the item exists, update its quantity
            val updatedItem = existingList[existingItemIndex].copy(cart_quantity = cartQuantity)
            existingList[existingItemIndex] = updatedItem

            // Save the updated list to SharedPreferences
            val json = gson.toJson(existingList)
            sharedPreferences.edit().putString("searchResultsList", json).apply()
        }
    }


    // Remove an item based on product_id and distribution_pack_id
    fun removeItem(product_id: String, distribution_pack_id: Int) {
        val existingList = getSearchResultsList().toMutableList()

        // Remove the item with the specified product_id and distribution_pack_id
        existingList.removeIf {
            it.product_id == product_id && it.distribution_pack_id == distribution_pack_id
        }

        // Save the updated list to SharedPreferences
        val json = gson.toJson(existingList)
        sharedPreferences.edit().putString("searchResultsList", json).apply()
    }

    fun clearStockList() {
        sharedPreferences.edit().remove("searchResultsList").apply()
    }


    // Check if a product is already added based on product_id and distribution_pack_id
    fun isProductAdded(product_id: String, distribution_pack_id: Int): Boolean {
        val existingList = getSearchResultsList()
        return existingList.any {
            it.product_id == product_id && it.distribution_pack_id == distribution_pack_id
        }
    }

    companion object {
        private const val KEY_LAST_INVOICE_ID = "last_invoice_id"
        private const val KEY_CURRENT_USER = "current_invoice_user"
        private const val KEY_CURRENT_STORE = "current_invoice_store"
    }

    /**
     * ✅ Set the current user identifier (store_manager_id) so that invoice IDs
     *    are stored per-user. Call this at login time.
     *    NOTE: The primary key for invoice persistence is now store_id (see
     *    setCurrentInvoiceStore) so that every login session for the same store
     *    keeps accumulating the same sequence. We still track the user for
     *    backward compatibility with any previously saved values.
     */
    fun setCurrentInvoiceUser(storeManagerId: String) {
        invoicePrefs.edit().putString(KEY_CURRENT_USER, storeManagerId).apply()
    }

    /**
     * ✅ Set the current store identifier so invoice IDs are persisted per
     *    store_id. The last invoice ID is stored under a key like
     *    "last_invoice_id_<storeId>" in the dedicated InvoiceIdPrefs file, which
     *    is NEVER cleared on logout. That way, when the user logs out and logs
     *    back in (online or offline), the next offline sale (and every offline
     *    return counter-bump) continues from the last known invoice number for
     *    that store rather than falling back to an "OFF-..." id.
     */
    fun setCurrentInvoiceStore(storeId: String) {
        if (storeId.isNotBlank()) {
            invoicePrefs.edit().putString(KEY_CURRENT_STORE, storeId).apply()
        }
    }

    private fun invoiceKey(): String {
        // Prefer keying by store_id so the sequence is per-store and survives
        // logout/login cycles for that store (even if a different manager logs
        // in for the same store).
        val storeId = invoicePrefs.getString(KEY_CURRENT_STORE, "") ?: ""
        if (storeId.isNotEmpty()) {
            return "${KEY_LAST_INVOICE_ID}_store_$storeId"
        }
        // Backward-compat fallback: if store_id wasn't set, fall back to the
        // legacy per-user key so we don't lose previously-saved values.
        val userId = invoicePrefs.getString(KEY_CURRENT_USER, "") ?: ""
        return if (userId.isNotEmpty()) "${KEY_LAST_INVOICE_ID}_$userId" else KEY_LAST_INVOICE_ID
    }

    fun setLastInvoiceId(invoiceId: String) {
        if (!invoiceId.startsWith("OFF_") && !invoiceId.startsWith("OFF-")) {
            invoicePrefs.edit().putString(invoiceKey(), invoiceId).apply()
        }
    }

    /**
     * ✅ Bump the per-store last_invoice_id counter by +1 — preserving the numeric
     *    suffix format (prefix + zero-padding) — and return the new id, or null if
     *    there was no valid numeric counter to bump.
     *
     *    Call this from every code path where the backend consumes an invoice_id
     *    (online or offline sale/return/replace), so the next offline sale always
     *    generates the next-in-sequence id instead of colliding with one the
     *    server has already used.
     *
     *    Safe to call without a known "previous" id: if there is no counter yet,
     *    or the stored value is an OFF_/OFF- fallback, this is a no-op.
     */
    fun bumpLastInvoiceId(tag: String = "INVOICE_TRACKER"): String? {
        val currentId = getLastInvoiceId()
        if (currentId.isNullOrBlank()) {
            android.util.Log.d(tag, "bumpLastInvoiceId: no last_invoice_id saved yet — nothing to bump")
            return null
        }
        if (currentId.startsWith("OFF_") || currentId.startsWith("OFF-")) {
            android.util.Log.d(tag, "bumpLastInvoiceId: last_invoice_id is a fallback id ('$currentId') — skipping bump")
            return null
        }

        val match = Regex("^(.*?)(\\d+)$").find(currentId)
        if (match == null) {
            android.util.Log.w(tag, "bumpLastInvoiceId: last_invoice_id '$currentId' has no trailing digits — skipping bump")
            return null
        }

        val prefix = match.groupValues[1]
        val numStr = match.groupValues[2]
        val num = numStr.toLongOrNull() ?: return null
        val bumped = num + 1
        val padFmt = if (numStr.isNotEmpty()) "%0${numStr.length}d" else "%d"
        val bumpedId = "${prefix}${String.format(padFmt, bumped)}"
        setLastInvoiceId(bumpedId)
        android.util.Log.d(tag, "bumpLastInvoiceId: '$currentId' → '$bumpedId'")
        return bumpedId
    }

    fun getLastInvoiceId(): String? {
        val primary = invoicePrefs.getString(invoiceKey(), null)
        if (!primary.isNullOrEmpty()) return primary
        // Legacy fallback: if we just migrated from per-user to per-store keying,
        // read the old per-user value one time so the very first offline sale
        // after the upgrade still continues the sequence.
        val userId = invoicePrefs.getString(KEY_CURRENT_USER, "") ?: ""
        if (userId.isNotEmpty()) {
            val legacy = invoicePrefs.getString("${KEY_LAST_INVOICE_ID}_$userId", null)
            if (!legacy.isNullOrEmpty()) return legacy
        }
        return invoicePrefs.getString(KEY_LAST_INVOICE_ID, null)
    }

    /**
     * Clear all user-specific data from SharedPreferences (called on logout).
     *
     * IMPORTANT: This only clears the general "MyPrefs" file. It does NOT
     * touch the dedicated "InvoiceIdPrefs" file, because the last invoice ID
     * per store must survive logout so offline invoice numbering continues
     * correctly after the user logs back in.
     */
    fun clearAllUserData() {
        sharedPreferences.edit().clear().apply()
    }

    // Get the quantity of a specific product based on product_id and distribution_pack_id
    fun getQuantity(product_id: String, distribution_pack_id: Int): String {
        val existingList = getSearchResultsList()
        val selectedItem = existingList.find {
            it.product_id == product_id && it.distribution_pack_id == distribution_pack_id
        }
        return selectedItem?.cart_quantity ?: "0"
    }

}
