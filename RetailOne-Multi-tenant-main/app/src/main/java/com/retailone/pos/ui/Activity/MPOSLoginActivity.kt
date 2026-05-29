package com.retailone.pos.ui.Activity

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import androidx.appcompat.app.AlertDialog
import com.retailone.pos.ui.Activity.LocalizedAppCompatActivity
import android.os.Bundle
import com.retailone.pos.R
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.retailone.pos.databinding.ActivityMposloginBinding
import com.retailone.pos.localstorage.DataStore.LoginSession
import com.retailone.pos.localstorage.SharedPreference.TimeoutHelper
import com.retailone.pos.models.LoginModels.LoginResponse
import com.retailone.pos.viewmodels.DashboardViewodel.ProfileAttendanceViewmodel
import com.retailone.pos.viewmodels.MPOSLoginViewmodel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import com.retailone.pos.utils.FeatureManager
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.google.gson.Gson
import com.retailone.pos.localstorage.RoomDB.PosDatabase
import com.retailone.pos.localstorage.RoomDB.StoreSettingsEntity
import com.retailone.pos.localstorage.RoomDB.UserEntity
import com.retailone.pos.localstorage.SharedPreference.ActiveStoreHelper
import com.retailone.pos.localstorage.SharedPreference.ProfileAttendanceHelper
import com.retailone.pos.localstorage.SharedPreference.SharedPrefHelper
import com.retailone.pos.models.UserProfileModels.UserProfileResponse
import com.retailone.pos.utils.LanguageManager
import com.retailone.pos.utils.NetworkUtils
import com.retailone.pos.utils.OfflineDataResetUtil
//Rawanda Code
class MPOSLoginActivity : LocalizedAppCompatActivity() {
    lateinit var  binding :ActivityMposloginBinding
    lateinit var  loginviewmodel:MPOSLoginViewmodel
    lateinit var  loginSession: LoginSession
    lateinit var  profileAttendanceViewmodel: ProfileAttendanceViewmodel
    private var loginResponse: LoginResponse? = null
    private var yourCoroutineJob: Job? = null
    private var currentUserEmail: String = ""
    private var currentUserPin: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMposloginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        loginviewmodel = ViewModelProvider(this)[MPOSLoginViewmodel::class.java]
        profileAttendanceViewmodel = ViewModelProvider(this)[ProfileAttendanceViewmodel::class.java]
        loginSession = LoginSession.getInstance(this)


     /*   lifecycleScope.launch {
            // Check if the user is logged in
            val isLoggedIn = loginSession.getLoginStatus().first()

            if (isLoggedIn) {
                navigateToActivity(MPOSDashboardActivity::class.java)
            }

        }*/

        loginviewmodel.loadingLiveData.observe(this){
            binding.progress.isVisible = it.isProgress

            if(it.isMessage)
                showMessage(it.message)
        }



        loginviewmodel.loginLiveData.observe(this){

            if(it.status == 1){
                loginResponse = it
                //store the token for calling profile Api but login status = false
                //then call profile APi to save store id then save loginsession = true (by loginresponse)

                val cashupDateTime = it.cashup_date_time

                CoroutineScope(Dispatchers.IO).launch {
                    loginSession.storeLoginSession(it.data.token,false)
                    loginSession.storeCashupDateTime(it.cashup_date_time.toString())
                    Log.d("LoginSession", "Saving cashup time: ${it.cashup_date_time}")
                    val spotDiscount = it.data.spot_discount
                    val isEnabled = spotDiscount?.is_spot_discount_enabled == 1
                    val maxLimit = spotDiscount?.max_spot_discount_limit ?: "0.00"
                    loginSession.storeSpotDiscount(isEnabled, maxLimit)
                    Log.d("LoginSession", "Spot Discount Enabled: $isEnabled | Max Limit: $maxLimit")
                    profileAttendanceViewmodel.callUserProfileApi(this@MPOSLoginActivity)
                    //get store Id And Save it
                }

                //navigateToHomepage()
                //showMessage(it.message)
                showMessage(getString(R.string.fetching_store_details))
            }else{
                showMessage(it.message)
            }
        }

        profileAttendanceViewmodel.loadingLiveData.observe(this){
            binding.progress.isVisible = it.isProgress
            if(it.isMessage)
                showMessage(it.message)
        }

