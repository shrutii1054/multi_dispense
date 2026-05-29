package com.retailone.pos.ui.Activity.DashboardActivity

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.retailone.pos.ui.Activity.LocalizedAppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.common.CommonConstants
import com.common.apiutil.pos.RS232Reader
import com.common.callback.IRSReaderListener
import com.google.android.material.button.MaterialButton
import com.retailone.pos.R
import com.retailone.pos.databinding.ActivityCashUpDetailsBinding
import com.retailone.pos.localstorage.DataStore.LoginSession
import com.retailone.pos.localstorage.SharedPreference.LocalizationHelper
import com.retailone.pos.localstorage.SharedPreference.OrganisationDetailsHelper
import com.retailone.pos.models.CashupModel.CashupDetails.CashupDetailsReq
import com.retailone.pos.models.CashupModel.CashupSubmit.CashupSubmitReq
import com.retailone.pos.models.CashupModel.SendOTP.SendOtpReq
import com.retailone.pos.models.CashupModel.VerifyOTP.VerifyOtpReq
import com.retailone.pos.models.LocalizationModel.LocalizationData
import com.retailone.pos.ui.Activity.FetchTOT
import com.retailone.pos.ui.Activity.MPOSLoginActivity
import com.retailone.pos.ui.Activity.RSSerialActivity
import com.retailone.pos.utils.FunUtils
import com.retailone.pos.utils.LanguageManager
import NumberFormatter
import com.retailone.pos.viewmodels.DashboardViewodel.CashupDetailsViewmodel
import com.retailone.pos.viewmodels.MPOSLoginViewmodel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import com.retailone.pos.utils.FeatureManager
import java.util.Locale
import java.util.TimeZone
//Rwanda for this code enabled
class CashUpDetailsActivity : LocalizedAppCompatActivity(), IRSReaderListener {

    lateinit var binding: ActivityCashUpDetailsBinding
    lateinit var localizationData: LocalizationData
    lateinit var viewmodel: CashupDetailsViewmodel
    lateinit var loginViewmodel: MPOSLoginViewmodel

    private var storeid = ""
    private var store_manager_id = ""
    private var cashval = ""
    private var cashedit = ""
    private var cashdiff = ""
    private var pettycashval = ""
    private var pettycashedit = ""
    private var pettycashdiff = ""
    private var ccardedit = ""
    private var ccarddiff = ""
    private var dcardval = ""
    private var dcardedit = ""
    private var dcarddiff = ""
    private var ccardval = ""
    private var endTOTValue = ""


    private var mmonval = ""
    private var mmonedit = ""
    private var mmondiff = ""
    private var cashrefund = ""
    private var startingfloat = ""
    private var expenses = ""
    private var date = ""
    private var bankEditAmount = "0.0"

    ///DU RELATED
    private var lastIssuedCmd: FetchTOT.Last_CMD? = null
    private var polhandler: Handler? = null
    private var polrunnable: Runnable? = null
    private var mRS232Reader: RS232Reader? = null
    private var startTotalizer = ""
    private var endTotalizer = ""
    private val DU_Unit_Id = 1
    private val Hose_No = 1
    private var DU_STATUS = -1
    private var ProcessStep = 0
    private val startTotalizerString = ""
    private lateinit var sharedPreferences: SharedPreferences

    private var mode: String? = ""
    private var str_mode: String? = ""
    private var startPolling = true
    private var DU_Retry_Counter = 0

    private var cashUpDateTime =""

    enum class Last_CMD {
        STATUS, START_TOTALIZER, END_TOTALIZER, PRESET, LAST_TXN
    }

    val PollingTimer: Int = 1000

    ///DU


    private var bankmob = ""
    private var bankstatus = false
    private var bankneed = false
    private var cashupTypeName: String = "Cash Only"
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCashUpDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        sharedPreferences = getSharedPreferences("totalizer_value", Context.MODE_PRIVATE)

        startTotalizer= sharedPreferences.getString("startTOT", "0.0") ?: "0.0"
        str_mode = sharedPreferences.getString("startTOTMode", "OFFLINE") ?: "OFFLINE"


        Log.d("MY Data", "Start Totalizer Value is : $startTotalizer")
        Log.d("MY Data", "Start Totalizer Mode is : $str_mode")



