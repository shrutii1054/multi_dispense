package com.retailone.pos.viewmodels.DashboardViewodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

import com.retailone.pos.models.ProgressModel.ProgressData
import com.retailone.pos.models.ReplaceModel.ReplaceSaleReq
import com.retailone.pos.models.ReturnSalesItemModel.ReturnItemReq
import com.retailone.pos.models.ReturnSalesItemModel.ReturnItemRes
import com.retailone.pos.models.ReturnSalesItemModel.ReturnSaleReqModel.ReturnSaleReq
import com.retailone.pos.models.ReturnSalesItemModel.ReturnSaleReqModel.SalesListRequest
import com.retailone.pos.models.ReturnSalesItemModel.ReturnSaleResModel.ReturnSaleRes
import com.retailone.pos.models.ReturnSalesItemModel.SalesReturnReasonModel.SalesReturnReasonRes
import com.retailone.pos.models.SalesListResponse
import com.retailone.pos.network.ApiClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.retailone.pos.models.ReplaceModel.ReturnSaleResMapper
import com.retailone.pos.models.ReplaceModel.ReturnSaleResRaw
import com.retailone.pos.repository.CompletedSaleRepository
import com.retailone.pos.repository.DetailedSaleRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.retailone.pos.models.SalesData
import com.retailone.pos.models.ReturnSalesItemModel.ReturnItemData
import com.retailone.pos.models.ReturnSalesItemModel.SalesReturnReasonModel.ReturnReasonData
import com.retailone.pos.repository.ReturnReasonRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.retailone.pos.repository.PendingReturnRepository
import com.retailone.pos.utils.NetworkUtils
import com.retailone.pos.localstorage.RoomDB.PosDatabase
import com.retailone.pos.localstorage.RoomDB.PendingSaleEntity
import com.retailone.pos.localstorage.RoomDB.PendingReturnEntity
import com.retailone.pos.models.ReturnSalesItemModel.Product
import com.retailone.pos.models.ReturnSalesItemModel.DistributionPack
import com.retailone.pos.models.ReturnSalesItemModel.BatchReturnItem
import com.retailone.pos.models.StoreDetails as PosStoreDetailsRef
import com.retailone.pos.models.StoreManagerDetails as PosStoreManagerDetailsRef
import com.retailone.pos.localstorage.RoomDB.PendingReplaceRepository
import com.retailone.pos.localstorage.RoomDB.PendingReplaceDao
import com.retailone.pos.localstorage.RoomDB.PendingReturnDao
import com.retailone.pos.localstorage.RoomDB.PendingReplaceEntity
import com.retailone.pos.models.ReturnSalesItemModel.SalesItem as ReturnSalesItemRef
import com.retailone.pos.models.ReturnSalesItemModel.Customer as ReturnCustomerRef
import com.retailone.pos.models.ReturnSalesItemModel.StoreDetails as ReturnStoreDetailsRef
import com.retailone.pos.models.ReturnSalesItemModel.StoreManagerDetails as ReturnStoreManagerDetailsRef
import com.retailone.pos.models.SalesItem as PosSalesItemRef
import com.retailone.pos.models.Customer as PosCustomerRef
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.abs
import com.retailone.pos.utils.CalculationHelper
import com.retailone.pos.localstorage.SharedPreference.OrganisationDetailsHelper
import com.retailone.pos.localstorage.DataStore.LoginSession
import com.retailone.pos.localstorage.RoomDB.StoreSettingsDao
import com.retailone.pos.localstorage.RoomDB.StoreSettingsEntity



class ReturnSalesDetailsViewmodel : ViewModel() {

    val returnitem_data = MutableLiveData<ReturnItemRes>()
    val returnitem_liveData: LiveData<ReturnItemRes>
        get() = returnitem_data

    private var completedSaleRepository: CompletedSaleRepository? = null
    private var detailedSaleRepository: DetailedSaleRepository? = null
    private val viewModelScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var returnReasonRepository: ReturnReasonRepository? = null
    private var pendingReturnRepository: PendingReturnRepository? = null
    private var pendingReplaceRepository: PendingReplaceRepository? = null
    private var storeSettingsDao: StoreSettingsDao? = null


    val loading = MutableLiveData<ProgressData>()
    val loadingLiveData: LiveData<ProgressData>
        get() = loading

    val returnsalesubmit_data = MutableLiveData<ReturnSaleRes>()
    val returnsalesubmit_liveData: LiveData<ReturnSaleRes>
        get() = returnsalesubmit_data

    val salesreturnreason_data = MutableLiveData<SalesReturnReasonRes>()
    val salesreturnreason_liveData: LiveData<SalesReturnReasonRes>
        get() = salesreturnreason_data

    val salesListLiveData = MutableLiveData<SalesListResponse>()

    // ✅ Initialize all repositories
    fun initRepository(context: Context) {
        if (completedSaleRepository == null) {
            completedSaleRepository = CompletedSaleRepository(context)
        }
        if (detailedSaleRepository == null) {
            detailedSaleRepository = DetailedSaleRepository(context)
        }
        if (returnReasonRepository == null) {
            returnReasonRepository = ReturnReasonRepository(context)
        }
        // Initialize pending return and replace repositories using DAOs
        val database = PosDatabase.getDatabase(context)
        if (pendingReturnRepository == null) {
            pendingReturnRepository = PendingReturnRepository(context)
        }
        if (pendingReplaceRepository == null) {
            val pendingReplaceDao = database.pendingReplaceDao()
            pendingReplaceRepository = PendingReplaceRepository(pendingReplaceDao)
        }
        if (storeSettingsDao == null) {
            storeSettingsDao = database.storeSettingsDao()
        }
    }

    // ✅ Get reason name by ID from local database
    suspend fun getReasonNameById(reasonId: Int): String {
        return withContext(Dispatchers.IO) {
            returnReasonRepository?.getReasonNameById(reasonId) ?: "Not Given"
        }
    }

    // ✅ Get sales from local database (offline-capable)
    //
    // NOTE: We do NOT filter by store_id here. The app enforces a "same store
    // only" policy at the login layer — when a different store logs in
    // online, all cached Room tables are wiped fresh (see MPOSLoginActivity).
    // Offline login for a different store is blocked entirely. So the local
    // DB at any point in time already contains only the current store's data.
    suspend fun getSalesFromLocalDB(context: Context): List<SalesData> {
        return withContext(Dispatchers.IO) {
            try {
                // Initialize repositories if needed
                if (completedSaleRepository == null || detailedSaleRepository == null) initRepository(context)

                val syncedSales = completedSaleRepository?.let { repo ->
                    val entities = repo.getAllSalesFlow().first()
                    repo.entitiesToSalesDataList(entities)
                } ?: emptyList()

                // Use the unified merge function to add pending sales and apply status overrides
                mergeLocalAndApiSales(syncedSales, context)
            } catch (e: Exception) {
                Log.e("ViewModel", "Error loading from local DB: ${e.message}")
                emptyList()
            }
        }
    }

    // ✅ Helper: Parse diverse date formats for accurate sorting (dd-MMM-yyyy, yyyy-MM-dd, etc.)
    private fun parseSaleDate(dateStr: String?): java.util.Date {
        if (dateStr.isNullOrBlank()) return java.util.Date(0)
        
        val formats = listOf(
            "yyyy-MM-dd HH:mm:ss",
            "dd-MMM-yyyy hh:mm a",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "dd/MM/yyyy HH:mm:ss"
        )
        
        formats.forEach { format ->
            try {
                return SimpleDateFormat(format, Locale.US).apply { 
                    isLenient = true 
                }.parse(dateStr) ?: java.util.Date(0)
            } catch (e: Exception) { /* skip to next */ }
        }
        return java.util.Date(0)
    }

    // ✅ Unified function to merge API results with local pending data and
    //    apply status overrides.
    //
    //    NOTE: No store filtering is done here. The app enforces the
    //    "same store only" rule at the login layer, so the local DB at any
    //    point already contains only the current store's data.
    private suspend fun mergeLocalAndApiSales(apiSales: List<SalesData>, context: Context): List<SalesData> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("RETAILONE_DEBUG", "🔄 mergeLocalAndApiSales START: apiSales=${apiSales.size}")
                val database = PosDatabase.getDatabase(context)

                // 1. Fetch ONLY pending/failed offline sales (not SYNCED ones).
                //    SYNCED sales already exist on the server and will come back via
                //    the API / completed_sales cache. Including them here caused
                //    duplicates when the server remapped invoice IDs during sync
                //    (e.g. offline sale "5" → server "4" after a return consumed "4").
                val allOfflineEntities = database.pendingSaleDao().getPendingSalesFlow().first()
                val gson = Gson()
                val onlineInvoiceIds = apiSales.mapNotNull { it.invoice_id }.toSet()

                val offlineSales = allOfflineEntities.map { mapPendingEntityToSalesData(it, gson) }
                    .filter { it.invoice_id !in onlineInvoiceIds }

                Log.d("RETAILONE_DEBUG", "Found ${offlineSales.size} pending offline sales not yet in API cache")

                // 2. Combine API and pending sales
                val combined = (apiSales + offlineSales).toMutableList()

