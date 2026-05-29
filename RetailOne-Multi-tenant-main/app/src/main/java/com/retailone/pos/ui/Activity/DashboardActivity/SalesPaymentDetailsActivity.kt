package com.retailone.pos.ui.Activity.DashboardActivity

import NumberFormatter
import com.retailone.pos.ui.Activity.LocalizedAppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.retailone.pos.R
import com.retailone.pos.adapter.SalesDetailsAdapter
import com.retailone.pos.databinding.ActivitySalesPaymentDetailsBinding
import com.retailone.pos.localstorage.SharedPreference.LocalizationHelper
import com.retailone.pos.localstorage.SharedPreference.OrganisationDetailsHelper
import com.retailone.pos.models.Dispatch.DispatchRequest
import com.retailone.pos.models.LocalizationModel.LocalizationData
import com.retailone.pos.models.SalesPaymentModel.InvoicePayment.CancelSaleitemRequest
import com.retailone.pos.localstorage.DataStore.LoginSession
import com.retailone.pos.models.SalesPaymentModel.SalesDetails.SalesDetailsData
import com.retailone.pos.models.SalesPaymentModel.SalesDetails.SalesDetailsReq
import com.retailone.pos.models.SalesPaymentModel.SalesDetails.SalesDetailsRes
import com.retailone.pos.utils.CalculationHelper
import com.retailone.pos.utils.DateTimeFormatting
import com.retailone.pos.utils.FeatureManager
import com.retailone.pos.utils.NetworkUtils
import com.retailone.pos.utils.PrinterUtil
import kotlinx.coroutines.flow.first
import com.retailone.pos.viewmodels.DashboardViewodel.SalesPaymentViewmodel
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode

class SalesPaymentDetailsActivity : LocalizedAppCompatActivity() {

    lateinit var  binding: ActivitySalesPaymentDetailsBinding
    lateinit var viewmodel: SalesPaymentViewmodel
    lateinit var salesDetailsAdapter: SalesDetailsAdapter
    lateinit var  localizationData: LocalizationData
    private var printerUtil: PrinterUtil? = null
    private var currentSaleDetails: SalesDetailsData? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySalesPaymentDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        viewmodel = ViewModelProvider(this)[SalesPaymentViewmodel::class.java]
        localizationData = LocalizationHelper(this).getLocalizationData()
        printerUtil = PrinterUtil(this)

        // ✅ Initialize repository for offline support
        viewmodel.initRepository(this)

        enableBackButton()
        setToolbarImage()
        prepareRecycleview()
        setupCopyReceiptButton()
        
        val saleid = intent?.getStringExtra("sale_id")
        saleid?.let { id ->
            val saleIdInt = id.toIntOrNull() ?: 0

            lifecycleScope.launch {
                val isOffline = viewmodel.isOfflineSale(this@SalesPaymentDetailsActivity, saleIdInt)
                Log.d("SalesPaymentDetails", "Is offline sale: $isOffline")

                // ✅ 1. Load best available local data first (Instant UI)
                val offlineDetails = viewmodel.getOfflineSaleDetails(this@SalesPaymentDetailsActivity, saleIdInt)
                val cachedDetails = viewmodel.getCachedSalesDetails(saleIdInt)

                // Prefer locally-built data for offline sales, otherwise fall back to the
                // cached API response. Track whether the bootstrap payload came from the
                // API so the renderer knows not to recompute anything over it.
                val localData: SalesDetailsRes?
                val localFromApi: Boolean
                if (isOffline && offlineDetails != null) {
                    localData = offlineDetails
                    localFromApi = false
                } else if (cachedDetails != null) {
                    localData = cachedDetails
                    localFromApi = true
                } else {
                    localData = offlineDetails
                    localFromApi = false
                }

                if (localData != null) {
                    Log.d("SalesPaymentDetails", "✅ Loaded local data baseline (fromApi=$localFromApi)")
                    displaySalesDetails(localData, fromApi = localFromApi)
                }

                // ✅ 2. Priority: Only refresh from API for SYNCED (positive) IDs
                if (NetworkUtils.isInternetAvailable(this@SalesPaymentDetailsActivity) && saleIdInt > 0) {
                    Log.d("OFFLINE_DETAILS_DEBUG", "🌐 Positive ID found - refreshing from API...")
                    viewmodel.callSalesDetailsApi(SalesDetailsReq(id), this@SalesPaymentDetailsActivity)
                } else if (saleIdInt <= 0) {
                    Log.d("OFFLINE_DETAILS_DEBUG", "📦 Local ID ($saleIdInt) - bypassing API call")
                } else if (localData == null) {
                    Log.d("OFFLINE_DETAILS_DEBUG", "📴 Offline and no local data found")
                    showMessage(getString(R.string.no_offline_sales_data))
                }
            }
        }

