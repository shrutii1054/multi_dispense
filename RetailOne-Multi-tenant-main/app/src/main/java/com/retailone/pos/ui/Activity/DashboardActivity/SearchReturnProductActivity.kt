package com.retailone.pos.ui.Activity.DashboardActivity

import NumberFormatter
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.retailone.pos.ui.Activity.LocalizedAppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.google.gson.Gson
import com.retailone.pos.R
import com.retailone.pos.adapter.ReturnReasonAdapter
import com.retailone.pos.adapter.ReturnSalesItemAdapter
import com.retailone.pos.adapter.ReturnSalesItemBatchAdapter
import com.retailone.pos.adapter.SalesListAdapter
import com.retailone.pos.databinding.ActivityReturnSaleBinding
import com.retailone.pos.databinding.ActivitySearchReturnProductBinding
import com.retailone.pos.interfaces.OnReturnQuantityChangeListener
import com.retailone.pos.localstorage.DataStore.LoginSession
import com.retailone.pos.localstorage.SharedPreference.LocalReturnCartHelper
import com.retailone.pos.localstorage.SharedPreference.LocalizationHelper
import com.retailone.pos.localstorage.SharedPreference.OrganisationDetailsHelper
import com.retailone.pos.models.LocalizationModel.LocalizationData
import com.retailone.pos.models.ReplaceModel.ReplaceReturnedItem
import com.retailone.pos.models.ReturnSalesItemModel.BatchReturnItem
import com.retailone.pos.models.ReturnSalesItemModel.ReturnItemData
import com.retailone.pos.models.ReturnSalesItemModel.ReturnItemReq
import com.retailone.pos.models.ReturnSalesItemModel.ReturnSaleReqModel.ReturnSaleReq
import com.retailone.pos.models.ReturnSalesItemModel.ReturnSaleReqModel.ReturnedItem
import com.retailone.pos.models.ReturnSalesItemModel.ReturnSaleResModel.ReturnSaleRes
import com.retailone.pos.models.ReturnSalesItemModel.SalesItem
import com.retailone.pos.models.ReturnSalesItemModel.SalesReturnReasonModel.ReturnReasonData
import com.retailone.pos.ui.Activity.MPOSDashboardActivity
import com.retailone.pos.utils.CalculationHelper
import com.retailone.pos.utils.NetworkUtils
import com.retailone.pos.utils.OfflineReturnReceiptTaxHelper
import com.retailone.pos.utils.PrinterUtil
import com.retailone.pos.viewmodels.DashboardViewodel.ReturnSalesDetailsViewmodel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.log
import kotlin.math.roundToInt

class SearchReturnProductActivity : LocalizedAppCompatActivity(), OnReturnQuantityChangeListener {

    lateinit var binding: ActivitySearchReturnProductBinding
    lateinit var returnsale_viewmodel: ReturnSalesDetailsViewmodel
    lateinit var returnSalesItemAdapter: ReturnSalesItemAdapter
    lateinit var salesListAdapter: SalesListAdapter
    var returnItemList = mutableListOf<SalesItem>()
    var returnReasonList: MutableList<ReturnReasonData> = mutableListOf()
    lateinit var returnItemData: ReturnItemData
    var reasonid = -1
    var storeid = 0
    var store_manager_id = "0"
    lateinit var localizationData: LocalizationData
    private var printerUtil: PrinterUtil? = null

    // ✅ Both batch lists: returnbatchItemList for API, currentBatchList for live recalculation
    private var returnbatchItemList = mutableListOf<BatchReturnItem>()
    private var currentBatchList = mutableListOf<BatchReturnItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySearchReturnProductBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.relativeLayout.isVisible = false
        binding.relativeLayout2.isVisible = false
        val invoiceIdFromIntent = intent.getStringExtra("invoice_id")
        returnsale_viewmodel = ViewModelProvider(this)[ReturnSalesDetailsViewmodel::class.java]
        returnsale_viewmodel.initRepository(this)
        localizationData = LocalizationHelper(this).getLocalizationData()
        val loginSession = LoginSession.getInstance(this)

        lifecycleScope.launch {
            storeid = loginSession.getStoreID().first().toInt()
            store_manager_id = loginSession.getStoreManagerID().first().toString()

            // ✅ STEP 1: Load return reasons FIRST (before displaying any sale)
            val cachedReasons = returnsale_viewmodel.getReturnReasonsFromLocalDB()
            if (cachedReasons.isNotEmpty()) {
                Log.d("ReturnReasons", "✅ Loaded ${cachedReasons.size} reasons from cache")
                returnReasonList = cachedReasons.toMutableList()
                binding.reasonInput.setAdapter(ReturnReasonAdapter(this@SearchReturnProductActivity, 0, returnReasonList))
            } else {
                Log.w("ReturnReasons", "⚠️ No reasons in cache!")
            }

            // ✅ STEP 2: THEN load and display the sale (reasons are now available)
            if (!invoiceIdFromIntent.isNullOrEmpty()) {
                binding.searchBar.setQuery(invoiceIdFromIntent, false)
                returnsale_viewmodel.callReturnSalesDetailsApi(
                    ReturnItemReq(invoice_id = invoiceIdFromIntent, store_id = storeid.toString()),
                    this@SearchReturnProductActivity
                )
            }

            // ✅ STEP 3: Cleanup old data
            returnsale_viewmodel.cleanupOldDetailedSales()
        }

        binding.addcart.setOnClickListener {
            returnbatchItemList.clear()
            currentBatchList.clear()
            if (binding.searchBar.query.toString().trim() == "") {
                showMessage(getString(R.string.enter_valid_invoice_id))
            } else {
                val invoiceId = binding.searchBar.query.toString().trim()
                // ✅ Call unified API hub (handles both online/offline automatically)
                returnsale_viewmodel.callReturnSalesDetailsApi(
                    ReturnItemReq(invoice_id = invoiceId, store_id = storeid.toString()),
                    this@SearchReturnProductActivity
                )
            }
        }

        binding.addproductLayout.setOnClickListener {
            showMessage(getString(R.string.enter_invoice_id_search))
        }

        returnsale_viewmodel.loadingLiveData.observe(this) {
            binding.progress.isVisible = it.isProgress
            if (it.isMessage) showMessage(it.message)
        }

        printerUtil = PrinterUtil(this)
        enableBackButton()
        preparePositemRCV()

        returnsale_viewmodel.callSaleReturnReasonApi(this)