        profileAttendanceViewmodel.userProfileLiveData.observe(this){

           // if store id and login response is not null save the login session
            CoroutineScope(Dispatchers.IO).launch {
                val storeid = it.data.user_details.store_id
                val store_manager_id = it.data.user_details.id
                // ✅ Extract modules safely
                val modules = it.data.user_details.organization?.modules ?: emptyList()


                if(!storeid.isNullOrBlank() && loginResponse!= null){

                   // Timeout helper
                    val timeouthelper = TimeoutHelper(this@MPOSLoginActivity)
                    timeouthelper.saveSessionTimestamp()

                    // 🧹 STORE-SWITCH GUARD (online path)
                    // If this online login is for a DIFFERENT store than the last
                    // successful online login on this device, wipe every piece of
                    // store-specific offline/cached data (Room tables + SharedPrefs)
                    // so Store A's data never leaks into Store B's session.
                    // First-ever login on the device is NOT treated as a switch.
                    val activeStoreHelper = ActiveStoreHelper(this@MPOSLoginActivity)
                    val previousStoreId = activeStoreHelper.getActiveStoreId()
                    val isDifferentStore = !previousStoreId.isNullOrBlank() &&
                            previousStoreId != storeid
                    if (isDifferentStore) {
                        Log.w(
                            "Login",
                            "Store switch: $previousStoreId → $storeid. Wiping offline data."
                        )
                        OfflineDataResetUtil.resetAllOfflineData(this@MPOSLoginActivity)
                    }
                    // Record this as the new active store so subsequent offline
                    // logins and the next store-switch comparison work correctly.
                    activeStoreHelper.saveActiveStore(storeid, currentUserEmail)

                    loginSession.saveStoreID(storeid)
                    loginSession.saveStoreManagerID(store_manager_id.toString())
                    // ✅ Set current user + store for per-store invoice ID persistence.
                    //    store_id is the primary key so every login for the same
                    //    store continues the same invoice number sequence.
                    SharedPrefHelper(this@MPOSLoginActivity).apply {
                        setCurrentInvoiceUser(store_manager_id.toString())
                        setCurrentInvoiceStore(storeid)
                    }
                    loginSession.saveModules(modules)       // ✅ NEW
                    FeatureManager.init(modules)            // ✅ NEW
                    loginSession.storeLoginSession(loginResponse!!.data.token, true)
                    loginSession.setFreshLogin(true)
                    showMessage(getString(R.string.login_successful))


                    // ✅ SAVE USER FOR OFFLINE LOGIN
                    saveUserLocally(
                        currentUserEmail,
                        currentUserPin,
                        loginResponse!!.data.token,
                        storeid,
                        store_manager_id.toString(),
                        loginResponse!!.cashup_date_time.toString(),
                        it, // userProfileResponse
                        loginResponse!!.data.spot_discount?.is_spot_discount_enabled == 1,
                        loginResponse!!.data.spot_discount?.max_spot_discount_limit ?: "0.00",
                        it.data.user_details.organization?.is_inclusive ?: true
                    )

                    showMessage(getString(R.string.login_successful))
                   // navigateToActivity(FetchTOT::class.java,"USER_STATUS" ,"LoggedIn")
                    // ✅ Check totalizer module
                    if (FeatureManager.isEnabled("totalizer")) {
                        navigateToActivity(FetchTOT::class.java, "USER_STATUS", "LoggedIn")
                    } else {
                        navigateToHomepage()
                    }

                }else{
                    showMessage(getString(R.string.user_not_associated_store))
                }
            }

        }


        binding.loginBtn.setOnClickListener {
            val userid = binding.mobileedit.text.toString()
            val pin = binding.pinedit.text.toString()
            validateCredential(userid,pin)
        }

        binding.forgotpin.setOnClickListener {
            val intent = Intent(this@MPOSLoginActivity,ForgotPinActivity::class.java)
            startActivity(intent)
        }
       /* binding.biomatric.setOnClickListener {
            val intent = Intent(this@MPOSLoginActivity,MPOSDashboardActivity::class.java)
            startActivity(intent)
        }*/

        binding.forgotpin.paintFlags = Paint.UNDERLINE_TEXT_FLAG

        // english only: language picker hidden for the english-only build.
        // The languageButton ImageView is commented out in activity_mposlogin.xml,
        // so binding.languageButton no longer exists. Uncomment both this block
        // and the ImageView in the layout to restore multi-language support.
        // Language icon (top-right): English / Portuguese / Swahili only.
        // binding.languageButton.setOnClickListener {
        //     showLanguagePickerDialog()
        // }
        // english only: end

