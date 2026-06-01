package com.retailone.pos.utils

import android.content.Context
import android.util.Log
import com.retailone.pos.localstorage.RoomDB.PosDatabase
import com.retailone.pos.localstorage.SharedPreference.CustomerSessionHelper
import com.retailone.pos.localstorage.SharedPreference.LocalReturnCartHelper
import com.retailone.pos.localstorage.SharedPreference.OnHoldInvoiceHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Centralized helper that wipes EVERY piece of offline/cached data that was
 * saved for the previously-logged-in store.
 *
 * When to call this: on a successful ONLINE login where the new store_id
 * differs from the previously-saved ActiveStoreHelper.getActiveStoreId().
 *
 * When NOT to call this:
 *   - On logout (same-store offline re-login must continue to work).
 *   - On an online re-login for the SAME store.
 *
 * What is cleared:
 *   1. Room tables that cache store-specific data:
 *      - pending_sales, completed_sales, detailed_sales, sales_details,
 *        payment_invoices (sales/payment)
 *      - pending_returns, pending_replaces, pending_cancel_sales (returns/replaces)
 *      - pending_goods_returns, pending_dispatches, stock_return_list,
 *        warehouse_stock_list (goods return to warehouse)
 *      - product_inventory, store_products (product inventory / store products)
 *      - return_reasons, receipt_types, customer_discounts,
 *        store_settings (store-specific config)
 *   2. SharedPreferences files that cache store-specific data:
 *      - ProfileAttendanceCache (profile + attendance)
 *      - Inventory (cart / stock search list)
 *      - ExpenseCache
 *      - Organisation
 *      - CustomerSession
 *      - OnHoldInvoice (per-store on-hold invoices)
 *      - RETURN_CART_PREF (in-progress return cart)
 *
 * What is NOT cleared (by design):
 *   - InvoiceIdPrefs: the last_invoice_id is already keyed by store_id, so
 *     leaving it alone preserves offline invoice numbering for the store
 *     that is being switched away from, AND gives the incoming store a
 *     fresh starting point (its own per-store key hasn't been written yet).
 *   - ActiveStorePrefs: the caller overwrites it with the new store_id
 *     right after calling this function.
 *   - OfflineLogin: overwritten with the new user's credentials.
 *   - users table: we keep this so past online users can still log in
 *     offline for THEIR store (the login layer enforces same-store rule).
 */
object OfflineDataResetUtil {

    private const val TAG = "OfflineDataReset"

    /**
     * Wipe all offline/cached data for the previously-logged-in store.
     * Safe to call from any context — switches to IO dispatcher internally.
     */
    suspend fun resetAllOfflineData(context: Context) = withContext(Dispatchers.IO) {
        Log.w(TAG, "🧹 STORE SWITCH DETECTED — wiping all offline/cached data for previous store")
        val ctx = context.applicationContext
        val db = PosDatabase.getDatabase(ctx)

        // --------- Room tables ---------
        safe(TAG, "pending_sales") { db.pendingSaleDao().clearAll() }
        safe(TAG, "completed_sales") { db.completedSaleDao().clearAll() }
        safe(TAG, "detailed_sales") { db.detailedSaleDao().clearAll() }
        safe(TAG, "sales_details") { db.salesDetailsDao().clearAll() }
        safe(TAG, "payment_invoices") { db.paymentInvoiceDao().clearAll() }

        safe(TAG, "pending_returns") { db.pendingReturnDao().clearAll() }
        safe(TAG, "pending_replaces") { db.pendingReplaceDao().clearAll() }
        safe(TAG, "pending_cancel_sales") { db.pendingCancelSaleDao().clearAll() }

        safe(TAG, "pending_goods_returns") { db.pendingGoodsReturnDao().clearAll() }
        safe(TAG, "pending_dispatches") { db.pendingDispatchDao().clearAll() }
        safe(TAG, "stock_return_list") { db.stockReturnDao().clearAll() }
        safe(TAG, "warehouse_stock_list") { db.stockListDao().clearAll() }

        safe(TAG, "product_inventory") { db.productInventoryDao().clearAll() }
        safe(TAG, "store_products") { db.storeProductDao().clearAll() }

        safe(TAG, "return_reasons") { db.returnReasonDao().clearAll() }
        safe(TAG, "receipt_types") { db.receiptTypeDao().clearAll() }
        safe(TAG, "customer_discounts") { db.customerDiscountDao().clearAll() }
        safe(TAG, "store_settings") { db.storeSettingsDao().clearAll() }

        // --------- SharedPreferences files ---------
        // Nuke each file completely — simpler and safer than trying to clear
        // individual keys, since store-specific content is scattered inside.
        safe(TAG, "sp:ProfileAttendanceCache") {
            ctx.getSharedPreferences("ProfileAttendanceCache", Context.MODE_PRIVATE)
                .edit().clear().apply()
        }
        safe(TAG, "sp:Inventory") {
            ctx.getSharedPreferences("Inventory", Context.MODE_PRIVATE)
                .edit().clear().apply()
        }
        safe(TAG, "sp:ExpenseCache") {
            ctx.getSharedPreferences("ExpenseCache", Context.MODE_PRIVATE)
                .edit().clear().apply()
        }
        safe(TAG, "sp:Organisation") {
            ctx.getSharedPreferences("Organisation", Context.MODE_PRIVATE)
                .edit().clear().apply()
        }
        safe(TAG, "sp:CustomerSession") {
            try { CustomerSessionHelper(ctx).clearSession() } catch (_: Throwable) {}
            ctx.getSharedPreferences("CustomerSession", Context.MODE_PRIVATE)
                .edit().clear().apply()
        }
        safe(TAG, "sp:OnHoldInvoice") {
            try { OnHoldInvoiceHelper.clearAll(ctx) } catch (_: Throwable) {}
        }
        safe(TAG, "sp:RETURN_CART_PREF") {
            try { LocalReturnCartHelper.clearCart(ctx) } catch (_: Throwable) {}
            ctx.getSharedPreferences("RETURN_CART_PREF", Context.MODE_PRIVATE)
                .edit().clear().apply()
        }

        Log.w(TAG, "✅ Offline data reset complete. Incoming store starts fresh.")
    }

    private inline fun safe(tag: String, what: String, block: () -> Unit) {
        try {
            block()
            Log.d(tag, "  cleared $what")
        } catch (t: Throwable) {
            Log.e(tag, "  ⚠️ failed to clear $what: ${t.message}")
        }
    }
}
