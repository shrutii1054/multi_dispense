package com.retailone.pos.ui.Activity.DashboardActivity

import NumberFormatter
import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.util.Log
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import com.retailone.pos.ui.Activity.LocalizedAppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipDrawable
import com.google.gson.Gson
import com.retailone.pos.R
import com.retailone.pos.databinding.ActivityPointofSaleDetailsBinding
import com.retailone.pos.localstorage.DataStore.LoginSession
import com.retailone.pos.localstorage.SharedPreference.LocalizationHelper
import com.retailone.pos.localstorage.SharedPreference.OrganisationDetailsHelper
import com.retailone.pos.models.AddNewCustomerModel.AddNewCustReq
import com.retailone.pos.models.GetCustomerModel.getCustomerReq
import com.retailone.pos.models.LocalizationModel.LocalizationData
import com.retailone.pos.models.PointofsaleModel.PosAddToCartModel.PosAddToCartRes
import com.retailone.pos.models.PointofsaleModel.PosSaleModel.PosSaleReq
import com.retailone.pos.models.PointofsaleModel.PosSaleModel.PosSalesItem
import com.retailone.pos.models.PosSalesDetailsModel.PosSalesDetails
import com.retailone.pos.models.PosSalesDetailsModel.ReceiptType
import com.retailone.pos.ui.Activity.MPOSDashboardActivity
import com.retailone.pos.utils.FunUtils
import com.retailone.pos.utils.PrinterUtil
import com.retailone.pos.viewmodels.DashboardViewodel.PointofSaleViewmodel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

object InvoiceSubmissionTracker {
    private val submitted = mutableSetOf<String>()
    private fun key(storeId: String, invoice: String): String =
        "${storeId.trim()}::${invoice.trim().uppercase()}"
    @Synchronized fun alreadySubmitted(storeId: String, invoice: String) =
        submitted.contains(key(storeId, invoice))
    @Synchronized fun markSubmitted(storeId: String, invoice: String) {
        submitted.add(key(storeId, invoice))
    }
    @Synchronized fun clear() = submitted.clear()
}

class PointofSaleDetailsActivity : LocalizedAppCompatActivity() {
    lateinit var binding: ActivityPointofSaleDetailsBinding

    private data class PaymentMethodOption(val apiValue: String, val labelResId: Int)

    private val pmtmethod_list = listOf(
        PaymentMethodOption("Cash", R.string.cash_label),
        PaymentMethodOption("M-Money", R.string.mmoney_label)
    )

    lateinit var posAddToCartRes: PosAddToCartRes
    lateinit var pos_viewmodel: PointofSaleViewmodel

    var pos_saledata: PosSaleReq? = null

    var payment_type = ""
    lateinit var localizationData: LocalizationData
    var storeid = ""
    var store_manager_id = ""

    var cnamex = ""
    var cmobx = ""
    var ctpinx = ""
    var cidx = 0
    var total_amountx = 0.0
    var spotDiscountPercent = 0.0

    val SALE_LIMIT = 100000.0

    private var printerUtil: PrinterUtil? = null

    private var receiptTypeList = mutableListOf<ReceiptType>()
    private var selectedReceiptType: ReceiptType? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPointofSaleDetailsBinding.inflate(layoutInflater)
        pos_viewmodel = ViewModelProvider(this)[PointofSaleViewmodel::class.java]
        localizationData = LocalizationHelper(this).getLocalizationData()
        setContentView(binding.root)

        enableBackButton()
        printerUtil = PrinterUtil(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            posAddToCartRes = intent.getParcelableExtra("saleitem", PosAddToCartRes::class.java)!!
        } else {
            posAddToCartRes = intent.getParcelableExtra<PosAddToCartRes>("saleitem")!!
        }

        cmobx = intent.getStringExtra("c_mobile").toString()
        cnamex = intent.getStringExtra("c_name").toString()
        cidx = intent.getIntExtra("c_id", 0)
        ctpinx = intent.getStringExtra("c_tpin").toString()
        total_amountx = intent.getDoubleExtra("total_amount", 0.0)
        spotDiscountPercent = intent.getDoubleExtra("spot_discount_percent", 0.0)

        if (cidx != 0) {
            binding.apply {
                nameInput.keyListener = null
                mobileInput.keyListener = null
                tinInput.keyListener = null
                existingcust.isChecked = true
            }
        } else {
            binding.newcust.isChecked = true
        }

        if (isEligibleNewCustomer(total_amountx, cidx)) {
            binding.nameLayout.hint = getString(R.string.customer_name_req)
            binding.mobileLayout.hint = getString(R.string.customer_mobile_req)
            binding.tinLayout.hint = getString(R.string.customer_tin_req)
        } else {
            binding.nameLayout.hint = getString(R.string.customer_name_optional)
            binding.mobileLayout.hint = getString(R.string.customer_mobile_optional)
            binding.tinLayout.hint = getString(R.string.customer_tin_optional)
        }

