package com.retailone.pos.adapter

import NumberFormatter
import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.retailone.pos.R
import com.retailone.pos.databinding.SalesDetailsItemLayoutBinding
import com.retailone.pos.localstorage.SharedPreference.LocalizationHelper
import com.retailone.pos.models.SalesPaymentModel.SalesDetails.SalesDetailsData
import com.retailone.pos.utils.CalculationHelper
import com.retailone.pos.utils.FunUtils
import java.math.BigDecimal
import java.math.RoundingMode

class SalesDetailsAdapter(
    val context: Context,
    val salesdetails: SalesDetailsData,
    val fromApi: Boolean = false
) : RecyclerView.Adapter<SalesDetailsAdapter.SalesDetailsViewHolder>() {

    private val localizationData = LocalizationHelper(context).getLocalizationData()

    class SalesDetailsViewHolder(val binding: SalesDetailsItemLayoutBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SalesDetailsViewHolder {
        return SalesDetailsViewHolder(
            SalesDetailsItemLayoutBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: SalesDetailsViewHolder, position: Int) {

        val item = salesdetails.sales_items[position]

        // Format the tax variable (Int)
        val taxValue = item.tax ?: 0
        val formattedTax = NumberFormatter().formatPrice(
            taxValue.toString(),
            localizationData
        )

        // Per-item Subtotal, Tax, and Total:
        //
        // Both online (fromApi=true) and offline (fromApi=false) use the same convention:
        //   • sub_total    = tax-exclusive base AFTER all discounts (item discount + spot)
        //   • tax_amount   = tax AFTER all discounts
        //   • total_amount = gross AFTER all discounts  (= sub_total + tax_amount)
        //   • discount     = item discount AMOUNT (shown separately)
        //
        // For offline sales, buildFinalSalesItems() in PointofSaleDetailsActivity computes
        // these post-spot values from grossAfterItemDisc * (1 - spotPct%) before storing
        // them in PosSalesItem, so the stored values are already final.
        val itemBaseForDisplay: Double
        val itemTaxForDisplay: Double
        if (item.sub_total > 0.0) {
            // Stored post-spot values available — use them directly.
            itemBaseForDisplay = item.sub_total
            itemTaxForDisplay = item.tax_amount
        } else {
            // Fallback: stored values missing — recompute from retail_price.
            val isInclusive = salesdetails.is_inclusive ?: true
            val itemTaxPercent = (item.tax ?: 0).toDouble()
            val itemLevelDiscount = (item.discount ?: 0).toDouble()

            val line = CalculationHelper.computeLine(
                retailPrice = item.retail_price,
                quantity = item.quantity,
                taxRate = itemTaxPercent,
                isInclusive = isInclusive,
                itemDiscount = itemLevelDiscount
            )
            itemBaseForDisplay = line.baseAfterDiscount.toDouble()
            itemTaxForDisplay = line.taxAfterDiscount.toDouble()
        }

        // Total: use total_amount (post-all-discounts gross) for both online and offline.
        val itemTotalForDisplay = item.total_amount.coerceAtLeast(0.0)

        val formattedPrice = NumberFormatter().formatPrice(
            BigDecimal.valueOf(itemTotalForDisplay).setScale(0, RoundingMode.HALF_UP).toPlainString(),
            localizationData
        )

        val roundedTaxStr = BigDecimal.valueOf(itemTaxForDisplay)
            .setScale(0, RoundingMode.HALF_UP)
            .toPlainString()

        val formattedTaxAmount = NumberFormatter().formatPrice(
            roundedTaxStr,
            localizationData
        )

        val roundedSubTotalStr = BigDecimal.valueOf(itemBaseForDisplay)
            .setScale(0, RoundingMode.HALF_UP)
            .toPlainString()

        val formattedSubTotal = NumberFormatter().formatPrice(
            roundedSubTotalStr,
            localizationData
        )

        // ✅ Get discount value from backend
        val discountValue = item.discount ?: 0

        // ✅ Format discount amount
        val roundedDiscount = BigDecimal.valueOf(discountValue.toDouble())
            .setScale(0, RoundingMode.HALF_UP)
            .toPlainString()

        val formattedDiscount = NumberFormatter().formatPrice(
            roundedDiscount,
            localizationData
        )

        holder.binding.apply {
            date.text = item.product.product_name
            name.text = item.distribution_pack_name
            category.text = item.total_quantity.let { FunUtils.DtoString(it) }

            tax.text = context.getString(R.string.tax_label_single, (item.tax ?: 0).toString()) + ":   "
            taxamount.text = formattedTaxAmount
            subtotal.text = formattedSubTotal
            totalamount.text = formattedPrice

            // ✅ Show discount row if discount exists
            if (discountValue > 0) {
                discountRow.isVisible = true
                discountamount.text = formattedDiscount
            } else {
                discountRow.isVisible = false
            }
        }
    }


    override fun getItemCount(): Int {
        return salesdetails.sales_items.size
    }
}