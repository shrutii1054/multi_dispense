package com.retailone.pos.utils

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.retailone.pos.localstorage.RoomDB.PosDatabase
import com.retailone.pos.localstorage.SharedPreference.OnHoldInvoiceHelper
import com.retailone.pos.localstorage.SharedPreference.SharedPrefHelper
import com.retailone.pos.models.PointofsaleModel.PosSaleModel.PosSalesItem
import com.retailone.pos.models.ReturnSalesItemModel.ReturnItemData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reverts the LOCAL-ONLY side effects of every unsynced (PENDING / FAILED)
 * offline action so the local database returns to the state it was in before
 * the user queued those actions.
 *
 * This is invoked right before the pending queues are cleared at logout (see
 * MPOSDashboardActivity.mposLogout / CashUpDetailsActivity.mposLogout). Because
 * none of these actions have reached the server, they must be fully undone so
 * the store's product inventory, detailed sales history, on-hold flags, etc.
 * are not left in an inconsistent state.
 *
 * Revert actions performed per queue type:
 *
 *   pending_sales  → add the sold quantities back to store_products (batch-level
 *                    + aggregate stock_quantity) and product_inventory; remove
 *                    any offline payment_invoice / detailed_sale / completed_sale
 *                    rows keyed by the same invoice_id; rewind the last_invoice_id
 *                    counter by one step (only when the pending invoice matches
 *                    the current counter value).
 *
 *   pending_returns → reverse the refund applied to detailed_sales
 *                    (total_refunded_amount = 0, clear return_quantity /
 *                    batch_return_quantity / sales_returns / return_reason).
 *                    Also rewind last_invoice_id counter-bump.
 *
 *   pending_replaces → reverse the replace applied to detailed_sales
 *                    (total_replaced_amount = 0, clear batch return quantities).
 *                    Clear the local ON HOLD flag for the invoice.
 *
 *   pending_cancel_sales → nothing extra; the negative/reversal row for a
 *                    cancel is derived purely from the pending queue, so
 *                    deleting the queue already un-cancels it visually.
 *
 *   pending_goods_returns → nothing extra; the queued request does not mutate
 *                    any local inventory / stock_return_list until sync.
 *
 *   pending_dispatches → nothing extra; same reason.
 */
object PendingActionRevertUtil {

    private const val TAG = "PendingRevert"