        mRS232Reader = RS232Reader(applicationContext)
        val ret = mRS232Reader?.rsOpen(CommonConstants.RS232Type.RS232_1, 4800, 2)
        mRS232Reader?.setRSReaderListener(this)
        localizationData = LocalizationHelper(this).getLocalizationData()
        enableBackButton()
        getCurrentTime()
        setToolbarImage()
        binding.mobileContainer.isVisible = true
        binding.otpContainer.isVisible = false
        binding.amountContainer.isVisible = false
        binding.notVerified.isVisible = true
        binding.verified.isVisible = false
        binding.sendotp.setOnClickListener {
            binding.mobileContainer.visibility = View.GONE
            binding.otpContainer.visibility = View.VISIBLE
            binding.amountContainer.visibility = View.GONE
        }
        viewmodel = ViewModelProvider(this)[CashupDetailsViewmodel::class.java]
        loginViewmodel = ViewModelProvider(this)[MPOSLoginViewmodel::class.java]
        //val formattedPrice = NumberFormatter().formatPrice(productitem.whole_sale_price?:"-",localizationData)
        lifecycleScope.launch {
            storeid =
                LoginSession.getInstance(this@CashUpDetailsActivity).getStoreID().first().toString()
            store_manager_id =
                LoginSession.getInstance(this@CashUpDetailsActivity).getStoreManagerID().first()
                    .toString()
            viewmodel.callcashupDetailsApi(
                CashupDetailsReq(storeid.toInt(), store_manager_id),
                this@CashUpDetailsActivity
            )
        }
        viewmodel.loadingLiveData.observe(this) {
            binding.progress.isVisible = it.isProgress
            if (it.isMessage)
                showMessage(it.message)
        }
       /* viewmodel.sendotp_liveData.observe(this) {
            if (it.status == 1) {
                binding.mobileContainer.isVisible = false
                binding.otpContainer.isVisible = true
                binding.amountContainer.isVisible = false


                val otp = res.data?.otp_code?.toString().orEmpty()
                binding.showOtp.isVisible = otp.isNotEmpty()
                binding.showOtp.text = getString(R.string.otp_label, otp) // uses the string resource


            } else {
                showMessage(it.message)
            }
        }*/
       // <-- make sure this import exists

                viewmodel.sendotp_liveData.observe(this) { res ->
                    if (res.status == 1) {
                        binding.mobileContainer.isVisible = false
                        binding.otpContainer.isVisible = true
                        binding.amountContainer.isVisible = false

                        // NEW: show OTP in the TextView
                        val otp = res.data?.otp_code?.toString().orEmpty()
                       binding.showOtp.isVisible = otp.isNotEmpty()
                       binding.showOtp.text = getString(R.string.otp_label, otp) // uses the string resource


                    } else {
                        showMessage(res.message)
                    }
                }

