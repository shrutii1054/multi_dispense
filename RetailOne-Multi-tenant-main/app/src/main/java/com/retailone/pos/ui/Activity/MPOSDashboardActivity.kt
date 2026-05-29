package com.retailone.pos.ui.Activity

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.Dialog
import android.content.Context
import android.content.Intent
import com.retailone.pos.ui.Activity.LocalizedAppCompatActivity
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.retailone.pos.R
import com.retailone.pos.databinding.ActivityMposdashboardBinding
import com.retailone.pos.databinding.CustomerDetailsBottomsheetBinding
import com.retailone.pos.localstorage.DataStore.LoginSession
import com.retailone.pos.localstorage.SharedPreference.CustomerLocalHelper
import com.retailone.pos.localstorage.SharedPreference.CustomerSessionHelper
import com.retailone.pos.localstorage.SharedPreference.InventoryStockHelper
import com.retailone.pos.localstorage.SharedPreference.LocalizationHelper
import com.retailone.pos.localstorage.SharedPreference.OrganisationDetailsHelper
import com.retailone.pos.localstorage.SharedPreference.SharedPrefHelper
import com.retailone.pos.localstorage.SharedPreference.TimeoutHelper
import com.retailone.pos.models.GetCustomerModel.getCustomerReq
import com.retailone.pos.repository.PendingCancelSaleRepository
import com.retailone.pos.repository.PosSaleRepository
import com.retailone.pos.repository.PendingReturnRepository
import com.retailone.pos.localstorage.RoomDB.PendingReplaceRepository
import com.retailone.pos.localstorage.RoomDB.PosDatabase
import com.retailone.pos.utils.LanguageManager
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import com.retailone.pos.ui.Activity.DashboardActivity.*
import com.retailone.pos.utils.CrashHandler
import com.retailone.pos.utils.FeatureManager
import com.retailone.pos.utils.NetworkUtils
import com.retailone.pos.viewmodels.DashboardViewodel.HomeDashboardViewmodel
import com.retailone.pos.viewmodels.MPOSLoginViewmodel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MPOSDashboardActivity : LocalizedAppCompatActivity() {

    lateinit var binding: ActivityMposdashboardBinding
    lateinit var actionBarDrawerToggle: ActionBarDrawerToggle
    lateinit var drawer: DrawerLayout
    lateinit var loginSession: LoginSession
    lateinit var sharedPrefHelper: SharedPrefHelper
    lateinit var inventoryStockHelper: InventoryStockHelper
    lateinit var viewmodel: HomeDashboardViewmodel
    lateinit var loginViewmodel: MPOSLoginViewmodel
    lateinit var localizationHelper: LocalizationHelper
    lateinit var organisationDetailsHelper: OrganisationDetailsHelper

    private lateinit var posSaleRepository: PosSaleRepository
    private lateinit var pendingReturnRepository: PendingReturnRepository
    private lateinit var pendingReplaceRepository: PendingReplaceRepository
    private lateinit var pendingCancelRepository: PendingCancelSaleRepository
    private lateinit var pendingGoodsReturnRepository: com.retailone.pos.localstorage.RoomDB.PendingGoodsReturnRepository
    private lateinit var pendingDispatchRepository: com.retailone.pos.localstorage.RoomDB.PendingDispatchRepository
    private var isSyncing = false

    var storemanager_id = ""

    // Button state tracking
    private enum class SyncButtonState {
        IDLE,        // State 1: "SYNC" with arrow
        SYNCING,     // State 2: "SYNCING ITEMS" with rotating arrow
        SUCCESS,     // State 3: "SYNC Successful"
        IDLE_AGAIN   // State 4: Back to idle
    }

    private var currentState = SyncButtonState.IDLE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMposdashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // ... truncated generic setup routines
        setSupportActionBar(binding.toolbar)
        drawer = binding.drawer
        actionBarDrawerToggle = ActionBarDrawerToggle(this, drawer, R.string.nav_open, R.string.nav_close)
        drawer.addDrawerListener(actionBarDrawerToggle)
        actionBarDrawerToggle.syncState()

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeAsUpIndicator(R.drawable.svg_menu)

        val headerView = binding.navView.getHeaderView(0)
        val headerImageView = headerView.findViewById<ImageView>(R.id.header_imageView)

        loginSession = LoginSession.getInstance(this)
        sharedPrefHelper = SharedPrefHelper(this)
        inventoryStockHelper = InventoryStockHelper(this)
        localizationHelper = LocalizationHelper(this)
        organisationDetailsHelper = OrganisationDetailsHelper(this)

        viewmodel = ViewModelProvider(this)[HomeDashboardViewmodel::class.java]
        loginViewmodel = ViewModelProvider(this)[MPOSLoginViewmodel::class.java]

        posSaleRepository = PosSaleRepository(this)
        pendingReturnRepository = PendingReturnRepository(this)
        pendingCancelRepository = PendingCancelSaleRepository(this)
        
        val db = PosDatabase.getDatabase(this)
        pendingReplaceRepository = PendingReplaceRepository(db.pendingReplaceDao())
        pendingGoodsReturnRepository = com.retailone.pos.localstorage.RoomDB.PendingGoodsReturnRepository(db.pendingGoodsReturnDao())
        pendingDispatchRepository = com.retailone.pos.localstorage.RoomDB.PendingDispatchRepository(db.pendingDispatchDao())

        setupSyncButton()
        observePendingSalesCount()

        val crashHandler = CrashHandler(this)
        Thread.setDefaultUncaughtExceptionHandler(crashHandler)

        lifecycleScope.launch {
            val storeid = loginSession.getStoreID().first().toInt()
            storemanager_id = loginSession.getStoreManagerID().first().toString()

            val timeouthelper = TimeoutHelper(this@MPOSDashboardActivity)

            if (!timeouthelper.isSessionValid()) {
                mposLogout()
            }

            viewmodel.fetchDashboardConfig(storeid, this@MPOSDashboardActivity)
            val isFreshLogin = loginSession.getFreshLogin().first()
            if (isFreshLogin) {
                viewmodel.callNoticesApi("20231120191141", this@MPOSDashboardActivity)
                loginSession.setFreshLogin(false) // ✅ Consume the flag immediately
            }
        }

        viewmodel.localization_liveData.observe(this) {
            localizationHelper.saveLocalizationData(it.data)
        }

        viewmodel.organization_liveData.observe(this) {
            organisationDetailsHelper.saveOrganisationData(it.data)

            // ✅ Propagate fresh is_inclusive to DataStore + StoreSettingsEntity.
            // The login API only sets IS_TAX_INCLUSIVE once (at login) from the
            // organization payload. If the backend later toggles the flag, or if
            // that payload and the per-store tax config drift, the offline POS
            // would keep using a stale value. This observer fires every time the
            // dashboard loads online, so it's the right place to refresh.
            val freshIsInclusive = it.data.is_inclusive ?: true
            lifecycleScope.launch {
                loginSession.storeTaxMode(freshIsInclusive)
                val storeIdStr = loginSession.getStoreID().first()
                if (!storeIdStr.isNullOrBlank()) {
                    try {
                        val db = PosDatabase.getDatabase(this@MPOSDashboardActivity)
                        val dao = db.storeSettingsDao()
                        val existing = dao.getSettingsByStoreId(storeIdStr)
                        if (existing != null) {
                            dao.insertOrUpdate(existing.copy(isTaxInclusive = freshIsInclusive))
                        } else {
                            dao.insertOrUpdate(
                                com.retailone.pos.localstorage.RoomDB.StoreSettingsEntity(
                                    storeId = storeIdStr,
                                    isSpotDiscountEnabled = false,
                                    spotDiscountLimit = "0.00",
                                    isTaxInclusive = freshIsInclusive
                                )
                            )
                        }
                    } catch (e: Exception) {
                        Log.e("DashboardIsInclusive", "Failed to update StoreSettings.isTaxInclusive", e)
                    }
                }
                Log.d("DashboardIsInclusive", "Refreshed IS_TAX_INCLUSIVE from org API -> $freshIsInclusive")
            }
        }

        loginViewmodel.loadingLiveData.observe(this) {
            binding.progress.isVisible = it.isProgress
            if (it.isMessage) showMessage(it.message)
        }

        binding.navView.setNavigationItemSelectedListener {
            when (it.itemId) {
                R.id.about_us -> Toast.makeText(this, getString(R.string.about_us_msg), Toast.LENGTH_SHORT).show()
                R.id.contact_us -> Toast.makeText(this, getString(R.string.contact_us_msg), Toast.LENGTH_SHORT).show()
                R.id.log_data -> startActivity(Intent(this, CrashLogsActivity::class.java))
                // english only: drawer language entry is hidden for the english-only build.
                // R.id.language_section is removed from navigationmenu.xml, so the
                // generated R.id.language_section constant no longer exists.
                // Uncomment the <item android:id="@+id/language_section" ...> in
                // res/menu/navigationmenu.xml AND the branch below to restore it.
                // R.id.language_section -> showLanguagePickerDialog()
                // english only: end
            }
            drawer.closeDrawer(androidx.core.view.GravityCompat.START)
            true
        }

        // All your existing click listeners
        binding.poscard.setOnClickListener {
            lifecycleScope.launch {
                val cashupTime = loginSession.getCashupDateTime().first()
                if (isCashupOutdated(cashupTime)) {
                    showCashupPopup(cashupTime)
                } else {
                    customerBottomSheet()
                }
            }
        }

        binding.returncard.setOnClickListener {
            val canReturn = FeatureManager.isEnabled("sales return")
            val canReplace = FeatureManager.isEnabled("sales replacement")

            when {
                canReturn -> {
                    // covers "only sales return" AND "both enabled" cases
                    // ReturnSaleActivity has left/right switch inside for replacement
                    startActivity(Intent(this@MPOSDashboardActivity, ReturnSaleActivity::class.java))
                }
                canReplace -> {
                    // only sales replacement enabled — go directly
                    startActivity(Intent(this@MPOSDashboardActivity, ReplacedSaleActivity::class.java))
                }
                else -> {
                    // safe fallback — card should already be hidden
                    showMessage(getString(R.string.feature_not_enabled_org))
                }
            }        }

        binding.goodsRWcard.setOnClickListener {
            val intent = Intent(this@MPOSDashboardActivity, proceedToDispatchActivity::class.java)
            startActivity(intent)
        }

        binding.stockcard.setOnClickListener {
            sharedPrefHelper.clearStockList()
            val intent = Intent(this@MPOSDashboardActivity, StockRequisitionActivity::class.java)
            startActivity(intent)
        }

        binding.materialrcvCard.setOnClickListener {
            startActivity(Intent(this, MaterialRecivingItemsActivity::class.java))
        }

        binding.pdtInventoryCard.setOnClickListener {
            startActivity(Intent(this, ProductInventoryActivity::class.java))
        }

        binding.cashupcard.setOnClickListener {
            lifecycleScope.launch {
                val cashupTime = loginSession.getCashupDateTime().first()
                val intent = Intent(this@MPOSDashboardActivity, CashUpActivity::class.java)
                if (isCashupOutdated(cashupTime)) {
                    intent.putExtra("CASHUP_DATE_TIME", cashupTime)
                }
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                startActivity(intent)
            }
        }

        binding.salesPaymentCard.setOnClickListener {
            startActivity(Intent(this, SalesAndPaymentActivity::class.java))
        }

        binding.expensecard.setOnClickListener {
            lifecycleScope.launch {
                val cashupTime = loginSession.getCashupDateTime().first()
                val intent = Intent(this@MPOSDashboardActivity, ExpenseRegisterActivity::class.java)
                intent.putExtra("CASHUP_DATE_TIME", cashupTime)
                startActivity(intent)
            }
        }

        binding.profileCard.setOnClickListener {
            startActivity(Intent(this, ProfileAttendanceActivity::class.java))
        }

        binding.logout.setOnClickListener {
            showLogoutDialog()
        }

        val organisation_data = organisationDetailsHelper.getOrganisationData()

        Glide.with(this)
            .load(organisation_data.image_url + organisation_data.fabicon)
            .fitCenter()
            .placeholder(R.drawable.mlogo)
            .error(R.drawable.mlogo)
            .into(binding.toolImage)

        Glide.with(this)
            .load(organisation_data.image_url + organisation_data.logo)
            .fitCenter()
            .placeholder(R.drawable.mlogo)
            .error(R.drawable.mlogo)
            .into(headerImageView)

        lifecycleScope.launch {
            val modules = loginSession.getModules().first()
            FeatureManager.init(modules)
            applyFeatureVisibility()
        }
    }
    // ✅ Controls which cards show based on org modules
    private fun applyFeatureVisibility() {
        // Return Product card — show if either is enabled
        binding.row6linear?.isVisible = FeatureManager.isEnabled("sales return")
                || FeatureManager.isEnabled("sales replacement")
        // Sales & Payment — mapped to cancel sales
        // Sales & Payment — default true
        binding.salesPaymentCard?.isVisible = true
        // Expense
        binding.expensecard?.isVisible = FeatureManager.isEnabled("expense")
        // Goods Return to Warehouse
        binding.goodsRWcard?.isVisible = FeatureManager.isEnabled("stock return")
    }

    // ✅ ENHANCED SYNC BUTTON SETUP
    private fun setupSyncButton() {
        binding.syncButtonContainer.setOnClickListener {
            if (!isSyncing && currentState == SyncButtonState.IDLE) {
                syncOfflineSales()
            }
        }
    }

    // ✅ OBSERVE PENDING COUNTS AND UPDATE BADGE
    private fun observePendingSalesCount() {
        lifecycleScope.launch {
            combine(
                listOf(
                    posSaleRepository.getPendingSalesCountFlow(),
                    pendingReturnRepository.getPendingReturnsCountFlow(),
                    pendingReplaceRepository.getPendingCountFlow(),
                    pendingCancelRepository.getPendingCancelCountFlow(),
                    pendingGoodsReturnRepository.getPendingCountFlow(),
                    pendingDispatchRepository.getPendingCountFlow()
                )
            ) { counts ->
                counts.sum()
            }.collectLatest { totalCount ->
                Log.d("SyncBadge", "Total pending items: $totalCount")

                if (totalCount > 0) {
                    binding.tvSyncBadge.visibility = android.view.View.VISIBLE
                    binding.tvSyncBadge.text = if (totalCount > 99) "99+" else totalCount.toString()
                } else {
                    binding.tvSyncBadge.visibility = android.view.View.GONE
                }
            }
        }
    }

    // ✅ STATE 1 → STATE 2: IDLE TO SYNCING
    private fun transitionToSyncing() {
        currentState = SyncButtonState.SYNCING

        // Change text to "SYNCING ITEMS"
        binding.tvSyncText.text = getString(R.string.syncing_items)

        // Expand button width
        val currentWidth = binding.syncButtonContainer.width
        val targetWidth = dpToPx(220) // Expanded width

        val widthAnimator = ValueAnimator.ofInt(currentWidth, targetWidth)
        widthAnimator.duration = 100
        widthAnimator.interpolator = AccelerateDecelerateInterpolator()
        widthAnimator.addUpdateListener { animator ->
            val params = binding.syncButtonContainer.layoutParams
            params.width = animator.animatedValue as Int
            binding.syncButtonContainer.layoutParams = params
        }
        widthAnimator.start()

        // Start rotating the icon
        val rotateAnim = AnimationUtils.loadAnimation(this, R.anim.rotate_sync)
        binding.ivSyncIcon.startAnimation(rotateAnim)
    }

    // ✅ STATE 2 → STATE 3: SYNCING TO SUCCESS
    private fun transitionToSuccess() {
        currentState = SyncButtonState.SUCCESS

        // Stop rotation
        binding.ivSyncIcon.clearAnimation()

        // Change background to green
        binding.syncButtonContainer.setBackgroundResource(R.drawable.bg_sync_button_green)

        // Change text to "SYNC Successful"
        binding.tvSyncText.text = getString(R.string.sync_successful)

        // Shrink button width back
        val currentWidth = binding.syncButtonContainer.width
        val targetWidth = dpToPx(220)

        val widthAnimator = ValueAnimator.ofInt(currentWidth, targetWidth)
        widthAnimator.duration = 400
        widthAnimator.interpolator = AccelerateDecelerateInterpolator()
        widthAnimator.addUpdateListener { animator ->
            val params = binding.syncButtonContainer.layoutParams
            params.width = animator.animatedValue as Int
            binding.syncButtonContainer.layoutParams = params
        }
        widthAnimator.start()

        // After 2 seconds, revert to idle
        lifecycleScope.launch {
            delay(2500)
            transitionToIdle()
        }
    }

    // ✅ STATE 3 → STATE 1: SUCCESS TO IDLE
    private fun transitionToIdle() {
        currentState = SyncButtonState.IDLE

        // Change background back to red
        binding.syncButtonContainer.setBackgroundResource(R.drawable.bg_sync_button_red)

        // Change text back to "SYNC" (translated)
        binding.tvSyncText.text = getString(R.string.sync_text)

        // Measure the natural width needed for the current (translated) text
        // so the button fits correctly in all languages instead of hardcoding 120dp.
        binding.syncButtonContainer.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED),
            android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED)
        )
        val targetWidth = binding.syncButtonContainer.measuredWidth
            .coerceAtLeast(dpToPx(120)) // never shrink below the XML minWidth

        // Shrink button width to natural size for the current locale
        val currentWidth = binding.syncButtonContainer.width

        val widthAnimator = ValueAnimator.ofInt(currentWidth, targetWidth)
        widthAnimator.duration = 400
        widthAnimator.interpolator = AccelerateDecelerateInterpolator()
        widthAnimator.addUpdateListener { animator ->
            val params = binding.syncButtonContainer.layoutParams
            params.width = animator.animatedValue as Int
            binding.syncButtonContainer.layoutParams = params
        }
        widthAnimator.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                // Reset to wrap_content so the button always adapts to its text
                val params = binding.syncButtonContainer.layoutParams
                params.width = android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                binding.syncButtonContainer.layoutParams = params
            }
        })
        widthAnimator.start()
    }

    // ✅ MAIN SYNC FUNCTION
    private fun syncOfflineSales() {
        if (!isInternetAvailable()) {
            showMessage(getString(R.string.no_internet_error))
            return
        }
        // ✅ Check badge visibility (if badge is hidden, no pending sales)
        if (binding.tvSyncBadge.visibility == android.view.View.GONE) {
            showMessage(getString(R.string.sales_up_to_date))
            return
        }

        isSyncing = true
        transitionToSyncing()

        lifecycleScope.launch {
            try {
                delay(300)

                // ---------------------------------------------------------------
                // 🔖 PRE-SYNC SNAPSHOT: capture every PENDING/FAILED sale's local
                //    invoice_id BEFORE we hit the network. After the server responds
                //    with an authoritative invoice_id, we compare against this snapshot
                //    to build an (oldLocalId → newServerId) map.
                //
                //    WHY: backend generates its own invoice_id when an offline sale
                //    syncs. If that server-assigned id differs from the local one
                //    (common when the backend's sequence also consumes IDs for returns,
                //    e.g. sale=2, return=3, next sale=4), any pending_returns row that
                //    references the old local id becomes an orphan and the return sync
                //    can't recover it via invoice_id → sale_id lookup. We must rewrite
                //    the reference on the fly, otherwise PendingReturnRepository marks
                //    the return as FAILED and you see "Some items failed to sync".
                // ---------------------------------------------------------------
                val posDb = PosDatabase.getDatabase(this@MPOSDashboardActivity)
                val pendingSaleDaoLocal = posDb.pendingSaleDao()
                val pendingReturnDaoLocal = posDb.pendingReturnDao()

                // ---------------------------------------------------------------
                // 🧵 UNIFIED CHRONOLOGICAL QUEUE
                //
                //    Offline operations (sales AND returns) must be replayed against
                //    the server in the EXACT order the user performed them, because
                //    the backend consumes a sequential invoice_id for every sale AND
                //    every return.
                //
                //    If the user did: Sale A → Return A → Sale B, the backend will
                //    assign invoice ids 208 → 209 → 210 in that order. Syncing all
                //    sales first (A, B) and then all returns (Return A) would make
                //    Sale B collide with invoice id 209 — which the backend would
                //    actually assign to Return A when it arrives.
                //
                //    We merge pending_sales and pending_returns, sort by created_at,
                //    and dispatch each op to the appropriate per-item sync method.
                //
                //    A pre-snapshot of sale invoice ids is still captured so the
                //    [INVOICE_REMAP] reconciliation can rewrite any pending_returns
                //    row that points at an old local invoice id, immediately after
                //    its sale syncs and before its return tries to sync.
                // ---------------------------------------------------------------
                val preSyncSales = pendingSaleDaoLocal.getPendingSales()
                val preSyncInvoiceMap: MutableMap<Int, String> =
                    preSyncSales.associate { it.id to it.invoice_id }.toMutableMap()
                Log.d(
                    "OFFLINE_SYNC_DEBUG",
                    "📸 [PRE-SNAPSHOT] ${preSyncInvoiceMap.size} pending sale(s) about to sync: $preSyncInvoiceMap"
                )

                val preSyncReturns = pendingReturnDaoLocal.getAllPendingReturns()

                // Sealed-style container: tag each op so we can branch on type
                // without leaking entity types into the merge logic.
                data class QueueOp(
                    val createdAt: Long,
                    val kind: String,                    // "SALE" or "RETURN"
                    val sale: com.retailone.pos.localstorage.RoomDB.PendingSaleEntity? = null,
                    val returnEntity: com.retailone.pos.localstorage.RoomDB.PendingReturnEntity? = null
                )

                val unifiedQueue: List<QueueOp> = buildList {
                    preSyncSales.forEach { add(QueueOp(it.created_at, "SALE", sale = it)) }
                    preSyncReturns.forEach { add(QueueOp(it.created_at, "RETURN", returnEntity = it)) }
                }.sortedBy { it.createdAt }

                Log.d(
                    "OFFLINE_SYNC_DEBUG",
                    "🧵 [UNIFIED-QUEUE] ${unifiedQueue.size} op(s) to sync in chronological order: " +
                            unifiedQueue.joinToString {
                                when (it.kind) {
                                    "SALE" -> "SALE(row=${it.sale?.id}, invoice=${it.sale?.invoice_id}, t=${it.createdAt})"
                                    else   -> "RETURN(row=${it.returnEntity?.id}, invoice=${it.returnEntity?.invoice_id}, t=${it.createdAt})"
                                }
                            }
                )

                // ---------------------------------------------------------------
                // Drain the queue. After each SALE we reconcile its invoice id so
                // later RETURN rows that referenced the old local id now point at
                // the server-assigned id before we attempt the return sync.
                // ---------------------------------------------------------------
                var saleSuccess = true
                var returnSuccess = true

                for (op in unifiedQueue) {
                    when (op.kind) {
                        "SALE" -> {
                            val pendingSale = op.sale ?: continue
                            val oldInvoiceId = pendingSale.invoice_id
                            Log.d(
                                "OFFLINE_SYNC_DEBUG",
                                "➡️ [QUEUE-STEP] SALE row=${pendingSale.id}, invoice=$oldInvoiceId"
                            )
                            val ok = posSaleRepository.syncSale(pendingSale)
                            if (!ok) saleSuccess = false

                            // 🔁 Immediate reconciliation for THIS sale only, so any
                            //    pending_returns referencing its old local invoice id
                            //    are rewritten before the next op (which could be
                            //    exactly that return).
                            try {
                                val updated = pendingSaleDaoLocal.getSaleById(pendingSale.id)
                                if (updated != null
                                    && updated.sync_status == "SYNCED"
                                    && updated.invoice_id.isNotBlank()
                                    && updated.invoice_id != oldInvoiceId
                                ) {
                                    Log.d(
                                        "OFFLINE_SYNC_DEBUG",
                                        "🔁 [INVOICE_REMAP] sale row=${pendingSale.id} : '$oldInvoiceId' → '${updated.invoice_id}' — updating pending_returns"
                                    )
                                    pendingReturnDaoLocal.updateInvoiceId(oldInvoiceId, updated.invoice_id)
                                    preSyncInvoiceMap[pendingSale.id] = updated.invoice_id
                                } else {
                                    Log.d(
                                        "OFFLINE_SYNC_DEBUG",
                                        "ℹ️ [INVOICE_REMAP] sale row=${pendingSale.id} unchanged (status=${updated?.sync_status}, invoice=${updated?.invoice_id})"
                                    )
                                }
                            } catch (e: Exception) {
                                Log.e(
                                    "OFFLINE_SYNC_DEBUG",
                                    "❌ [INVOICE_REMAP] Error reconciling sale row=${pendingSale.id}: ${e.message}",
                                    e
                                )
                            }
                        }
                        "RETURN" -> {
                            val pendingReturn = op.returnEntity ?: continue
                            // Re-read the row because its invoice_id may have been
                            // rewritten by the [INVOICE_REMAP] step above.
                            val fresh = pendingReturnDaoLocal.getPendingReturnById(pendingReturn.id) ?: pendingReturn
                            Log.d(
                                "OFFLINE_SYNC_DEBUG",
                                "➡️ [QUEUE-STEP] RETURN row=${fresh.id}, invoice=${fresh.invoice_id}, sales_id=${fresh.sales_id}"
                            )
                            val ok = pendingReturnRepository.syncSingleReturn(
                                this@MPOSDashboardActivity,
                                fresh
                            )
                            if (!ok) returnSuccess = false
                        }
                    }
                }

                Log.d(
                    "OFFLINE_SYNC_DEBUG",
                    "🧵 [UNIFIED-QUEUE] Drain complete — saleSuccess=$saleSuccess, returnSuccess=$returnSuccess"
                )
                
                // ✅ 3. Sync Replaces
                val replaceSuccess = pendingReplaceRepository.syncAllPendingReplaces(this@MPOSDashboardActivity)

                // ✅ 4. Sync Cancels
                val cancelSuccess = pendingCancelRepository.syncAllPendingCancels()
                
                // ✅ 5. Sync Warehouse Goods Returns
                val goodsSuccess = pendingGoodsReturnRepository.syncAllPendingReturns(this@MPOSDashboardActivity)

                
                // ✅ 7. Sync Dispatches
                val dispatchSuccess = pendingDispatchRepository.syncAllPendingDispatches(this@MPOSDashboardActivity)
                
                delay(1500)

                if (saleSuccess && returnSuccess && replaceSuccess && cancelSuccess && goodsSuccess && dispatchSuccess) {
                    transitionToSuccess()
                } else {
                    // Failed - revert to idle
                    binding.ivSyncIcon.clearAnimation()
                    transitionToIdle()
                    showMessage(getString(R.string.sync_failed))
                }

            } catch (e: Exception) {
                binding.ivSyncIcon.clearAnimation()
                transitionToIdle()
                Log.e("SyncError", "Error syncing sales: ${e.message}")
                showMessage(getString(R.string.sync_error, e.message ?: ""))
            } finally {
                isSyncing = false
            }
        }
    }

    // ✅ HELPER: Convert DP to PX
    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    // ... (Keep all your existing methods: isCashupOutdated, showCashupPopup,
    // customerBottomSheet, mposLogout, showLogoutDialog, etc.)

    private fun isCashupOutdated(cashupDateTime: String): Boolean {
        return try {
            val formatter = SimpleDateFormat("dd-MMM-yyyy hh:mm a", Locale.getDefault())
            val cleanDate = cashupDateTime.trim().replace("\"", "")
            val cashupDate = formatter.parse(cleanDate) ?: return false

            val calendar = Calendar.getInstance()
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            calendar.add(Calendar.DAY_OF_YEAR, -1)

            cashupDate.before(calendar.time)
        } catch (e: Exception) {
            false
        }
    }

    private fun showCashupPopup(cashupDateTime: String) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_cashup, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).setCancelable(false).create()

        dialogView.findViewById<Button>(R.id.btnOk).setOnClickListener {
            val intent = Intent(this, CashUpDetailsActivity::class.java)
            intent.putExtra("CASHUP_DATE_TIME", cashupDateTime)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            dialog.dismiss()
        }
        dialogView.findViewById<ImageView>(R.id.btnClose).setOnClickListener {
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun isInternetAvailable(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun customerBottomSheet() {
        val d_binding = CustomerDetailsBottomsheetBinding.inflate(layoutInflater)
        val dialog = BottomSheetDialog(this)
        dialog.setContentView(d_binding.root)

        // ✅ Skip button always visible (works in both online and offline mode)
        d_binding.skipBtn.visibility = android.view.View.VISIBLE



        viewmodel.loadingLiveData.removeObservers(this)
        viewmodel.loadingLiveData.observe(this) {
            d_binding.progress.isVisible = it.isProgress
        }

        viewmodel.get_customer_liveData.removeObservers(this)
        viewmodel.get_customer_liveData.observe(this) {
            if (it.status == 1) {
                val customer = it.data
                val sessionHelper = CustomerSessionHelper(this)
                sessionHelper.saveLoggedInCustomer(
                    customerId = customer.id,
                    customerName = customer.customer_name,
                    mobile = customer.mobile_no
                )

                val intent = Intent(this, PointOfSaleActivity::class.java)
                intent.putExtra("c_id", customer.id)
                intent.putExtra("c_mobile", customer.mobile_no ?: "")
                intent.putExtra("c_name", customer.customer_name ?: "")
                intent.putExtra("c_tpin", customer.tin_tpin_no ?: "")
                startActivity(intent)

                if (dialog.isShowing) dialog.dismiss()
            } else {
                showMessage(it.message)
            }
        }

        d_binding.saveBtn.setOnClickListener {
            val inputValue = d_binding.mobileInput.text.toString().trim()

            if (inputValue.isEmpty() || inputValue.length < 9) {
                showMessage(getString(R.string.enter_valid_mobile_or_tin))
                return@setOnClickListener
            }

            if (!isInternetAvailable()) {
                val localHelper = CustomerLocalHelper(this)
                val isMobile = d_binding.toggle.checkedRadioButtonId == R.id.mobile_btn
                
                val matchedCustomer = if (isMobile) {
                    localHelper.getCustomers().find { it.mobile_no == inputValue }
                } else {
                    localHelper.getCustomers().find { it.tin_tpin_no == inputValue }
                }

                if (matchedCustomer != null) {
                    val sessionHelper = CustomerSessionHelper(this)
                    sessionHelper.saveLoggedInCustomer(
                        customerId = matchedCustomer.id,
                        customerName = matchedCustomer.customer_name,
                        mobile = matchedCustomer.mobile_no
                    )

                    val intent = Intent(this, PointOfSaleActivity::class.java)
                    intent.putExtra("c_id", matchedCustomer.id)
                    intent.putExtra("c_mobile", matchedCustomer.mobile_no ?: "")
                    intent.putExtra("c_name", matchedCustomer.customer_name ?: "")
                    intent.putExtra("c_tpin", matchedCustomer.tin_tpin_no ?: "")
                    startActivity(intent)
                    dialog.dismiss()
                } else {
                    showMessage(getString(R.string.customer_does_not_exist))
                }
                return@setOnClickListener
            }

            if (d_binding.toggle.checkedRadioButtonId == R.id.mobile_btn) {
                viewmodel.callGetCustomerDetailsApi(
                    getCustomerReq(mobile_no = inputValue, tin_tpin_no = ""),
                    this
                )
            } else {
                viewmodel.callGetCustomerDetailsApi(
                    getCustomerReq(mobile_no = "", tin_tpin_no = inputValue),
                    this
                )
            }
        }

        d_binding.skipBtn.setOnClickListener {
            if (dialog.isShowing) dialog.dismiss()
            val intent = Intent(this, PointOfSaleActivity::class.java)
            intent.putExtra("c_id", 0)
            intent.putExtra("c_mobile", "")
            intent.putExtra("c_name", "")
            intent.putExtra("c_tpin", "")
            startActivity(intent)
        }

        dialog.show()
    }

    private fun mposLogout() {
        val device_id = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) ?: ""
        if (device_id.isEmpty()) {
            showMessage(getString(R.string.couldnt_fetch_device_id))
        } else if (storemanager_id.isEmpty()) {
            showMessage(getString(R.string.couldnt_fetch_store_manager_info))
        } else {
            loginViewmodel.callLogoutApi(this, storemanager_id, device_id)
        }

        loginViewmodel.logoutLiveData.observe(this) {
            CoroutineScope(Dispatchers.IO).launch {
                // ✅ Same-store-only offline policy — logout keeps offline data intact.
                //
                // The Room tables (sales, returns, replaces, goods-return, inventory,
                // sale-details, store settings, etc.) are intentionally NOT wiped on
                // logout, because:
                //   • The SAME store logging back in — online or offline — needs its
                //     data to still be there so the user can resume seamlessly.
                //   • A DIFFERENT store is not allowed to log in offline (see
                //     MPOSLoginActivity offline guard — it shows
                //     "Login in online mode first"). When a different store DOES log
                //     in online, the login activity calls
                //     OfflineDataResetUtil.resetAllOfflineData() to wipe everything
                //     fresh before saving the new store's data. So at no point can
                //     Store-A's data be observed by Store-B.
                //
                // The dedicated "InvoiceIdPrefs" SharedPreferences file is not touched
                // by clearAllUserData() either, so the per-store last-invoice-id
                // always survives logout and the next offline sale continues the
                // correct sequence for the same store.
                try {
                    Log.d("Logout", "✅ Offline data preserved for same-store re-login")
                } catch (e: Exception) {
                    Log.e("Logout", "❌ Error during logout cleanup: ${e.message}")
                }

                // ✅ Preserve the pending-sync queues across logout.
                //
                // Pending tasks (sale / return / replace / cancel / goods-return /
                // dispatch) belong to the store they were created in. When the
                // SAME store logs back in — online OR offline — those tasks must
                // still be present so the user can finish syncing them.
                //
                // Per-store isolation is enforced two ways:
                //   • Offline login is gated by store (see MPOSLoginActivity guard).
                //   • A DIFFERENT-store online login wipes everything via
                //     OfflineDataResetUtil.resetAllOfflineData(), which already
                //     clears pending_sales / pending_returns / pending_replaces /
                //     pending_cancel_sales / pending_goods_returns /
                //     pending_dispatches. So Store-B never sees Store-A's pending
                //     tasks.
                //
                // We therefore do NOT revert and do NOT clear the pending queues
                // here. The sync badge will simply reflect the same pending count
                // when the user re-logs into the same store.
                Log.d("Logout", "✅ Pending sync queues preserved for same-store re-login")

                // ✅ Clear SharedPreferences user data (session-scoped only).
                //    This does NOT touch InvoiceIdPrefs (per-store invoice counter)
                //    or ActiveStorePrefs (last-online-store marker), both of which
                //    MUST survive logout for the same-store offline re-login to work.
                try {
                    sharedPrefHelper.clearAllUserData()
                    Log.d("Logout", "✅ SharedPreferences cleared on logout")
                } catch (e: Exception) {
                    Log.e("Logout", "❌ Error clearing SharedPreferences: ${e.message}")
                }

                // Clear login session
                loginSession.clearLoginSession()

                val intent = Intent(this@MPOSDashboardActivity, MPOSLoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
                finish()
            }
        }
    }

    private fun showLogoutDialog() {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.logout_dialog_layout)
        dialog.setCancelable(false)
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val cancel = dialog.findViewById<MaterialButton>(R.id.prefer_cancel)
        val confirm = dialog.findViewById<MaterialButton>(R.id.prefer_confirm)
        val logoutMsg = dialog.findViewById<TextView>(R.id.logout_msg)
        val logoutImg = dialog.findViewById<ImageView>(R.id.dialog_logo)

        logoutImg.setImageResource(R.drawable.svg_off)

        // 🚨 Sync-pending warning.
        // Compute total pending sync items across every queue (sales, returns,
        // replaces, cancels, goods returns, dispatches). If anything is still
        // pending, inform the user — pending tasks are preserved across logout
        // so that re-logging into the SAME store (online or offline) will still
        // see them queued up to sync.
        logoutMsg.text = getString(R.string.logout_confirm_title)
        lifecycleScope.launch {
            val pendingTotal = try { getTotalPendingSyncCount() } catch (e: Exception) {
                Log.e("Logout", "Failed to compute pending sync count: ${e.message}")
                0
            }
            if (pendingTotal > 0) {
                logoutMsg.text = getString(R.string.unsynced_actions_warning, pendingTotal)
            }
        }

        confirm.setOnClickListener {
            dialog.dismiss()
            mposLogout()
        }

        cancel.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    /**
     * Totals the badge-count sources — the same Flows already observed by
     * observePendingSalesCount(). `.first()` takes the latest value and
     * returns. Kept private so only the logout dialog and mposLogout use it.
     */
    private suspend fun getTotalPendingSyncCount(): Int {
        return (posSaleRepository.getPendingSalesCountFlow().first() +
                pendingReturnRepository.getPendingReturnsCountFlow().first() +
                pendingReplaceRepository.getPendingCountFlow().first() +
                pendingCancelRepository.getPendingCancelCountFlow().first() +
                pendingGoodsReturnRepository.getPendingCountFlow().first() +
                pendingDispatchRepository.getPendingCountFlow().first())
    }

    private fun showMessage(msg: String) {
        Toast.makeText(this, com.retailone.pos.utils.LocalizationUtils.localizeUserMessage(this, msg), Toast.LENGTH_SHORT).show()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (actionBarDrawerToggle.onOptionsItemSelected(item)) {
            return true
        }
        return super.onOptionsItemSelected(item)
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

    override fun onBackPressed() {
        if (drawer.isDrawerOpen(androidx.core.view.GravityCompat.START)) {
            drawer.closeDrawer(androidx.core.view.GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }

    override fun onResume() {
        super.onResume()
        
        // Auto-sync if there are pending items and internet is available
        if (isInternetAvailable() && !isSyncing && currentState == SyncButtonState.IDLE) {
            lifecycleScope.launch {
                delay(500) // Give UI time to update badge visibility if coming from onCreate or returning
                if (binding.tvSyncBadge.visibility == android.view.View.VISIBLE) {
                    syncOfflineSales()
                }
            }
        }
    }
}