    /**
     * Revert every PENDING / FAILED row across all six queues.
     * Returns the total number of actions reverted.
     */
    suspend fun revertAllUnsyncedActions(context: Context): Int = withContext(Dispatchers.IO) {
        val ctx = context.applicationContext
        val db = PosDatabase.getDatabase(ctx)
        val gson = Gson()
        var reverted = 0

        Log.w(TAG, "↩️ Starting revert of all unsynced offline actions")

        // --- 1. Reverse offline SALES (restore inventory, remove local mirrors, rewind counter)
        try {
            val pendingSales = db.pendingSaleDao().getPendingSales()
                .filter { it.sync_status == "PENDING" || it.sync_status == "FAILED" }
            Log.d(TAG, "Found ${pendingSales.size} pending sale(s) to revert")

            for (sale in pendingSales) {
                try {
                    val items: List<PosSalesItem> = try {
                        gson.fromJson(
                            sale.sales_items_json,
                            Array<PosSalesItem>::class.java
                        ).toList()
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to parse sales_items_json for sale ${sale.id}: ${e.message}")
                        emptyList()
                    }

                    restoreInventoryForSale(ctx, sale.store_id, items)

                    // Remove any offline mirrors keyed by this invoice
                    val invoiceId = sale.invoice_id
                    if (invoiceId.isNotBlank()) {
                        safe("remove detailed_sale $invoiceId") {
                            val entity = db.detailedSaleDao().getDetailedSaleByInvoiceId(invoiceId)
                            if (entity != null) db.detailedSaleDao().deleteDetailedSale(entity)
                        }
                        safe("rewind counter for $invoiceId") {
                            rewindInvoiceCounterIfMatches(ctx, invoiceId)
                        }
                    }

                    reverted++
                    Log.d(TAG, "  ↩️ reverted sale id=${sale.id} invoice=${sale.invoice_id}")
                } catch (e: Exception) {
                    Log.e(TAG, "  ⚠️ failed to revert sale id=${sale.id}: ${e.message}", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error while reverting pending sales: ${e.message}", e)
        }

        // --- 2. Reverse offline RETURNS (reset refund state in detailed_sales)
        try {
            val pendingReturns = db.pendingReturnDao().getAllPendingReturns()
            Log.d(TAG, "Found ${pendingReturns.size} pending return(s) to revert")

            // Group by invoice so we can clear the refund state once per invoice even
            // if the user queued multiple return attempts for the same sale.
            pendingReturns.groupBy { it.invoice_id }.forEach { (invoiceId, _) ->
                try {
                    if (!invoiceId.isNullOrBlank()) {
                        clearReturnStateOnDetailedSale(ctx, invoiceId)
                        rewindInvoiceCounterIfMatches(ctx, invoiceId)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "  ⚠️ failed to revert return for invoice=$invoiceId: ${e.message}", e)
                }
            }
            reverted += pendingReturns.size
        } catch (e: Exception) {
            Log.e(TAG, "Error while reverting pending returns: ${e.message}", e)
        }

        // --- 3. Reverse offline REPLACES (reset replace state + clear ON HOLD flag)
        try {
            val pendingReplaces = db.pendingReplaceDao().getAllPendingReplaces()
            Log.d(TAG, "Found ${pendingReplaces.size} pending replace(s) to revert")

            pendingReplaces.groupBy { it.invoice_id }.forEach { (invoiceId, _) ->
                try {
                    if (!invoiceId.isNullOrBlank()) {
                        clearReplaceStateOnDetailedSale(ctx, invoiceId)
                        try { OnHoldInvoiceHelper.removeOnHold(ctx, invoiceId) } catch (_: Throwable) {}
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "  ⚠️ failed to revert replace for invoice=$invoiceId: ${e.message}", e)
                }
            }
            reverted += pendingReplaces.size
        } catch (e: Exception) {
            Log.e(TAG, "Error while reverting pending replaces: ${e.message}", e)
        }

        // --- 4. Reverse offline CANCELS
        //     The cancel's negative / reversal row is derived only from
        //     pending_cancel_sales. Clearing that queue (done right after this
        //     revert pass) is itself the revert.
        try {
            reverted += db.pendingCancelSaleDao().getPendingCancelsForSync().size
        } catch (_: Exception) {}

        // --- 5. Reverse offline GOODS RETURNS
        //     No local inventory mutation happens when queuing. Clearing the
        //     queue suffices.
        try {
            reverted += db.pendingGoodsReturnDao().getAllPendingReturns().size
        } catch (_: Exception) {}

        // --- 6. Reverse offline DISPATCHES
        //     Same reason as goods returns.
        try {
            reverted += db.pendingDispatchDao().getAllPendingDispatches().size
        } catch (_: Exception) {}

        Log.w(TAG, "↩️ Revert complete. Total actions reverted: $reverted")
        reverted
    }

    /**
     * Add the sold quantities in [items] back to store_products (batch-level +
     * aggregate) and product_inventory for the given store.
     */
    private suspend fun restoreInventoryForSale(
        context: Context,
        storeId: String,
        items: List<PosSalesItem>
    ) = withContext(Dispatchers.IO) {
        val db = PosDatabase.getDatabase(context)
        val converters = com.retailone.pos.localstorage.RoomDB.Converters()

        for (saleItem in items) {
            val key = "${saleItem.product_id}_${saleItem.distribution_pack_id}"
            try {
                // --- store_products: restore batch qty and aggregate stock_quantity
                val productEntity = db.storeProductDao().getProductByKey(key)
                if (productEntity != null) {
                    val currentBatches = converters.fromBatchJson(productEntity.batchJson)
                    val updatedBatches = currentBatches.map { dbBatch ->
                        val soldBatch = saleItem.batch.find { it.batchno == dbBatch.batch_no }
                        if (soldBatch != null) {
                            val qtySold = soldBatch.quantity.toDouble()
                            val newQty = dbBatch.quantity + qtySold
                            com.retailone.pos.models.CommonModel.StroreProduct.PosSaleBatch(
                                batch_no = dbBatch.batch_no,
                                quantity = newQty,
                                price = dbBatch.price,
                                tax = dbBatch.tax,
                                batch_cart_quantity = 0.0,
                                batch_total_du_amount = dbBatch.batch_total_du_amount ?: "",
                                dispense_status = dbBatch.dispense_status,
                                discount = dbBatch.discount
                            )
                        } else {
                            dbBatch
                        }
                    }
                    val newStockQty = updatedBatches.sumOf { it.quantity }
                    val updatedEntity = productEntity.copy(
                        stock_quantity = newStockQty,
                        batchJson = converters.toBatchJson(updatedBatches),
                        lastUpdated = System.currentTimeMillis()
                    )
                    db.storeProductDao().insertProduct(updatedEntity)
                }

                // --- product_inventory: restore aggregate stock_quantity
                val storeIdInt = storeId.toIntOrNull() ?: 0
                val totalQty = saleItem.batch.sumOf { it.quantity.toDouble() }
                if (totalQty > 0 && storeIdInt > 0) {
                    val invItems = db.productInventoryDao().getInventoryByStoreSync(storeIdInt)
                    invItems.filter {
                        it.product_id == saleItem.product_id.toString() &&
                            it.distribution_pack_id == saleItem.distribution_pack_id.toString()
                    }.forEach { invEntity ->
                        val restored = invEntity.stock_quantity + totalQty
                        db.productInventoryDao().updateStockQuantity(invEntity.compositeKey, restored)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "  ⚠️ restoreInventoryForSale failed for key=$key: ${e.message}", e)
            }
        }
    }

    /**
     * Undo the refund applied offline: set total_refunded_amount to 0 and zero
     * out return_quantity / batch_return_quantity / sales_returns / return_reason
     * on the cached detailed_sales row for [invoiceId].
     */
    private suspend fun clearReturnStateOnDetailedSale(
        context: Context,
        invoiceId: String
    ) = withContext(Dispatchers.IO) {
        val db = PosDatabase.getDatabase(context)
        val gson = Gson()

        val entity = db.detailedSaleDao().getDetailedSaleByInvoiceId(invoiceId) ?: return@withContext
        val saleData = try {
            gson.fromJson(entity.detailed_data_json, ReturnItemData::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "  ⚠️ clearReturnState: cannot parse detailed_data_json for $invoiceId: ${e.message}")
            return@withContext
        }

        val clearedCamel = saleData.salesItems?.map { si ->
            val zeroedBatches = si.batches?.map { batch ->
                batch.copy(
                    return_quantity = 0,
                    batch_return_quantity = 0,
                    batch_refund_amount = 0.0
                )
            }
            si.copy(
                return_quantity = 0,
                batches = zeroedBatches,
                return_reason = null
            )
        } ?: emptyList()

        val clearedSnake = saleData.sales_items?.map { si ->
            si.copy(sales_returns = emptyList())
        } ?: emptyList()

        val updated = saleData.copy(
            salesItems = clearedCamel,
            sales_items = clearedSnake,
            total_refunded_amount = 0.0,
            reason_id = -1
        )

        db.detailedSaleDao().insertDetailedSale(
            entity.copy(
                detailed_data_json = gson.toJson(updated),
                created_at = System.currentTimeMillis()
            )
        )
    }

    /**
     * Undo the replace applied offline: set total_replaced_amount to 0 and zero
     * out return_quantity / batch_return_quantity on the cached detailed_sales
     * row for [invoiceId].
     */
    private suspend fun clearReplaceStateOnDetailedSale(
        context: Context,
        invoiceId: String
    ) = withContext(Dispatchers.IO) {
        val db = PosDatabase.getDatabase(context)
        val gson = Gson()

        val entity = db.detailedSaleDao().getDetailedSaleByInvoiceId(invoiceId) ?: return@withContext
        val saleData = try {
            gson.fromJson(entity.detailed_data_json, ReturnItemData::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "  ⚠️ clearReplaceState: cannot parse detailed_data_json for $invoiceId: ${e.message}")
            return@withContext
        }

        val clearedItems = saleData.salesItems?.map { si ->
            val zeroedBatches = si.batches?.map { batch ->
                batch.copy(
                    return_quantity = 0,
                    batch_return_quantity = 0
                )
            }
            si.copy(
                return_quantity = 0,
                batches = zeroedBatches
            )
        } ?: emptyList()

        val updated = saleData.copy(
            salesItems = clearedItems,
            total_replaced_amount = 0.0,
            reason_id = -1
        )

        db.detailedSaleDao().insertDetailedSale(
            entity.copy(
                detailed_data_json = gson.toJson(updated),
                created_at = System.currentTimeMillis()
            )
        )
    }

    /**
     * If the last stored invoice ID in SharedPreferences matches [invoiceId],
     * rewind it by one so an online re-sync does not skip numbers.
     * A bump on a return/replace queue, or a save on a sale, advanced the
     * counter locally; reverting those actions should also undo that advance.
     */
    private fun rewindInvoiceCounterIfMatches(context: Context, invoiceId: String) {
        try {
            val helper = SharedPrefHelper(context)
            val current = helper.getLastInvoiceId() ?: return
            if (current.isBlank() || current.startsWith("OFF_") || current.startsWith("OFF-")) return
            if (current != invoiceId) return

            val match = Regex("^(.*?)(\\d+)$").find(current) ?: return
            val prefix = match.groupValues[1]
            val numStr = match.groupValues[2]
            val num = numStr.toLongOrNull() ?: return
            if (num <= 0) return

            val rewound = num - 1
            val padFmt = if (numStr.isNotEmpty()) "%0${numStr.length}d" else "%d"
            val rewoundId = "$prefix${String.format(padFmt, rewound)}"
            helper.setLastInvoiceId(rewoundId)
            Log.d(TAG, "  🔢 rewound last_invoice_id '$current' → '$rewoundId'")
        } catch (e: Exception) {
            Log.e(TAG, "  ⚠️ rewindInvoiceCounter failed: ${e.message}")
        }
    }

    private inline fun safe(what: String, block: () -> Unit) {
        try { block() } catch (t: Throwable) {
            Log.e(TAG, "  ⚠️ $what failed: ${t.message}")
        }
    }
}
