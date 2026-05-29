package com.retailone.pos.ui.Activity.DashboardActivity



import ReturnedProduct
import StockReturn
import StockReturnAdapter
import StockReturnResponse
import android.content.Context
import android.content.Intent
import com.retailone.pos.ui.Activity.LocalizedAppCompatActivity
import android.os.Bundle
import android.widget.FrameLayout
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.retailone.pos.R
import com.retailone.pos.adapter.PastRequDetailsAdapter
import com.retailone.pos.adapter.PastRequisitionAdapter
import com.retailone.pos.adapter.ProductReorderAlertAdapter
import com.retailone.pos.adapter.ProductReturnAlertAdapter
import com.retailone.pos.databinding.ActivityPrceedtoDispatchBinding
import com.retailone.pos.databinding.ActivityStockRequisitionBinding
import com.retailone.pos.databinding.BottomsheetPastRequisitionLayoutBinding
import com.retailone.pos.databinding.BottomsheetReturnproductLayoutBinding
import com.retailone.pos.localstorage.DataStore.LoginSession
import com.retailone.pos.localstorage.SharedPreference.LocalizationHelper
import com.retailone.pos.localstorage.SharedPreference.OrganisationDetailsHelper
import com.retailone.pos.models.GoodsToWarehouseModel.ReturnStocks.ProductModel
import com.retailone.pos.models.LocalizationModel.LocalizationData
import com.retailone.pos.models.StockRequisitionModel.PastReqDetailsModel.PastReqDetailsList
import com.retailone.pos.utils.DateTimeFormatting
import com.retailone.pos.viewmodels.DashboardViewodel.MaterialReceivingViewmodel
import com.retailone.pos.viewmodels.DashboardViewodel.StockRequisitionViewmodel
import com.retailone.pos.viewmodels.DashboardViewodel.StockReturnViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

class proceedToDispatchActivity : LocalizedAppCompatActivity() {
    lateinit var  binding:ActivityPrceedtoDispatchBinding

    private lateinit var viewModel: StockReturnViewModel
    private  var storeid = ""

    //GoodsReturnToWarehouseActivity
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityPrceedtoDispatchBinding.inflate(layoutInflater)
        setContentView(binding.root)
        enableBackButton()

        viewModel = ViewModelProvider(this)[StockReturnViewModel::class.java]
        
        lifecycleScope.launch {
            // ✅ Initialize repository for offline support
            viewModel.initRepository(this@proceedToDispatchActivity)
            
            val loginSession = LoginSession.getInstance(this@proceedToDispatchActivity)
            storeid = loginSession.getStoreID().first()
            
            // ✅ Load from local DB first (instant, works offline!)
            val cachedReturns = viewModel.getStockReturnsFromCache(storeid.toInt())
            if (cachedReturns != null) {
                // Update UI with cached data immediately
                updateDispatchUI(cachedReturns)
            }

            // ✅ Refresh from API
            viewModel.fetchStockReturns(this@proceedToDispatchActivity)
        }

        binding.dispatchRcv.layoutManager = LinearLayoutManager(this)


      /*  viewModel.stockReturns.observe(this) {
            val allItems = it.data // DO NOT FILTER
            val adapter = StockReturnAdapter(allItems) { item ->
                Toast.makeText(this, "Dispatch clicked for ID: ${item.id}", Toast.LENGTH_SHORT).show()
            }
            binding.dispatchRcv.layoutManager = LinearLayoutManager(this)
            binding.dispatchRcv.adapter = adapter
        }*/


        viewModel.loading.observe(this) {
            binding.progressBar.isVisible = it
        }
        viewModel.stockReturns.observe(this) {
            updateDispatchUI(it)
        }

        binding.relativeLayout.setOnClickListener {
            val intent = Intent(this@proceedToDispatchActivity,GoodsReturnToWarehouseActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP

            startActivity(intent)
        }
    }

    private fun updateDispatchUI(it: StockReturnResponse) {
        val approvedList = it.data
        val adapter = StockReturnAdapter(
            approvedList,
            onDispatchClicked = { selectedItem ->
                val intent = Intent(this, ConfirmReturnReceiptActivity::class.java)
                intent.putExtra("return_id", selectedItem.id)
                intent.putExtra("status", selectedItem.status)
                intent.putExtra("date", selectedItem.requested_date)
                intent.putExtra("seal_no", selectedItem.products[0].seal_no)
                intent.putParcelableArrayListExtra("product_list", ArrayList(selectedItem.products))
                startActivity(intent)
            },
            onItemClicked = { selectedItem ->
                showProductListBottomSheet(selectedItem) // ✅ pass whole selectedItem
            }
        )
        binding.dispatchRcv.adapter = adapter
    }
    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
    override fun onResume() {
        super.onResume()
        viewModel.fetchStockReturns(this) // Refresh the list on returning back
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










/* private fun showProductListBottomSheet(products: List<ReturnedProduct>) {
     val bottomSheetDialog = BottomSheetDialog(this)
     val bottomSheetBinding = BottomsheetPastRequisitionLayoutBinding.inflate(layoutInflater)
     bottomSheetDialog.setContentView(bottomSheetBinding.root)

     val adapter = ProductReturnAlertAdapter(products)
     bottomSheetBinding.detailsRcv.layoutManager = LinearLayoutManager(this)
     bottomSheetBinding.detailsRcv.adapter = adapter

     bottomSheetBinding.closeBottomsheet.setOnClickListener {
         bottomSheetDialog.dismiss()
     }

     bottomSheetDialog.show()
 }*/


    private fun getStatusText(status: Int): String {
        return when (status) {
            2 -> getString(R.string.status_return_approved)
            3 -> getString(R.string.status_rejected)
            4 -> getString(R.string.status_dispatched)
            5 -> getString(R.string.status_received)
            else -> getString(R.string.status_pending)
        }
    }

    private fun showProductListBottomSheet(selectedItem: StockReturn) {
        val bottomSheetDialog = BottomSheetDialog(this)
        val bottomSheetBinding =  BottomsheetReturnproductLayoutBinding.inflate(layoutInflater)
        bottomSheetDialog.setContentView(bottomSheetBinding.root)
        bottomSheetBinding.detailsRcv.isNestedScrollingEnabled = true
        // Set item details
        bottomSheetBinding.tvReturnId.text = getString(R.string.return_id_colon, selectedItem.id.toString())

        bottomSheetBinding.tvStatus.text = getString(R.string.status_colon_text, getStatusText(selectedItem.status))
        bottomSheetBinding.tvRequestedDate.text = getString(R.string.requested_date_colon, formatDate(selectedItem.requested_date))


        val adapter = ProductReturnAlertAdapter(selectedItem.products)
        bottomSheetBinding.detailsRcv.layoutManager = LinearLayoutManager(this)
        bottomSheetBinding.detailsRcv.adapter = adapter

        bottomSheetBinding.closeBottomsheet.setOnClickListener {
            bottomSheetDialog.dismiss()
        }

        bottomSheetDialog.show()
    }




    private fun formatDate(dateStr: String): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) // adjust pattern to match your string
            val outputFormat = SimpleDateFormat("dd-MMM-yyyy", Locale.getDefault())
            val date = inputFormat.parse(dateStr)
            if (date != null) outputFormat.format(date) else dateStr
        } catch (e: Exception) {
            dateStr // fallback
        }
    }
}