                // 3. Fetch all unsynced returns and replaces to override status flags.
                val pendingReturns = pendingReturnRepository?.getAllPendingReturns() ?: emptyList()
                val pendingReplaces = pendingReplaceRepository?.getAllPendingReplaces() ?: emptyList()
                Log.d("RETAILONE_DEBUG", "Found ${pendingReturns.size} pending returns, ${pendingReplaces.size} pending replaces")

                // Create lookup maps by invoice_id
                val returnMap = pendingReturns.associateBy { it.invoice_id }
                val replaceMap = pendingReplaces.associateBy { it.invoice_id }

                // 3.5 Fetch all cancelled sales to hide them from return/replace.
                val cancelledSales = database.pendingCancelSaleDao().getAllCancels()
                val cancelledInvoiceIds = cancelledSales.map { it.invoice_id }.toSet()
                
                // 4. Decorate sales with local "dirty" state (overriding server status if we have a pending offline action)
                combined.filter { it.invoice_id !in cancelledInvoiceIds }.map { sale ->
                    var updatedSale = sale
                    
                    // Priority 1: Check pending returns table (unsynced)
                    val pendingReturn = returnMap[sale.invoice_id]
                    if (pendingReturn != null) {
                        updatedSale = updatedSale.copy(total_refunded_amount = sale.grand_total)
                        Log.d("RETAILONE_DEBUG", "Local Override (Pending): Marked ${sale.invoice_id} as Returned")
                    } else {
                        // Priority 2: Check detailed cache (for synced-but-not-updated-in-list items, or dirty local state)
                        val cachedDetail = detailedSaleRepository?.getDetailedSaleByInvoiceId(sale.invoice_id)
                        if (cachedDetail != null && cachedDetail.total_refunded_amount > 0) {
                            updatedSale = updatedSale.copy(total_refunded_amount = cachedDetail.total_refunded_amount)
                            Log.d("RETAILONE_DEBUG", "Local Override (Cache): Marked ${sale.invoice_id} as Returned (refunded: ${cachedDetail.total_refunded_amount})")
                        }
                    }
                    
                    // Same for Replace
                    val pendingReplace = replaceMap[sale.invoice_id]
                    if (pendingReplace != null) {
                        updatedSale = updatedSale.copy(total_replaced_amount = sale.grand_total)
                    } else {
                        val cachedDetail = detailedSaleRepository?.getDetailedSaleByInvoiceId(sale.invoice_id)
                        if (cachedDetail != null && cachedDetail.total_replaced_amount > 0) {
                            updatedSale = updatedSale.copy(total_replaced_amount = cachedDetail.total_replaced_amount)
                        }
                    }
                    
                    updatedSale
                }.sortedByDescending { it.id }.also {
                    Log.d("RETAILONE_DEBUG", "🔄 mergeLocalAndApiSales END: final count = ${it.size}")
                }
            } catch (e: Exception) {
                Log.e("RETAILONE_DEBUG", "Error merging sales: ${e.message}")
                apiSales // Fallback to original list on error
            }
        }
    }

    // ✅ NEW: Fetch pending offline sales and map to SalesData
    private suspend fun getPendingOfflineSales(context: Context): List<SalesData> {
        return try {
            val database = PosDatabase.getDatabase(context)
            val pendingSales = database.pendingSaleDao().getPendingSales()
            val gson = Gson()
            
            // ✅ Initialize repository before use if needed
            if (detailedSaleRepository == null) initRepository(context)
            
            pendingSales.map { entity ->
                mapPendingEntityToSalesData(entity, gson)
            }
        } catch (e: Exception) {
            Log.e("ViewModel", "Error fetching pending sales: ${e.message}")
            emptyList()
        }
    }

    private suspend fun mapPendingEntityToSalesData(entity: PendingSaleEntity, gson: Gson): SalesData {
        // Parse items from JSON
        val posItems = try {
            gson.fromJson(entity.sales_items_json, Array<com.retailone.pos.models.PointofsaleModel.PosSaleModel.PosSalesItem>::class.java).toList()
        } catch (e: Exception) { emptyList() }

        // ✅ NEW: Fetch actual refunded/replaced amounts from local cache if they exist
        // This ensures the dashboard flag ("Returned") updates correctly offline
        val cachedDetail = detailedSaleRepository?.getDetailedSaleByInvoiceId(entity.invoice_id)
        val refundedAmt = cachedDetail?.total_refunded_amount ?: 0.0
        val replacedAmt = cachedDetail?.total_replaced_amount ?: 0.0

        val salesItems = posItems.mapIndexed { index, posItem ->
            val salesItemId = index + 1
             PosSalesItemRef(
                id = salesItemId,
                sales_id = entity.invoice_id,
                product_id = posItem.product_id.toIntOrNull() ?: 0,
                product_name = posItem.product_name,
                distribution_pack_id = posItem.distribution_pack_id.toIntOrNull() ?: 0,
                distribution_pack_name = posItem.distribution_pack_name,
                // PosSalesItemRef.quantity (com.retailone.pos.models.SalesItem) is
                // now Double, and BatchCartItem.quantity is Double, so the
                // fractional dispensed-liter values pass through unchanged.
                quantity = posItem.batch.sumOf { it.quantity },
                whole_sale_price = posItem.whole_sale_price.toDoubleOrNull() ?: 0.0,
                retail_price = posItem.whole_sale_price.toDoubleOrNull() ?: 0.0,
                total_amount = posItem.total_amount.toDoubleOrNull() ?: 0.0,
                batch = posItem.batch.map { it.batchno }.joinToString(", "),
                created_at = entity.sale_date_time,
                updated_at = entity.sale_date_time,
                status = 1,
                sales_return_id = null,
                on_hold = 0
            )
        }

        return SalesData(
            id = entity.id,
            store_id = entity.store_id.toIntOrNull() ?: 0,
            store_manager_id = entity.store_manager_id.toIntOrNull() ?: 0,
            payment_type = entity.payment_type,
            sub_total = entity.sub_total.toDoubleOrNull() ?: 0.0,
            tax = entity.tax,
            tax_amount = entity.tax_amount.toDoubleOrNull() ?: 0.0,
            discount_amount = entity.discount_amount.toDoubleOrNull() ?: 0.0,
            subtotal_after_discount = entity.subtotal_after_discount.toDoubleOrNull() ?: 0.0,
            grand_total = entity.grand_total.replace(Regex("[^0-9.]"), "").toDoubleOrNull() ?: 0.0,
            amount_tendered = entity.amount_tendered.toDoubleOrNull() ?: 0.0,
            sale_date_time = entity.sale_date_time,
            invoice_id = entity.invoice_id,
            total_refunded_amount = refundedAmt,
            total_replaced_amount = replacedAmt,
            created_at = entity.sale_date_time,
            updated_at = entity.sale_date_time,
            status = 1,
            canceled_at = null,
            reversal_of = null,
            store_details = null,
            store_manager_details = null,
            sales_items = salesItems,
            customer = PosCustomerRef(
                id = entity.customer_id,
                sales_id = entity.invoice_id,
                customer_name = entity.customer_name,
                customer_mob_no = entity.customer_mob_no,
                created_at = entity.sale_date_time,
                updated_at = entity.sale_date_time,
                status = 1
            ),
            on_hold = null
        )
    }

    // ✅ Queue return request for offline submission
    suspend fun queueReturnRequest(invoiceId: String, returnRequest: ReturnSaleReq): Long {
        return withContext(Dispatchers.IO) {
            pendingReturnRepository?.queueReturnRequest(invoiceId, returnRequest) ?: -1L
        }
    }

    // ✅ Queue replace request for offline submission.
    //    Replace does NOT consume a new invoice_id on the backend (it's attached
    //    to the original sale's invoice), so we intentionally do NOT bump
    //    last_invoice_id here — unlike queueReturnRequest which does.
    suspend fun queueReplaceRequest(invoiceId: String, replaceRequest: ReplaceSaleReq): Long {
        return withContext(Dispatchers.IO) {
            pendingReplaceRepository?.queueReplaceRequest(invoiceId, replaceRequest) ?: -1L
        }
    }

    // ✅ Get pending returns count
    suspend fun getPendingReturnsCount(): Int {
        return withContext(Dispatchers.IO) {
            pendingReturnRepository?.getPendingReturnsCount() ?: 0
        }
    }

    // ✅ Get pending replaces count
    suspend fun getPendingReplacesCount(): Int {
        return withContext(Dispatchers.IO) {
            pendingReplaceRepository?.getPendingCount() ?: 0
        }
    }


    // ✅ Sync all pending replaces
    suspend fun syncPendingReplaces(context: Context) {
        withContext(Dispatchers.IO) {
            val pendingReplaces = pendingReplaceRepository?.getAllPendingReplaces() ?: emptyList()

            if (pendingReplaces.isEmpty()) {
                Log.d("PendingReplaces", "No pending replaces to sync")
                return@withContext
            }

            Log.d("PendingReplaces", "🔄 Syncing ${pendingReplaces.size} pending replaces...")

            pendingReplaces.forEach { entity ->
                try {
                    val replaceRequest = pendingReplaceRepository?.entityToReplaceRequest(entity)
                    if (replaceRequest != null) {
                        val response = ApiClient().getApiService(context)
                            .replaceSale(replaceRequest)
                            .execute()

                        if (response.isSuccessful) {
                            pendingReplaceRepository?.markAsSynced(entity.id)
                            Log.d("PendingReplaces", "✅ Synced replace ${entity.id}")
                            
                            // ✅ Update local cache to reflect replacement (for dashboard flags)
                            try {
                                val invoiceId = entity.invoice_id
                                if (!invoiceId.isNullOrEmpty()) {
                                    val saleDetails = detailedSaleRepository?.getDetailedSaleByInvoiceId(invoiceId)
                                    if (saleDetails != null) {
                                        val grandTotal = saleDetails.grand_total
                                        detailedSaleRepository?.updateReplacedAmount(
                                            invoiceId,
                                            grandTotal,
                                            replaceRequest.reason_id
                                        )
                                        Log.d("PendingReplaces", "✅ Updated cache for $invoiceId")
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e("PendingReplaces", "❌ Cache update failed: ${e.message}")
                            }
                        } else {
                            pendingReplaceRepository?.updateSyncStatus(entity.id, "FAILED", response.message())
                        }
                    }
                } catch (e: Exception) {
                    pendingReplaceRepository?.updateSyncStatus(entity.id, "FAILED", e.message)
                }
            }
        }
    }

    // ✅ Sync all pending returns
    suspend fun syncPendingReturns(context: Context) {
        withContext(Dispatchers.IO) {
            val pendingReturns = pendingReturnRepository?.getAllPendingReturns() ?: emptyList()

            if (pendingReturns.isEmpty()) {
                Log.d("RETAILONE_DEBUG", "No pending returns to sync")
                return@withContext
            }

            Log.d("RETAILONE_DEBUG", "🔄 syncPendingReturns START: Syncing ${pendingReturns.size} returns...")

            pendingReturns.forEach { entity ->
                try {
                    Log.d("RETAILONE_DEBUG", "Syncing return ID: ${entity.id}, Invoice: ${entity.invoice_id}")
                    // Update status to SYNCING
                    pendingReturnRepository?.updateSyncStatus(entity.id, "SYNCING")

                    // Convert back to ReturnSaleReq
                    val returnRequest = pendingReturnRepository?.entityToReturnRequest(entity)

                    if (returnRequest != null) {
                        // Call API synchronously
                        val response = ApiClient().getApiService(context)
                            .getReturnSalesSubmitAPI(returnRequest)
                            .execute()

                        if (response.isSuccessful && response.body()?.status == 1) {
                            Log.d("RETAILONE_DEBUG", "✅ API Success for return ${entity.id}")
                            // Mark as synced
                            pendingReturnRepository?.markAsSynced(entity.id)

                            // ✅ Update cached sale to mark as refunded with reason
                            try {
                                // Get invoice_id from the pending return entity
                                val invoiceId = entity.invoice_id

                                if (!invoiceId.isNullOrEmpty()) {
                                    val saleDetails = detailedSaleRepository?.getDetailedSaleByInvoiceId(invoiceId)

                                    if (saleDetails != null) {
                                        val grandTotal = saleDetails.grand_total
                                        val reasonId = returnRequest.reason_id ?: -1

                                        // ✅ FIX: Convert returned_items to BatchReturnItem list so
                                        //    updateRefundedAmount uses the PARTIAL-return path.
                                        //    Without this, non-returned items show as returned in view-only mode.
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
                                        Log.d("RETAILONE_DEBUG", "Updating cache for $invoiceId: amount=$grandTotal, reason=$reasonId, items=${batchItems.size}")
                                        detailedSaleRepository?.updateRefundedAmount(
                                            invoiceId,
                                            grandTotal,
                                            reasonId,
                                            batchItems
                                        )
                                        Log.d("RETAILONE_DEBUG", "✅ Updated cache for $invoiceId")
                                    } else {
                                        Log.w("RETAILONE_DEBUG", "⚠️ Sale not found in cache for invoice: $invoiceId")
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e("RETAILONE_DEBUG", "❌ Cache update failed: ${e.message}")
                            }
                        } else {
                            val errorMsg = response.body()?.message ?: "Sync failed"
                            pendingReturnRepository?.updateSyncStatus(entity.id, "FAILED", errorMsg)
                            Log.e("RETAILONE_DEBUG", "❌ API Failed for return ${entity.id}: $errorMsg")
                        }
                    }
                } catch (e: Exception) {
                    pendingReturnRepository?.updateSyncStatus(entity.id, "FAILED", e.message)
                    Log.e("RETAILONE_DEBUG", "❌ Error syncing return ${entity.id}: ${e.message}")
                }
            }
            Log.d("RETAILONE_DEBUG", "🔄 syncPendingReturns END")
        }
    }


    // ✅ Search sale by invoice ID from local database (offline)
    suspend fun searchSaleByInvoiceOffline(invoiceId: String, storeId: String, context: Context): ReturnItemData? {
        return withContext(Dispatchers.IO) {
            val database = PosDatabase.getDatabase(context)

            // ✅ Cancelled sales should not be returnable/replaceable
            if (database.pendingCancelSaleDao().anyCancelRequestExists(invoiceId)) {
                return@withContext null
            }

            // Priority 1: Check completed/cached sales
            var data: ReturnItemData? = detailedSaleRepository?.getDetailedSaleByInvoiceId(invoiceId)
            
            // Priority 2: Check pending offline sales
            if (data == null) {
                try {
                    val database = PosDatabase.getDatabase(context)
                    val pendingEntity = database.pendingSaleDao().getSaleByInvoice(invoiceId, storeId)
                    if (pendingEntity != null) {
                        // ✅ TAX FIX: Pre-fetch per-product tax rates from the local store_products
                        // cache so mapPendingEntityToReturnItemData can use them as a fallback when
                        // posItem.tax is null (happens for sales saved before the tax field was added).
                        val gson = Gson()
                        val posItems = try {
                            gson.fromJson(
                                pendingEntity.sales_items_json,
                                Array<com.retailone.pos.models.PointofsaleModel.PosSaleModel.PosSalesItem>::class.java
                            ).toList()
                        } catch (_: Exception) { emptyList() }
                        val productIds = posItems.mapNotNull { it.product_id.toIntOrNull() }.filter { it > 0 }
                        val storeIdInt = storeId.toIntOrNull() ?: 0
                        val productTaxMap: Map<Int, Int> = if (productIds.isNotEmpty()) {
                            database.storeProductDao()
                                .getProductsByProductIds(productIds, storeIdInt)
                                .associate { it.product_id to (it.tax ?: 0) }
                        } else emptyMap()
                        Log.d("TAX_FIX", "productTaxMap for $invoiceId: $productTaxMap")

                        // ✅ SMART TAX DETECTION: determine if sale was inclusive or exclusive from the numbers
                        var sumRetailTimesQty = 0.0
                        posItems.forEach { item ->
                            item.batch.forEach { b ->
                                sumRetailTimesQty += b.retail_price * b.quantity
                            }
                        }
                        
                        val entGrandTotal = pendingEntity.grand_total.replace(Regex("[^0-9.]"), "").toDoubleOrNull() ?: 0.0
                        val entSubTotal = pendingEntity.sub_total.toDoubleOrNull() ?: 0.0
                        
                        // Exclusive: Sum of (unit price * qty) == sub_total
                        // Inclusive: Sum of (unit price * qty) == grand_total
                        // (We use a 1.0 margin of error for rounding)
                        val isTaxInclusive = if (sumRetailTimesQty > 0.0) {
                            val diffIncl = abs(sumRetailTimesQty - entGrandTotal)
                            val diffExcl = abs(sumRetailTimesQty - entSubTotal)
                            
                            Log.d("TAX_FIX", "Smart Detection -> $invoiceId: sumQtyRetail=$sumRetailTimesQty, sub=$entSubTotal, grand=$entGrandTotal")
                            Log.d("TAX_FIX", "Diffs -> Inclusive-diff: $diffIncl, Exclusive-diff: $diffExcl")

                            if (diffExcl < diffIncl && diffExcl < 1.0) {
                                Log.d("TAX_FIX", "✅ Sale detected as EXCLUSIVE")
                                false
                            } else if (diffIncl < diffExcl && diffIncl < 1.0) {
                                Log.d("TAX_FIX", "✅ Sale detected as INCLUSIVE")
                                true
                            } else {
                                // ✅ Fallback chain: StoreSettings (Local DB) -> OrgData (SharedPref) -> LoginSession (DataStore) -> Default True
                                val storeSettings = storeSettingsDao?.getSettingsByStoreId(storeId)
                                val orgIsIncl = try { OrganisationDetailsHelper(context).getOrganisationData().is_inclusive } catch(_:Exception){ null }
                                val dataStoreIsIncl = LoginSession.getInstance(context).isTaxInclusive().first()
                                
                                val fallback = storeSettings?.isTaxInclusive ?: orgIsIncl ?: dataStoreIsIncl ?: true
                                Log.d("TAX_FIX", "Ambiguous numbers, fallback resolved to: $fallback (storeSettings=${storeSettings?.isTaxInclusive}, org=$orgIsIncl, dataStore=$dataStoreIsIncl)")
                                fallback
                            }
                        } else {
                            // Fallback chain for empty items case (matching logic above)
                            val storeSettings = storeSettingsDao?.getSettingsByStoreId(storeId)
                            val orgIsIncl = try { OrganisationDetailsHelper(context).getOrganisationData().is_inclusive } catch(_:Exception){ null }
                            val dataStoreIsIncl = LoginSession.getInstance(context).isTaxInclusive().first()
                            storeSettings?.isTaxInclusive ?: orgIsIncl ?: dataStoreIsIncl ?: true
                        }

                        Log.d("TAX_FIX", "Final resolved isTaxInclusive: $isTaxInclusive")

                        data = mapPendingEntityToReturnItemData(pendingEntity, gson, isTaxInclusive, productTaxMap)
                        // ✅ Save to detailed_sales locally so any future offline returns can update it
                        saveDetailedSaleToLocalDB(data)
                    }
                } catch (e: Exception) {
                    Log.e("ViewModel", "Error searching pending by invoice: ${e.message}")
                }
            }
            
            // Final Step: Decorate with pending offline return/replace states
            if (data != null) {
                val pendingReturnEntity = pendingReturnRepository?.getPendingReturnByInvoice(invoiceId)
                val hasPendingReturn = pendingReturnEntity != null
                val hasPendingReplace = pendingReplaceRepository?.hasPendingReplaceForInvoice(invoiceId) ?: false
                
                Log.d("OFFLINE_DEBUG_TAG", "Decorating $invoiceId: hasPendingReturn=$hasPendingReturn, hasPendingReplace=$hasPendingReplace")

                if (hasPendingReturn && pendingReturnEntity != null) {
                    val grandTotal = data.grand_total
                    val req = pendingReturnRepository!!.entityToReturnRequest(pendingReturnEntity)
                    val reasonName = detailedSaleRepository?.getReasonNameById(req.reason_id ?: -1) ?: "Offline Return"

                    Log.d("OFFLINE_DEBUG_TAG", "🛠️ [RESTORE] Found pending return for $invoiceId. ReasonID=${req.reason_id}, ItemsCount=${req.returned_items.size}")

                    // Restore camelCase list (salesItems)
                    val currentData = data // Create a stable copy for smart casting inside the closure
                    val restoredItems = currentData.salesItems?.mapIndexed { index, si ->
                        val match = req.returned_items.find { rit ->
                            // 1. Exact ID match
                            val idMatch = (rit.id > 0 && rit.id == si.id)
                            // 2. Full product+pack match
                            val productFullMatch = (rit.product_id ?: 0 > 0 && rit.product_id == si.product_id && rit.distribution_pack_id == si.distribution_pack_id)
                            // 3. Product-only match (when distribution_pack_id is missing/0 in returned item)
                            val productOnlyMatch = (rit.product_id ?: 0 > 0 && rit.product_id == si.product_id && (rit.distribution_pack_id == 0 || rit.distribution_pack_id == null))
                            idMatch || productFullMatch || productOnlyMatch
                        } ?: if (currentData.salesItems?.size == req.returned_items.size) {
                            // 4. Index-based last resort: if item counts match, map by position
                            Log.d("OFFLINE_DEBUG_TAG", "   - ⚠️ INDEX FALLBACK: Mapping item at index $index")
                            req.returned_items.getOrNull(index)
                        } else if (currentData.salesItems?.size == 1 && req.returned_items.size == 1) {
                            Log.d("OFFLINE_DEBUG_TAG", "   - ⚠️ LAST RESORT: Mapping single item invoice to single item return")
                            req.returned_items[0]
                        } else null

                        if (match != null) {
                            Log.d("OFFLINE_DEBUG_TAG", "   - RESTORED match for ${si.product_name ?: "Item $index"}: qty=${match.return_quantity}")
                            val itemReturnQty = match.return_quantity ?: 0
                            // If item has batches, update them; otherwise create a synthetic batch so read-only card shows values
                            val updatedBatches = if (!si.batches.isNullOrEmpty()) {
                                si.batches.map { batch ->
                                    batch.copy(
                                        return_quantity = itemReturnQty,
                                        batch_return_quantity = itemReturnQty,
                                        batch_refund_amount = itemReturnQty.toDouble() * (si.retail_price ?: 0.0),
                                        sales_item_id = si.id
                                    )
                                }
                            } else {
                                // No batches - synthesize one so the adapter can display totals
                                listOf(com.retailone.pos.models.ReturnSalesItemModel.BatchReturnItem(
                                    batch = null,
                                    quantity = si.quantity,
                                    retail_price = si.retail_price,
                                    tax_exclusive_price = si.tax_exclusive_price,
                                    subtotal = si.total_amount,
                                    product_id = si.product_id,
                                    distribution_pack_id = si.distribution_pack_id,
                                    sales_item_id = si.id,
                                    return_quantity = itemReturnQty,
                                    batch_return_quantity = itemReturnQty,
                                    return_reason = reasonName
                                ))
                            }
                            si.copy(
                                return_quantity = itemReturnQty,
                                batches = updatedBatches,
                                return_reason = reasonName,
                                refund_amount = itemReturnQty.toDouble() * (si.retail_price ?: 0.0)
                            )
                        } else {
                            // ✅ FIX: Item was NOT returned — explicitly zero out return values
                            //    so non-returned items don't show stale return quantities in view-only mode.
                            Log.d("OFFLINE_DEBUG_TAG", "   - NOT returned: ${si.product_name ?: "Item $index"} — zeroing out")
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
                                return_reason = null,
                                refund_amount = 0.0
                            )
                        }
                    }

                    // Restore snake_case list (sales_items) - Important for SearchReturnActivity adapter
                    val restoredItemsDetailed = currentData.sales_items?.map { si ->
                        val match = req.returned_items.find { rit ->
                            (rit.id > 0 && rit.id == si.id) ||
                            (rit.product_id ?: 0 > 0 && rit.product_id == si.product_id && rit.distribution_pack_id == si.distribution_pack_id)
                        }
                        if (match != null) {
                            // ✅ Returned item: populate sales_returns so the adapter
                            //    recognises it as returned in view-only mode
                            si.copy(sales_returns = listOf(
                                com.retailone.pos.models.ReturnSalesItemModel.SalesReturn(
                                    id = 0,
                                    sales_id = si.id ?: 0,
                                    invoice_id = currentData.invoice_id ?: "",
                                    sales_item_id = si.id ?: 0,
                                    return_quantity = (match.return_quantity ?: 0).toDouble(),
                                    reason_id = req.reason_id ?: -1,
                                    rate = si.retail_price,
                                    amount = (match.return_quantity ?: 0).toDouble() * si.retail_price,
                                    reason = com.retailone.pos.models.ReturnSalesItemModel.SalesReturnReason(req.reason_id ?: -1, reasonName)
                                )
                            ))
                        } else {
                            // ✅ Not returned: explicitly clear sales_returns
                            si.copy(sales_returns = emptyList())
                        }
                    }

                    // Log totals before restoration
                    Log.d("OFFLINE_DEBUG_TAG", "   - BEFORE: sub_total=${data.sub_total}, tax_amount=${data.tax_amount}, grand_total=${data.grand_total}")
                    
                    data = data.copy(
                        total_refunded_amount = grandTotal,
                        reason_id = req.reason_id ?: -1,
                        salesItems = restoredItems,
                        sales_items = restoredItemsDetailed,
                        sub_total = data.sub_total,
                        tax_amount = data.tax_amount,
                        grand_total = data.grand_total
                    )
                    Log.d("OFFLINE_DEBUG_TAG", "   - AFTER: sub_total=${data.sub_total}, tax_amount=${data.tax_amount}, grand_total=${data.grand_total}, refunded=${data.total_refunded_amount}")
                }
                if (hasPendingReplace) {
                    val grandTotal = data?.grand_total ?: 0.0
                    data = data?.copy(total_replaced_amount = grandTotal)
                }

                // ✅ FIX: If ONLY replaced (no real refund yet), reset return_quantity 
                // so the Return screen starts clean and doesn't block the user.
                if ((data?.total_refunded_amount ?: 0.0) <= 0.0 && (data?.total_replaced_amount ?: 0.0) > 0.0) {
                    Log.d("ViewModel", "🔄 Resetting return quantities for replaced sale: $invoiceId")
                    data = data?.copy(salesItems = data?.salesItems?.map { it.copy(
                        return_quantity = 0,
                        batches = it.batches?.map { it.copy(
                            return_quantity = 0, 
                            batch_return_quantity = 0,
                            batch_refund_amount = 0.0
                        ) }
                    ) })
                }
            }
            
            data
        }
    }

    private fun mapPendingEntityToReturnItemData(
        entity: PendingSaleEntity,
        gson: Gson,
        isInclusive: Boolean,
        productTaxMap: Map<Int, Int> = emptyMap()   // product_id -> tax% from store_products table
    ): ReturnItemData {
        val posItems = try {
            gson.fromJson(entity.sales_items_json, Array<com.retailone.pos.models.PointofsaleModel.PosSaleModel.PosSalesItem>::class.java).toList()
        } catch (e: Exception) { emptyList() }

        val salesItems = posItems.mapIndexed { index, posItem ->
            val salesItemId = index + 1
            val itemBatches = posItem.batch.map { b ->
                // ✅ TAX FIX: use stored value first; fall back to locally-cached store_products tax
                val taxRate = posItem.tax
                    ?: productTaxMap[posItem.product_id.toIntOrNull() ?: 0]?.toDouble()
                    ?: 0.0
                
                // ✅ Use CalculationHelper for consistent math (Fixed for Exclusive mode)
                val calc = CalculationHelper.computeLine(
                    retailPrice = b.retail_price,
                    quantity = 1.0,  // unit price base
                    taxRate = taxRate,
                    isInclusive = isInclusive
                )
                
                val taxExclusivePrice = calc.base.toDouble()
                
                BatchReturnItem(
                    batch = b.batchno,
                    quantity = b.quantity.toDouble(),
                    retail_price = b.retail_price,
                    tax_exclusive_price = taxExclusivePrice,
                    subtotal = b.retail_price * b.quantity,
                    sales_item_id = salesItemId,
                    return_quantity = 0,
                    return_reason = null,
                    product_id = posItem.product_id.toIntOrNull() ?: 0,
                    distribution_pack_id = posItem.distribution_pack_id.toIntOrNull() ?: 0,
                    batch_return_quantity = 0,
                    batch_refund_amount = 0.0,
                    defective_bottles = 0,
                    discount = posItem.discount?.toDouble() ?: 0.0
                )
            }

            // ✅ FIX: Use the actual tax-inclusive retail_price from batch items,
            // NOT posItem.whole_sale_price which is the tax-exclusive subtotal (price_without_discount).
            val actualRetailPrice = if (posItem.batch.isNotEmpty()) {
                posItem.batch.first().retail_price
            } else {
                posItem.whole_sale_price.toDoubleOrNull() ?: 0.0
            }
            // ✅ TAX FIX: same fallback chain as batch-level taxRate above
            val taxRate = posItem.tax
                ?: productTaxMap[posItem.product_id.toIntOrNull() ?: 0]?.toDouble()
                ?: 0.0

            val calcItem = CalculationHelper.computeLine(
                retailPrice = actualRetailPrice,
                quantity = 1.0,
                taxRate = taxRate,
                isInclusive = isInclusive
            )
            val actualTaxExclusivePrice = calcItem.base.toDouble()

            ReturnSalesItemRef(
                created_at = entity.sale_date_time,
                distribution_pack = DistributionPack(id = posItem.distribution_pack_id.toIntOrNull() ?: 0, no_of_packs = 1, product_description = ""),
                distribution_pack_id = posItem.distribution_pack_id.toIntOrNull() ?: 0,
                distribution_pack_name = posItem.distribution_pack_name,
                id = salesItemId,
                product = Product(id = posItem.product_id.toIntOrNull() ?: 0, product_name = posItem.product_name),
                product_id = posItem.product_id.toIntOrNull() ?: 0,
                product_name = posItem.product_name,
                quantity = posItem.batch.sumOf { it.quantity }.toDouble(),
                batches = itemBatches,
                retail_price = actualRetailPrice,
                tax_exclusive_price = actualTaxExclusivePrice,
                sales_id = entity.invoice_id,
                status = 1,
                total_amount = posItem.total_amount.toDoubleOrNull() ?: 0.0,
                updated_at = entity.sale_date_time,
                whole_sale_price = posItem.whole_sale_price.toDoubleOrNull() ?: 0.0,
                tax = (posItem.tax
                    ?: productTaxMap[posItem.product_id.toIntOrNull() ?: 0]?.toDouble()
                    ?: 0.0).toInt(),
                tax_amount = posItem.tax_amount ?: 0.0,
                discount = posItem.discount?.toDouble() ?: 0.0,
                readonlyMode = false,
                isExpired = false
            )
        }

        return ReturnItemData(
            amount_tendered = entity.amount_tendered.toDoubleOrNull()?.toInt() ?: 0,
            created_at = entity.sale_date_time,
            customer = ReturnCustomerRef(
                id = entity.customer_id,
                sales_id = entity.invoice_id,
                customer_name = entity.customer_name,
                customer_mob_no = entity.customer_mob_no,
                created_at = entity.sale_date_time,
                updated_at = entity.sale_date_time,
                status = 1
            ),
            discount_amount = entity.discount_amount.toIntOrNull() ?: 0,
            grand_total = entity.grand_total.replace(Regex("[^0-9.]"), "").toDoubleOrNull() ?: 0.0,
            id = entity.id,
            invoice_id = entity.invoice_id,
            payment_type = entity.payment_type,
            salesItems = salesItems,
            status = 1,
            store_details = ReturnStoreDetailsRef(
                address = "",
                cluster_id = 0,
                created_at = entity.sale_date_time,
                deleted_at = "",
                ho_manager_id = 0,
                id = entity.store_id.toIntOrNull() ?: 0,
                induction_date = "",
                latitude = "",
                location = "",
                logo = "",
                longitude = "",
                organization_id = 0,
                phone_no = "",
                station_code = "",
                status = 1,
                store_name = "",
                updated_at = entity.sale_date_time
            ),
            store_id = entity.store_id.toIntOrNull() ?: 0,
            store_manager_details = ReturnStoreManagerDetailsRef(
                active_status = "1",
                address = "",
                allow_login = 1,
                alt_contact_no = "",
                business_id = entity.store_id,
                cluster_id = "",
                contact_no = "",
                created_at = entity.sale_date_time,
                current_address = "",
                deleted_at = "",
                dob = "",
                email = "",
                email_verified_at = "",
                first_name = "",
                gender = "",
                id = entity.store_manager_id.toIntOrNull() ?: 0,
                last_name = "",
                password = "",
                password_changed_at = "",
                permanent_address = "",
                role_id = "1",
                status = 1,
                surname = "",
                updated_at = entity.sale_date_time,
                user_type = "",
                username = ""
            ),
            store_manager_id = entity.store_manager_id.toIntOrNull() ?: 0,
            total_refunded_amount = 0.0,
            total_replaced_amount = 0.0,
            sub_total = entity.sub_total.toDoubleOrNull() ?: 0.0,
            subtotal_after_discount = entity.subtotal_after_discount.toDoubleOrNull() ?: 0.0,
            tax = entity.tax,
            tax_amount = entity.tax_amount.toDoubleOrNull() ?: 0.0,
            is_inclusive = isInclusive,
            updated_at = entity.sale_date_time,
            spot_discount_amount = entity.spot_discount_amount ?: "0",
            spot_discount_percentage = entity.spot_discount_percentage?.toFloat() ?: 0f,
            trxn_code = entity.trxn_code,
            prc_no = entity.prc_no,
            tax_details = null,
            tax_summery = null,
            sales_items = salesItems.map { si ->
                // ✅ FIX: Use the first batch's name so the adapter can match by batch name
                val firstBatchName = si.batches?.firstOrNull()?.batch
                com.retailone.pos.models.ReturnSalesItemModel.SalesItemDetailed(
                    id = si.id,
                    sales_id = si.sales_id,
                    product_id = si.product_id,
                    on_hold = 0,
                    distribution_pack_id = si.distribution_pack_id,
                    distribution_pack_name = si.distribution_pack_name,
                    batch = firstBatchName,
                    quantity = si.quantity,
                    store_stock = 0,
                    retail_price = si.retail_price,
                    discount = si.discount ?: 0.0,
                    tax_exclusive_price = si.tax_exclusive_price,
                    total_amount = si.total_amount,
                    tax = si.tax,  // ✅ Pass the tax rate from the source item
                    product = si.product,
                    distribution_pack = si.distribution_pack,
                    sales_returns = null
                )
            }
        )
    }

    // ✅ UPDATED: Update sale's refunded amount AND reason_id after successful return
    suspend fun updateSaleRefundedAmount(invoiceId: String, refundedAmount: Double, reasonId: Int, returnedItems: List<BatchReturnItem>? = null) {
        withContext(Dispatchers.IO) {
            detailedSaleRepository?.updateRefundedAmount(invoiceId, refundedAmount, reasonId, returnedItems)
        }
    }


    // ✅ Save sales to local database after API call
    private suspend fun saveSalesToLocalDB(salesList: List<SalesData>) {
        withContext(Dispatchers.IO) {
            completedSaleRepository?.saveSalesFromList(salesList)
        }
    }

    // ✅ Delete old sales (7+ days)
    suspend fun cleanupOldSales() {
        withContext(Dispatchers.IO) {
            completedSaleRepository?.deleteOldSales()
        }
    }

    // ✅ Save detailed sale to local DB (with Merge Protection)
    suspend fun saveDetailedSaleToLocalDB(incomingData: ReturnItemData) {
        withContext(Dispatchers.IO) {
            try {
                val apiRefunded = incomingData.total_refunded_amount
                Log.d("RETAILONE_DEBUG", "--------------------------------------------------")
                Log.d("RETAILONE_DEBUG", "💾 saveDetailedSaleToLocalDB START: ${incomingData.invoice_id ?: ""}")
                Log.d("RETAILONE_DEBUG", "Incoming: total_refunded=$apiRefunded, total_replaced=${incomingData.total_replaced_amount}")

                // 1. Fetch existing local record
                val existingEntity = detailedSaleRepository?.getDetailedSaleEntityByInvoiceId(incomingData.invoice_id ?: "")
                var dataToSave = incomingData

                if (existingEntity != null) {
                    val localData = Gson().fromJson(existingEntity.detailed_data_json, ReturnItemData::class.java)
                    
                    val localRefunded = localData.total_refunded_amount
                    val localReplaced = localData.total_replaced_amount
                    
                    Log.d("RETAILONE_DEBUG", "Existing Cache: refunded=$localRefunded, replaced=$localReplaced")

                    val pendingReturnEntity = pendingReturnRepository?.getPendingReturnByInvoice(incomingData.invoice_id ?: "")
                    val hasLocalReturn = (localRefunded > 0) 
                        || (pendingReturnEntity != null)
                        || (localReplaced <= 0 && (localData.salesItems?.any { (it.return_quantity ?: 0) > 0 } ?: false))

                    val hasLocalReplace = (localReplaced > 0) || (pendingReplaceRepository?.hasPendingReplaceForInvoice(incomingData.invoice_id ?: "") ?: false)

                    Log.d("RETAILONE_DEBUG", "Checking Merge: hasLocalReturn=$hasLocalReturn, hasLocalReplace=$hasLocalReplace, apiRefunded=${incomingData.total_refunded_amount}")

                    // ✅ IMPROVED MERGE: Perform merge if local data exists, 
                    // and prioritize local quantities if API quantities are 0 but local are > 0.
                    if (hasLocalReturn || hasLocalReplace) {
                        Log.d("RETAILONE_DEBUG", "🛡️ MERGE PROTECTION TRIGGERED for ${incomingData.invoice_id}")
                        
                        dataToSave = incomingData.copy(
                            total_refunded_amount = if (incomingData.total_refunded_amount <= 0 && hasLocalReturn) {
                                if (localRefunded > 0) localRefunded else localData.grand_total
                            } else incomingData.total_refunded_amount,
                            
                            total_replaced_amount = if (incomingData.total_replaced_amount <= 0 && hasLocalReplace) localReplaced else incomingData.total_replaced_amount,
                            
                            reason_id = if (incomingData.reason_id <= 0 && (localData.reason_id ?: -1) != -1) localData.reason_id ?: -1 else incomingData.reason_id,
                            
                            // Merge item-level and batch-level quantities
                            salesItems = incomingData.salesItems?.map { incomingItem ->
                                // Try matching by server ID first, fallback to product+pack for pending sales
                                val localItem = localData.salesItems?.find { it.id == incomingItem.id }
                                    ?: localData.salesItems?.find { it.product_id == incomingItem.product_id && it.distribution_pack_id == incomingItem.distribution_pack_id }
                                
                                if (localItem != null) {
                                    // Use local quantity if API says 0 but local says > 0
                                    val finalReturnQty = if (incomingItem.return_quantity <= 0) localItem.return_quantity else incomingItem.return_quantity
                                    
                                    val updatedBatches = incomingItem.batches?.map { incomingBatch ->
                                        val incomingBatchKey = incomingBatch.batch?.trim()?.lowercase() ?: ""
                                        val localBatch = localItem.batches?.find { 
                                            (it.batch?.trim()?.lowercase() ?: "") == incomingBatchKey 
                                        }
                                        if (localBatch != null || pendingReturnEntity != null) {
                                            // Fallback to pending return queue if local cache doesn't have it
                                            var pendingQty = 0
                                            if (pendingReturnEntity != null) {
                                                val req = pendingReturnRepository!!.entityToReturnRequest(pendingReturnEntity)
                                                pendingQty = req.returned_items.find { it.id == incomingItem.id }?.return_quantity ?: 0
                                            }

                                            val finalBatchQty = if ((incomingBatch.batch_return_quantity ?: 0) <= 0) {
                                                if (pendingQty > 0) pendingQty else (localBatch?.batch_return_quantity ?: 0)
                                            } else (incomingBatch.batch_return_quantity ?: 0)
                                            
                                            incomingBatch.copy(
                                                return_quantity = finalBatchQty,
                                                batch_return_quantity = finalBatchQty,
                                                batch_refund_amount = if ((incomingBatch.batch_refund_amount ?: 0.0) <= 0.0) (localBatch?.batch_refund_amount ?: 0.0) else incomingBatch.batch_refund_amount
                                            )
                                        } else {
                                            incomingBatch
                                        }
                                    } ?: incomingItem.batches
                                    
                                    incomingItem.copy(
                                        return_quantity = if (finalReturnQty <= 0 && updatedBatches?.any { (it.batch_return_quantity ?: 0) > 0 } == true) {
                                            updatedBatches.sumOf { it.batch_return_quantity ?: 0 }
                                        } else finalReturnQty,
                                        batches = updatedBatches
                                    )
                                } else {
                                    incomingItem
                                }
                            } ?: incomingData.salesItems,
                            
                            // ✅ ALSO MERGE snake_case list
                            sales_items = incomingData.sales_items?.map { incomingDetailedItem ->
                                // Same robust matching for detailed items
                                val localDetailedItem = localData.sales_items?.find { it.id == incomingDetailedItem.id }
                                    ?: localData.sales_items?.find { it.product_id == incomingDetailedItem.product_id && it.distribution_pack_id == incomingDetailedItem.distribution_pack_id }
                                    
                                if (localDetailedItem != null && (incomingDetailedItem.sales_returns.isNullOrEmpty()) && !localDetailedItem.sales_returns.isNullOrEmpty()) {
                                    incomingDetailedItem.copy(sales_returns = localDetailedItem.sales_returns)
                                } else {
                                    incomingDetailedItem
                                }
                            }
                        )
                        Log.d("RETAILONE_DEBUG", "✅ Merge Complete: New total_refunded=${dataToSave.total_refunded_amount}")

                        // ✅ FIX: If already replaced (synced or local), and we are NOT in a pending return flow, 
                        // reset the working return_quantity fields so the NEXT action starts clean.
                        if ((dataToSave.total_refunded_amount ?: 0.0) <= 0.0 && (dataToSave.total_replaced_amount ?: 0.0) > 0.0) {
                            Log.d("RETAILONE_DEBUG", "🔄 Resetting working quantities for already replaced sale in cache.")
                            dataToSave = dataToSave.copy(
                                salesItems = dataToSave.salesItems?.map { item ->
                                    item.copy(
                                        return_quantity = 0,
                                        batches = item.batches?.map { it.copy(return_quantity = 0, batch_return_quantity = 0, batch_refund_amount = 0.0) }
                                    )
                                }
                            )
                        }
                    } else {
                        Log.d("RETAILONE_DEBUG", "No local return data to merge for ${incomingData.invoice_id ?: ""}")
                    }
                } else {
                    Log.d("RETAILONE_DEBUG", "No local cache for ${incomingData.invoice_id}")
                }

                detailedSaleRepository?.saveDetailedSale(dataToSave)
                Log.d("RETAILONE_DEBUG", "💾 saveDetailedSaleToLocalDB END")
            } catch (e: Exception) {
                Log.e("RETAILONE_DEBUG", "❌ Error in saveDetailedSaleToLocalDB: ${e.message}", e)
                // CRITICAL FIX: If merge fails, do NOT overwrite the local cache with potentially empty API data.
                // If we have an existing entity, we keep it rather than saving the "bad" incomingData.
                val existingEntity = detailedSaleRepository?.getDetailedSaleEntityByInvoiceId(incomingData.invoice_id ?: "")
                if (existingEntity == null) {
                    // Only save if we have nothing at all locally
                    detailedSaleRepository?.saveDetailedSale(incomingData)
                } else {
                    Log.w("RETAILONE_DEBUG", "⚠️ Merge failed, keeping original local cache for ${incomingData.invoice_id} to prevent data loss.")
                }
            }
        }
    }

    // ✅ Get detailed sale from local DB (for offline viewing)
    suspend fun getDetailedSaleFromLocalDB(invoiceId: String): ReturnItemData? {
        return withContext(Dispatchers.IO) {
            detailedSaleRepository?.getDetailedSaleByInvoiceId(invoiceId)
        }
    }

    // ✅ Check if detailed sale exists in local DB
    suspend fun detailedSaleExistsInDB(invoiceId: String): Boolean {
        return withContext(Dispatchers.IO) {
            detailedSaleRepository?.detailedSaleExists(invoiceId) ?: false
        }
    }

    // ✅ Save return reasons to local DB
    suspend fun saveReturnReasonsToLocalDB(reasons: List<ReturnReasonData>) {
        withContext(Dispatchers.IO) {
            returnReasonRepository?.saveReturnReasons(reasons)
        }
    }

    // ✅ Get return reasons from local DB
    suspend fun getReturnReasonsFromLocalDB(): List<ReturnReasonData> {
        return withContext(Dispatchers.IO) {
            returnReasonRepository?.getAllReturnReasons() ?: emptyList()
        }
    }

    // ✅ Check if return reasons exist in local DB
    suspend fun hasReturnReasonsInDB(): Boolean {
        return withContext(Dispatchers.IO) {
            returnReasonRepository?.hasReturnReasons() ?: false
        }
    }


    // ✅ Cleanup old detailed sales (7+ days)
    suspend fun cleanupOldDetailedSales() {
        withContext(Dispatchers.IO) {
            detailedSaleRepository?.deleteOldDetailedSales()
        }
    }

    private fun logLong(tag: String, msg: String) {
        if (msg.length <= 4000) {
            Log.d(tag, msg)
            return
        }
        var i = 0
        while (i < msg.length) {
            val end = (i + 4000).coerceAtMost(msg.length)
            Log.d(tag, msg.substring(i, end))
            i = end
        }
    }

    fun callSalesListApi(context: Context, storeId: String) {
        loading.postValue(ProgressData(isProgress = true))

        val request = SalesListRequest(store_id = storeId)

        val gsonPretty = GsonBuilder().setPrettyPrinting().create()
        logLong("SalesListAPIRequest", "days=7\nbody=\n${gsonPretty.toJson(request)}")

        ApiClient().getApiService(context).getSalesList(days = 7, request)
            .enqueue(object : Callback<SalesListResponse> {
                override fun onResponse(
                    call: Call<SalesListResponse>, response: Response<SalesListResponse>
                ) {
                    val code = response.code()
                    val headersStr = response.headers().toString()

                    if (response.isSuccessful && response.body() != null) {
                        val body = response.body()!!
                        
                        // ✅ Merge API results with local pending data before posting
                        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                            val mergedData = mergeLocalAndApiSales(body.data, context)
                            salesListLiveData.postValue(body.copy(data = mergedData))
                            
                            // Save original API data to local cache
                            saveSalesToLocalDB(body.data)
                        }

                        loading.postValue(ProgressData(isProgress = false))
                    } else {
                        // Silently ignore non-successful responses on the sales list screen.
                        loading.postValue(ProgressData(isProgress = false))
                    }
                }

                override fun onFailure(call: Call<SalesListResponse>, t: Throwable) {
                    Log.e("SalesListAPI", "Error: ${t.localizedMessage}", t)
                    // Silently ignore network failures on the sales list screen.
                    loading.postValue(ProgressData(isProgress = false))
                }
            })
    }

    fun callreplaceSalesListApi(context: Context, storeId: String) {
        loading.postValue(ProgressData(isProgress = true))

        val request = SalesListRequest(store_id = storeId)

        ApiClient().getApiService(context).getReplaceSalesList(days = 7, request)
            .enqueue(object : Callback<SalesListResponse> {
                override fun onResponse(
                    call: Call<SalesListResponse>, response: Response<SalesListResponse>
                ) {
                    if (response.isSuccessful && response.body() != null) {
                        val body = response.body()!!
                        
                        // ✅ Merge API results with local pending data (overriding status if returns are pending)
                        viewModelScope.launch {
                            val mergedData = mergeLocalAndApiSales(body.data, context)
                            salesListLiveData.postValue(body.copy(data = mergedData))
                            
                            // Save to local database for offline viewing
                            saveSalesToLocalDB(body.data)
                        }
                        
                        loading.postValue(ProgressData(isProgress = false))
                    } else {
                        // Silently ignore non-successful responses on the sales list screen.
                        loading.postValue(ProgressData(isProgress = false))
                    }
                }

                override fun onFailure(call: Call<SalesListResponse>, t: Throwable) {
                    Log.e("SalesListAPI", "Error: ${t.localizedMessage}")
                    // Silently ignore network failures on the sales list screen.
                    loading.postValue(ProgressData(isProgress = false))
                }
            })
    }

    fun callReturnSalesDetailsApi(returnItemReq: ReturnItemReq, context: Context) {
        loading.postValue(ProgressData(isProgress = true))
        Log.e("SalesListAPIRequest", "Request: ${returnItemReq}")

        if (!NetworkUtils.isInternetAvailable(context)) {
            viewModelScope.launch {
                val offlineData = searchSaleByInvoiceOffline(returnItemReq.invoice_id ?: "", returnItemReq.store_id ?: "", context)
                if (offlineData != null) {
                    returnitem_data.postValue(ReturnItemRes(listOf(offlineData!!), "Offline Results", 1))
                } else {
                    loading.postValue(ProgressData(isProgress = false, isMessage = true, message = "Sale not found offline"))
                }
                loading.postValue(ProgressData(isProgress = false))
            }
            return
        }

        ApiClient().getApiService(context).getReturnSalesItemAPI(returnItemReq)
            .enqueue(object : Callback<ReturnItemRes> {
                override fun onResponse(
                    call: Call<ReturnItemRes>, response: Response<ReturnItemRes>
                ) {
                    Log.e("SalesListAPIResponse", "Response: ${response.body()}")
                    Log.e("SalesListAPIResponseX", "Response: ${Gson().toJson(response.body())}")
                    if (response.isSuccessful && response.body() != null) {
                        val body = response.body()!!
                        
                        if (body.data.isEmpty()) {
                            // ✅ Fallback to offline search if API returns 0 results
                            viewModelScope.launch {
                                val offlineData = searchSaleByInvoiceOffline(returnItemReq.invoice_id ?: "", returnItemReq.store_id ?: "", context)
                                if (offlineData != null) {
                                    Log.d("ViewModel", "✅ API empty, found in offline cache: ${returnItemReq.invoice_id}")
                                    returnitem_data.postValue(ReturnItemRes(listOf(offlineData!!), "Offline Results", 1))
                                } else {
                                    returnitem_data.postValue(body)
                                }
                                loading.postValue(ProgressData(isProgress = false))
                            }
                        } else {
                            // ✅ API Success: MERGE with local state before posting
                            viewModelScope.launch {
                                val invoiceId = returnItemReq.invoice_id
                                val apiData = body.data.firstOrNull { it.invoice_id?.toString() == (invoiceId?.toString() ?: "") } ?: body.data[0]
                                Log.d("RETAILONE_DEBUG", "callReturnSalesDetailsApi: API Success for ${apiData.invoice_id}")
                                saveDetailedSaleToLocalDB(apiData) // This now handles the merge protection
                                
                                // ✅ OFFLINE CACHE FALLBACK: Save is_inclusive setting for this store to StoreSettings table
                                if (apiData.is_inclusive != null) {
                                    val sid = apiData.store_id.toString()
                                    if (sid != "0" && sid != "") {
                                        try {
                                            val dao = PosDatabase.getDatabase(context).storeSettingsDao()
                                            val existing = dao.getSettingsByStoreId(sid)
                                            if (existing != null) {
                                                dao.insertOrUpdate(existing.copy(isTaxInclusive = apiData.is_inclusive))
                                            } else {
                                                // Create default settings entry if missing
                                                dao.insertOrUpdate(StoreSettingsEntity(
                                                    storeId = sid,
                                                    isSpotDiscountEnabled = false,
                                                    spotDiscountLimit = "0.00",
                                                    isTaxInclusive = apiData.is_inclusive
                                                ))
                                            }
                                        } catch (e: Exception) {
                                            Log.e("RETAILONE_DEBUG", "Failed to update store settings fallback: ${e.message}")
                                        }
                                    }
                                }
                                
                                // Read back the merged data to show to user
                                val finalData = detailedSaleRepository?.getDetailedSaleByInvoiceId(apiData.invoice_id ?: "") ?: apiData
                                Log.d("RETAILONE_DEBUG", "callReturnSalesDetailsApi: Final Data total_refunded=${finalData.total_refunded_amount}")
                                
                                returnitem_data.postValue(ReturnItemRes(listOf(finalData), body.message, body.status))
                                loading.postValue(ProgressData(isProgress = false))
                            }
                        }
                    } else {
                        // Fallback to offline search on API failure
                        viewModelScope.launch {
                            val offlineData = searchSaleByInvoiceOffline(returnItemReq.invoice_id ?: "", returnItemReq.store_id ?: "", context)
                            if (offlineData != null) {
                                returnitem_data.postValue(ReturnItemRes(listOf(offlineData!!), "Offline Results", 1))
                            } else {
                                val showMsg = NetworkUtils.isInternetAvailable(context)
                                loading.postValue(
                                    ProgressData(
                                        isProgress = false,
                                        isMessage = showMsg,
                                        message = if (showMsg) "Failed to fetch data, Try again" else ""
                                    )
                                )
                            }
                            loading.postValue(ProgressData(isProgress = false))
                        }
                    }
                }

                override fun onFailure(call: Call<ReturnItemRes>, t: Throwable) {
                    Log.d("rty", t.message.toString())
                    // Fallback to offline search on failure
                    viewModelScope.launch {
                        val offlineData = searchSaleByInvoiceOffline(returnItemReq.invoice_id ?: "", returnItemReq.store_id ?: "", context)
                        if (offlineData != null) {
                            returnitem_data.postValue(ReturnItemRes(listOf(offlineData!!), "Offline Results", 1))
                        } else {
                            // ✅ Only show message if online, else it's expected missing cache
                            val showMsg = NetworkUtils.isInternetAvailable(context)
                            loading.postValue(
                                ProgressData(
                                    isProgress = false, 
                                    isMessage = showMsg, 
                                    message = if (showMsg) context.getString(com.retailone.pos.R.string.something_went_wrong_short) else ""
                                )
                            )
                        }
                        loading.postValue(ProgressData(isProgress = false))
                    }
                }
            })
    }

    fun callReturnSalesSubmitApi(returnSaleReq: ReturnSaleReq, context: Context) {
        loading.postValue(ProgressData(isProgress = true))

        Log.e("SalesListAPIRequestreturn", "Request: ${returnSaleReq}")
        ApiClient().getApiService(context).getReturnSalesSubmitAPI(returnSaleReq)
            .enqueue(object : Callback<ReturnSaleRes> {
                override fun onResponse(
                    call: Call<ReturnSaleRes>, response: Response<ReturnSaleRes>
                ) {
                    if (response.isSuccessful && response.body() != null
                        && (response.body()?.status ?: 0) == 1) {
                        // ✅ Online return succeeded — bump the persistent last_invoice_id
                        //    counter by +1 so the next offline sale does NOT collide with
                        //    the invoice_id the backend consumed for this return. Mirrors
                        //    the offline counter-bump in PendingReturnRepository.queueReturnRequest.
                        try {
                            bumpLastInvoiceIdForReturn(context)
                        } catch (e: Exception) {
                            Log.e("RETURN_COUNTER_BUMP", "Error bumping after online return: ${e.message}", e)
                        }
                        returnsalesubmit_data.postValue(response.body())
                        loading.postValue(ProgressData(isProgress = false))
                    } else if (response.isSuccessful && response.body() != null) {
                        // Backend returned 200 but status != 1 — treat as non-success, don't bump
                        returnsalesubmit_data.postValue(response.body())
                        loading.postValue(ProgressData(isProgress = false))
                    } else {
                        val showMsgVal = NetworkUtils.isInternetAvailable(context)
                        loading.postValue(
                            ProgressData(
                                isProgress = false,
                                isMessage = showMsgVal,
                                message = if (showMsgVal) "Failed to fetch data, Try again" else ""
                            )
                        )
                    }
                }

                override fun onFailure(call: Call<ReturnSaleRes>, t: Throwable) {
                    Log.d("rty", t.message.toString())
                    // ✅ Suppress error message when offline
                    val showMsg = NetworkUtils.isInternetAvailable(context)
                    loading.postValue(
                        ProgressData(
                            isProgress = false, 
                            isMessage = showMsg, 
                            message = if (showMsg) context.getString(com.retailone.pos.R.string.something_went_wrong_short) else ""
                        )
                    )
                }
            })
    }

    fun callReplaceSaleApi(req: ReplaceSaleReq, context: Context) {
        loading.postValue(ProgressData(isProgress = true))
        Log.e("ReplaceSaleRequest", "Request: $req")

        ApiClient().getApiService(context).replaceSale(req)
            .enqueue(object : Callback<okhttp3.ResponseBody> {
                override fun onResponse(
                    call: Call<okhttp3.ResponseBody>, response: Response<okhttp3.ResponseBody>
                ) {
                    val code = response.code()
                    val ct = response.headers()["Content-Type"]
                    
                    if (response.isSuccessful) {
                        val bodyString = try { response.body()?.string() } catch(_:Exception){ null }
                        if (bodyString.isNullOrEmpty()) {
                            Log.e("ReplaceSale", "Empty body (HTTP $code).")
                            loading.postValue(
                                ProgressData(false, true, "Empty server response")
                            )
                            return
                        }

                        try {
                            val parsedRaw = Gson().fromJson(bodyString, ReturnSaleResRaw::class.java)
                            val normalized = ReturnSaleResMapper.toReturnSaleRes(parsedRaw, Gson())

                            // NOTE: Replace (online or offline) does NOT consume a new
                            // invoice_id on the backend — the replace is attached to
                            // the original sale's invoice — so we intentionally do
                            // NOT bump last_invoice_id here. Only sales and returns
                            // bump the counter.

                            returnsalesubmit_data.postValue(normalized)
                            loading.postValue(ProgressData(isProgress = false))
                        } catch (e: Exception) {
                            Log.e("ReplaceSale", "JSON parse failed for string: $bodyString", e)
                            loading.postValue(
                                ProgressData(false, true, "Response parsing failed")
                            )
                        }
                    } else {
                        val showMsg = NetworkUtils.isInternetAvailable(context)
                        val err = try { response.errorBody()?.string() } catch (e: Exception) { "errorBody read failed: ${e.message}" } ?: "null"
                        Log.e("ReplaceSale", "HTTP $code ct=$ct err=$err")
                        loading.postValue(
                            ProgressData(
                                false,
                                showMsg,
                                if (showMsg) {
                                    context.getString(
                                        com.retailone.pos.R.string.failed_to_fetch_data_with_code,
                                        code.toString()
                                    )
                                } else ""
                            )
                        )
                    }
                }

                override fun onFailure(call: Call<okhttp3.ResponseBody>, t: Throwable) {
                    Log.e("ReplaceSale", "Network/IO error", t)
                    // ✅ Suppress error message when offline
                    val showMsg = NetworkUtils.isInternetAvailable(context)
                    loading.postValue(
                        ProgressData(
                            isProgress = false, 
                            isMessage = showMsg, 
                            message = if (showMsg) context.getString(com.retailone.pos.R.string.something_went_wrong_short) else ""
                        )
                    )
                }
            })
    }

    fun callSaleReturnReasonApi(context: Context) {
        Log.d("RETURN_REASON_DEBUG", "🚀 API CALL: salesreturnreasons started")
        loading.postValue(ProgressData(isProgress = true))

        ApiClient().getApiService(context).getReturnReasonAPI()
            .enqueue(object : Callback<SalesReturnReasonRes> {
                override fun onResponse(
                    call: Call<SalesReturnReasonRes>, response: Response<SalesReturnReasonRes>
                ) {
                    val code = response.code()
                    Log.d("RETURN_REASON_DEBUG", "📡 RESPONSE RECEIVED: Code $code")
                    
                    if (response.isSuccessful && response.body() != null) {
                        val body = response.body()!!
                        Log.d("RETURN_REASON_DEBUG", "✅ SUCCESS: status=${body.status}, reasons_count=${body.data.size}")
                        
                        if (body.data.isEmpty()) {
                            Log.w("RETURN_REASON_DEBUG", "⚠️ WARNING: Response status is 1 but data list is EMPTY")
                        } else {
                            body.data.forEachIndexed { index, reason ->
                                Log.d("RETURN_REASON_DEBUG", "   [$index] ID: ${reason.id}, Name: ${reason.reason_name}")
                            }
                        }

                        salesreturnreason_data.postValue(body)

                        // ✅ Save to local DB for offline use
                        viewModelScope.launch(Dispatchers.IO) {
                            saveReturnReasonsToLocalDB(body.data)
                            Log.d("RETURN_REASON_DEBUG", "💾 Cached ${body.data.size} reasons to local database")
                        }

                        loading.postValue(ProgressData(isProgress = false))
                    } else {
                        val errorBody = response.errorBody()?.string() ?: "No error body"
                        Log.e("RETURN_REASON_DEBUG", "❌ API ERROR: Code $code, Error: $errorBody")
                        
                        val showMsgVal = NetworkUtils.isInternetAvailable(context)
                        loading.postValue(
                            ProgressData(
                                isProgress = false,
                                isMessage = showMsgVal,
                                message = if (showMsgVal) "Failed to fetch data, Try again" else ""
                            )
                        )
                    }
                }

                override fun onFailure(call: Call<SalesReturnReasonRes>, t: Throwable) {
                    Log.e("RETURN_REASON_DEBUG", "❌ FATAL FAILURE: ${t.message}", t)
                    // ✅ Suppress error message when offline
                    val showMsg = NetworkUtils.isInternetAvailable(context)
                            loading.postValue(
                                ProgressData(
                                    isProgress = false, 
                                    isMessage = showMsg, 
                                    message = if (showMsg) context.getString(com.retailone.pos.R.string.something_went_wrong_short) else ""
                                )
                            )
                }
            })
    }

    // ✅ Batch fetch and cache all sales details (background, no blocking)
    fun batchCacheSalesDetails(context: Context, invoiceIds: List<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            invoiceIds.forEach { invoiceId ->
                try {
                    // Check if already cached
                    val exists = detailedSaleExistsInDB(invoiceId)

                    if (!exists) {
                        // Not cached, fetch from API (silently in background)
                        Log.d("BatchCache", "📡 Fetching: $invoiceId")

                        val response = ApiClient().getApiService(context)
                            .getReturnSalesItemAPI(ReturnItemReq(invoice_id = invoiceId))
                            .execute()  // Synchronous call in background thread

                        if (response.isSuccessful && response.body()?.data?.isNotEmpty() == true) {
                            val data = response.body()!!.data[0]
                            saveDetailedSaleToLocalDB(data)
                            Log.d("BatchCache", "💾 Cached: $invoiceId")
                        }
                    } else {
                        Log.d("BatchCache", "✅ Already cached: $invoiceId")
                    }
                } catch (e: Exception) {
                    Log.e("BatchCache", "❌ Failed: $invoiceId - ${e.message}")
                    // Continue with next invoice even if one fails
                }
            }
            Log.d("BatchCache", "🎉 Batch caching complete!")
        }
    }

    /**
     * ✅ Shared counter-bump used for BOTH online and offline returns.
     *
     * The backend assigns a sequential invoice_id to every return the same way
     * it does to every sale (sale=208 -> return=209 -> next sale=210). If we
     * don't reserve that id locally, the next offline sale will generate the
     * same id and collide with the one the server already used for the return.
     *
     * This reads the current last_invoice_id from SharedPreferences, parses
     * its numeric suffix (preserving prefix + zero padding), increments by +1,
     * and writes it back. For OFF_/OFF- fallback ids we skip the bump.
     */
    private fun bumpLastInvoiceIdForReturn(context: Context) {
        // ✅ Delegate to the shared helper in SharedPrefHelper. Every online/offline
        //    sale, return and replace path uses the same bump so the per-store
        //    counter always advances past whatever id the backend consumed.
        com.retailone.pos.localstorage.SharedPreference.SharedPrefHelper(context)
            .bumpLastInvoiceId(tag = "RETURN_COUNTER_BUMP")
    }
}