        viewmodel.loadingLiveData.observe(this){
            binding.progress.isVisible = it.isProgress

            if(it.isMessage)
                showMessage(it.message)
        }

        viewmodel.copyReceiptSale_liveData.observe(this) { receiptRes ->
            if ((receiptRes.status ?: 0) == 1 && receiptRes.data != null) {
                printerUtil?.printCopySaleReceiptData(receiptRes)
            } else {
                showMessage(receiptRes.message ?: "Unable to prepare copy receipt")
            }
        }

        viewmodel.salesdetails_liveData.observe(this){ salesDetailsRes ->

            if(salesDetailsRes.status==1){
                Log.d("SalesPaymentDetails", "✅ API response received")
                
                // ✅ Cache the response for offline use
                val saleid = intent?.getStringExtra("sale_id")
                saleid?.let { id ->
                    lifecycleScope.launch {
                        viewmodel.cacheSalesDetails(
                            id.toIntOrNull() ?: 0,
                            salesDetailsRes.data[0].invoice_id,
                            salesDetailsRes,
                            this@SalesPaymentDetailsActivity
                        )
                    }
                }
                
                // Online path: render API response verbatim — no client-side recomputation.
                displaySalesDetails(salesDetailsRes, fromApi = true)
            }


        }

    }
    
    /**
     * Display sales details data on the screen.
     *
     * @param fromApi When true (online mode / cached API response) the screen renders the
     *                payload verbatim — no client-side recomputation. When false (offline
     *                sale built locally) we fall back to CalculationHelper so totals still
     *                show up even if header fields are zero.
     */
    private fun displaySalesDetails(salesDetailsRes: SalesDetailsRes, fromApi: Boolean = false) {
        val salesdata = salesDetailsRes.data[0]
        currentSaleDetails = salesdata

        // 🔍 Log the response to check discount_amount per item
        salesdata.sales_items.forEachIndexed { index, item ->
            Log.d("SalesDetails", "Item $index: ${item.product_name}")
            Log.d("SalesDetails", "  Sub Total: ${item.sub_total}")
            Log.d("SalesDetails", "  Total: ${item.total_amount}")
            // Check if discount_amount exists in the response
            Log.d("SalesDetails", "  Raw JSON: $item")
        }

        val isInclusive = salesdata.is_inclusive ?: true
        val headerSpotPct = salesdata.spot_discount_percentage?.toDoubleOrNull() ?: 0.0

        // Only run the recompute when we're NOT in online / fromApi mode. In online mode
        // the API payload is the single source of truth and we must not overlay anything
        // the client computed.
        val recomputed = if (fromApi) null else CalculationHelper.computeCart(
            lines = salesdata.sales_items.map { si ->
                CalculationHelper.LineInput(
                    retailPrice = si.retail_price,
                    quantity = si.quantity,
                    taxRate = (si.tax ?: 0).toDouble(),
                    discount = (si.discount ?: 0).toDouble()
                )
            },
            isInclusive = isInclusive,
            spotDiscountPercent = headerSpotPct
        )

        val subtotalValue: Double
        val grandTotal: Double
        val taxValue: Double
        if (fromApi || recomputed == null) {
            // Online / cached API — display API values exactly as returned.
            subtotalValue = salesdata.subtotal_after_discount
            grandTotal = salesdata.grand_total
            taxValue = salesdata.tax_amount
        } else {
            // Offline-built sale — prefer header when populated, otherwise recompute.
            // The displayed "Sub Total" must reflect the after-all-discounts value:
            //   - INCLUSIVE: tax-exclusive base AFTER all discounts (subTotalAfterSpot),
            //     so subtotal+tax=grand_total.
            //   - EXCLUSIVE (is_inclusive=false): tax-exclusive base AFTER both the
            //     item-level discount AND the spot discount, i.e.
            //     subtotal_after_discount − spot_discount_amount on the persisted entity,
            //     or recomputed.subTotalAfterItemDiscount − spotDiscountAmount as fallback.
            // For exclusive offline sales the persisted `salesdata.sub_total` is the
            // PRE-discount base (kept that way to match server-bound sync semantics), so
            // we don't use it directly for display.
            val spotAmtForDisplay =
                salesdata.spot_discount_amount?.toDoubleOrNull() ?: 0.0
            val recomputedSubtotal = if (isInclusive) {
                recomputed.subTotalAfterSpot.toDouble()
            } else {
                (recomputed.subTotalAfterItemDiscount - recomputed.spotDiscountAmount)
                    .coerceAtLeast(java.math.BigDecimal.ZERO)
                    .toDouble()
            }
            subtotalValue = if (!isInclusive) {
                // Exclusive offline: show the after-all-discounts base.
                if (salesdata.subtotal_after_discount > 0.0) {
                    (salesdata.subtotal_after_discount - spotAmtForDisplay)
                        .coerceAtLeast(0.0)
                } else {
                    recomputedSubtotal
                }
            } else {
                // Inclusive offline: persisted sub_total is already after-all-discounts.
                if (salesdata.sub_total > 0.0) salesdata.sub_total else recomputedSubtotal
            }
            grandTotal = if (salesdata.grand_total > 0.0) salesdata.grand_total else recomputed.grandTotal.toDouble()
            taxValue = if (salesdata.tax_amount > 0.0) salesdata.tax_amount else recomputed.taxAmount.toDouble()
        }

        val totalValue = grandTotal
        val safeTax = taxValue

        val formattedPrice = NumberFormatter().formatPrice(
            java.math.BigDecimal.valueOf(grandTotal).setScale(0, java.math.RoundingMode.HALF_UP).toPlainString(),
            localizationData
        )
        binding.apply {
            orderId.text = getString(R.string.receipt_id) + salesdata?.invoice_id?.toString()
            date.text = getString(R.string.receipt_date) + DateTimeFormatting.formatGlobalTime(
                salesdata.created_at,
                localizationData.timezone,
                this@SalesPaymentDetailsActivity
            )
            val spotPercent = salesdata.spot_discount_percentage?.toDoubleOrNull() ?: 0.0
            val headerSpotAmount = salesdata.spot_discount_amount?.toDoubleOrNull() ?: 0.0
            // In online / fromApi mode use the API value verbatim. Only fall back to the
            // recomputed value for offline-built sales that didn't persist a header amount.
            val spotAmount = if (fromApi || recomputed == null) {
                headerSpotAmount
            } else if (headerSpotAmount > 0.0) {
                headerSpotAmount
            } else {
                recomputed.spotDiscountAmount.toDouble()
            }
            if (spotPercent > 0.0 || spotAmount > 0.0) {
                val discountPercent = if (spotPercent % 1.0 == 0.0) spotPercent.toInt().toString() else spotPercent.toString()
                spotDiscountPercentText.isVisible = true
                spotDiscountAmountText.isVisible = true
                spotDiscountPercentText.text = getString(R.string.receipt_spot_discount, discountPercent)
                val roundedSpotAmt = BigDecimal.valueOf(spotAmount).setScale(0, RoundingMode.HALF_UP)
                spotDiscountAmountText.text = getString(R.string.receipt_spot_discount_amount) + NumberFormatter().formatPrice(roundedSpotAmt.toPlainString(), localizationData)
            } else {
                spotDiscountPercentText.isVisible = false
                spotDiscountAmountText.isVisible = false
            }
            grandtotal.text = getString(R.string.receipt_grand_total) + formattedPrice
            paymenttype.text = getString(R.string.receipt_payment_type) + salesdata.payment_type.toString()
            storename.text = getString(R.string.receipt_store_name) + (salesdata.store_details.store_name ?: "")
            //  vat.text = "(+) Tax @"+(salesdata.tax?:"")+"%"+":   "+"ZWL"+(salesdata.tax_amount?:"")
            //customername.text = "Customer name: "+(salesdata.customer.customer_name?:"")
            customername.text = getString(R.string.receipt_customer_name) + (salesdata.customer?.customer_name ?: "N/A")
            val isCancelFeatureEnabled = FeatureManager.isEnabled("cancel sales")
            // binding.btnConfirmcancel.isVisible = salesdata.grand_total >= 0
            
            lifecycleScope.launch {
                val isQueued = viewmodel.isCancelQueued(salesdata.invoice_id, this@SalesPaymentDetailsActivity)
                binding.btnConfirmcancel.isVisible =
                    isCancelFeatureEnabled && salesdata.grand_total >= 0 && salesdata.total_refunded_amount <= 0.0 && !isQueued
                
                if (isQueued) {
                    binding.btnConfirmcancel.text = getString(R.string.cancellation_pending)
                    binding.btnConfirmcancel.isEnabled = false
                }
            }


            val roundedSubtotal = BigDecimal.valueOf(subtotalValue)
                .setScale(0, RoundingMode.HALF_UP)

            tvSubtotalValue.text = NumberFormatter().formatPrice(
                roundedSubtotal.toPlainString(),
                localizationData
            )
            //tvSubtotalValue.text = NumberFormatter().formatPrice(subtotalValue.toString(), localizationData)
            val roundedTaxStr = BigDecimal.valueOf(safeTax)
                .setScale(0, RoundingMode.HALF_UP)
                .toPlainString()

            tvTaxValue.text = NumberFormatter().formatPrice(
                roundedTaxStr,
                localizationData
            )
            // Spot discount row (cart-level %) — use the already-resolved spotAmount
            if (spotAmount > 0.0) {
                binding.discountSummaryRow.isVisible = true
                val spotLabelPct = if (spotPercent > 0.0) {
                    if (spotPercent % 1.0 == 0.0) spotPercent.toInt().toString() else spotPercent.toString()
                } else ""
                binding.tvDiscountLabel.text = if (spotLabelPct.isNotEmpty()) {
                    getString(R.string.spot_discount_prefix, spotLabelPct)
                } else {
                    getString(R.string.spot_discount_minus)
                }
                val roundedDiscount = BigDecimal.valueOf(spotAmount).setScale(0, RoundingMode.HALF_UP)
                binding.tvDiscountValue.text = NumberFormatter().formatPrice(roundedDiscount.toPlainString(), localizationData)
            } else {
                binding.discountSummaryRow.isVisible = false
            }

            // Item-level Discount row — prefer header value; for offline-built sales
            // fall back to summed per-item discounts or the recomputed item-discount total.
            val headerItemDiscount = salesdata.discount_amount.let { if (it.isNaN()) 0.0 else it }
            val itemDiscountAmount: Double = if (fromApi || recomputed == null) {
                headerItemDiscount
            } else if (headerItemDiscount > 0.0) {
                headerItemDiscount
            } else {
                val summed = salesdata.sales_items.sumOf { (it.discount ?: 0).toDouble() }
                if (summed > 0.0) summed else recomputed.itemDiscountTotal.toDouble()
            }
            if (itemDiscountAmount > 0.0) {
                binding.itemDiscountSummaryRow.isVisible = true
                val roundedItemDisc = BigDecimal.valueOf(itemDiscountAmount).setScale(0, RoundingMode.HALF_UP)
                binding.tvItemDiscountValue.text = NumberFormatter().formatPrice(roundedItemDisc.toPlainString(), localizationData)
            } else {
                binding.itemDiscountSummaryRow.isVisible = false
            }

            val roundedTotal = BigDecimal.valueOf(totalValue).setScale(0, RoundingMode.HALF_UP)
            tvTotalValue.text    = NumberFormatter().formatPrice(roundedTotal.toPlainString(), localizationData)
        }

        salesDetailsAdapter = SalesDetailsAdapter(this, salesdata, fromApi = fromApi)

        binding.itemsRcv.adapter = salesDetailsAdapter


        binding.btnConfirmcancel.setOnClickListener {
            //var invoiceId = binding.orderId.text.toString().trim()
            val invoiceIdRaw = binding.orderId.text.toString().trim()
            val invoiceId = invoiceIdRaw.replace("ID:", "").trim()  // ✅ clean prefix


            if (invoiceId.isEmpty()) {
                Toast.makeText(this, getString(R.string.toast_invoice_id_required), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val request = CancelSaleitemRequest(
                invoiceID = invoiceId
            )
            Log.d("CancelSale", "Calling API with invoiceId: $invoiceId")

            // ✅ NEW: Use offline-aware cancel API
            // Get sale details for offline queuing
            val saleId = salesdata?.id ?: 0
            val saleDateTime = salesdata?.created_at ?: ""
            val storeId = (salesdata?.store_id ?: 0).toString()
            val grandTotal = (salesdata?.grand_total ?: 0.0).toString()
            val paymentType = salesdata?.payment_type ?: ""
            // Forward the original sale's Type code so the offline reversal row
            // can display it in the Sales & Payments list.
            val trxnCode = salesdata?.trxn_code

            viewmodel.callCancelSaleAPIOfflineAware(
                request = request,
                context = this,
                saleId = saleId,
                saleDateTime = saleDateTime,
                storeId = storeId,
                grandTotal = grandTotal,
                paymentType = paymentType,
                trxnCode = trxnCode
            )
        }
    }



    private fun setupCopyReceiptButton() {
        binding.btnCopyReceipt.setOnClickListener {
            val sale = currentSaleDetails
            if (sale == null) {
                showMessage(getString(R.string.sale_details_not_loaded))
                return@setOnClickListener
            }
            lifecycleScope.launch {
                val storeId = LoginSession.getInstance(this@SalesPaymentDetailsActivity)
                    .getStoreID().first().orEmpty()
                if (storeId.isEmpty()) {
                    showMessage(getString(R.string.store_id_not_available))
                    return@launch
                }
                viewmodel.requestCopySaleReceipt(sale, storeId, this@SalesPaymentDetailsActivity)
            }
        }
    }

    private fun showMessage(msg: String) {
        Toast.makeText(this@SalesPaymentDetailsActivity, com.retailone.pos.utils.LocalizationUtils.localizeUserMessage(this, msg), Toast.LENGTH_SHORT).show()
    }

    override fun onResume() {
        super.onResume()
        printerUtil?.registerBatteryReceiver()
    }

    override fun onPause() {
        super.onPause()
        printerUtil?.unregisterBatteryReceiver()
    }

    private fun prepareRecycleview() {
        binding.itemsRcv.apply {
            layoutManager = LinearLayoutManager(this@SalesPaymentDetailsActivity,
                RecyclerView.VERTICAL,false)

        }
    }



    private fun setToolbarImage() {
        val organisation_data = OrganisationDetailsHelper(this).getOrganisationData()

        Glide.with(this)
            .load(organisation_data.image_url + organisation_data.fabicon)
            .fitCenter() // Add center crop
            .placeholder(R.drawable.mlogo) // Add a placeholder drawable
            .error(R.drawable.mlogo) // Add an error drawable (if needed)
            .into(binding.image)
    }
    private fun enableBackButton() {
        setSupportActionBar(binding.toolbar)
        //actionbar
        val actionbar = supportActionBar
        //set actionbar title
        actionbar!!.title = "New Activity"
        //set back button
        actionbar.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeAsUpIndicator(R.drawable.svg_back_arrow_white)
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}