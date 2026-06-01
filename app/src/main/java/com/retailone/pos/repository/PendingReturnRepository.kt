package com.retailone.pos.repository

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.retailone.pos.localstorage.RoomDB.PendingReturnDao
import com.retailone.pos.localstorage.RoomDB.PendingReturnEntity
import com.retailone.pos.localstorage.RoomDB.PosDatabase
import com.retailone.pos.localstorage.SharedPreference.SharedPrefHelper
import com.retailone.pos.models.ReturnSalesItemModel.ReturnSaleReqModel.ReturnSaleReq
import com.retailone.pos.network.ApiClient
import com.retailone.pos.models.ReturnSalesItemModel.ReturnItemReq
import com.retailone.pos.models.ReturnSalesItemModel.ReturnItemRes
import com.retailone.pos.models.ReturnSalesItemModel.ReturnSaleReqModel.ReturnedItem
import com.retailone.pos.models.ReturnSalesItemModel.BatchReturnItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class PendingReturnRepository(private val context: Context) {

    private val database = PosDatabase.getDatabase(context)
    private val dao: PendingReturnDao = database.pendingReturnDao()
    private val gson = Gson()

    companion object {
        private const val TAG = "PendingReturnRepo"
        private const val SEVEN_DAYS_MILLIS = 7L * 24 * 60 * 60 * 1000
    }

    /**
     * Save return request to queue (for offline submission)
     */
    suspend fun queueReturnRequest(invoiceId: String, returnRequest: ReturnSaleReq): Long {
        return try {
            val entity = PendingReturnEntity(
                invoice_id = invoiceId,
                store_id = returnRequest.store_id,
                store_manager_id = returnRequest.store_manager_id,
                reason_id = returnRequest.reason_id,
                sales_id = returnRequest.sales_id,
                return_request_json = gson.toJson(returnRequest),
                sync_status = "PENDING"
            )

            val id = dao.insertPendingReturn(entity)
            Log.d(TAG, "✅ Queued return request (ID: $id) for offline sync")

            // ✅ COUNTER-BUMP: The backend assigns a sequential invoice_id to every return the
            //    same way it does to every sale (sale=208 -> return=209 -> next sale=210).
            //    When we queue an offline return, the backend will consume the NEXT invoice_id
            //    at sync time. If we don't reserve that id locally, the next offline sale will
            //    generate the same id and collide with the one the server already used for the
            //    return — producing "Invoice details not found" / "Some items failed to sync".
            //    Fix: bump the last_invoice_id in SharedPreferences by +1 right after a
            //    successful queue, preserving the numeric suffix format (with/without prefix,
            //    zero-padding).
            if (id > 0) {
                try {
                    val sharedPrefHelper = SharedPrefHelper(context)
                    val currentId = sharedPrefHelper.getLastInvoiceId()

                    if (currentId.isNullOrBlank()) {
                        Log.d(TAG, "🔢 [COUNTER-BUMP] No last_invoice_id saved yet — nothing to bump (queued return invoice='$invoiceId')")
                    } else if (currentId.startsWith("OFF_") || currentId.startsWith("OFF-")) {
                        Log.d(TAG, "🔢 [COUNTER-BUMP] last_invoice_id is a fallback id ('$currentId') — skipping bump")
                    } else {
                        val match = Regex("^(.*?)(\\d+)$").find(currentId)
                        if (match != null) {
                            val prefix = match.groupValues[1]
                            val numStr = match.groupValues[2]
                            val num = numStr.toLongOrNull()
                            if (num != null) {
                                val bumped = num + 1
                                val padFmt = if (numStr.isNotEmpty()) "%0${numStr.length}d" else "%d"
                                val bumpedId = "${prefix}${String.format(padFmt, bumped)}"
                                sharedPrefHelper.setLastInvoiceId(bumpedId)
                                Log.d(
                                    TAG,
                                    "🔢 [COUNTER-BUMP] Queued offline return for invoice='$invoiceId' — " +
                                            "bumping last_invoice_id '$currentId' → '$bumpedId' so the next " +
                                            "offline sale skips the id the backend will consume for this return."
                                )
                            } else {
                                Log.w(TAG, "🔢 [COUNTER-BUMP] Could not parse numeric suffix of '$currentId' — skipping bump")
                            }
                        } else {
                            Log.w(TAG, "🔢 [COUNTER-BUMP] last_invoice_id '$currentId' has no trailing digits — skipping bump")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ [COUNTER-BUMP] Error bumping last_invoice_id after queueReturnRequest: ${e.message}", e)
                }
            }

            id
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error queuing return request: ${e.message}", e)
            -1L
        }
    }

    /**
     * Get all pending returns waiting to be synced
     */
    suspend fun getAllPendingReturns(): List<PendingReturnEntity> {
        return dao.getAllPendingReturns()
    }

    /**
     * Get pending returns as Flow (for real-time updates)
     */
    fun getPendingReturnsFlow(): Flow<List<PendingReturnEntity>> {
        return dao.getPendingReturnsFlow()
    }

    /**
     * Get count of pending returns as Flow
     */
    fun getPendingReturnsCountFlow(): Flow<Int> {
        return dao.getPendingReturnsCountFlow()
    }

    /**
     * Get count of pending returns (synchronous/suspend)
     */
    suspend fun getPendingReturnsCount(): Int {
        return dao.getPendingReturnsCount()
    }

    /**
     * Convert PendingReturnEntity back to ReturnSaleReq
     */
    fun entityToReturnRequest(entity: PendingReturnEntity): ReturnSaleReq {
        return gson.fromJson(entity.return_request_json, ReturnSaleReq::class.java)
    }

    /**
     * Mark return as successfully synced
     */
    suspend fun markAsSynced(id: Int) {
        dao.markAsSynced(id)
        Log.d(TAG, "✅ Marked return $id as synced")
    }

    /**
     * Update sync status (SYNCING, FAILED, etc.)
     */
    suspend fun updateSyncStatus(id: Int, status: String, errorMessage: String? = null) {
        dao.updateSyncStatus(id, status, errorMessage)
        Log.d(TAG, "📝 Updated return $id status to: $status")
    }

    /**
     * Delete synced returns older than 7 days
     */
    suspend fun deleteSyncedReturns(): Int {
        val sevenDaysAgo = System.currentTimeMillis() - SEVEN_DAYS_MILLIS
        val deletedCount = dao.deleteSyncedReturnsOlderThan(sevenDaysAgo)
        Log.d(TAG, "🗑️ Deleted $deletedCount old synced returns")
        return deletedCount
    }

    /**
     * Delete a specific pending return
     */
    suspend fun deletePendingReturn(entity: PendingReturnEntity) {
        dao.deletePendingReturn(entity)
    }

    /**
     * Check if a pending return exists for a specific invoice
     */
    suspend fun hasPendingReturnForInvoice(invoiceId: String): Boolean {
        return dao.getPendingReturnByInvoice(invoiceId) != null
    }

    /**
     * Get pending return entity by invoice ID
     */
    suspend fun getPendingReturnByInvoice(invoiceId: String): PendingReturnEntity? {
        return dao.getPendingReturnByInvoice(invoiceId)
    }

    /**
     * Clear all pending returns
     */
    suspend fun clearAll() {
        dao.clearAll()
        Log.d(TAG, "🗑️ Cleared all pending returns")
    }

    /**
     * Recovery: Fetches real server-assigned IDs for an invoice, with retry.
     *
     * The sale sync response returns ~16 ms before this runs, which is too fast — the server
     * may not have committed the record yet when `getsaledetails` is queried immediately.
     * We retry up to [maxAttempts] times with [retryDelayMs] between attempts so that the
     * server has time to commit the newly-synced sale before we look it up.
     */
    private suspend fun recoverRealServerIds(
        context: Context,
        invoiceId: String,
        currentReq: ReturnSaleReq,
        maxAttempts: Int = 3,
        retryDelayMs: Long = 1500L
    ): ReturnSaleReq? {
        Log.d(
            "OFFLINE_SYNC_DEBUG",
            "🔍 [RECOVERY] Fetching real IDs for Invoice '$invoiceId' " +
                "(store=${currentReq.store_id}, current sales_id=${currentReq.sales_id}, " +
                "local item_ids=${currentReq.returned_items.map { it.id }})..."
        )

        repeat(maxAttempts) { attempt ->
            if (attempt > 0) {
                Log.d("OFFLINE_SYNC_DEBUG", "🔁 [RECOVERY] Retry ${attempt}/${ maxAttempts - 1} for invoice='$invoiceId' after ${retryDelayMs}ms delay...")
                delay(retryDelayMs)
            }
            try {
                val response = ApiClient().getApiService(context)
                    .getReturnSalesItemAPI(ReturnItemReq(invoiceId, currentReq.store_id.toString()))
                    .execute()

                if (response.isSuccessful && response.body()?.status == 1 && response.body()?.data?.isNotEmpty() == true) {
                    val serverSale = response.body()!!.data[0]
                    val serverId = serverSale.id

                    Log.d("OFFLINE_SYNC_DEBUG", "✅ [RECOVERY] Found server sale ID: $serverId for '$invoiceId' (attempt ${attempt + 1})")

                    val serverItems = serverSale.sales_items ?: emptyList()
                    var patchedCount = 0

                    val patchedItems = currentReq.returned_items.map { local ->
                        // Try to match by product_id + distribution_pack_id first
                        var match = serverItems.find { si ->
                            val localProdId = local.product_id ?: 0
                            val localPackId = local.distribution_pack_id ?: 0
                            localProdId != 0 && si.product_id == localProdId && si.distribution_pack_id == localPackId
                        }
                        // Fallback: index-based match (offline items get IDs 1, 2, 3...)
                        if (match == null) {
                            val assumedIndex = (local.id - 1).coerceIn(0, serverItems.lastIndex)
                            if (assumedIndex in serverItems.indices) match = serverItems[assumedIndex]
                        }

                        if (match != null) {
                            patchedCount++
                            local.copy(id = match.id)
                        } else {
                            Log.w("OFFLINE_SYNC_DEBUG", "⚠️ [RECOVERY] No match for item Prod:${local.product_id} Pack:${local.distribution_pack_id} in '$invoiceId'")
                            local
                        }
                    }

                    Log.d("OFFLINE_SYNC_DEBUG", "🛠️ [RECOVERY] Patched $patchedCount/${currentReq.returned_items.size} items with real IDs")
                    return currentReq.copy(sales_id = serverId, returned_items = patchedItems)
                } else {
                    val msg = response.body()?.message ?: "Not found"
                    val httpCode = response.code()
                    val bodyStatus = response.body()?.status
                    Log.w(
                        "OFFLINE_SYNC_DEBUG",
                        "⚠️ [RECOVERY] Attempt ${attempt + 1}/$maxAttempts — no server data for invoice='$invoiceId' " +
                            "(http=$httpCode, body.status=$bodyStatus, msg='$msg')"
                    )
                    // Continue to next retry
                }
            } catch (e: Exception) {
                Log.e("OFFLINE_SYNC_DEBUG", "❌ [RECOVERY] Attempt ${attempt + 1}/$maxAttempts — error for '$invoiceId': ${e.message}")
                // Continue to next retry
            }
        }

        Log.w(
            "OFFLINE_SYNC_DEBUG",
            "⚠️ [RECOVERY] All $maxAttempts attempt(s) exhausted for invoice='$invoiceId'. " +
                "The sale may not have committed on the server yet, or the invoice ID is mismatched."
        )
        return null
    }
    
    /**
     * Sync a single pending return with the server.
     * Extracted from syncAllPendingReturns so callers (e.g. the chronological
     * unified-queue sync in MPOSDashboardActivity) can invoke one return sync
     * at a time, interleaved with sale syncs in timestamp order.
     *
     * Returns true on success, false otherwise. Status is updated in-place.
     */
    suspend fun syncSingleReturn(context: Context, entity: PendingReturnEntity): Boolean = withContext(Dispatchers.IO) {
        val detailedSaleRepo = DetailedSaleRepository(context)
        try {
            Log.d(TAG, "🔄 Syncing return: Invoice ${entity.invoice_id}")
            updateSyncStatus(entity.id, "SYNCING")

            var returnRequest = entityToReturnRequest(entity)

            // 🛠️ RECOVERY: Patch IDs from server if they might be wrong (offline sale case)
            val patchedRequest = recoverRealServerIds(context, entity.invoice_id, returnRequest)
            if (patchedRequest != null) {
                returnRequest = patchedRequest
                Log.d("OFFLINE_SYNC_DEBUG", "🛠️ [PATCHED] Applied real server ID: ${returnRequest.sales_id}")
            } else {
                // Recovery failed after all retries — reset to PENDING so the next sync attempt
                // can try again (server may need more time to commit the newly-synced sale).
                Log.w(
                    "OFFLINE_SYNC_DEBUG",
                    "⏳ [RETURN WAIT] Could not recover server IDs for pending_return id=${entity.id} " +
                        "(invoice_id='${entity.invoice_id}', sales_id=${entity.sales_id}). " +
                        "Resetting to PENDING for next sync attempt."
                )
                updateSyncStatus(entity.id, "PENDING", "Waiting for sale to be committed on server (will retry on next sync)")
                return@withContext false
            }

            // 🔍 DEBUG: Log the full request JSON
            val requestJson = gson.toJson(returnRequest)
            Log.d("OFFLINE_SYNC_DEBUG", "🚀 [REQUEST] Syncing Return for Invoice ${entity.invoice_id}:\n$requestJson")

            val response = ApiClient().getApiService(context)
                .getReturnSalesSubmitAPI(returnRequest)
                .execute()

            Log.d("OFFLINE_SYNC_DEBUG", "📡 [RESPONSE] Received response for ${entity.invoice_id}. Code: ${response.code()}")

            if (response.isSuccessful && response.body()?.status == 1) {
                val responseBody = gson.toJson(response.body())
                Log.d("OFFLINE_SYNC_DEBUG", "✅ [SUCCESS] Return ${entity.invoice_id} synced! Response:\n$responseBody")
                markAsSynced(entity.id)

                // ✅ Update cached sale to mark as refunded
                val invoiceId = entity.invoice_id
                val saleDetails = detailedSaleRepo.getDetailedSaleByInvoiceId(invoiceId)

                if (saleDetails != null) {
                    val grandTotal = saleDetails.grand_total
                    val reasonId = returnRequest.reason_id ?: -1
                    // ✅ FIX: Convert returned_items to BatchReturnItem list so
                    //    updateRefundedAmount uses the PARTIAL-return path and only
                    //    marks the actually-returned items. Without this, the
                    //    FULL-return fallback sets every item to max return_quantity,
                    //    causing non-returned items to show as returned in view-only mode.
                    val batchItems = returnRequest.returned_items.map { ri ->
                        BatchReturnItem(
                            batch = null,
                            quantity = null,
                            retail_price = null,
                            tax_exclusive_price = null,
                            subtotal = null,
                            sales_item_id = ri.id,
                            return_quantity = ri.return_quantity,
                            return_reason = null,
                            batch_return_quantity = ri.return_quantity,
                            batch_refund_amount = 0.0,
                            product_id = ri.product_id ?: 0,
                            distribution_pack_id = ri.distribution_pack_id ?: 0
                        )
                    }
                    detailedSaleRepo.updateRefundedAmount(invoiceId, grandTotal, reasonId, batchItems)
                    Log.d("OFFLINE_SYNC_DEBUG", "💾 [CACHE] Updated local cache for $invoiceId (${batchItems.size} returned items)")
                }
                return@withContext true
            } else {
                val errorBody = response.errorBody()?.string() ?: "No error body"
                val responseMsg = response.body()?.message ?: "No message"
                val status = response.body()?.status ?: -1
                Log.e("OFFLINE_SYNC_DEBUG", "❌ [FAILED] Return ${entity.invoice_id} failed. Status: $status, Msg: $responseMsg, ErrorBody: $errorBody")

                val errorMsg = responseMsg
                updateSyncStatus(entity.id, "FAILED", errorMsg)
                return@withContext false
            }
        } catch (e: Exception) {
            Log.e("OFFLINE_SYNC_DEBUG", "❌ [EXCEPTION] Error syncing return ${entity.invoice_id}: ${e.message}", e)
            updateSyncStatus(entity.id, "FAILED", e.message)
            return@withContext false
        }
    }

    /**
     * Sync all pending returns with the server.
     * Returns true if all syncs succeeded.
     *
     * NOTE: For the correct chronological sync order (sale → return → sale ...),
     * prefer the unified queue flow in MPOSDashboardActivity that interleaves
     * syncSale / syncSingleReturn by created_at. This method is kept for
     * compatibility and for code paths that only need to drain pending returns.
     */
    suspend fun syncAllPendingReturns(context: Context): Boolean = withContext(Dispatchers.IO) {
        val pendingReturns = getAllPendingReturns()
        if (pendingReturns.isEmpty()) {
            Log.d(TAG, "No pending returns to sync")
            return@withContext true
        }

        var allSuccessful = true
        pendingReturns.forEach { entity ->
            if (!syncSingleReturn(context, entity)) {
                allSuccessful = false
            }
        }
        allSuccessful
    }
}