        // Check if we came from a logout or session clear, but allow auto login if session exists?
        // Actually, the requirement is to allow login in offline mode.
    }

    private fun showLanguagePickerDialog() {
        val codes = arrayOf("en", "pt", "sw")
        val labels = arrayOf(
            getString(R.string.english),
            getString(R.string.portuguese),
            getString(R.string.swahili)
        )
        val current = LanguageManager.getSavedLanguage(this)
        val checked = codes.indexOf(current).let { if (it < 0) 0 else it }

        AlertDialog.Builder(this)
            .setTitle(R.string.select_language_title)
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                val chosen = codes[which]
                if (chosen != current) {
                    dialog.dismiss()
                    LanguageManager.restartActivityForLocale(this, chosen)
                } else {
                    dialog.dismiss()
                }
            }
            .setNegativeButton(R.string.cancel) { d, _ -> d.dismiss() }
            .show()
    }

    private fun navigateToHomepage() {
        val intent = Intent(this@MPOSLoginActivity, MPOSDashboardActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun validateCredential(userid: String, pin: String) {
        val emailPattern = "[a-zA-Z0-9._-]+@[a-z]+\\.+[a-z]+"


        val device_id = getMyDeviceId(this)

        if(userid.isEmpty() || userid.isBlank()){
            showMessage(getString(R.string.enter_registered_email))
        }else if (!userid.matches(emailPattern.toRegex())) {
            showMessage(getString(R.string.enter_valid_email))
        }else if(pin.isEmpty() || pin.isBlank() || pin.trim().length != 6){
            showMessage(getString(R.string.please_enter_6_digit_pin))
        }else{
            login(userid,pin,device_id)
        }

    }


    fun getMyDeviceId(context: Context): String {
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: ""
    }

    private fun login(userid: String, pin: String, device_id: String) {
       // val intent = Intent(this@MPOSLoginActivity,MPOSDashboardActivity::class.java)
        //startActivity(intent)

        currentUserEmail = userid
        currentUserPin = pin

        if (NetworkUtils.isInternetAvailable(this)) {
            loginviewmodel.callLoginApi(this@MPOSLoginActivity,userid, pin,device_id)
        } else {
            // ✅ PERFORM OFFLINE LOGIN
            lifecycleScope.launch(Dispatchers.IO) {
                val db = PosDatabase.getDatabase(this@MPOSLoginActivity)
                val user = db.userDao().getUserByCredentials(userid, pin)

                // 🚫 STORE-SWITCH GUARD (offline path)
                // Offline login is only allowed for the SAME store as the last
                // successful online login on this device. If the user we matched
                // belongs to a different store (or there is no active store
                // recorded yet), we refuse and ask them to go online.
                val activeStoreHelper = ActiveStoreHelper(this@MPOSLoginActivity)
                val activeStoreId = activeStoreHelper.getActiveStoreId()
                if (user != null) {
                    val sameStore = activeStoreId != null && activeStoreId == user.storeId
                    if (!sameStore) {
                        launch(Dispatchers.Main) {
                            showMessage(getString(R.string.login_online_first))
                        }
                        return@launch
                    }
                }

                if (user != null) {
                    // Restore session from local data
                    loginSession.saveStoreID(user.storeId)
                    loginSession.saveStoreManagerID(user.storeManagerId)
                    // ✅ Set current user + store for per-store invoice ID persistence.
                    //    Keying by store_id ensures offline invoice numbering
                    //    continues correctly after logout/login for the same store.
                    SharedPrefHelper(this@MPOSLoginActivity).apply {
                        setCurrentInvoiceUser(user.storeManagerId)
                        setCurrentInvoiceStore(user.storeId)
                    }
                    loginSession.storeCashupDateTime(user.cashupDateTime)
                    loginSession.storeLoginSession(user.token, true)

                    // ✅ RESTORE SPOT DISCOUNT AND TAX MODE FOR THIS STORE
                    val settings = db.storeSettingsDao().getSettingsByStoreId(user.storeId)
                    if (settings != null) {
                        loginSession.storeSpotDiscount(settings.isSpotDiscountEnabled, settings.spotDiscountLimit)
                        // Restore is_inclusive flag so offline POS uses the correct tax math.
                        // Without this, the DataStore key keeps whatever value was last written
                        // (or the default true) regardless of what this store actually uses, and
                        // offline subtotal/tax come out wrong when the store is inclusive/exclusive.
                        loginSession.storeTaxMode(settings.isTaxInclusive)
                        Log.d(
                            "OfflineLogin",
                            "Restored spot discount: ${settings.isSpotDiscountEnabled}, limit: ${settings.spotDiscountLimit}, isTaxInclusive: ${settings.isTaxInclusive}"
                        )
                    }

                    // Restore user profile in helper for other components
                    val profileHelper = ProfileAttendanceHelper(this@MPOSLoginActivity)
                    val gson = Gson()
                    val profileResponse = gson.fromJson(user.userProfileJson, UserProfileResponse::class.java)
                    profileHelper.saveUserProfile(profileResponse)

                    // ✅ Re-initialise FeatureManager from the cached enabled-modules
                    //    list saved during the last online login. Without this,
                    //    FeatureManager stays empty after offline login and every
                    //    isEnabled(...) check returns false → modules disappear from
                    //    the dashboard (e.g. expense, stock return, sales return /
                    //    replacement, totalizer).
                    val cachedModules = try {
                        loginSession.getModules().first()
                    } catch (e: Exception) {
                        Log.e("OfflineLogin", "Failed to read cached modules: ${e.message}")
                        emptyList<String>()
                    }
                    FeatureManager.init(cachedModules)
                    Log.d(
                        "OfflineLogin",
                        "Restored FeatureManager from cache (${cachedModules.size} modules): $cachedModules"
                    )

                    launch(Dispatchers.Main) {
                        showMessage(getString(R.string.offline_login_successful))
                        navigateToHomepage()
                    }
                } else {
                    launch(Dispatchers.Main) {
                        // No offline user record at all — either wrong credentials
                        // or this device/user has never logged in online before.
                        val msg = if (activeStoreId.isNullOrBlank()) {
                            getString(R.string.login_online_first)
                        } else {
                            getString(R.string.incorrect_credentials_offline)
                        }
                        showMessage(msg)
                    }
                }
            }
        }
    }

    private suspend fun saveUserLocally(
        email: String,
        pin: String,
        token: String,
        storeId: String,
        storeManagerId: String,
        cashupTime: String,
        profileResponse: UserProfileResponse,
        spotDiscountEnabled: Boolean,
        spotDiscountLimit: String,
        isInclusive: Boolean
    ) {
        val db = PosDatabase.getDatabase(this)
        
        // Save store specific settings
        val storeSettings = StoreSettingsEntity(
            storeId = storeId,
            isSpotDiscountEnabled = spotDiscountEnabled,
            spotDiscountLimit = spotDiscountLimit,
            isTaxInclusive = isInclusive
        )
        db.storeSettingsDao().insertOrUpdate(storeSettings)
        
        // Also save to DataStore for immediate global access
        loginSession.storeTaxMode(isInclusive)
        
        val userProfileJson = Gson().toJson(profileResponse)
        val userEntity = UserEntity(
            email = email,
            pin = pin,
            token = token,
            storeId = storeId,
            storeManagerId = storeManagerId,
            cashupDateTime = cashupTime,
            userProfileJson = userProfileJson
        )
        db.userDao().insertOrUpdate(userEntity)
        Log.d("OfflineLogin", "User saved for offline login: $email")
    }

    private fun navigateToActivity(activityClass: Class<*>) {
        val intent = Intent(this@MPOSLoginActivity, activityClass)
        startActivity(intent)
        finish()
    }


        private fun showMessage(msg: String) {

        yourCoroutineJob = GlobalScope.launch(Dispatchers.Main) {
            Toast.makeText(this@MPOSLoginActivity, com.retailone.pos.utils.LocalizationUtils.localizeUserMessage(this@MPOSLoginActivity, msg), Toast.LENGTH_SHORT).show()
        }
    }

    private fun <T> navigateToActivity(target: Class<T>, key: String? = null, value: String? = null) {
        val intent = Intent(this, target)
        if (key != null && value != null) {
            intent.putExtra(key, value) // Add the key-value pair to the intent
        }
        startActivity(intent)
        finish() // Remove login from back stack so it is never recreated on config changes (e.g. language switch)
    }

    override fun onDestroy() {
        // Cancel the coroutine job if it's not null
        yourCoroutineJob?.cancel()

        super.onDestroy()
    }

}