        if (cidx != 0) {
            binding.nameLayout.hint = getString(R.string.customer_name_label)
            binding.mobileLayout.hint = getString(R.string.customer_mobile_label)
            binding.tinLayout.hint = getString(R.string.customer_tin_label)
        }

        if (cidx == 0 && total_amountx < SALE_LIMIT) {
            binding.toggle.isClickable = true
            binding.existingcust.isClickable = true
            binding.newcust.isClickable = true
        }

        binding.toggle.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == R.id.newcust) {
                binding.nameLayout.hint = getString(R.string.customer_name_optional)
                binding.mobileLayout.hint = getString(R.string.customer_mobile_optional)
                binding.tinLayout.hint = getString(R.string.customer_tin_optional)
            } else {
                binding.nameLayout.hint = getString(R.string.customer_name_req)
                binding.mobileLayout.hint = getString(R.string.customer_mobile_req)
                binding.tinLayout.hint = getString(R.string.customer_tin_optional)
            }
        }

        binding.nameInput.text = Editable.Factory.getInstance().newEditable(cnamex)
        binding.mobileInput.text = Editable.Factory.getInstance().newEditable(cmobx)
        binding.tinInput.text = Editable.Factory.getInstance().newEditable(ctpinx)

        lifecycleScope.launch {
            storeid = LoginSession.getInstance(this@PointofSaleDetailsActivity).getStoreID().first()
                .toString()
            store_manager_id =
                LoginSession.getInstance(this@PointofSaleDetailsActivity).getStoreManagerID()
                    .first().toString()
        }

        // ✅ For offline sales with is_inclusive=false, the displayed Sub Total should be
        //    the "subtotal after discount" (after both item-level and spot discounts). The
        //    raw `sub_total` field keeps the pre-discount base (preserved for server-bound
        //    sync semantics), so for display we compute:
        //       sub_total − discount_amount − spot_discount_amount.
        //    Online mode and inclusive offline mode keep using `sub_total` exactly as the
        //    API/calculator returned it.
        val isOfflineModeForDisplay =
            !com.retailone.pos.utils.NetworkUtils.isInternetAvailable(this)
        val isInclusiveForDisplay = try {
            OrganisationDetailsHelper(this).getOrganisationData().is_inclusive
        } catch (e: Exception) {
            true
        } ?: true
        val subtotalForDisplay: String =
            if (isOfflineModeForDisplay && !isInclusiveForDisplay) {
                val rawSub = posAddToCartRes.sub_total
                val itemDisc = posAddToCartRes.discount_amount
                val spotDisc =
                    posAddToCartRes.spot_discount_amount.toDoubleOrNull() ?: 0.0
                (rawSub - itemDisc - spotDisc).coerceAtLeast(0.0).toString()
            } else {
                posAddToCartRes.sub_total.toString()
            }

        binding.apply {
            subtotal.text = NumberFormatter().formatPrice(
                subtotalForDisplay, localizationData
            )
            val spotAmount = posAddToCartRes.spot_discount_amount.toDoubleOrNull() ?: 0.0
            if (spotAmount > 0.0) {
                spotDiscountRow.isVisible = true
                spotDiscountPercentField.text =
                    getString(R.string.spot_discount_prefix, posAddToCartRes.spot_discount_percentage.toString())
                spotDiscountAmountValue.text = NumberFormatter().formatPrice(
                    posAddToCartRes.spot_discount_amount, localizationData
                )
            } else {
                spotDiscountRow.isVisible = false
            }
            // ✅ DEBUG LOGGING FOR TAX ISSUE
            Log.d("TAX_DEBUG_SCREEN", "========================================")
            Log.d("TAX_DEBUG_SCREEN", "Raw Summary Tax from API: '${posAddToCartRes.tax}'")
            Log.d("TAX_DEBUG_SCREEN", "Raw Tax Amount from API: '${posAddToCartRes.tax_amount}'")

            posAddToCartRes.data.forEachIndexed { index, item ->
                Log.d("TAX_DEBUG_SCREEN", "Item [$index]: ${item.product_name}")
                Log.d("TAX_DEBUG_SCREEN", "   - tax (Int): ${item.tax}")
                Log.d("TAX_DEBUG_SCREEN", "   - taxrate (String): '${item.taxrate}'")
                Log.d("TAX_DEBUG_SCREEN", "   - tax_amount (String): '${item.tax_amount}'")
            }

            // ✅ IMPROVED TAX DISPLAY: Clean string + Item-level fallback if header is 0
            val rawTax = posAddToCartRes.tax
            val cleanedHeaderTax = rawTax.replace("@", "").replace("%", "").trim()
            val headerTaxDouble = cleanedHeaderTax.toDoubleOrNull() ?: 0.0
            val taxAmtVal = posAddToCartRes.tax_amount.toDoubleOrNull() ?: 0.0

            val taxToDisplay = if (headerTaxDouble <= 0.0 && taxAmtVal > 0.0) {
                // If header says 0% but we have a tax amount, try to extract from items
                val maxItemTax = posAddToCartRes.data.map { it.tax }.maxByOrNull { it } ?: 0

                if (maxItemTax > 0) {
                    Log.d("TAX_DEBUG_SCREEN", "Using fallback from item.tax: $maxItemTax")
                    maxItemTax.toString()
                } else {
                    // Try parsing taxrate string if tax Int is 0
                    val firstTaxRateStr = posAddToCartRes.data.mapNotNull {
                        it.taxrate.replace("@", "").replace("%", "").trim().toDoubleOrNull()
                    }.filter { it > 0.0 }.maxByOrNull { it }

                    if (firstTaxRateStr != null) {
                        Log.d(
                            "TAX_DEBUG_SCREEN",
                            "Using fallback from item.taxrate string: $firstTaxRateStr"
                        )
                        firstTaxRateStr.toInt().toString()
                    } else {
                        Log.d(
                            "TAX_DEBUG_SCREEN",
                            "No tax found in items, keeping header: $cleanedHeaderTax"
                        )
                        cleanedHeaderTax
                    }
                }
            } else {
                cleanedHeaderTax
            }

            Log.d("TAX_DEBUG_SCREEN", "FINAL RESOLVED TAX TO DISPLAY: $taxToDisplay")
            Log.d("TAX_DEBUG_SCREEN", "========================================")

            taxfield.text = getString(R.string.tax_label_single, taxToDisplay.replace("%", ""))

            taxAmount.text = NumberFormatter().formatPrice(
                posAddToCartRes.tax_amount.toString(), localizationData
            )
            // ✅ Show normal (item-level) discount row whenever discount_amount > 0,
            //    in both online and offline modes. The discount amount here is the
            //    sum of per-item discounts (different from spot discount which has
            //    its own dedicated row above).
            val discountAmtVal = posAddToCartRes.discount_amount ?: 0.0
            if (discountAmtVal > 0.0) {
                discountRow.isVisible = true
                val roundedDiscount =
                    java.math.BigDecimal(posAddToCartRes.discount_amount.toString())
                        .setScale(0, java.math.RoundingMode.HALF_UP)
                        .toPlainString()
                discountvalue.text =
                    NumberFormatter().formatPrice(roundedDiscount, localizationData)
            } else {
                discountRow.isVisible = false
                discountvalue.text = NumberFormatter().formatPrice("0", localizationData)
            }
            alltotalAmount.text = NumberFormatter().formatPrice(
                java.math.BigDecimal(posAddToCartRes.grand_total.toString())
                    .setScale(0, java.math.RoundingMode.HALF_UP).toPlainString(), localizationData
            )
        }

        pos_viewmodel.loadingLiveData.observe(this) {
            binding.progress.isVisible = it.isProgress
            if (it.isMessage) showMessage(it.message)
        }

        pos_viewmodel.posSaleLivedata.observe(this) { pos_sale_data ->
            if (pos_sale_data.status == 1) {
                try {
                    val storeIdTrim = (storeid ?: "").trim()
                    val invoiceTrim = pos_saledata?.invoice_id?.trim().orEmpty()
                    if (storeIdTrim.isNotEmpty() && invoiceTrim.isNotEmpty()) {
                        InvoiceSubmissionTracker.markSubmitted(storeIdTrim, invoiceTrim)
                    }

                    val _customer_name = binding.nameInput.text.toString()
                    val _mobile_no = binding.mobileInput.text.toString()
                    val _tin_tpin_no = binding.tinInput.text.toString()

                    if (isEligibleNewCustomer(
                            total_amountx,
                            cidx
                        ) && com.retailone.pos.utils.NetworkUtils.isInternetAvailable(this)
                    ) {
                        pos_viewmodel.callAddNewCustApi(
                            AddNewCustReq(
                                customer_name = _customer_name,
                                mobile_no = _mobile_no,
                                tin_tpin_no = _tin_tpin_no
                            ), this
                        )
                    } else if (total_amountx < SALE_LIMIT && cidx == 0 &&
                        _customer_name.trim().isNotEmpty() && _tin_tpin_no.trim().isNotEmpty() &&
                        com.retailone.pos.utils.NetworkUtils.isInternetAvailable(this)
                    ) {
                        pos_viewmodel.callAddNewCustApi(
                            AddNewCustReq(
                                customer_name = _customer_name,
                                mobile_no = _mobile_no,
                                tin_tpin_no = _tin_tpin_no
                            ), this
                        )
                    }
                } catch (e: Exception) {
                    Log.e("POS", "post-sale follow-ups", e)
                } finally {
                    showSucessDialog(pos_sale_data.message, pos_sale_data)
                }
            } else if (pos_sale_data.status == 2) {
                Toast.makeText(this, pos_sale_data.message, Toast.LENGTH_SHORT).show()
            }
        }
        pos_viewmodel.receiptTypeLiveData.observe(this) { response ->
            if (response.status == 1) {
                receiptTypeList.clear()
                receiptTypeList.addAll(response.data ?: emptyList())
                populateReceiptTypeDropdown()
            } else {
                showMessage(getString(R.string.failed_load_receipt_types))
            }
        }

        pos_viewmodel.addNewCustLivedata.observe(this) {
            // unchanged
        }

        for (item in pmtmethod_list) {
            val chip: Chip = getPaymentMethodChip(item.apiValue, getString(item.labelResId))
            binding.paymentmethodChipgroup.addView(chip)
        }

        binding.linearcomplete.setOnClickListener {
            binding.linearcomplete.isEnabled = false
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                binding.linearcomplete.isEnabled = true
            }, 2000)

            validateDetails(
                binding.mobileInput.text.toString(),
                binding.nameInput.text.toString(),
                binding.tinInput.text.toString(),
                binding.invoiceNum.text.toString(),
                payment_type,
                posAddToCartRes
            )
        }

        binding.registerSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                val intent =
                    Intent(this@PointofSaleDetailsActivity, NewCustomerRegisterActivity::class.java)
                launchSomeActivity.launch(intent)
            }
        }

        setToolbarImage()
        fetchReceiptTypes()
        setupReceiptTypeDropdown()
    }

    private fun isEligibleNewCustomer(totalAmountx: Double, cidx: Int): Boolean {
        return totalAmountx >= SALE_LIMIT && cidx == 0
    }

    private val launchSomeActivity =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val data = result.data
                val name = data?.getStringExtra("name")
                showMessage(name ?: "name")
            } else {
                binding.registerSwitch.isChecked = false
            }
        }

    private fun setToolbarImage() {
        val organisation_data = OrganisationDetailsHelper(this).getOrganisationData()
        Glide.with(this)
            .load(organisation_data.image_url + organisation_data.fabicon)
            .fitCenter()
            .placeholder(R.drawable.mlogo)
            .error(R.drawable.mlogo)
            .into(binding.image)
    }

    private fun showSucessDialog(msg: String, pos_sale_data: PosSalesDetails) {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.pos_sucess_dialog)
        dialog.setCancelable(false)
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
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
            val intent =
                Intent(this@PointofSaleDetailsActivity, MPOSDashboardActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
            startActivity(intent)
            finish()
        }

        print_receipt.setOnClickListener {
            Log.d("xxx", Gson().toJson(pos_sale_data))
            printerUtil?.printReceiptData(pos_sale_data)
            dialog.dismiss()
            val intent =
                Intent(this@PointofSaleDetailsActivity, MPOSDashboardActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
            startActivity(intent)
            finish()
        }
        dialog.show()
    }

    // ✅ UPDATED: Handle both String ("@18%") and pass through for numeric tax
    // Also falls back to item-level tax if header is 0 but amount is non-zero
    private fun getTaxString(taxValue: Any, dataRes: PosAddToCartRes): String {
        val rawCleaned = when (taxValue) {
            is String -> {
                // Parse string like "@18%" or "18"
                val cleaned = taxValue.trim()
                    .replace(Regex("[^0-9.,]"), "")
                    .replace(',', '.')
                val normalized = buildString {
                    var dotSeen = false
                    for (ch in cleaned) {
                        if (ch.isDigit()) append(ch)
                        else if (ch == '.' && !dotSeen) {
                            append('.'); dotSeen = true
                        }
                    }
                }
                if (normalized.isEmpty() || normalized == ".") "0"
                else {
                    try {
                        normalized.toBigDecimal().stripTrailingZeros().toPlainString()
                    } catch (_: Exception) {
                        "0"
                    }
                }
            }

            is Int -> taxValue.toString()
            is Double -> taxValue.toInt().toString()
            else -> "0"
        }

        val numericTax = rawCleaned.toDoubleOrNull() ?: 0.0
        val taxAmountVal = dataRes.tax_amount.toDoubleOrNull() ?: 0.0

        return if (numericTax <= 0.0 && taxAmountVal > 0.0) {
            // Fallback to highest item tax rate if header is missing
            val maxItemTax = dataRes.data.map { it.tax }.maxByOrNull { it } ?: 0

            if (maxItemTax > 0) {
                maxItemTax.toString()
            } else {
                // Secondary fallback: parse item-level taxrate strings
                val maxItemTaxRateStr = dataRes.data.mapNotNull {
                    it.taxrate.replace("@", "").replace("%", "").trim().toDoubleOrNull()
                }.filter { it > 0.0 }.maxByOrNull { it }

                maxItemTaxRateStr?.toInt()?.toString() ?: rawCleaned
            }
        } else {
            rawCleaned
        }
    }


    private fun validateDetails(
        mobileInput: String,
        nameInput: String,
        tinInput: String,
        invoiceNum: String,
        paymentType: String,
        data: PosAddToCartRes,
    ) {
        // === STRICT: Name & TIN are mandatory in all cases ===
        /*    if (nameInput.trim().isEmpty()) {
            showMessage(getString(R.string.please_enter_customer_name)); return
        }
        if (tinInput.trim().isEmpty()) {
            showMessage(getString(R.string.please_enter_customer_tin)); return
        }

        // Your existing validations
        if (mobileInput.trim().isNotEmpty() && mobileInput.trim().length != 9) {
            showMessage(getString(R.string.please_enter_valid_mobile_number)); return
        } else if (tinInput.trim().isNotEmpty() && tinInput.trim().length < 9) {
            showMessage(getString(R.string.please_enter_valid_tin_number)); return
        } else if (mobileInput.trim().isEmpty() && binding.toggle.checkedRadioButtonId == R.id.existingcust) {
            showMessage(getString(R.string.please_enter_customer_mobile)); return
        }

        if (paymentType.isBlank()) { showMessage(getString(R.string.please_enter_payment_type)); return }
        if (storeid == "") { showMessage(getString(R.string.couldnt_fetch_store_info)); return }
        if (store_manager_id == "") { showMessage(getString(R.string.couldnt_fetch_store_manager_info)); return }
*/
        //pallab code
        /////////////////////////////////
        val invoiceTrim = invoiceNum.trim()
        val storeIdTrim = (storeid ?: "").trim()
        val isInclusiveForSale =
            OrganisationDetailsHelper(this).getOrganisationData().is_inclusive ?: true
        val item_list = buildFinalSalesItems(data, spotDiscountPercent, isInclusiveForSale)
        if (mobileInput.trim().isNotEmpty() && mobileInput.trim().length != 9) {
            showMessage(getString(R.string.please_enter_valid_mobile_number))
        } else if (tinInput.trim().isNotEmpty() && tinInput.trim().length < 9) {

            showMessage(getString(R.string.please_enter_valid_tin_number))
        } else if (tinInput.trim().isEmpty() && isEligibleNewCustomer(total_amountx, cidx)) {
            showMessage(getString(R.string.please_enter_customer_tin))
        } else if (mobileInput.trim()
                .isEmpty() && (binding.toggle.checkedRadioButtonId == R.id.existingcust || isEligibleNewCustomer(
                total_amountx,
                cidx
            ))
        ) {
            showMessage(getString(R.string.please_enter_customer_mobile))
        } else if (nameInput.trim().isEmpty() && isEligibleNewCustomer(total_amountx, cidx)) {
            showMessage(getString(R.string.please_enter_customer_name))

            // if newcustomer sale lessthen store can change customer type

        } else if (tinInput.trim()
                .isEmpty() && binding.toggle.checkedRadioButtonId == R.id.existingcust && cidx == 0
        ) {
            showMessage(getString(R.string.please_enter_customer_tin))
        }

        /* else if (mobileInput.trim()
                 .isEmpty() && binding.toggle.checkedRadioButtonId == R.id.existingcust && cidx == 0
         ) {
             showMessage(getString(R.string.please_enter_customer_mobile))
         } */
        else if (nameInput.trim()
                .isEmpty() && binding.toggle.checkedRadioButtonId == R.id.existingcust && cidx == 0
        ) {
            showMessage(getString(R.string.please_enter_customer_name))
        }


        /*else if (nameInput.isBlank() || nameInput.isEmpty()) {
            showMessage(getString(R.string.enter_driver_name))
        }*/ else if (paymentType.isBlank() || paymentType.isEmpty()) {
            showMessage(getString(R.string.please_enter_payment_type))
        } else if (storeid == "") {
            showMessage(getString(R.string.couldnt_fetch_store_info))
        } else if (selectedReceiptType == null) {
            showMessage(getString(R.string.please_select_receipt_type))
        } else if (store_manager_id == "") {
            showMessage(getString(R.string.couldnt_fetch_store_manager_info))
        }
        //////////////////////////

        /* else  if (invoiceTrim.isEmpty()) {
            showMessage(getString(R.string.please_enter_valid_invoice_number)); return
        } */
        else if (invoiceTrim.isNotEmpty() && InvoiceSubmissionTracker.alreadySubmitted(
                storeIdTrim,
                invoiceTrim
            )
        ) {
            showMessage(getString(R.string.invoice_number_already_used))
            return
        }

        // === Build de-duplicated list without touching batch internals ===

        else if (item_list.isEmpty()) {
            showMessage(getString(R.string.no_valid_items_to_sell))
            return
        } else {
            val new_grand_total = removeThousandSeparator(data.grand_total)
            var amt_tndr = binding.amtEdit.text.toString()
            if (amt_tndr.isEmpty() || amt_tndr.isBlank()) amt_tndr = "0"
            pos_saledata = PosSaleReq(
                customer_name = nameInput,
                customer_mob_no = mobileInput,
                customer_id = cidx,
                payment_type = paymentType,
                sub_total = data.sub_total.toString(),
                tax = getTaxString(data.tax, data),
                tax_amount = data.tax_amount,
                discount_amount = data.discount_amount.toString(),
                subtotal_after_discount = data.sub_total_after_discount.toString(),
                grand_total = new_grand_total,
                store_id = storeid.toString(),
                sales_items = item_list,
                store_manager_id = store_manager_id,
                amount_tendered = amt_tndr,
                sale_date_time = getSaleDateTime(),
                tin_tpin_no = tinInput,
                invoice_id = "", // Backend will generate this
                prc_no = if (invoiceTrim.isEmpty()) null else invoiceTrim,
                trxn_code = selectedReceiptType?.code ?: "",
                tax_details = null,
                tax_summery = null,
                discount_rate = 0,
                total_after_discount = 0,
                spot_discount_percentage = spotDiscountPercent,
                spot_discount_amount = data.spot_discount_amount,
                is_inclusive = OrganisationDetailsHelper(this).getOrganisationData().is_inclusive
            )

            Log.d("nm", Gson().toJson(pos_saledata))

            val isExistingCustomer = binding.toggle.checkedRadioButtonId == R.id.existingcust
            if (isExistingCustomer) {
                if (!com.retailone.pos.utils.NetworkUtils.isInternetAvailable(this)) {
                    val mobile = binding.mobileInput.text.toString().trim()
                    val tpin = binding.tinInput.text.toString().trim()
                    val localHelper =
                        com.retailone.pos.localstorage.SharedPreference.CustomerLocalHelper(this)
                    val customers = localHelper.getCustomers()
                    val matchedCustomer = customers.find {
                        (mobile.isNotEmpty() && it.mobile_no == mobile) ||
                                (tpin.isNotEmpty() && it.tin_tpin_no == tpin)
                    }
                    if (matchedCustomer == null) {
                        showMessage(getString(R.string.customer_not_in_offline_records))
                        return
                    } else {
                        pos_saledata = pos_saledata?.copy(customer_id = matchedCustomer.id)
                        pos_viewmodel.callposSaleApiPatched(pos_saledata!!, this)
                    }
                } else {
                    // pos_viewmodel.callposSaleApi(pos_saledata!!, this)
                    pos_viewmodel.callposSaleApiPatched(pos_saledata!!, this)
                }
            } else {
                if (!com.retailone.pos.utils.NetworkUtils.isInternetAvailable(this)) {
                    // NEW: If offline, we can't check if the customer already exists 
                    // via API, so just proceed with saving the offline sale directly.
                    pos_viewmodel.callposSaleApiPatched(pos_saledata!!, this)
                } else {
                    // avoid stacking observers on multiple clicks
                    pos_viewmodel.get_customer_liveData.removeObservers(this)
                    pos_viewmodel.callGetCustomerDetailsApi(
                        getCustomerReq(mobile_no = mobileInput, tin_tpin_no = tinInput),
                        this
                    )
                    pos_viewmodel.get_customer_liveData.observe(this) {
                        if (it.status == 1) {
                            showMessage(getString(R.string.customer_already_exists))
                        } else {
                            // pos_viewmodel.callposSaleApi(pos_saledata!!, this)
                            pos_viewmodel.callposSaleApiPatched(pos_saledata!!, this)
                        }
                    }
                }
            }
        }


    }

    /**
     * De-duplicate by (product_id, distribution_pack_id). Before grouping:
     *  - Drop lines where both total == 0 and sum(batch.quantity) == 0.
     * When merging:
     *  - Filter OUT zero-quantity batches so the API never sees them.
     *  - If after filtering, both merged batch quantity and computed total are 0, skip the item.
     *
     * Per-item stored values match the online API convention (post-spot-discount):
     *   whole_sale_price  = tax-exclusive base after item discount AND spot discount
     *   tax_amount        = tax after item discount AND spot discount
     *   total_amount      = gross after item discount AND spot discount
     *   discount          = item discount AMOUNT (not %)
     *
     * This ensures SalesPaymentDetailsActivity shows the same values as the online receipt.
     */
    private fun buildFinalSalesItems(
        data: PosAddToCartRes,
        spotDiscountPct: Double = 0.0,
        isInclusive: Boolean = true
    ): List<PosSalesItem> {
        val src = data.data ?: return emptyList()

        // 1) Drop completely-zero lines early
        val nonZero = src.filter { line ->
            val totalVal = FunUtils.stringToDouble(line.total?.toString() ?: "0")
            val qtySum = (line.batch ?: emptyList()).sumOf { b ->
                FunUtils.stringToDouble(b.quantity?.toString() ?: "0")
            }
            (totalVal > 0.0) || (qtySum > 0.0)
        }
        if (nonZero.isEmpty()) return emptyList()

        // 2) Group and merge
        val grouped = nonZero.groupBy { Pair(it.product_id, it.distribution_pack_id) }
        val result = mutableListOf<PosSalesItem>()
        val spotComplement = 1.0 - spotDiscountPct.coerceAtLeast(0.0) / 100.0

        grouped.forEach { (_, items) ->
            val first = items.first()

            // Keep only batches with qty > 0 so we never send zero lines
            val mergedBatches = items
                .flatMap { it.batch ?: emptyList() }
                .filter { b -> FunUtils.stringToDouble(b.quantity?.toString() ?: "0") > 0.0 }

            val mergedQty = mergedBatches.sumOf { b ->
                FunUtils.stringToDouble(b.quantity?.toString() ?: "0")
            }

            // computedTotal = gross after item discount (from OfflineCartCalculator.total)
            val computedTotal = items.sumOf { FunUtils.stringToDouble(it.total?.toString() ?: "0") }

            // If after merge everything is still zero, skip
            if (mergedQty <= 0.0 && computedTotal <= 0.0) return@forEach

            val mergedTaxRate = first.tax.toDouble()
            val mergedDiscount = items.sumOf { it.discount }

            // Apply spot discount proportionally to this item's gross-after-item-discount.
            // This matches the online API convention where sub_total/tax_amount/total_amount
            // are all post-spot values, so SalesPaymentDetailsActivity shows correct numbers.
            val totalAfterSpot: Double
            val subTotalAfterSpot: Double
            val taxAfterSpot: Double
            if (isInclusive) {
                // Inclusive: spot applied on gross (tax-embedded).
                //   totalAfterSpot    = grossAfterItemDisc * (1 - spot%)
                //   subTotalAfterSpot = totalAfterSpot / (1 + rate)   [tax-exclusive base]
                //   taxAfterSpot      = totalAfterSpot * rate / (1 + rate)
                totalAfterSpot = computedTotal * spotComplement
                val rateFrac = mergedTaxRate / 100.0
                subTotalAfterSpot =
                    if (rateFrac > 0.0) totalAfterSpot / (1.0 + rateFrac) else totalAfterSpot
                taxAfterSpot = totalAfterSpot - subTotalAfterSpot
            } else {
                // Exclusive: spot applied on the tax-exclusive base.
                //   For exclusive, computedTotal = baseAfterDisc + taxAfterDisc.
                //   Back-calc base: baseAfterDisc = computedTotal / (1 + rate)
                //   Apply spot on base, recompute tax.
                val rateFrac = mergedTaxRate / 100.0
                val baseAfterItemDisc =
                    if (rateFrac > 0.0) computedTotal / (1.0 + rateFrac) else computedTotal
                val baseAfterSpot = baseAfterItemDisc * spotComplement
                taxAfterSpot = baseAfterSpot * rateFrac
                totalAfterSpot = baseAfterSpot + taxAfterSpot
                subTotalAfterSpot = baseAfterSpot
            }

            result.add(
                PosSalesItem(
                    product_id = first.product_id.toString(),
                    distribution_pack_id = first.distribution_pack_id.toString(),
                    // whole_sale_price stores the post-spot tax-exclusive base (matches API sub_total)
                    whole_sale_price = java.math.BigDecimal.valueOf(subTotalAfterSpot)
                        .setScale(2, java.math.RoundingMode.HALF_UP).toPlainString(),
                    // total_amount stores the post-spot gross (matches API total_amount)
                    total_amount = java.math.BigDecimal.valueOf(totalAfterSpot)
                        .setScale(2, java.math.RoundingMode.HALF_UP).toPlainString(),
                    batch = mergedBatches,
                    product_name = first.product_name,
                    distribution_pack_name = first.distribution_pack.product_description,
                    uom = first.distribution_pack.uom,
                    tax = mergedTaxRate,
                    // tax_amount stores the post-spot tax (matches API tax_amount)
                    tax_amount = taxAfterSpot,
                    discount = mergedDiscount,
                    // Derive tax code letter from tax rate for offline receipt printing:
                    // 0% → "A" (Zero-rated/Exempt), >0% → "B" (Standard-rated)
                    tax_code = if (mergedTaxRate <= 0.0) "A" else "B"
                )
            )

        }
        return result
    }

    private fun getSaleDateTime(): String {
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
        val dateFormat = SimpleDateFormat("dd-MMM-yyyy hh:mm a", Locale.getDefault())
        dateFormat.timeZone = zambiaTimeZone
        return dateFormat.format(currentDateTime)
    }

    private fun getPaymentMethodChip(apiValue: String, displayLabel: String): Chip {
        val chip = Chip(this)
        chip.setChipDrawable(ChipDrawable.createFromResource(this, R.xml.chipchoice_xml))
        chip.setChipDrawable(
            ChipDrawable.createFromAttributes(this, null, 0, R.style.custom_choice_chips)
        )
        chip.typeface = Typeface.create(
            ResourcesCompat.getFont(this, R.font.avenirnextltpro_medium),
            Typeface.BOLD
        )
        chip.text = displayLabel
        chip.setTextColor(this.getColorStateList(R.color.color_active_inctive_text))
        chip.setOnClickListener { payment_type = apiValue }
        if (apiValue == "Cash") {
            chip.isChecked = true
            payment_type = apiValue
        }
        return chip
    }

    private fun enableBackButton() {
        setSupportActionBar(binding.toolbar)
        val actionbar = supportActionBar
        actionbar!!.title = "New Activity"
        actionbar.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeAsUpIndicator(R.drawable.svg_back_arrow_white)
    }

    private fun showMessage(msg: String) {
        Toast.makeText(this@PointofSaleDetailsActivity, com.retailone.pos.utils.LocalizationUtils.localizeUserMessage(this, msg), Toast.LENGTH_SHORT).show()
    }

    fun removeThousandSeparator(input: String): String {
        return input.replace(Regex("[^\\d.]"), "")
    }

    private fun setupReceiptTypeDropdown() {
        binding.receiptTypeDropdown.setOnItemClickListener { parent, view, position, id ->
            selectedReceiptType = receiptTypeList[position]
            Log.d(
                "ReceiptType",
                "Selected: ${selectedReceiptType?.name} (ID: ${selectedReceiptType?.id})"
            )
        }
    }

    private fun fetchReceiptTypes() {
        pos_viewmodel.callGetReceiptTypesApi(this)
    }

    private fun populateReceiptTypeDropdown() {
        val receiptTypeNames = receiptTypeList.map { it.name }

        // Use a non-filtering adapter to ensure all items are always visible in the dropdown
        val adapter = object : android.widget.ArrayAdapter<String>(
            this,
            android.R.layout.simple_dropdown_item_1line,
            receiptTypeNames
        ) {
            override fun getFilter(): android.widget.Filter {
                return object : android.widget.Filter() {
                    override fun performFiltering(constraint: CharSequence?): FilterResults {
                        val results = FilterResults()
                        results.values = receiptTypeNames
                        results.count = receiptTypeNames.size
                        return results
                    }

                    override fun publishResults(
                        constraint: CharSequence?,
                        results: FilterResults?
                    ) {
                        notifyDataSetChanged()
                    }
                }
            }
        }

        binding.receiptTypeDropdown.setAdapter(adapter)

        // Auto-select "Normal" if not already selected (i.e. on first load or after list refresh)
        if (selectedReceiptType == null) {
            val normalIndex = receiptTypeList.indexOfFirst {
                it.name.contains("Normal", ignoreCase = true)
            }
            if (normalIndex >= 0) {
                selectedReceiptType = receiptTypeList[normalIndex]
                binding.receiptTypeDropdown.setText(receiptTypeList[normalIndex].name, false)
                Log.d("ReceiptType", "Default selected: ${selectedReceiptType?.name} (code=${selectedReceiptType?.code})")
            } else if (receiptTypeList.isNotEmpty()) {
                // Fallback: select the first available type if "Normal" isn't found
                selectedReceiptType = receiptTypeList[0]
                binding.receiptTypeDropdown.setText(receiptTypeList[0].name, false)
                Log.d("ReceiptType", "Fallback default selected: ${selectedReceiptType?.name} (code=${selectedReceiptType?.code})")
            }
        }
    }
}