        viewmodel.verifyotp_liveData.observe(this) {
            if (it.status == 1) {
                showMessage(it.message)
                bankstatus = true
                binding.mobileContainer.isVisible = false
                binding.otpContainer.isVisible = false
                binding.amountContainer.isVisible = true
                binding.notVerified.isVisible = false
                binding.verified.isVisible = true
            } else {
                showMessage(it.message)
            }
        }
        viewmodel.cashupsubmit_liveData.observe(this) {
            if (it.status == 1) {
                showSucessDialog(it.message ?: "")
            } else {
                showMessage(getString(R.string.cashup_failed))
            }
        }
        viewmodel.cashupdetails_liveData.observe(this) {
            if (it.status == 1) {
                val data = it.data
                cashupTypeName = it.data.cashup_type?.name ?: "Cash Only"

                binding.r1tv2.text = formatValue(data.starting_float)
                binding.r2tv2.text = formatValue(data.cash_payments)
                //binding.r3tv2.text= formatValue(data.expenses)
                binding.r3tv2.text = formatValue(data.petty_cash_out ?: 0.0)
                binding.r7tv2.text = formatValue(data.petty_cash_in ?: 0.0)
                binding.r4tv2.text = formatValue(data.cash_refunds)
                binding.cashVal.text =
                    formatValue(strToDouble(data.cash_payment_actual_amount.toString()))
                binding.ccardVal.text = formatValue(data.creditcard_actual_payment)
                binding.dcardVal.text = formatValue(data.debitcard_actual_payment)
                binding.mmonVal.text = formatValue(data.mmoney_actual_payment)
                binding.pettyCashVal.text = formatValue(data.pettycash_expected ?: 0.0)
                data.PettyCashOpeningbalance?.let {
                    binding.rl8.visibility = View.VISIBLE
                    binding.r8tv2.text = formatValue(data.PettyCashOpeningbalance ?: 0.0)

                }
//                binding.mmonEdit.text = Editable.Factory.getInstance()
//                    .newEditable(formatValue(data.mmoney_actual_payment))
                if (isZeroAmount(data.cash_payment_actual_amount)) {
                    // if cash transaction is zero no nreed for bank transaction
                    binding.cashEdit.text =
                        Editable.Factory.getInstance().newEditable(formatValue(0.00))

                    binding.bankContainer.isVisible = false
                    bankneed = false
                } else {
                    binding.bankContainer.isVisible = true
                    bankneed = true
                }
                val cashVal = strToDouble(data.cash_payment_actual_amount.toString())

                if (cashVal <= 0) {
                    binding.cashEdit.isFocusable = false
                    binding.cashEdit.setOnClickListener {
                        showMessage(getString(R.string.insufficient_expected_cash_up))
                    }
                } else {
                    binding.cashEdit.isFocusable = true

                }

            }

        }
        binding.cashEdit.addTextChangedListener(object : TextWatcher {

            var oldnum: Double = 0.00
            var isUpdating: Boolean = false // Prevent recursive updates

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                oldnum = parseAmount(s)
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (isUpdating) return // Avoid recursive calls

                isUpdating = true // Indicate update in progress

                try {
                    val currentValue = parseAmount(s)

                    val cashVal = parseAmount(binding.cashVal.text)

                    if (currentValue > cashVal) {
                        // Reset the text to the old value
                        binding.cashEdit.text =
                            Editable.Factory.getInstance().newEditable(FunUtils.DtoString(oldnum))
                        showMessage(getString(R.string.actual_cash_exceeds_expected))
                        binding.cashEdit.setSelection(binding.cashEdit.text.length) // Move the cursor to the end
                    } else {
                        val difference = updateDifference(
                            currentValue,
                            parseAmount(binding.cashVal.text)
                        )
                        binding.cashDiff.text = formatValue(difference)
                    }
                } finally {
                    isUpdating = false // Reset update flag
                }
            }

            override fun afterTextChanged(s: Editable?) {}
        })
        binding.pettyCashEdit.addTextChangedListener(object : TextWatcher {
            var oldnum: Double = 0.00
            var isUpdating: Boolean = false
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                oldnum = parseAmount(s)
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {

                if (isUpdating) return // Avoid recursive calls

                isUpdating = true // Indicate update in progress
                try {
                    val pettycurrentValue = parseAmount(s)

                    val pettycashVal = parseAmount(binding.pettyCashVal.text)

                    if (pettycurrentValue > pettycashVal) {
                        // Reset the text to the old value
                        binding.pettyCashEdit.text =
                            Editable.Factory.getInstance().newEditable(FunUtils.DtoString(oldnum))
                        showMessage(getString(R.string.actual_pettycash_exceeds_expected))
                        binding.pettyCashEdit.setSelection(binding.cashEdit.text.length) // Move the cursor to the end
                    } else {
                        val difference = updateDifference(
                            pettycurrentValue,
                            parseAmount(binding.pettyCashVal.text)
                        )
                        binding.pettyCashDiff.text = formatValue(difference)
                    }
                } finally {
                    isUpdating = false // Reset update flag
                }


//                if(binding.pettyCashDiff.text){
//                    showMessage(getString(R.string.actual_pettycash_exceeds_expected))
//                }
//
//
//                val difference = updateDifference(s.toString(), binding.pettyCashVal.text.toString())
//                binding.pettyCashDiff.text = "${String.format("%.2f", difference)}"
            }

            override fun afterTextChanged(s: Editable?) {
            }

        })
        binding.ccardEdit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val difference = updateDifference(
                    parseAmount(s),
                    parseAmount(binding.ccardVal.text)
                )
                binding.ccardDiff.text = formatValue(difference)

            }

            override fun afterTextChanged(s: Editable?) {
            }

        })
        binding.dcardEdit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val difference = updateDifference(
                    parseAmount(s),
                    parseAmount(binding.dcardVal.text)
                )
                binding.dcardDiff.text = formatValue(difference)
            }

            override fun afterTextChanged(s: Editable?) {
            }

        })

        binding.mmonEdit.addTextChangedListener(object :TextWatcher{
            var oldnum: Double = 0.00
            var isUpdating: Boolean = false
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (isUpdating) return
                isUpdating = true
                try {
                    val actualmMoney = parseAmount(s)

                    val mMoney = parseAmount(binding.mmonVal.text)

                    if (actualmMoney > mMoney) {
                        // Reset the text to the old value
                        binding.mmonEdit.text =
                            Editable.Factory.getInstance().newEditable(FunUtils.DtoString(oldnum))
                        showMessage(getString(R.string.actual_mmoney_exceeds_expected))
                        binding.mmonEdit.setSelection(binding.mmonEdit.text.length) // Move the cursor to the end
                    } else {
                        val difference = updateDifference(
                            actualmMoney,
                            parseAmount(binding.mmonVal.text)
                        )
                        binding.mmonDiff.text = formatValue(difference)

                    }
                } finally {
                    isUpdating = false // Reset update flag
                }
            }

            override fun afterTextChanged(s: Editable?) {
            }

        })


        binding.bankAmountEdit.addTextChangedListener(object : TextWatcher {

            var oldnum: Double = 0.00

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                oldnum = parseAmount(s)
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val currentValue = parseAmount(s)

                val cashVal = parseAmount(binding.cashVal.text)

                val mmoneyVal = parseAmount(binding.mmonVal.text)

// Decide allowed limit based on cash-up type
                val allowedLimit = if (cashupTypeName.equals("Cash And M-Money", ignoreCase = true)) {
                    cashVal + mmoneyVal   // allow both
                } else {
                    cashVal               // cash only
                }

                if (currentValue > allowedLimit) {

                    binding.bankAmountEdit.text =
                        Editable.Factory.getInstance().newEditable(FunUtils.DtoString(oldnum))

                    if (cashupTypeName.equals("Cash And M-Money", ignoreCase = true)) {
                        showMessage(getString(R.string.transfer_exceeds_cash_mmoney))
                    } else {
                        showMessage(getString(R.string.transfer_exceeds_expected_cash))
                    }

                    binding.bankAmountEdit.setSelection(binding.bankAmountEdit.text.length)
                    return
                }


                // val difference = updateDifference(currentValue.toString(), binding.cashVal.text.toString())
                // binding.cashDiff.text = String.format("%.2f", difference)
            }

            override fun afterTextChanged(s: Editable?) {}
        })
        binding.sendotp.setOnClickListener {
            bankmob = binding.mobileInput.text.toString()
            if (bankmob.length < 9) {
                showMessage(getString(R.string.enter_valid_mobile))
            } else {
                viewmodel.callSendBankOtpApi(SendOtpReq(bankmob), this)
            }
        }
       // binding.showOtp.setText()
        binding.verifyOtp.setOnClickListener {
            val otp = binding.otpInput.text.toString()

            if (otp.length != 6) {
                showMessage(getString(R.string.please_enter_6_digit_otp))
            } else {
                viewmodel.callVerifyOtpApi(VerifyOtpReq(bankmob, otp), this)
            }
        }
        binding.viewSummary.setOnClickListener {
            startActivity(Intent(this, SalesAndPaymentActivity::class.java))
        }



        binding.nextlayout.setOnClickListener {

            val date = ""
            var bankEditAmount = "0.0"
            if(bankneed){
                bankEditAmount = binding.bankAmountEdit.text.toString().trim()
            }


            val cashval = binding.cashVal.text.toString().trim()
            val cashedit = binding.cashEdit.text.toString().trim()
            val cashdiff = binding.cashDiff.text.toString().trim()

            val pettycashval = binding.pettyCashVal.text.toString().trim()
            val pettycashedit = binding.pettyCashEdit.text.toString().trim()
            val pettycashdiff = binding.pettyCashDiff.text.toString().trim()

            val ccardval = binding.ccardVal.text.toString().trim()
            val ccardedit = binding.ccardEdit.text.toString().trim()
            val ccarddiff = binding.ccardDiff.text.toString().trim()

            val dcardval = binding.dcardVal.text.toString().trim()
            val dcardedit = binding.dcardEdit.text.toString().trim()
            val dcarddiff = binding.dcardDiff.text.toString().trim()

            val mmonval = binding.mmonVal.text.toString().trim()
            val mmonedit = binding.mmonEdit.text.toString().trim()
            val mmondiff = binding.mmonDiff.text.toString().trim()

            val cashrefund = binding.r4tv2.text.toString().trim()
            val startingfloat = binding.r1tv1.text.toString().trim()
            val expenses = binding.r3tv2.text.toString().trim()

            if(cashedit.isBlank()||cashedit.isEmpty() || cashedit.startsWith(".")){
                showMessage(getString(R.string.please_enter_valid_cash_amount))
            }
            else if(pettycashedit.isBlank()||pettycashedit.isEmpty() || pettycashedit.startsWith(".")){
                showMessage(getString(R.string.please_enter_valid_pettycash_amount))

            }else if(bankneed && !bankstatus){
                showMessage(getString(R.string.please_verify_bank_mobile))
            }else if(bankneed && (bankEditAmount.isBlank() || bankEditAmount.isEmpty() || bankEditAmount.startsWith("."))){
                showMessage(getString(R.string.please_enter_valid_transfer_amount))
            } else if(ccardedit.isBlank()||ccardedit.isEmpty() || ccardedit.startsWith(".")){
                showMessage(getString(R.string.please_enter_valid_credit_card_amount))
            }else if(dcardedit.isBlank()||dcardedit.isEmpty() || dcardedit.startsWith(".")){
                showMessage(getString(R.string.please_enter_valid_debit_card_amount))
            }else if(mmonedit.isBlank()||mmonedit.isEmpty()|| mmonedit.startsWith(".")){
                showMessage(getString(R.string.please_enter_valid_mmoney_amount))
            }else if(strToDouble(mmonedit)>strToDouble(mmonval)){
                showMessage(getString(R.string.actual_mmoney_exceeds_expected_short))
            }else{

                val cashupSubmitReq = CashupSubmitReq(

                    amount_given_to_bank= strToDouble(bankEditAmount),
                    cash_payment_actual_amount= strToDouble(cashval),
                    cash_payment_entered_amount= strToDouble(cashedit),
                    cash_refunds= strToDouble(cashrefund),
                    cashup_date_time= getCashupTime(),
                    cit_id=1,
                    creditcard_actual_payment= strToDouble(ccardval),
                    creditcard_entered_payment= strToDouble(ccardedit),
                    debitcard_actual_payment= strToDouble(dcardval),
                    debitcard_entered_payment= strToDouble(dcardedit),
                    expense= strToDouble(expenses),
                    mmoney_actual_payment= strToDouble(mmonval),
                    mmoney_entered_payment= strToDouble(mmonedit),
                    // starting_float= strToDouble(startingfloat),
                    starting_float= strToDouble(cashval)-strToDouble(bankEditAmount),
                    closing_balance = strToDouble(cashval)-strToDouble(bankEditAmount),
                    store_id= storeid.toInt(),
                    store_manager_id= store_manager_id.toInt(),

                    petty_cash_in = strToDouble(binding.r7tv2.text.toString()),
                    petty_cash_out = strToDouble(binding.r3tv2.text.toString()),
                    pettycash_expected = strToDouble(pettycashval),
                    petty_cash_closing_balance_entered = strToDouble(pettycashedit),
                    startTotalizer_value = 0.0,
                    startTotalizer_mode = "",
                    endTotalizer_value = "",
                    endTotalizer_mode = ""
                )

                // Log.d("cashup", Gson().toJson(cashupSubmitReq).toString())

                if(FeatureManager.isEnabled("totalizer")){
                    showConfirmEndTotValue()
                }else{
                    showConfirmCashupDialog(cashupSubmitReq,this)
                }
            }

        }
    }

    //DU


    fun stopPolling() {
        startPolling = false
        if (polhandler != null && polrunnable != null) {
            polhandler!!.removeCallbacks(polrunnable!!)
            Log.d("MY Data", "Polling stopped")
        }
    }

    private fun HexToByteArr(inHex: String): ByteArray {
        var inHex = inHex
        var hexlen = inHex.length
        val result: ByteArray
        if (isOdd(hexlen) == 1) { // Odd
            hexlen++
            result = ByteArray(hexlen / 2)
            inHex = "0$inHex"
        } else { // Even
            result = ByteArray(hexlen / 2)
        }

        var j = 0
        var i = 0
        while (i < hexlen) {
            result[j] = HexToByte(inHex.substring(i, i + 2))
            j++
            i += 2
        }
        return result
    }

    private fun isOdd(num: Int): Int {
        return num and 0x1
    }

    private fun HexToByte(inHex: String): Byte {
        return inHex.toInt(16).toByte()
    }

    private fun showConfirmEndTotValue() {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.fetch_end_tot)
        dialog.setCancelable(false)
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.setCanceledOnTouchOutside(false)
        val cancel = dialog.findViewById<MaterialButton>(R.id.prefer_cancel)
        val confirm = dialog.findViewById<MaterialButton>(R.id.prefer_confirm)
        val endToT = dialog.findViewById<TextView>(R.id.tvendPleaseWait)
        val etEndToT = dialog.findViewById<EditText>(R.id.etendTotalizerValue)
        fun LoopPolling(timer: Int) {
            polhandler = Handler()
            polrunnable = object : Runnable {
                @SuppressLint("SetTextI18n")
                override fun run() {
                    try {
                        Log.d("MY Data", "Polling started 1")
                        if (startPolling) {
                            if (DU_Retry_Counter <= 3) {
                                if (ProcessStep == 0) {
                                    lastIssuedCmd = FetchTOT.Last_CMD.STATUS
                                    mRS232Reader?.rsSend(HexToByteArr("F" + DU_Unit_Id + Hose_No + "0B0"))
                                    DU_Retry_Counter++
                                } else if (ProcessStep == 1) {
                                    if (DU_STATUS == 1) {
                                        lastIssuedCmd = FetchTOT.Last_CMD.END_TOTALIZER
                                        mode = "ONLINE"
                                        mRS232Reader?.rsSend(HexToByteArr("F" + DU_Unit_Id + Hose_No + "9B0"))
                                        endToT.isVisible = true
                                        endToT.text = endTotalizer
                                        confirm.isVisible = true
                                        confirm.text = getString(R.string.proceed_button)
                                    }
                                }
                            } else {
                                startPolling = false
                                endToT.visibility = View.GONE
                                etEndToT.visibility = View.VISIBLE
                                confirm.text = getString(R.string.proceed_button)
                                confirm.isVisible = true
                                mode = "OFFLINE"
                            }
                        }
                        polhandler!!.postDelayed(this, timer.toLong())
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            polhandler!!.post(polrunnable!!)
        }

        confirm.setOnClickListener {
            Log.d("MY Data", "Confirm btn clicked")
            if (confirm.text == "PROCEED") {
                stopPolling()
                endTOTValue = endTotalizer
                if (endTOTValue.isEmpty()) {
                    endTOTValue = etEndToT.text.toString().trim()
                }

                if (endTOTValue.isEmpty()) {
                    Toast.makeText(this, getString(R.string.totalizer_value_error), Toast.LENGTH_SHORT)
                        .show()

                } else {
                    val cashupSubmitReq = CashupSubmitReq(
                        amount_given_to_bank = strToDouble(bankEditAmount),
                        cash_payment_actual_amount = strToDouble(cashval),
                        cash_payment_entered_amount = strToDouble(cashedit),
                        cash_refunds = strToDouble(cashrefund),
                        cashup_date_time = getCashupTime(),
                        cit_id = 1,
                        creditcard_actual_payment = strToDouble(ccardval),
                        creditcard_entered_payment = strToDouble(ccardedit),
                        debitcard_actual_payment = strToDouble(dcardval),
                        debitcard_entered_payment = strToDouble(dcardedit),
                        expense = strToDouble(expenses),
                        mmoney_actual_payment = strToDouble(mmonval),
                        mmoney_entered_payment = strToDouble(mmonedit),
                        // starting_float= strToDouble(startingfloat),
                        starting_float = strToDouble(cashval) - strToDouble(bankEditAmount),
                        closing_balance = strToDouble(cashval) - strToDouble(bankEditAmount),
                        store_id = storeid.toInt(),
                        store_manager_id = store_manager_id.toInt(),
                        petty_cash_in = strToDouble(binding.r7tv2.text.toString()),
                        petty_cash_out = strToDouble(binding.r3tv2.text.toString()),
                        pettycash_expected = strToDouble(pettycashval),
                        petty_cash_closing_balance_entered = strToDouble(pettycashedit),
                        startTotalizer_value = strToDouble(startTotalizer.toString()),
                        startTotalizer_mode = str_mode.toString(),
                        endTotalizer_value = endTOTValue,
                        endTotalizer_mode = mode.toString()
                    )
                    val editor = sharedPreferences.edit()
                    editor.clear()
                    editor.apply()
                    showConfirmCashupDialog(cashupSubmitReq, this)


                }
            } else {
                Log.d("MY Data", "Polling started")
                LoopPolling(PollingTimer)
                confirm.isVisible = false

            }
        }


        cancel.setOnClickListener {
            dialog.dismiss()
        }
        dialog.show()

    }

    private fun updateDifference(enteredValue: Double, actualValue: Double): Double =
        actualValue - enteredValue

    private fun parseAmount(value: CharSequence?): Double =
        if (value.isNullOrBlank()) 0.0 else FunUtils.parseLocaleNumber(value.toString(), appLocale())

    private fun isZeroAmount(value: Double): Boolean = kotlin.math.abs(value) < 0.005

    private fun appLocale(): Locale = Locale(LanguageManager.getSavedLanguage(this))

    fun formatValue(value: Double): String {
        if (!value.isFinite()) return formatValue(0.0)
        return if (localizationData.thousand_separator == "1") {
            NumberFormatter(appLocale()).formatWithThousandSeparator(value)
        } else {
            String.format(appLocale(), "%.2f", value)
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




//original code from pallav


    private fun getCurrentTime() {
        val zone = localizationData.timezone
        val timezone = when (zone) {
            "IST" -> "Asia/Kolkata"
            "CAT" -> "Africa/Lusaka"
            else -> "Africa/Lusaka"
        }

        val dateFormat = SimpleDateFormat("dd-MMM-yyyy hh:mm a", Locale.getDefault())
        dateFormat.timeZone = TimeZone.getTimeZone(timezone)

        val inputFormat = SimpleDateFormat("dd-MMM-yyyy hh:mm a", Locale.getDefault())
        inputFormat.timeZone = TimeZone.getTimeZone(timezone)

        val cashupDateTime = intent.getStringExtra("CASHUP_DATE_TIME")
        val rawDate = cashupDateTime?.trim()?.replace("\"", "")

        val displayDateTime: String = if (!rawDate.isNullOrEmpty()) {
            try {
                val parsedDate = inputFormat.parse(rawDate)
                val cashupCal = Calendar.getInstance()
                cashupCal.time = parsedDate ?: Date()
                cashupCal.timeZone = TimeZone.getTimeZone(timezone)

                val todayCal = Calendar.getInstance()
                todayCal.timeZone = TimeZone.getTimeZone(timezone)

                val isSameDay =
                    cashupCal.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR) &&
                            cashupCal.get(Calendar.DAY_OF_YEAR) == todayCal.get(Calendar.DAY_OF_YEAR)

                if (isSameDay) {
                    // Show actual cashup time if it's today
                    dateFormat.format(cashupCal.time)
                } else {
                    // Show yesterday's date and time (today - 1 day)
                    todayCal.add(Calendar.DAY_OF_YEAR, -1)
                    dateFormat.format(todayCal.time)
                }
            } catch (e: Exception) {
                dateFormat.format(Date())
            }
        } else {
            dateFormat.format(Date())
        }

        binding.calenderText.text = getString(R.string.cashup_time_dynamic, displayDateTime)
    }

    private fun getCashupTime(): String {
        val zone = localizationData.timezone
        val timezone = when (zone) {
            "IST" -> "Asia/Kolkata"
            "CAT" -> "Africa/Lusaka"
            else -> "Africa/Lusaka"
        }

        val dateFormat = SimpleDateFormat("dd-MMM-yyyy hh:mm a", Locale.getDefault())
        dateFormat.timeZone = TimeZone.getTimeZone(timezone)

        val inputFormat = SimpleDateFormat("dd-MMM-yyyy hh:mm a", Locale.getDefault())
        inputFormat.timeZone = TimeZone.getTimeZone(timezone)

        val cashupDateTime = intent.getStringExtra("CASHUP_DATE_TIME")
        val rawDate = cashupDateTime?.trim()?.replace("\"", "")

        return if (!rawDate.isNullOrEmpty()) {
            try {
                val parsedDate = inputFormat.parse(rawDate)
                val cashupCal = Calendar.getInstance()
                cashupCal.time = parsedDate ?: Date()
                cashupCal.timeZone = TimeZone.getTimeZone(timezone)

                val todayCal = Calendar.getInstance()
                todayCal.timeZone = TimeZone.getTimeZone(timezone)

                val isSameDay =
                    cashupCal.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR) &&
                            cashupCal.get(Calendar.DAY_OF_YEAR) == todayCal.get(Calendar.DAY_OF_YEAR)

                if (isSameDay) {
                    // Show actual cashup time if it's today
                    dateFormat.format(cashupCal.time)
                } else {
                    // Show yesterday's date with current time
                    val yesterdayCal = Calendar.getInstance()
                    yesterdayCal.timeZone = TimeZone.getTimeZone(timezone)
                    yesterdayCal.add(Calendar.DAY_OF_YEAR, -1)

                    // Preserve the current time
                    yesterdayCal.set(Calendar.HOUR_OF_DAY, todayCal.get(Calendar.HOUR_OF_DAY))
                    yesterdayCal.set(Calendar.MINUTE, todayCal.get(Calendar.MINUTE))
                    yesterdayCal.set(Calendar.SECOND, todayCal.get(Calendar.SECOND))
                    yesterdayCal.set(Calendar.MILLISECOND, todayCal.get(Calendar.MILLISECOND))

                    dateFormat.format(yesterdayCal.time)
                }
            } catch (e: Exception) {
                // Fallback to current datetime
                dateFormat.format(Date())
            }
        } else {
            // If no cashup time, return current datetime
            dateFormat.format(Date())
        }
    }


    private fun showMessage(msg: String) {
        Toast.makeText(this@CashUpDetailsActivity, com.retailone.pos.utils.LocalizationUtils.localizeUserMessage(this, msg), Toast.LENGTH_SHORT).show()
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

    fun strToDouble(input: String): Double = FunUtils.parseLocaleNumber(input)

    private fun showConfirmCashupDialog(
        cashupSubmitReq: CashupSubmitReq,
        context: Context
    ) {

        val dialog = Dialog(this)
        dialog.setContentView(R.layout.logout_dialog_layout)
        dialog.setCancelable(false)
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.setCanceledOnTouchOutside(false)

        val cancel = dialog.findViewById<MaterialButton>(R.id.prefer_cancel)
        val confirm = dialog.findViewById<MaterialButton>(R.id.prefer_confirm)
        val logoutMsg = dialog.findViewById<TextView>(R.id.logout_msg)
        val logoutImg = dialog.findViewById<ImageView>(R.id.dialog_logo)

        logoutMsg.text = getString(R.string.confirm_cashup_msg)
        logoutMsg.textSize = 16F
        logoutImg.setImageResource(R.drawable.svg_attention)
        logoutImg.scaleType = ImageView.ScaleType.FIT_CENTER

        confirm.setOnClickListener {
            // guard against double taps
            if (!confirm.isEnabled) return@setOnClickListener
            confirm.isEnabled = false
            confirm.alpha = 0.6f
            confirm.text = getString(R.string.submitting_label)

            // proceed once
            dialog.dismiss()
            viewmodel.callSubmitCashupApi(cashupSubmitReq, context)
            //dialog.dismiss()
            //viewmodel.callSubmitCashupApi(cashupSubmitReq, context)
        }
        cancel.setOnClickListener {
            dialog.dismiss()
        }
        dialog.show()

    }

    private fun showSucessDialog(msg: String) {

        val dialog = Dialog(this)
        dialog.setContentView(R.layout.sucess_dialog_layout)
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

        logoutMsg.text = msg
        logoutMsg.textSize = 16F
        //logoutImg.setImageResource(R.drawable.svg_off)
        //logoutImg.scaleType = ImageView.ScaleType.FIT_CENTER
        confirm.setOnClickListener {
            dialog.dismiss()
            mposLogout()

        }

        dialog.show()


    }

    private fun mposLogout() {

        val device_id =
            Settings.Secure.getString(this.contentResolver, Settings.Secure.ANDROID_ID) ?: ""
        if (device_id.isEmpty()) {
            showMessage(getString(R.string.couldnt_fetch_device_id))
        } else if (store_manager_id.isEmpty()) {
            showMessage(getString(R.string.couldnt_fetch_store_manager_info))
        } else {
            loginViewmodel.callLogoutApi(this, store_manager_id, device_id)
        }

        loginViewmodel.logoutLiveData.observe(this) {
            if (it.status == 1) {
                //showMessage(it.message)
                CoroutineScope(Dispatchers.IO).launch {
                    // ✅ Preserve the pending-sync queues across logout. Pending
                    //    tasks belong to the store they were created in and must
                    //    survive same-store re-login. A different-store online
                    //    login wipes them via OfflineDataResetUtil. Kept in sync
                    //    with MPOSDashboardActivity so every logout path behaves
                    //    identically.
                    android.util.Log.d(
                        "Logout",
                        "✅ Pending sync queues preserved for same-store re-login (cashup path)"
                    )

                    LoginSession.getInstance(this@CashUpDetailsActivity).clearLoginSession()
                    val intent = Intent(this@CashUpDetailsActivity, MPOSLoginActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK

                    startActivity(intent)
                    finish()
                }
            } else {
                showMessage(it.message)
            }
        }

    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    override fun onRecvData(data: ByteArray?) {
        val msg = RSSerialActivity.byteArrayToHex(data)
        if (lastIssuedCmd == FetchTOT.Last_CMD.STATUS && msg.length == 2) {
            DU_STATUS = msg.substring(1, 2).toInt()
            if (DU_STATUS > 0) {
                ProcessStep = 1
            }
        } else if (lastIssuedCmd == FetchTOT.Last_CMD.START_TOTALIZER && msg.length >= 20) {
            var tot = msg.substring(4, 16)
            tot = ((tot.substring(11) + tot[9] + tot[7] + tot[5]
                    + tot[3] + tot[1]))
            startTotalizer = tot
        } else if (lastIssuedCmd == FetchTOT.Last_CMD.END_TOTALIZER && msg.length >= 20) {
            var tot = msg.substring(4, 16)
            tot = ((tot.substring(11) + tot[9] + tot[7] + tot[5]
                    + tot[3] + tot[1]))
            endTotalizer = tot
            Toast.makeText(
                this,
                "Du End TOT Data $endTotalizer",
                Toast.LENGTH_SHORT
            ).show()


        }
    }
}