        returnsale_viewmodel.returnitem_liveData.observe(this) {
            if (it.data.isNotEmpty()) {
                val data = it.data[0]
                Log.d("INITIAL_TAX_DEBUG", "========== API RESPONSE ==========")
                Log.d("INITIAL_TAX_DEBUG", "invoice_id: ${data.invoice_id}")
                Log.d("INITIAL_TAX_DEBUG", "sub_total: ${data.sub_total}")
                Log.d("INITIAL_TAX_DEBUG", "tax (String): '${data.tax}'")
                Log.d("INITIAL_TAX_DEBUG", "tax_amount (String): '${data.tax_amount}'")
                Log.d("INITIAL_TAX_DEBUG", "tax_amount (toDouble): ${data.tax_amount?.toDouble() ?: 0.0}")
                Log.d("INITIAL_TAX_DEBUG", "grand_total: ${data.grand_total}")
                Log.d("INITIAL_TAX_DEBUG", "total_refunded_amount: ${data.total_refunded_amount}")
                Log.d("INITIAL_TAX_DEBUG", "spot_discount_percentage: ${data.spot_discount_percentage}")
                Log.d("INITIAL_TAX_DEBUG", "==================================")

                lifecycleScope.launch {
                    returnsale_viewmodel.saveDetailedSaleToLocalDB(data)
                    Log.d("OFFLINE_DEBUG_TAG", "💾 Saved to cache: ${data.invoice_id}")
                }

                // ✅ CRITICAL FIX: Ensure that even if an item has no returns, it still has a batch entry 
                // so the UI isn't blank (especially for returnable/not-yet-returned sales).
                var finalData = data
                if (!data.sales_items.isNullOrEmpty()) {
                    Log.d("DISPLAY_SALE_DEBUG", "   - Mapping sales_items -> salesItems")
                    val mappedItems = data.sales_items!!.map { d ->
                        val itemReturnQty = d.sales_returns?.sumOf { it.return_quantity.toDouble().toInt() } ?: 0
                        val itemReturnReason = d.sales_returns?.firstOrNull()?.reason?.reason_name ?: "Returned"

                        // If already returned, map the returns. If not, map the original batch so the UI card isn't empty.
                        val itemBatches = if (!d.sales_returns.isNullOrEmpty()) {
                            d.sales_returns!!.map { sr ->
                                BatchReturnItem(
                                    batch = d.batch,
                                    quantity = d.quantity,
                                    retail_price = d.retail_price,
                                    tax_exclusive_price = d.tax_exclusive_price,
                                    subtotal = d.total_amount,
                                    product_id = d.product_id,
                                    distribution_pack_id = d.distribution_pack_id,
                                    sales_item_id = d.id,
                                    return_quantity = sr.return_quantity.toDouble().toInt(),
                                    batch_return_quantity = sr.return_quantity.toDouble().toInt(),
                                    return_reason = sr.reason?.reason_name ?: "Returned"
                                )
                            }
                        } else {
                            listOf(
                                BatchReturnItem(
                                    batch = d.batch,
                                    quantity = d.quantity,
                                    retail_price = d.retail_price,
                                    tax_exclusive_price = d.tax_exclusive_price,
                                    subtotal = d.total_amount,
                                    product_id = d.product_id,
                                    distribution_pack_id = d.distribution_pack_id,
                                    sales_item_id = d.id,
                                    return_quantity = 0,
                                    batch_return_quantity = 0,
                                    return_reason = null
                                )
                            )
                        }

                            // ✅ Resolve is_inclusive from org settings if invoice didn't carry it.
                            val isInclusive = resolveIsInclusive(data)
                            val itemTaxPercent = parseTaxPercent(d.tax?.takeIf { it > 0 } ?: data.tax)

                            // ✅ Route the per-unit tax calc through CalculationHelper so that
                            //    is_inclusive True/False behaves the same everywhere, including
                            //    when tax_exclusive_price is absent (offline / legacy sales).
                            val calcTaxAmount = CalculationHelper.computeLine(
                                retailPrice = d.retail_price,
                                quantity = 1.0,
                                taxRate = itemTaxPercent,
                                isInclusive = isInclusive
                            ).tax.toDouble()

                            SalesItem(
                                created_at = null,
                                distribution_pack = d.distribution_pack,
                                distribution_pack_id = d.distribution_pack_id,
                                distribution_pack_name = d.distribution_pack_name ?: d.distribution_pack?.product_description,
                                id = d.id,
                                product = d.product,
                                product_id = d.product_id,
                                product_name = d.product?.product_name ?: d.distribution_pack_name,
                                quantity = d.quantity,
                                batches = itemBatches,
                                retail_price = d.retail_price,
                                tax_exclusive_price = d.tax_exclusive_price,
                                sales_id = null,
                                status = 0,
                                total_amount = d.total_amount,
                                updated_at = null,
                                whole_sale_price = 0.0,
                                tax = itemTaxPercent.toInt(),
                                tax_amount = calcTaxAmount,
                                return_quantity = itemReturnQty,
                                refund_amount = 0.0,
                                return_reason = itemReturnReason,
                                discount = d.discount ?: 0.0,
                                readonlyMode = false,
                                isExpired = false
                            )
                    }
                    finalData = data.copy(salesItems = mappedItems)
                }

                // ✅ Use displaySaleDetails with enriched data
                displaySaleDetails(finalData)

                // ✅ Save enriched data to cache for future offline access
                lifecycleScope.launch {
                    returnsale_viewmodel.saveDetailedSaleToLocalDB(finalData)
                }
            } else {
                showMessage(getString(R.string.no_invoice_found))
                binding.positemRcv.isVisible = false
                binding.addproductLayout.isVisible = true
                binding.reasonLayout.isVisible = false
                binding.summaryCard.isVisible = false
                binding.paymentcard.isVisible = false
                binding.relativeLayout.isVisible = false
                binding.relativeLayout2.isVisible = false
            }
        }

        returnsale_viewmodel.returnsalesubmit_liveData.observe(this) {
            if (it.status == 1) {
                lifecycleScope.launch {
                    val grandTotal = returnItemData.grand_total
                    // ✅ FIX: Pass the actual returned-items list so the cache reflects a
                    //    PARTIAL return (only the items/qty actually returned). Without this,
                    //    DetailedSaleRepository.updateRefundedAmount falls into the FULL-return
                    //    branch and marks every item in the invoice as fully returned, which
                    //    then pollutes the view-only screen (non-returned items appear with
                    //    max return qty). Works the same in online and offline viewing because
                    //    both read from this same cache when the user revisits the invoice.
                    returnsale_viewmodel.updateSaleRefundedAmount(
                        returnItemData.invoice_id ?: "",
                        grandTotal,
                        reasonid,
                        returnbatchItemList
                    )
                    Log.d("OnlineReturn", "💾 Updated cache for ${returnItemData.invoice_id ?: ""} as refunded (partial=${returnbatchItemList.size} items)")
                }
                showSucessDialog(it.message, it)
            } else {
                showMessage(it.message)
            }
        }

        returnsale_viewmodel.loadingLiveData.observe(this) {
            binding.progress.isVisible = it.isProgress
            if (it.isMessage) showMessage(it.message)
        }

        setToolbarImage()

        returnsale_viewmodel.salesreturnreason_liveData.observe(this) {
            returnReasonList = it.data.toMutableList()
            binding.reasonInput.setAdapter(ReturnReasonAdapter(this, 0, returnReasonList))
            Log.d("ReturnReasons", "🔄 Updated with ${it.data.size} reasons from API")
        }

        binding.reasonInput.setOnClickListener {
            if (returnReasonList.isEmpty()) {
                showMessage(getString(R.string.return_reason_not_found))
            }
        }

        binding.reasonInput.setOnItemClickListener { parent, view, position, id ->
            binding.reasonInput.setText(
                com.retailone.pos.utils.LocalizationUtils.getLocalizedStatus(this, returnReasonList[position].reason_name), false)
            reasonid = returnReasonList[position].id
        }

