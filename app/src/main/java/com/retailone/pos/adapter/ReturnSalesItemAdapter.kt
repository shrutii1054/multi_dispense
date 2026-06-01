package com.retailone.pos.adapter

import NumberFormatter
import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.retailone.pos.R
import com.retailone.pos.databinding.ReturnItemLayoutBinding
import com.retailone.pos.interfaces.OnReturnQuantityChangeListener
import com.retailone.pos.localstorage.SharedPreference.LocalizationHelper
import com.retailone.pos.localstorage.SharedPreference.SharedPrefHelper
import com.retailone.pos.models.MaterialRcvModel.MaterialRcvInv.MatRcvItem
import com.retailone.pos.models.ReturnSalesItemModel.BatchReturnItem
import com.retailone.pos.models.ReturnSalesItemModel.ReturnItemData
import com.retailone.pos.models.ReturnSalesItemModel.SalesItem
import com.retailone.pos.models.StockRequisitionModel.PastReqDetailsModel.DispatchBatchDetails
import com.retailone.pos.utils.FunUtils

class ReturnSalesItemAdapter(
    private val returnitem: List<ReturnItemData>,
    val context: Context,
    private val onReturnQuantityChangeListener: OnReturnQuantityChangeListener,
    val onBatchChange: (List<BatchReturnItem>) -> Unit,
    private val returnReasonName: String = "Not Given",
    val isInclusive: Boolean = true
) : RecyclerView.Adapter<ReturnSalesItemAdapter.StockSearchViewHolder>() {

    /* private var matrcvd = MaterialReceived()

     fun setMatRcvdData(matrcvd: MaterialReceived) {
         this.matrcvd = matrcvd
         notifyDataSetChanged()
     }*/
    private val batchAdapters = mutableMapOf<Int, ReturnSalesItemBatchAdapter>()

    private val sharedPrefHelper = SharedPrefHelper(context)
    val localizationData = LocalizationHelper(context).getLocalizationData()

    private val returnbatchItemList = mutableListOf<BatchReturnItem>()
    private val batchAdaptersMap = mutableMapOf<Int, ReturnSalesItemBatchAdapter>()
//    init {
//        // Initialize matReceivedList with default values
//        returnbatchItemList.addAll(returnitem.map { MatRcvItem(it.id, 0,
//            emptyList()
//        ) })
//    }



    class StockSearchViewHolder(val binding: ReturnItemLayoutBinding) :
        RecyclerView.ViewHolder(binding.root) {

    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StockSearchViewHolder {
        return StockSearchViewHolder(
            ReturnItemLayoutBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: StockSearchViewHolder, position: Int) {
        //  val isInvoiceAlreadyReturned = returnitem[0].total_refunded_amount > 0

        val productitem = returnitem[0].salesItems?.getOrNull(position) ?: return
        val isInvoiceAlreadyReturned = returnitem[0].total_refunded_amount > 0
        val isLooseOil = FunUtils.isLooseOil(productitem.product?.id ?: -1, productitem.distribution_pack?.product_description ?: "")

        holder.binding.itemName.text = productitem.product?.product_name ?: productitem.product_name ?: "Unknown Item"
        holder.binding.itemDesc.text = productitem.distribution_pack?.product_description ?: productitem.distribution_pack_name ?: ""
        holder.binding.itemUnit.text = context.getString(R.string.purchase_qty_label, FunUtils.DtoString(productitem.quantity))

        val formattedPrice = NumberFormatter().formatPrice(productitem.retail_price.toString() ?: "-", localizationData)
        holder.binding.itemPrice.text = context.getString(R.string.rate_label_prefix, formattedPrice)

        val returnQty = productitem.return_quantity

        val refundPrice = productitem.return_quantity * productitem.retail_price
        val formattedRefundPrice = NumberFormatter().formatPrice(refundPrice.toString() ?: "-", localizationData)

        holder.binding.refundPrice.text = context.getString(R.string.refund_label_prefix, formattedRefundPrice)

        val position = holder.adapterPosition

        // ✅ If invoice already returned
        if (isInvoiceAlreadyReturned) {
            holder.binding.addcart.isEnabled = false
            holder.binding.quantEdit.isEnabled = false
            holder.binding.addcart.isVisible = false
            holder.binding.quantLayout.isVisible = false
            holder.binding.batchRcv.isVisible = true
            holder.binding.batchLayouts.isVisible = true

            // ✅ Determine whether THIS specific item was actually returned.
            // The authoritative signal is `sales_items[].sales_returns` (snake_case list)
            // which the online API populates only for items that were actually returned,
            // and which the offline partial-return cache path also populates correctly.
            // `SalesItem.return_quantity` (camelCase) can be stale if a prior FULL-return
            // cache write marked every item — we don't want that stale value to drive the
            // item card display.
            val detailedMatch = returnitem.firstOrNull()?.sales_items?.find { si ->
                si.id == productitem.id ||
                    (si.product_id == productitem.product_id &&
                     si.distribution_pack_id == productitem.distribution_pack_id)
            }
            val hasActualReturns = !detailedMatch?.sales_returns.isNullOrEmpty()
            val actualReturnQty = if (hasActualReturns) {
                detailedMatch?.sales_returns?.sumOf { it.return_quantity.toInt() } ?: 0
            } else 0
            // If the authoritative list exists AND this item has no returns in it → force
            // zeros for this card (non-returned item must not contribute to item card values).
            // If sales_items isn't available at all, fall back to the legacy behavior so we
            // don't accidentally hide values that used to show.
            val salesItemsAvailable = !returnitem.firstOrNull()?.sales_items.isNullOrEmpty()
            val forceZero = salesItemsAvailable && !hasActualReturns

            // ✅ Reflect the correct qty in the top-level "quantEdit" display too.
            val displayReturnQty = when {
                forceZero -> 0
                hasActualReturns -> actualReturnQty
                else -> returnQty
            }
            holder.binding.quantEdit.setText(displayReturnQty.toString())

            // ✅ Enrich batch items with the parent item's return_quantity.
            // When data comes from the API, batch objects have return_quantity=0 because the
            // API stores return data in sales_returns (not in the batch list). The parent
            // SalesItem.return_quantity is correctly populated by the offline restore logic.
            val effectiveParentReturnQty = when {
                forceZero -> 0
                hasActualReturns -> actualReturnQty
                else -> productitem.return_quantity
            }

            val enrichedBatches = if (!productitem.batches.isNullOrEmpty()) {
                productitem.batches.map { batch ->
                    if (forceZero) {
                        // ✅ Non-returned item: zero out per-batch return values so the
                        //    item card shows RWF0.00 / return qty = 0, independent of any
                        //    stale cache values that might be lingering in the batch objects.
                        batch.copy(
                            return_quantity = 0,
                            batch_return_quantity = 0,
                            batch_refund_amount = 0.0,
                            sales_item_id = batch.sales_item_id?.takeIf { it > 0 } ?: productitem.id
                        )
                    } else if ((batch.return_quantity ?: 0) == 0 && effectiveParentReturnQty > 0) {
                        batch.copy(
                            return_quantity = effectiveParentReturnQty,
                            batch_return_quantity = effectiveParentReturnQty,
                            sales_item_id = batch.sales_item_id?.takeIf { it > 0 } ?: productitem.id
                        )
                    } else batch
                }
            } else if (!forceZero && effectiveParentReturnQty > 0) {
                // No batches at all but item was returned → synthesize one for display
                listOf(BatchReturnItem(
                    batch = null,
                    quantity = productitem.quantity,
                    retail_price = productitem.retail_price,
                    tax_exclusive_price = productitem.tax_exclusive_price,
                    subtotal = productitem.total_amount,
                    product_id = productitem.product_id,
                    distribution_pack_id = productitem.distribution_pack_id,
                    sales_item_id = productitem.id,
                    return_quantity = effectiveParentReturnQty,
                    batch_return_quantity = effectiveParentReturnQty,
                    return_reason = returnReasonName
                ))
            } else productitem.batches // null → ?.let won't fire below

            enrichedBatches?.let {
                holder.binding.batchRcv.apply {
                    layoutManager = LinearLayoutManager(holder.itemView.context, RecyclerView.VERTICAL, false)
                    setHasFixedSize(true)
                    adapter = ReturnSalesItemBatchAdapter(
                        returnitem,
                        context,
                        enrichedBatches,
                        isInvoiceAlreadyReturned,
                        returnReasonName,
                        isInclusive
                    ) { updatedBatches ->
                        updateReceivedQuantityX(updatedBatches, position)
                    }
                }
            }

        } else {
            // ✅ Allow normal editing
            holder.binding.addcart.setOnClickListener {
                if (!isLooseOil) {
                    holder.binding.addlayout.isVisible = false
                    // holder.binding.returnreason.isVisible = false
                    productitem.batches?.let {
                        holder.binding.batchRcv.isVisible = true
                        holder.binding.batchLayout.isVisible = true

                        holder.binding.batchRcv.apply {
                            layoutManager = LinearLayoutManager(holder.itemView.context, RecyclerView.VERTICAL, false)
                            setHasFixedSize(true)
                            adapter = ReturnSalesItemBatchAdapter(
                                returnitem,
                                context,

                                productitem.batches,
                                isInvoiceAlreadyReturned,
                                "Not Given",
                                isInclusive,
                            ) { updatedBatches ->
                               updateReceivedQuantityX(updatedBatches, position)

                            }
                             /*it,
                             onBatchChange = {} */// Read-only, so no update needed

                        }
                    }
                } else {
                    Toast.makeText(context, context.getString(R.string.loose_oil_return_error), Toast.LENGTH_SHORT).show()
                }
            }

            // Quantity editing only for non-returned invoices
            cartControl(holder.binding, productitem, position)
        }
    }



    override fun getItemCount(): Int {
        return returnitem[0].salesItems?.size ?: 0
    }


    override fun getItemViewType(position: Int) = position

    override fun getItemId(position: Int) = position.toLong()

    override fun onViewRecycled(holder: ReturnSalesItemAdapter.StockSearchViewHolder) {
        super.onViewRecycled(holder)
        //holder.binding.quantEdit.removeTextChangedListener(holder.textWatcher)
    }



  private fun updateReceivedQuantityX(batch_list: List<BatchReturnItem>, position: Int) {
      for (item in batch_list) {
          val batchKey = item.batch?.trim()?.lowercase() ?: ""
          val existingItemIndex = returnbatchItemList.indexOfFirst {
              it.sales_item_id == item.sales_item_id &&
                      (it.batch?.trim()?.lowercase() ?: "") == batchKey
          }

          if ((item.batch_return_quantity ?: 0) > 0) {
              if (existingItemIndex != -1) {
                  returnbatchItemList[existingItemIndex] = item
              } else {
                  returnbatchItemList.add(item)
              }
          } else {
              if (existingItemIndex != -1) {
                  returnbatchItemList.removeAt(existingItemIndex)
                  Log.d("ReturnAdapter", "Removed from returnbatchItemList: ${item.batch}")
              }
          }
      }

      // Ensure only items with non-zero quantity are passed
      val filteredList = returnbatchItemList.filter { (it.batch_return_quantity ?: 0) > 0 }
      Log.d("ReturnAdapter", "Final returnbatchItemList: ${filteredList.map { it.batch to it.batch_return_quantity }}")

      // ✅ This filtered list will only include valid return entries
      onBatchChange(filteredList)
  }




    private fun cartControl(
        binding: ReturnItemLayoutBinding,
        productitem: SalesItem,
        position: Int
    ) {
        var oldnum = 0
        var isProgrammaticChange = false

        binding.quantEdit.addTextChangedListener(object : TextWatcher {

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                if (!isProgrammaticChange) {
                    try {
                        oldnum = s.toString().toInt()
                    } catch (e: NumberFormatException) {
                        // Handle the exception if needed
                    }
                }
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (isProgrammaticChange) return

                try {
                    val enteredValue = s.toString().toIntOrNull() ?: 0

                    if (enteredValue == 0) {
                        isProgrammaticChange = true
                        binding.quantEdit.text = Editable.Factory.getInstance().newEditable("")
                        Toast.makeText(context, context.getString(R.string.add_return_quantity_error), Toast.LENGTH_SHORT).show()
                        onReturnQuantityChangeListener.onReturnQuantityChange(position, 0)

                        val refundPrice =enteredValue * productitem.retail_price
                        val formattedRefundPrice = NumberFormatter().formatPrice(refundPrice.toString()?:"-",localizationData)
                        binding.refundPrice.text = context.getString(R.string.refund_label_prefix, formattedRefundPrice)

                    } else if (enteredValue.toString().startsWith("00")) {
                        isProgrammaticChange = true
                        binding.quantEdit.text = Editable.Factory.getInstance().newEditable("0")
                        Toast.makeText(context, context.getString(R.string.leading_zeros_replaced), Toast.LENGTH_SHORT).show()
                        onReturnQuantityChangeListener.onReturnQuantityChange(position, 0)

                        val refundPrice =enteredValue * productitem.retail_price
                        val formattedRefundPrice = NumberFormatter().formatPrice(refundPrice.toString()?:"-",localizationData)
                        binding.refundPrice.text = context.getString(R.string.refund_label_prefix, formattedRefundPrice)

                    } else if (enteredValue <= productitem.quantity.toInt()) {
                        onReturnQuantityChangeListener.onReturnQuantityChange(position, enteredValue)

                        val refundPrice =enteredValue * productitem.retail_price
                        val formattedRefundPrice = NumberFormatter().formatPrice(refundPrice.toString()?:"-",localizationData)
                        binding.refundPrice.text = context.getString(R.string.refund_label_prefix, formattedRefundPrice)

                    } else {
                        Toast.makeText(context, context.getString(R.string.cannot_return_more_than_purchase), Toast.LENGTH_SHORT).show()
                        isProgrammaticChange = true

                        //binding.quantEdit.text = Editable.Factory.getInstance().newEditable(oldnum.toString())

                        if(oldnum>0){
                            binding.quantEdit.text = Editable.Factory.getInstance().newEditable(oldnum.toString())
                        }else{
                            binding.quantEdit.text = Editable.Factory.getInstance().newEditable("")
                        }
                        binding.quantEdit.setSelection(binding.quantEdit.text.length)

                    }
                } catch (e: NumberFormatException) {
                    // Handle the exception if needed
                } finally {
                    isProgrammaticChange = false
                }
            }

            override fun afterTextChanged(s: Editable?) {
                // Perform actions after the text has changed if needed
            }
        })
    }



//    private fun cartControl(
//        binding: ReturnItemLayoutBinding,
//        productitem: SalesItem,
//        position: Int
//    ) {
//        var oldnum = 0
//        binding.quantEdit.addTextChangedListener(object : TextWatcher {
//
//            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
//                try {
//                    oldnum = s.toString().toInt()
//                } catch (e: NumberFormatException) {
//                    // Handle the exception if needed
//                }
//            }
//
//            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
//                try {
//                    val enteredValue = s.toString().toIntOrNull() ?: 0
//
//                    //val enteredValue = s.toString().toInt()
//                    if (enteredValue.toString().startsWith("00")) {
//                        binding.quantEdit.text = Editable.Factory.getInstance().newEditable("0")
//                        Toast.makeText(context, "Leading zeros replaced with 0", Toast.LENGTH_SHORT).show()
//
//                        onReturnQuantityChangeListener.onReturnQuantityChange(position, 0)
//
//                        val refundPrice =productitem.return_quantity * productitem.retail_price
//                        val formattedRefundPrice = NumberFormatter().formatPrice(refundPrice.toString()?:"-",localizationData)
//                        binding.refundPrice.text = context.getString(R.string.refund_label_prefix, formattedRefundPrice)
//
//                    } else if (enteredValue == 0) {
//                        //binding.quantEdit.text = Editable.Factory.getInstance().newEditable("")
//
//
//                        // Check if the entered value is exactly 0
//                       // Toast.makeText(context, "Product Quantity can't be zero", Toast.LENGTH_SHORT).show()
//                        // Uncomment the line below if you want to clear the input when it's exactly 0
//                        // binding.quantEdit.text = Editable.Factory.getInstance().newEditable("")
//
//                        onReturnQuantityChangeListener.onReturnQuantityChange(position, 0)
//
//
//                        val refundPrice =productitem.return_quantity * productitem.retail_price
//                        val formattedRefundPrice = NumberFormatter().formatPrice(refundPrice.toString()?:"-",localizationData)
//                        binding.refundPrice.text = context.getString(R.string.refund_label_prefix, formattedRefundPrice)
//
//
//                    } else if(enteredValue <= productitem.quantity.toInt()) {
//
//                        /// notifyDataSetChanged()
//                        onReturnQuantityChangeListener.onReturnQuantityChange(position, enteredValue)
//
//                        val refundPrice =productitem.return_quantity * productitem.retail_price
//                        val formattedRefundPrice = NumberFormatter().formatPrice(refundPrice.toString()?:"-",localizationData)
//                        binding.refundPrice.text = context.getString(R.string.refund_label_prefix, formattedRefundPrice)
//
//                        /*sharedPrefHelper.updateQuantity(
//                            productitem.product_id,
//                            productitem.distribution_pack_id,
//                            enteredValue.toString()
//                        )*/
//                    } else {
//                        Toast.makeText(context, context.getString(R.string.cannot_return_more_than_purchase), Toast.LENGTH_SHORT).show()
//                        binding.quantEdit.text = Editable.Factory.getInstance().newEditable(oldnum.toString())
//                        // Set the cursor to the last position
//                        binding.quantEdit.setSelection(binding.quantEdit.text.length)
//                        // binding.quantEdit.clearFocus()
//                    }
//                } catch (e: NumberFormatException) {
//                    // Handle the exception if needed
//                }
//            }
//
//            override fun afterTextChanged(s: Editable?) {
//                // Perform actions after the text has changed if needed
//            }
//        })
//
//    }




}