        binding.nextlayout.setOnClickListener {
            if (returnbatchItemList.isNotEmpty()) {
                callReturnAPI(returnbatchItemList)
            } else {
                showMessage(getString(R.string.you_havent_returned_anything))
            }
        }
    }

    // ✅ Unified display method for both online and offline
    private fun displaySaleDetails(data: ReturnItemData) {
        Log.d("DISPLAY_SALE_DEBUG", "========== displaySaleDetails START ==========")
        Log.d("DISPLAY_SALE_DEBUG", "invoice_id: ${data.invoice_id}")
        Log.d("DISPLAY_SALE_DEBUG", "total_refunded_amount (flag): ${data.total_refunded_amount}")
        Log.d("DISPLAY_SALE_DEBUG", "Invoice Header - sub_total: ${data.sub_total}")
        Log.d("DISPLAY_SALE_DEBUG", "Invoice Header - tax_amount: ${data.tax_amount}")
        Log.d("DISPLAY_SALE_DEBUG", "Invoice Header - grand_total: ${data.grand_total}")
        Log.d("DISPLAY_SALE_DEBUG", "Invoice Header - spot_discount%: ${data.spot_discount_percentage}")
        Log.d("DISPLAY_SALE_DEBUG", "Invoice Header - tax (global): ${data.tax}")
        
        Log.d("DISPLAY_SALE_DEBUG", "--- camelCase salesItems (${data.salesItems?.size ?: 0} items) ---")
        data.salesItems?.forEachIndexed { index, item ->
            Log.d("DISPLAY_SALE_DEBUG", "Item[$index]: ${item.product_name}")
            Log.d("DISPLAY_SALE_DEBUG", "   - qty: ${item.quantity}, return_qty: ${item.return_quantity}")
            Log.d("DISPLAY_SALE_DEBUG", "   - price: ${item.retail_price}, tax: ${item.tax}, tax_excl: ${item.tax_exclusive_price}")
            Log.d("DISPLAY_SALE_DEBUG", "   - total: ${item.total_amount}")
        }
        
        Log.d("DISPLAY_SALE_DEBUG", "--- snake_case sales_items (${data.sales_items?.size ?: 0} items) ---")
        data.sales_items?.forEachIndexed { index, item ->
            Log.d("DISPLAY_SALE_DEBUG", "Item[$index]: ${item.product?.product_name ?: item.distribution_pack_name}")
            Log.d("DISPLAY_SALE_DEBUG", "   - qty: ${item.quantity}, returns_count: ${item.sales_returns?.size ?: 0}")
            item.sales_returns?.forEachIndexed { rIndex, ret ->
                Log.d("DISPLAY_SALE_DEBUG", "     - Return[$rIndex]: qty=${ret.return_quantity}, reason=${ret.reason?.reason_name}")
            }
            Log.d("DISPLAY_SALE_DEBUG", "   - price: ${item.retail_price}, tax: ${item.tax}, tax_excl: ${item.tax_exclusive_price}")
        }
        Log.d("DISPLAY_SALE_DEBUG", "========== displaySaleDetails END ==========")

        if (data.total_refunded_amount > 0) {
            // ✅ SALE ALREADY RETURNED - Fetch reason name and update UI
            lifecycleScope.launch {
                Log.d("DISPLAY_SALE", "🔍 Sale is refunded, fetching reason for ID: ${data.reason_id}")

                var reasonName = "Not Given"

                val firstReturnedItem = data.sales_items?.firstOrNull { !it.sales_returns.isNullOrEmpty() }
                val apiReason = firstReturnedItem?.sales_returns?.firstOrNull()?.reason?.reason_name

                if (!apiReason.isNullOrEmpty()) {
                    reasonName = apiReason
                    Log.d("DISPLAY_SALE", "✅ Extracted reason from API: '$reasonName'")
                } else if (data.reason_id > 0) {
                    reasonName = returnsale_viewmodel.getReasonNameById(data.reason_id)
                    Log.d("DISPLAY_SALE", "✅ Fetched reason from local DB: '$reasonName'")
                } else {
                    Log.w("DISPLAY_SALE", "⚠️ reason_id is ${data.reason_id} and API reason missing, showing 'Not Given'")
                }

                withContext(Dispatchers.Main) {
                    returnItemData = data
                    returnItemList = data.salesItems?.toMutableList() ?: mutableListOf()

                    returnSalesItemAdapter = ReturnSalesItemAdapter(
                        returnitem = listOf(data),
                        context = this@SearchReturnProductActivity,
                        onReturnQuantityChangeListener = this@SearchReturnProductActivity,
                        returnReasonName = reasonName,
                        // ✅ Resolve is_inclusive from org settings if invoice didn't carry it.
                        isInclusive = resolveIsInclusive(data),
                        onBatchChange = {
                            Log.d("rtn", it.toString())
                            returnbatchItemList = it.toMutableList()
                            currentBatchList = it.toMutableList()
                        }
                    )

                    binding.positemRcv.adapter = returnSalesItemAdapter
                    binding.positemRcv.isVisible = true
                    binding.addproductLayout.isVisible = false
                    binding.reasonLayout.isVisible = false

                    // ✅ Show summary card
                    binding.summaryCard.isVisible = true

                    val spotPct = parseSpotDiscountPercent(data.spot_discount_percentage)
                    val apiSpotAmt = data.spot_discount_amount?.toDoubleOrNull() ?: 0.0
                    Log.d("DISPLAY_SALE_DEBUG", "Raw API Discount: amt=$apiSpotAmt, pct=$spotPct%")

                    // ✅ SUMMARY FOR ALREADY-RETURNED SALE
                    //
                    // Prefer the server-provided invoice totals (sub_total / tax_amount /
                    // grand_total / spot_discount_amount / discount_amount) — for an
                    // already-returned invoice the API may not populate `salesItems[].return_quantity`
                    // (the refund rows live under `sales_items[].sales_returns[]` instead),
                    // which made the recomputation path yield an empty line list and a
                    // summary card full of zeros. Fall back to recomputing only when the
                    // header values are genuinely missing.
                    // ✅ Resolve is_inclusive from org settings if invoice didn't carry it.
                    val isInclusiveSim = resolveIsInclusive(data)
                    val spotPctSim = spotPct

                    // We ALWAYS recompute the summary from the partially returned quantities rather
                    // than using the original invoice header (grand_total/subtotal_after_discount), so that
                    // partial returns accurately reflect the returned items instead of the full invoice.
                    val summarySubtotal: Double
                    val summaryTax: Double
                    val summaryGrand: Double
                    val summarySpotAmt: Double
                    val summaryItemDiscount: Double

                    val simLines = data.salesItems?.mapNotNull { item ->
                        if (item.return_quantity <= 0) return@mapNotNull null
                        val detailed = data.sales_items?.find { it.id == item.id }
                        val tRate = (detailed?.tax ?: item.tax ?: 0).toDouble()

                        // ✅ Proportional item-level discount for qty being returned
                        val totalDiscount = detailed?.discount ?: item.discount
                        val soldQty = detailed?.quantity ?: item.quantity.toDouble()
                        val itemDiscount = if (totalDiscount > 0.0 && soldQty > 0.0) {
                            CalculationHelper.round2((totalDiscount / soldQty) * item.return_quantity.toDouble())
                        } else 0.0

                        CalculationHelper.LineInput(
                            retailPrice = item.retail_price ?: 0.0,
                            quantity = item.return_quantity.toDouble(),
                            taxRate = tRate,
                            discount = itemDiscount
                        )
                    } ?: emptyList()

                    val simCart = CalculationHelper.computeCart(
                        lines = simLines,
                        isInclusive = isInclusiveSim,
                        spotDiscountPercent = spotPctSim
                    )

                    // ✅ Summary card subtotal:
                    //    - INCLUSIVE: tax-exclusive base AFTER all discounts so subtotal+tax=total
                    //      (grand_total already embeds tax in inclusive mode).
                    //    - EXCLUSIVE: tax-exclusive base AFTER both item-level AND spot
                    //      discounts (i.e. "subtotal after discount"). Equivalent to
                    //      subTotalAfterItemDiscount − spotDiscountAmount.
                    summarySubtotal = if (isInclusiveSim) {
                        simCart.subTotalAfterSpot.toDouble()
                    } else {
                        (simCart.subTotalAfterItemDiscount - simCart.spotDiscountAmount)
                            .coerceAtLeast(java.math.BigDecimal.ZERO)
                            .toDouble()
                    }
                    summaryTax = simCart.taxAmount.toDouble()
                    summaryGrand = CalculationHelper.round0(simCart.grandTotal).toDouble()
                    summarySpotAmt = simCart.spotDiscountAmount.toDouble()
                    summaryItemDiscount = simCart.itemDiscountTotal.toDouble()

                    updateSummaryCard(
                        subtotal = summarySubtotal,
                        tax = summaryTax,
                        total = summaryGrand,
                        spotDiscountPercent = spotPctSim,
                        spotDiscountAmount = summarySpotAmt,
                        itemDiscountAmount = summaryItemDiscount
                    )

                    binding.paymentcard.isVisible = false
                    binding.relativeLayout.isVisible = false
                    binding.relativeLayout2.isVisible = false

                    showMessage(getString(R.string.invoice_already_returned_with_reason, reasonName))
                }
            }

        } else {
            // ✅ NORMAL RETURN FLOW
            returnItemData = data
            returnItemList = data.salesItems?.toMutableList() ?: mutableListOf()

            returnSalesItemAdapter = ReturnSalesItemAdapter(
                returnitem = listOf(data),
                context = this@SearchReturnProductActivity,
                onReturnQuantityChangeListener = this,
                returnReasonName = "Not Given",
                // ✅ Resolve is_inclusive from org settings if invoice didn't carry it.
                isInclusive = resolveIsInclusive(data),
                onBatchChange = {
                    Log.d("rtn", it.toString())
                    returnbatchItemList = it.toMutableList()
                    // ✅ Keep currentBatchList in sync and recalculate with spot discount
                    currentBatchList = it.toMutableList()
                    recalculateTotalsFromBatches()
                }
            )

            binding.positemRcv.adapter = returnSalesItemAdapter
            binding.positemRcv.isVisible = true
            binding.addproductLayout.isVisible = false
            binding.reasonLayout.isVisible = true

            // ✅ Hide summary card for normal returns (bottom layout is used instead)
            binding.summaryCard.isVisible = false

            binding.relativeLayout.isVisible = true
            binding.relativeLayout2.isVisible = true
            binding.paymentcard.isVisible = false

            // ✅ Show original invoice totals (spot discount applied in recalculateTotalsFromBatches when user picks items)
            val subtotalValue = data.subtotal_after_discount ?: 0.0
            val taxValue = data.tax_amount?.toDouble() ?: 0.0
            val grandValue = data.grand_total?.toDouble() ?: 0.0

            val roundedSubtotal = BigDecimal.valueOf(subtotalValue).setScale(0, RoundingMode.HALF_UP)
            val roundedTax = BigDecimal.valueOf(taxValue).setScale(0, RoundingMode.HALF_UP)

            binding.subtotal.setText(roundedSubtotal.toPlainString())

            val taxDisplay = formatTaxForDisplay(data.tax)
            binding.taxfield.setText(getString(R.string.tax_label_multi, taxDisplay))

            binding.taxAmount.setText(roundedTax.toPlainString())
            binding.alltotalAmount.setText(
                NumberFormatter().formatPrice(java.math.BigDecimal.valueOf(grandValue).setScale(0, RoundingMode.HALF_UP).toPlainString(), localizationData)
            )

            // ✅ Show spot discount label in tax field area if applicable
            val spotPct = data.spot_discount_percentage?.toDouble() ?: 0.0
            if (spotPct > 0) {
                binding.spotDiscountRow.isVisible = true
                binding.spotDiscountPercentField.text = getString(R.string.spot_discount_prefix, "%.2f".format(spotPct))
                val spotAmt = subtotalValue * spotPct / 100
                val roundedSpotAmt = BigDecimal.valueOf(spotAmt).setScale(0, RoundingMode.HALF_UP)
                binding.spotDiscountAmountValue.text = NumberFormatter().formatPrice(roundedSpotAmt.toPlainString(), localizationData)
            } else {
                binding.spotDiscountRow.isVisible = false
            }
        }

        Log.d("VISIBILITY_DEBUG", "summaryCard.isVisible = ${binding.summaryCard.isVisible}")
        Log.d("VISIBILITY_DEBUG", "paymentcard.isVisible = ${binding.paymentcard.isVisible}")
        Log.d("VISIBILITY_DEBUG", "relativeLayout.isVisible = ${binding.relativeLayout.isVisible}")
        Log.d("VISIBILITY_DEBUG", "relativeLayout2.isVisible = ${binding.relativeLayout2.isVisible}")
    }

    /**
     * ✅ Updates the summary card (used for already-returned invoices).
     * Shows subtotal, tax, spot discount amount, and grand total.
     */
    private fun updateSummaryCard(
        subtotal: Double,
        tax: Double,
        total: Double,
        spotDiscountPercent: Double = 0.0,
        spotDiscountAmount: Double = 0.0,
        itemDiscountAmount: Double = 0.0
    ) {
        Log.d("OFFLINE_DEBUG_TAG", "updateSummaryCard EXEC: sub=$subtotal, tax=$tax, total=$total, spotDiscount=$spotDiscountAmount ($spotDiscountPercent%), itemDiscount=$itemDiscountAmount")

        val roundedSubtotal = BigDecimal.valueOf(subtotal).setScale(0, RoundingMode.HALF_UP)
        val roundedTax = BigDecimal.valueOf(tax).setScale(0, RoundingMode.HALF_UP)
        val roundedTotal = BigDecimal.valueOf(total).setScale(0, RoundingMode.HALF_UP)

        binding.tvSubtotalValue.text = NumberFormatter().formatPrice(roundedSubtotal.toPlainString(), localizationData)
        binding.tvTaxValue.text = NumberFormatter().formatPrice(roundedTax.toPlainString(), localizationData)
        binding.tvTotalValue.text = NumberFormatter().formatPrice(roundedTotal.toPlainString(), localizationData)

        // ✅ Show spot discount row in summary card if applicable
        if (spotDiscountPercent > 0 && spotDiscountAmount > 0) {
            binding.discountSummaryRow.isVisible = true
            val roundedDiscount = BigDecimal.valueOf(spotDiscountAmount).setScale(0, RoundingMode.HALF_UP)
            binding.tvDiscountLabel.text = getString(R.string.spot_discount_prefix, "%.2f".format(spotDiscountPercent))
            binding.tvDiscountValue.text = NumberFormatter().formatPrice(
                roundedDiscount.toPlainString(),
                localizationData
            )
        } else {
            binding.discountSummaryRow.isVisible = false
        }

        // ✅ Show item-level discount row separately
        if (itemDiscountAmount > 0) {
            binding.itemDiscountSummaryRow.isVisible = true
            val roundedItemDiscount = BigDecimal.valueOf(itemDiscountAmount).setScale(0, RoundingMode.HALF_UP)
            binding.tvItemDiscountValue.text = NumberFormatter().formatPrice(
                roundedItemDiscount.toPlainString(),
                localizationData
            )
        } else {
            binding.itemDiscountSummaryRow.isVisible = false
        }

        // ✅ spotDiscountRow is used by the bottom layout; hide it in summary-card mode
        binding.spotDiscountRow.isVisible = false
    }

    /**
     * ✅ Live recalculation for normal return flow.
     * Applies spot discount on tax-exclusive subtotal, then calculates tax on discounted base.
     * Formula: grandTotal = (subtotal - spotDiscount) + tax_on_discounted_base
     */
    private fun recalculateTotalsFromBatches() {
        Log.e("TAX_FIX_DEBUG", "🔥 recalculateTotalsFromBatches() CALLED")

        if (!this::returnItemData.isInitialized) {
            Log.e("TAX_FIX_DEBUG", "❌ returnItemData NOT initialized yet - exiting")
            return
        }

        binding.relativeLayout.isVisible = true
        binding.relativeLayout2.isVisible = true

        Log.d("TAX_FIX_DEBUG", "========== recalculateTotalsFromBatches() START ==========")
        Log.d("TAX_FIX_DEBUG", "currentBatchList.size: ${currentBatchList.size}")

        // No batches selected → show original invoice totals
        if (currentBatchList.isEmpty() || currentBatchList.all { it.batch_return_quantity == 0 }) {
            Log.d("TAX_FIX_DEBUG", "No batches selected - showing original invoice totals")

            val subtotalValue = returnItemData.subtotal_after_discount ?: 0.0
            val taxValue = returnItemData.tax_amount?.toDouble() ?: 0.0
            val grandValue = returnItemData.grand_total?.toDouble() ?: 0.0

            val roundedSubtotal = BigDecimal.valueOf(subtotalValue).setScale(0, RoundingMode.HALF_UP)
            val roundedTax = BigDecimal.valueOf(taxValue).setScale(0, RoundingMode.HALF_UP)
            val roundedGrandTotal = BigDecimal.valueOf(grandValue).setScale(0, RoundingMode.HALF_UP)

            binding.subtotal.setText(roundedSubtotal.toPlainString())
            binding.taxAmount.setText(roundedTax.toPlainString())
            binding.alltotalAmount.setText(roundedGrandTotal.toPlainString())

            binding.tvSubtotalValue.text = NumberFormatter().formatPrice(roundedSubtotal.toPlainString(), localizationData)
            binding.tvTaxValue.text = NumberFormatter().formatPrice(roundedTax.toPlainString(), localizationData)
            binding.tvTotalValue.text = NumberFormatter().formatPrice(roundedGrandTotal.toPlainString(), localizationData)

            // ✅ Still show spot discount label if invoice has one
            val spotPct = returnItemData.spot_discount_percentage?.toDouble() ?: 0.0
            if (spotPct > 0) {
                val spotAmt = subtotalValue * spotPct / 100
                binding.spotDiscountPercentField.text = getString(R.string.spot_discount_prefix, "%.2f".format(spotPct))
                binding.spotDiscountAmountValue.text = NumberFormatter().formatPrice(spotAmt.toString(), localizationData)
                binding.spotDiscountRow.isVisible = true
            } else {
                binding.spotDiscountRow.isVisible = false
            }

            Log.d("TAX_FIX_DEBUG", "========== recalculateTotalsFromBatches() END (no batches) ==========")
            return
        }

        // ✅ LIVE RECALC — delegate to CalculationHelper so is_inclusive True/False, mixed
        //    taxes and spot discount all stay consistent with the POS cart and
        //    SalesPaymentDetails screens.
        // ✅ Resolve is_inclusive from org settings if invoice didn't carry it — fixes the
        //    case where the API response lacked the flag and the model defaulted to `true`,
        //    causing exclusive-tax invoices to display wrong subtotals / tax / totals.
        val isInclusive = resolveIsInclusive(returnItemData)
        val spotDiscountRate = parseSpotDiscountPercent(returnItemData.spot_discount_percentage)

        val liveLines = currentBatchList.mapNotNull { batch ->
            val q = batch.batch_return_quantity
            if (q <= 0) return@mapNotNull null
            val detailed = returnItemList.find { it.id == batch.sales_item_id }
            val tRate = (detailed?.tax ?: 0).toDouble()

            // ✅ Proportional item-level discount for qty being returned
            val totalDiscount = batch.discount ?: detailed?.discount?.toDouble() ?: 0.0
            val soldQty = batch.quantity?.takeIf { it > 0.0 } ?: detailed?.quantity?.toDouble() ?: 0.0
            val itemDiscount = if (totalDiscount > 0.0 && soldQty > 0.0) {
                CalculationHelper.round2((totalDiscount / soldQty) * q.toDouble())
            } else 0.0

            CalculationHelper.LineInput(
                retailPrice = batch.retail_price ?: 0.0,
                quantity = q.toDouble(),
                taxRate = tRate,
                discount = itemDiscount
            )
        }

        val liveCart = CalculationHelper.computeCart(
            lines = liveLines,
            isInclusive = isInclusive,
            spotDiscountPercent = spotDiscountRate
        )

        // ✅ Subtotal shown:
        //    - INCLUSIVE (is_inclusive=true): tax-exclusive base AFTER all discounts (matches
        //      server convention where grand_total embeds tax, so subtotal+tax=grandTotal).
        //    - EXCLUSIVE (is_inclusive=false): tax-exclusive base AFTER both item-level AND
        //      spot discounts (i.e. "subtotal after discount"). Equivalent to
        //      subTotalAfterItemDiscount − spotDiscountAmount. Matches the POS cart and
        //      PointofSaleDetails screens for offline sales.
        val totalBase = if (isInclusive) {
            liveCart.subTotalAfterSpot
        } else {
            (liveCart.subTotalAfterItemDiscount - liveCart.spotDiscountAmount)
                .coerceAtLeast(java.math.BigDecimal.ZERO)
        }
        val totalTax = liveCart.taxAmount
        val totalSpotAmount = liveCart.spotDiscountAmount
        val totalItemDiscount = liveCart.itemDiscountTotal
        val grandTotal = CalculationHelper.round0(liveCart.grandTotal)

        Log.d(
            "TAX_FIX_DEBUG",
            "LiveRecalc incl=$isInclusive spot=$spotDiscountRate% -> base=$totalBase, " +
                "tax=$totalTax, spot=$totalSpotAmount, grand=$grandTotal"
        )

        // Display rounded values in UI
        val roundedSubtotal = CalculationHelper.round0(totalBase)
        val roundedTax = CalculationHelper.round0(totalTax)
        val roundedGrandTotal = grandTotal

        binding.subtotal.setText(roundedSubtotal.toPlainString())
        binding.taxAmount.setText(roundedTax.toPlainString())
        binding.alltotalAmount.setText(roundedGrandTotal.toPlainString())

        binding.tvSubtotalValue.text = NumberFormatter().formatPrice(roundedSubtotal.toPlainString(), localizationData)
        binding.tvTaxValue.text = NumberFormatter().formatPrice(roundedTax.toPlainString(), localizationData)
        binding.tvTotalValue.text = NumberFormatter().formatPrice(roundedGrandTotal.toPlainString(), localizationData)

        // ✅ Show/hide spot discount row
        if (spotDiscountRate > 0 && totalSpotAmount.toDouble() > 0) {
            val roundedSpotDiscount = totalSpotAmount.setScale(0, RoundingMode.HALF_UP)
            binding.spotDiscountPercentField.text = getString(R.string.spot_discount_prefix, "%.2f".format(spotDiscountRate))
            binding.spotDiscountAmountValue.text = NumberFormatter().formatPrice(roundedSpotDiscount.toPlainString(), localizationData)
            binding.spotDiscountRow.isVisible = true
        } else {
            binding.spotDiscountRow.isVisible = false
        }

        Log.d("TAX_FIX_DEBUG", "========== recalculateTotalsFromBatches() END ==========")
    }

    // ✅ Robustly parses "18", "18%", "@18%" etc to Double (18.0)
    private fun parseTaxPercent(raw: Any?): Double {
        val s = raw?.toString()?.trim().orEmpty()
        if (s.isEmpty()) return 0.0
        return try {
            val numeric = s.replace(Regex("[^0-9.]"), "")
            numeric.toDoubleOrNull() ?: 0.0
        } catch (e: Exception) {
            0.0
        }
    }

    // ✅ Robustly parses spot discount percentage
    private fun parseSpotDiscountPercent(raw: Any?): Double {
        val s = raw?.toString()?.trim().orEmpty()
        if (s.isEmpty()) return 0.0
        return try {
            val numeric = s.replace(Regex("[^0-9.]"), "")
            numeric.toDoubleOrNull() ?: 0.0
        } catch (e: Exception) {
            0.0
        }
    }

    /**
     * ✅ Resolve `is_inclusive` for the current Return using a reliable source of truth.
     *
     * Problem: `ReturnItemData.is_inclusive` defaults to `true` in the model and the online
     * API response doesn't always carry this flag. That caused Return activity to run the
     * INCLUSIVE formulas even when the organisation is configured for EXCLUSIVE tax, which
     * made the Subtotal / Tax / Grand Total numbers wrong (e.g. for retail=17,530 @ 18% with
     * 1% spot: the screen showed Subtotal=14,707, Tax=2,647, Grand=17,355 instead of the
     * correct EXCLUSIVE values Subtotal=17,530, Tax=3,124, Grand=20,479).
     *
     * Resolution order:
     *   1. If the invoice explicitly carries `is_inclusive` (non-null), use it.
     *   2. Else fall back to the Organisation setting (stored in SharedPreferences at login).
     *   3. Else fall back to the DataStore login-session flag.
     *   4. Else default to `true` for backward-compat.
     *
     * This only changes the flag resolution — the actual math continues to flow through
     * `CalculationHelper.computeCart`, which already handles both modes correctly.
     */
    private fun resolveIsInclusive(data: com.retailone.pos.models.ReturnSalesItemModel.ReturnItemData): Boolean {
        // 1) Prefer the explicit value on the invoice if present.
        data.is_inclusive?.let { return it }

        // 2) Organisation-level setting (synchronous, backed by SharedPreferences).
        val orgIsIncl = try {
            OrganisationDetailsHelper(this).getOrganisationData().is_inclusive
        } catch (_: Exception) {
            null
        }
        if (orgIsIncl != null) return orgIsIncl

        // 3) Login-session flag (DataStore — read synchronously via runBlocking since this
        //    is a UI-thread calculation path; the first() call is cached and cheap).
        val sessionIsIncl = try {
            kotlinx.coroutines.runBlocking {
                LoginSession.getInstance(this@SearchReturnProductActivity).isTaxInclusive().first()
            }
        } catch (_: Exception) {
            null
        }
        if (sessionIsIncl != null) return sessionIsIncl

        // 4) Safe default for the rare case where none of the above are set.
        return true
    }

    // ✅ Converts "16.5", "16,5", "16.50%", or "165" -> rounded integer like "17"
    private fun formatTaxForDisplay(raw: Any?): String {
        val s0 = raw?.toString()?.trim().orEmpty()
        if (s0.isEmpty()) return "0"

        val s1 = s0.replace(Regex("[^0-9.,]"), "").replace(',', '.')
        if (s1.isEmpty() || s1 == ".") return "0"

        if (s1.contains('.')) {
            return try {
                val value = BigDecimal(s1)
                val rounded = value.setScale(0, RoundingMode.HALF_UP)
                rounded.toPlainString()
            } catch (_: Exception) {
                s1
            }
        }

        val n = s1.toLongOrNull() ?: return s1
        val scaled = n / 10.0
        return BigDecimal.valueOf(scaled).setScale(0, RoundingMode.HALF_UP).toPlainString()
    }

    /** Purchased (sold) qty for a given sales_item_id (ceil). */
    private fun purchasedQtyFor(salesItemId: Int): Int {
        val q = returnItemData.sales_items.orEmpty().firstOrNull { it.id == salesItemId }?.quantity
            ?: 0.0
        return kotlin.math.ceil(q).toInt()
    }

    private fun buildReplaceLines(): List<ReturnedItem> {

        // 1️⃣ Aggregate defect info per sales_item_id from live batch list
        val defectMap: Map<Int?, Pair<Int, Int>> = returnbatchItemList
            .groupBy { it.sales_item_id }
            .mapValues { (_, batches) ->
                val totalBoxes = batches.sumOf { it.defective_boxes ?: 0 }
                val totalPacks = batches.sumOf { it.defective_bottles ?: 0 }
                totalBoxes to totalPacks
            }

        val output = mutableListOf<ReturnedItem>()

        // 2️⃣ Items saved in local cart (user pressed pencil/save) → always send
        val cartLines = LocalReturnCartHelper.getCartItems(this)
        if (cartLines.isNotEmpty()) {
            cartLines.forEach { line ->
                val (boxes, packs) = defectMap[line.id] ?: (0 to 0)
                val pId = if ((line.product_id ?: 0) > 0) line.product_id!! else 0
                Log.d("OFFLINE_DEBUG_TAG", "   - Queuing Item (Cart Mode): SI_ID=${line.id}, P_ID=$pId, Qty=${line.return_quantity}")
                output.add(
                    ReturnedItem(
                        id = line.id,
                        return_quantity = line.return_quantity,
                        defective_boxes = boxes,
                        defective_bottles = packs,
                        product_id = pId,
                        distribution_pack_id = line.distribution_pack_id
                    )
                )
            }
            return output
        }

        // 3️⃣ No cart, but we have edits in batches → derive qty + defects from batches
        if (defectMap.isNotEmpty()) {
            val qtyMap = returnbatchItemList
                .groupBy { it.sales_item_id }
                .mapValues { (_, batches) ->
                    batches.sumOf { it.return_quantity ?: 0 }
                }

            defectMap.forEach { (salesItemId, boxesPacks) ->
                val (boxes, packs) = boxesPacks
                val userQty = qtyMap[salesItemId] ?: 0
                val firstBatch = returnbatchItemList.find { it.sales_item_id == salesItemId }
                val pId = firstBatch?.product_id ?: 0
                val dId = firstBatch?.distribution_pack_id ?: 0

                Log.d("OFFLINE_DEBUG_TAG", "   - Queuing Item (Batch Mode): SI_ID=$salesItemId, P_ID=$pId, D_ID=$dId, Qty=$userQty")

                output.add(
                    ReturnedItem(
                        id = salesItemId ?: 0,
                        return_quantity = userQty,
                        defective_boxes = boxes,
                        defective_bottles = packs,
                        product_id = pId,
                        distribution_pack_id = dId
                    )
                )
            }

            return output.filter {
                (it.defective_boxes ?: 0) > 0 ||
                        (it.defective_bottles ?: 0) > 0 ||
                        (it.return_quantity ?: 0) > 0
            }
        }

        // 4️⃣ Fallback: no edits at all → return full invoice with 0 defects
        val detailed = returnItemData.sales_items.orEmpty()
        detailed.forEach { si ->
            val pId = if (si.product_id > 0) si.product_id else (si.product?.id ?: 0)
            Log.d("OFFLINE_DEBUG_TAG", "   - Queuing Item (Fallback Mode): SI_ID=${si.id}, P_ID=$pId, Qty=${si.quantity}")
            output.add(
                ReturnedItem(
                    id = si.id,
                    return_quantity = kotlin.math.ceil(si.quantity ?: 0.0).toInt(),
                    defective_boxes = 0,
                    defective_bottles = 0,
                    product_id = pId,
                    distribution_pack_id = si.distribution_pack_id
                )
            )
        }

        return output
    }

    private fun callReturnAPI(returnbatchItemList: MutableList<BatchReturnItem>) {
        val savedItems = buildReplaceLines()
        if (savedItems.isEmpty()) {
            showMessage(getString(R.string.no_items_saved_for_return))
            return
        }
        if (reasonid != -1) {
            val return_data = ReturnSaleReq(
                store_id = storeid,
                store_manager_id = store_manager_id.toInt(),
                reason_id = reasonid,
                sales_id = returnItemData.id,
                returned_items = savedItems
            )

            if (NetworkUtils.isInternetAvailable(this)) {
                Log.d("ReturnSubmit", "📡 Online - submitting immediately")
                returnsale_viewmodel.callReturnSalesSubmitApi(
                    return_data, this@SearchReturnProductActivity
                )
            } else {
                Log.d("ReturnSubmit", "📴 Offline - queuing for later sync")
                lifecycleScope.launch {
                    val queueId = returnsale_viewmodel.queueReturnRequest(returnItemData.invoice_id ?: "", return_data)
                    if (queueId > 0) {
                        showOfflineSuccessDialog()
                    } else {
                        showMessage(getString(R.string.failed_queue_return_request))
                    }
                }
            }

            LocalReturnCartHelper.clearCart(this)
        } else {
            showMessage(getString(R.string.select_return_reason_error))
        }
    }

    private fun showOfflineSuccessDialog() {
        lifecycleScope.launch {
            val grandTotal = returnItemData.grand_total

            Log.d("OfflineReturn", "🔍 BEFORE SAVE: invoice=${returnItemData.invoice_id ?: ""}, reason=$reasonid")

            // ✅ STEP 1: Update cache with refunded amount AND reason_id AND detailed items
            returnsale_viewmodel.updateSaleRefundedAmount(
                returnItemData.invoice_id ?: "",
                grandTotal,
                reasonid,
                returnbatchItemList
            )

            Log.d("OfflineReturn", "💾 Called updateSaleRefundedAmount with reason=$reasonid")

            // ✅ STEP 2: Wait for DB write to complete
            kotlinx.coroutines.delay(200)

            // ✅ STEP 3: Reload sale from cache to get updated data with reason_id
            val updatedSale = returnsale_viewmodel.getDetailedSaleFromLocalDB(returnItemData.invoice_id ?: "")

            if (updatedSale != null) {
                Log.d("OfflineReturn", "🔄 AFTER RELOAD: reason_id=${updatedSale.reason_id}")
                val testReasonName = returnsale_viewmodel.getReasonNameById(updatedSale.reason_id)
                Log.d("OfflineReturn", "🧪 TEST: Fetched reason name = '$testReasonName'")

                // ✅ STEP 4: Update the in-memory data
                returnItemData = updatedSale

                // ✅ STEP 5: Display updated sale details (will show summary card with spot discount)
                displaySaleDetails(updatedSale)
            } else {
                Log.e("OfflineReturn", "❌ Failed to reload sale from cache!")
            }
        }

        val dialog = Dialog(this)
        dialog.setContentView(R.layout.pos_sucess_dialog)
        dialog.setCancelable(false)
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.setCanceledOnTouchOutside(false)

        val confirm = dialog.findViewById<MaterialButton>(R.id.prefer_confirm)
        val logoutMsg = dialog.findViewById<TextView>(R.id.logout_msg)
        val print_receipt = dialog.findViewById<MaterialButton>(R.id.print_receipt)

        logoutMsg.text = getString(R.string.return_request_saved_offline)
        logoutMsg.textSize = 16F
        print_receipt.isVisible = true

        val offlineRes = createOfflineReturnSaleRes()
        print_receipt.setOnClickListener {
            printerUtil?.printReturnReceiptData(offlineRes)
        }

        confirm.setOnClickListener {
            dialog.dismiss()
            val intent = Intent(this@SearchReturnProductActivity, MPOSDashboardActivity::class.java)
            startActivity(intent)
            finish()
        }

        dialog.show()
    }

    private fun createOfflineReturnSaleRes(): com.retailone.pos.models.ReturnSalesItemModel.ReturnSaleResModel.ReturnSaleRes {
        val isInclusive = resolveIsInclusive(returnItemData)
        val spotDiscountRate = parseSpotDiscountPercent(returnItemData.spot_discount_percentage)
        val invoiceTaxSummery = returnItemData.tax_summery

        val lineInputsByCode = linkedMapOf<String, MutableList<CalculationHelper.LineInput>>()
        val taxConfigByCode = linkedMapOf<String, OfflineReturnReceiptTaxHelper.TaxConfigEntry>()

        val returnedItems = buildReplaceLines().mapNotNull { reqItem ->
            val detailed = returnItemList.find { it.id == reqItem.id } ?: return@mapNotNull null
            val tRate = (detailed.tax).toDouble()
            val totalDiscount = detailed.discount
            val soldQty = detailed.quantity
            val itemDiscount = if (totalDiscount > 0.0 && soldQty > 0.0) {
                CalculationHelper.round2((totalDiscount / soldQty) * reqItem.return_quantity.toDouble())
            } else 0.0

            val taxConfig = OfflineReturnReceiptTaxHelper.resolveForRate(tRate, invoiceTaxSummery)
            taxConfigByCode[taxConfig.code] = taxConfig

            val lineInput = CalculationHelper.LineInput(
                retailPrice = detailed.retail_price,
                quantity = reqItem.return_quantity.toDouble(),
                taxRate = tRate,
                discount = itemDiscount
            )
            lineInputsByCode.getOrPut(taxConfig.code) { mutableListOf() }.add(lineInput)

            val lineResult = CalculationHelper.computeLine(
                retailPrice = detailed.retail_price,
                quantity = reqItem.return_quantity.toDouble(),
                taxRate = tRate,
                isInclusive = isInclusive,
                itemDiscount = itemDiscount
            )
            val returnLineTotal = lineResult.total.toDouble()

            com.retailone.pos.models.ReturnSalesItemModel.ReturnSaleResModel.ReturnedItem(
                id = reqItem.id,
                sales_id = returnItemData.id.toString(),
                product_id = reqItem.product_id ?: 0,
                product_name = detailed.product_name ?: "",
                distribution_pack_id = reqItem.distribution_pack_id ?: 0,
                distribution_pack_name = detailed.distribution_pack_name ?: "",
                quantity = detailed.quantity,
                whole_sale_price = detailed.whole_sale_price,
                retail_price = detailed.retail_price,
                total_amount = returnLineTotal,
                batch = null,
                sub_total = lineResult.baseAfterDiscount.toDouble(),
                tax_amount = lineResult.taxAfterDiscount.toDouble(),
                tax_inclusive_price = null,
                return_quantity = reqItem.return_quantity,
                total_returned_amount = returnLineTotal,
                created_at = getReturnDateTime(),
                updated_at = getReturnDateTime(),
                status = 1,
                sales_return_id = null,
                tax_details = OfflineReturnReceiptTaxHelper.toTaxDetails(taxConfig),
                discount = detailed.discount
            )
        }

        val liveLines = lineInputsByCode.values.flatten()
        val liveCart = CalculationHelper.computeCart(
            lines = liveLines,
            isInclusive = isInclusive,
            spotDiscountPercent = spotDiscountRate
        )

        val newTaxAmount = liveCart.taxAmount.toDouble()
        val newSubTotal = if (isInclusive) {
            liveCart.subTotalAfterSpot.toDouble()
        } else {
            (liveCart.subTotalAfterItemDiscount - liveCart.spotDiscountAmount)
                .coerceAtLeast(java.math.BigDecimal.ZERO).toDouble()
        }
        val newGrandTotal = CalculationHelper.round0(liveCart.grandTotal).toDouble()

        val refundTaxAmount = asRefundReceiptTotal(newTaxAmount)
        val refundSubTotal = asRefundReceiptTotal(newSubTotal)
        val refundGrandTotal = asRefundReceiptTotal(newGrandTotal)

        val offlineTaxSummery = OfflineReturnReceiptTaxHelper.buildTaxSummeryForReturn(
            linesByCode = lineInputsByCode,
            taxConfigByCode = taxConfigByCode,
            isInclusive = isInclusive,
            spotDiscountPercent = spotDiscountRate,
            negateForRefund = ::asRefundReceiptTotal
        )

        val storeRes = com.retailone.pos.models.ReturnSalesItemModel.ReturnSaleResModel.Store(
            id = returnItemData.store_details?.id ?: 0,
            store_name = returnItemData.store_details?.store_name ?: "",
            location = returnItemData.store_details?.location ?: "",
            address = returnItemData.store_details?.address ?: "",
            phone_no = returnItemData.store_details?.phone_no ?: "",
            station_code = returnItemData.store_details?.station_code ?: "",
            organization_id = returnItemData.store_details?.organization_id ?: 0,
            ho_manager_id = returnItemData.store_details?.ho_manager_id ?: 0,
            cluster_id = returnItemData.store_details?.cluster_id ?: 0,
            logo = returnItemData.store_details?.logo,
            latitude = returnItemData.store_details?.latitude ?: "",
            longitude = returnItemData.store_details?.longitude ?: "",
            induction_date = returnItemData.store_details?.induction_date ?: "",
            internal_data = null,
            created_at = returnItemData.store_details?.created_at ?: "",
            updated_at = returnItemData.store_details?.updated_at ?: "",
            status = returnItemData.store_details?.status ?: 1,
            deleted_at = returnItemData.store_details?.deleted_at,
            store_incharge = null
        )

        val isoFormat = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.ENGLISH)
        isoFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
        val currentIsoTime = isoFormat.format(java.util.Date())

        val orgData = com.retailone.pos.localstorage.SharedPreference.OrganisationDetailsHelper(this).getOrganisationData()

        val localInvoiceId = com.retailone.pos.localstorage.SharedPreference.SharedPrefHelper(this).getLastInvoiceId()

        val data = com.retailone.pos.models.ReturnSalesItemModel.ReturnSaleResModel.Data(
            returned_items = returnedItems,
            total = refundGrandTotal,
            returned_invoice_id = if (!localInvoiceId.isNullOrEmpty()) localInvoiceId else ("R" + (returnItemData.invoice_id ?: "")),
            tax = returnItemData.tax?.toString(),
            tax_amount = refundTaxAmount,
            tax_ex = "0.00",
            store = storeRes,
            vat_no = null,
            tpin_no = orgData?.registration_no ?: "",
            buyers_tpin = null,
            ej_no = null,
            ej_activation_date = null,
            sdc_id = null,
            receipt_no = null,
            internal_data = null,
            receipt_sign = null,
            rcptType = returnItemData.receipt_type,
            subtal = refundSubTotal.toString(),
            sub_total = refundSubTotal,
            grand_total = refundGrandTotal,
            tax_summery = offlineTaxSummery,
            ogRcpt_no = returnItemData.invoice_id?.filter { it.isDigit() }?.toDoubleOrNull() ?: 0.0,
            returned_date = currentIsoTime,
            customer_name = returnItemData.customer?.customer_name?.toString(),
            vsdc_reciept = null,
            customer_mob_no = returnItemData.customer?.customer_mob_no?.toString()
        )
        return com.retailone.pos.models.ReturnSalesItemModel.ReturnSaleResModel.ReturnSaleRes(
            status = 1, 
            message = "Offline Return", 
            data = data
        )
    }

    /** Online return API sends negative totals/taxes; item lines stay positive (printer adds "-"). */
    private fun asRefundReceiptTotal(amount: Double): Double = -kotlin.math.abs(amount)

    private fun getReturnDateTime(): String {
        val zone = localizationData.timezone
        val timezone = when (zone) {
            "IST" -> "Asia/Kolkata"
            "CAT" -> "Africa/Lusaka"
            else -> "Africa/Lusaka"
        }
        val calendar = Calendar.getInstance()
        val zambiaTimeZone = TimeZone.getTimeZone(timezone)
        calendar.timeZone = zambiaTimeZone
        val currentDateTime = calendar.time
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        dateFormat.timeZone = zambiaTimeZone
        return dateFormat.format(currentDateTime)
    }

    private fun setToolbarImage() {
        val organisation_data = OrganisationDetailsHelper(this).getOrganisationData()
        Glide.with(this).load(organisation_data.image_url + organisation_data.fabicon)
            .fitCenter()
            .placeholder(R.drawable.mlogo)
            .error(R.drawable.mlogo)
            .into(binding.image)
    }

    private fun preparePositemRCV() {
        binding.positemRcv.apply {
            layoutManager = LinearLayoutManager(this@SearchReturnProductActivity, RecyclerView.VERTICAL, false)
        }
    }

    private fun enableBackButton() {
        setSupportActionBar(binding.toolbar)
        val actionbar = supportActionBar
        actionbar!!.title = "New Activity"
        actionbar.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeAsUpIndicator(R.drawable.svg_back_arrow_white)
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    private fun showMessage(msg: String) {
        Toast.makeText(this@SearchReturnProductActivity, com.retailone.pos.utils.LocalizationUtils.localizeUserMessage(this, msg), Toast.LENGTH_SHORT).show()
    }

    override fun onReturnQuantityChange(position: Int, newQuantity: Int) {
        Log.d("QUANTITY_CHANGE_DEBUG", "========== onReturnQuantityChange ==========")
        Log.d("QUANTITY_CHANGE_DEBUG", "position: $position")
        Log.d("QUANTITY_CHANGE_DEBUG", "newQuantity: $newQuantity")
        returnItemList[position].return_quantity = newQuantity
        returnItemList[position].refund_amount = newQuantity * (returnItemList[position].retail_price ?: 0.0)
        Log.d("QUANTITY_CHANGE_DEBUG", "Calling recalculateTotalsFromBatches()")
        // ✅ Delegate to the spot-discount-aware recalculation
        recalculateTotalsFromBatches()
    }

    // ✅ Delegates to batch-aware recalculation (kept for interface compatibility)
    private fun recalculateTotals() {
        recalculateTotalsFromBatches()
    }

    private fun showSucessDialog(msg: String, returnSaleRes: ReturnSaleRes) {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.pos_sucess_dialog)
        dialog.setCancelable(false)
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.setCanceledOnTouchOutside(false)

        val confirm = dialog.findViewById<MaterialButton>(R.id.prefer_confirm)
        val logoutMsg = dialog.findViewById<TextView>(R.id.logout_msg)
        val logoutImg = dialog.findViewById<ImageView>(R.id.dialog_logo)
        val print_receipt = dialog.findViewById<MaterialButton>(R.id.print_receipt)

        logoutMsg.text = com.retailone.pos.utils.LocalizationUtils.getLocalizedApiMessage(this, msg)
        logoutMsg.textSize = 16F

        confirm.setOnClickListener {
            dialog.dismiss()
            val intent = Intent(this@SearchReturnProductActivity, MPOSDashboardActivity::class.java)
            startActivity(intent)
            finish()
        }

        print_receipt.setOnClickListener {
            printerUtil?.printReturnReceiptData(returnSaleRes)
        }
        dialog.show()
    }

    fun dismissKeyboard(view: View) {
        val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }

    override fun onBackPressed() {
        super.onBackPressed()
    }

    override fun onResume() {
        super.onResume()
        printerUtil?.registerBatteryReceiver()
    }

    override fun onPause() {
        super.onPause()
        printerUtil?.unregisterBatteryReceiver()
    }
}