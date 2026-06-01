package com.retailone.pos.utils

import android.util.Log
import com.retailone.pos.models.CommonModel.StroreProduct.StoreProData
import com.retailone.pos.models.PointofsaleModel.PosAddToCartModel.AddToCartResData
import com.retailone.pos.models.PointofsaleModel.PosAddToCartModel.BatchCartItem
import com.retailone.pos.models.PointofsaleModel.PosAddToCartModel.DistributionPackCart
import com.retailone.pos.models.PointofsaleModel.PosAddToCartModel.PosAddToCartRes
import com.retailone.pos.models.PointofsaleModel.PosAddToCartModel.PriceIncTaxItem
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Offline Point-of-Sale cart calculator.
 *
 * All arithmetic is delegated to [CalculationHelper] so the OFFLINE cart stays in lock-step
 * with the server, the Return flow and the SalesPaymentDetails display.
 *
 * KEY RULE: batch.discount from the searchstoreproduct API is a per-unit discount AMOUNT
 * (e.g. 5000 means 5000 currency units off per unit sold). It is NOT a percentage.
 *
 *   discount_amount = batch.discount * qty   (for both is_inclusive = true and false)
 *
 * The server formula (confirmed from online-mode screenshots) for is_inclusive = true:
 *   gross             = retail * qty
 *   sub_total         = gross / (1 + tax%)            [display: tax-exclusive base]
 *   discount_amount   = batch.discount * qty           [e.g. 5000 per unit × 3 qty = 15000]
 *   gross_after_disc  = gross - discount_amount
 *   spot_amount       = gross_after_disc * spot_pct / 100
 *   grand_total       = gross_after_disc - spot_amount  [tax embedded, NOT added again]
 *   tax_shown         = grand_total * rate / (1 + rate)  [informational]
 *
 * For is_inclusive = false:
 *   base              = retail * qty
 *   discount_amount   = batch.discount * qty
 *   base_after_disc   = base - discount_amount
 *   spot_amount       = base_after_disc * spot_pct / 100
 *   base_after_spot   = base_after_disc - spot_amount
 *   tax               = base_after_spot * tax%
 *   grand_total       = base_after_spot + tax
 */
object OfflineCartCalculator {

    private const val TAG = "OfflineCartCalc"

    fun calculateCartTotals(
        cartItems: List<StoreProData>,
        spotDiscountPercent: Double = 0.0,
        isInclusive: Boolean = true
    ): PosAddToCartRes {

        Log.e(TAG, "OFFLINE_CALC_DEBUG: is_inclusive is -> $isInclusive")

        val cartResDataList = mutableListOf<AddToCartResData>()
        val allLineInputs = mutableListOf<CalculationHelper.LineInput>()
        var maxTaxRate = 0

        cartItems.forEach { item ->
            val batchCartItems = mutableListOf<BatchCartItem>()
            val priceIncTaxItems = mutableListOf<PriceIncTaxItem>()

            // Per-product aggregates (to populate AddToCartResData)
            // PRE-discount base and tax — matches what the online API returns in sub_total/tax_amount
            var productBasePreDisc = BigDecimal.ZERO
            var productTaxPreDisc = BigDecimal.ZERO
            // POST-discount base and tax — used for computing the line total
            var productBase = BigDecimal.ZERO
            var productTax = BigDecimal.ZERO
            var productDiscountAmount = BigDecimal.ZERO   // total discount in AMOUNT (not %)
            var productTaxRate = 0

            item.batch.forEach { batch ->
                // 🔹 Keep qty as Double so fractional dispensed quantities
                // (e.g. 0.01 L of loose oil) are not truncated to 0. Whole-number
                // quantities behave identically because BigDecimal.valueOf(5.0)
                // == BigDecimal.valueOf(5L) for our math.
                val qty = batch.batch_cart_quantity
                val retailPrice = batch.price
                // batch.discount is a per-unit discount AMOUNT (e.g. 5000 = 5000 currency units off per unit sold).
                val discountPerUnit = batch.discount.coerceAtLeast(0.0)
                val taxRate = batch.tax?.toIntOrNull() ?: 0

                if (taxRate > productTaxRate) productTaxRate = taxRate
                if (taxRate > maxTaxRate) maxTaxRate = taxRate

                if (qty > 0.0) {
                    // discount_amount = discountPerUnit × qty — same formula for both is_inclusive = true and false.
                    val gross = BigDecimal.valueOf(retailPrice).multiply(BigDecimal.valueOf(qty))
                    val discountAmount = BigDecimal.valueOf(discountPerUnit)
                        .multiply(BigDecimal.valueOf(qty))
                        .setScale(10, RoundingMode.HALF_UP)

                    val line = CalculationHelper.computeLine(
                        retailPrice = retailPrice,
                        quantity = qty,
                        taxRate = taxRate.toDouble(),
                        isInclusive = isInclusive,
                        itemDiscount = discountAmount.toDouble()   // amount, not percent
                    )

                    // Accumulate for the product card
                    // Pre-discount values — matches online API's sub_total / tax_amount fields
                    productBasePreDisc = productBasePreDisc.add(line.base)
                    productTaxPreDisc = productTaxPreDisc.add(line.tax)
                    // Post-discount values — used to compute the line total (gross_after_disc)
                    productBase = productBase.add(line.baseAfterDiscount)
                    productTax = productTax.add(line.taxAfterDiscount)
                    productDiscountAmount = productDiscountAmount.add(discountAmount)

                    // Tax-exclusive unit price (for price_inclusive_tax list)
                    val unitPriceExclTax: Double = if (qty > 0.0) {
                        CalculationHelper.round2(
                            line.base.divide(BigDecimal.valueOf(qty), 10, RoundingMode.HALF_UP)
                        ).toDouble()
                    } else 0.0

                    batchCartItems.add(
                        BatchCartItem(
                            batchno = batch.batch_no,
                            retail_price = retailPrice,
                            quantity = qty,
                            discount = discountPerUnit   // per-unit discount amount
                        )
                    )
                    priceIncTaxItems.add(
                        PriceIncTaxItem(
                            unit_price = unitPriceExclTax,
                            batch_no = batch.batch_no
                        )
                    )

                    // Feed the cart-level aggregator — pass the discount AMOUNT
                    allLineInputs.add(
                        CalculationHelper.LineInput(
                            retailPrice = retailPrice,
                            quantity = qty,
                            taxRate = taxRate.toDouble(),
                            discount = discountAmount.toDouble()   // amount, not percent
                        )
                    )
                }
            }

            if (productBasePreDisc > BigDecimal.ZERO || productDiscountAmount > BigDecimal.ZERO) {
                val distPack = DistributionPackCart(
                    id = item.distribution_pack_id,
                    product_id = item.product_id,
                    product_description = item.pack_product_description,
                    no_of_packs = item.no_of_packs,
                    uom = item.uom ?: "",
                    barcode = item.barcode ?: "",
                    retail_sku = "",
                    status = 1
                )

                // Product line total = base-after-discount + tax-after-discount
                val productTotal = CalculationHelper.round2(productBase.add(productTax)).toDouble()

                // Store discount as the AMOUNT (not %) to match what the online API returns.
                // This is what PointofSaleDetailsActivity reads via AddToCartResData.discount
                // when building PosSalesItem, and what SalesPaymentViewmodel uses in
                // convertPendingSaleToSalesDetailsData.
                // Use round0 (nearest integer) since AddToCartResData.discount is Int,
                // matching the online API's type.
                val productDiscountAmountInt =
                    CalculationHelper.round0(productDiscountAmount).toInt()

                cartResDataList.add(
                    AddToCartResData(
                        distribution_pack_id = item.distribution_pack_id,
                        distribution_pack = distPack,
                        // price_without_discount = PRE-discount tax-exclusive base, matching the
                        // online API's sub_total field so SalesDetailsAdapter displays consistently.
                        price_without_discount = CalculationHelper.round2(productBasePreDisc).toDouble(),
                        product_id = item.product_id,
                        product_name = item.product_name,
                        batch = batchCartItems,
                        stock_id = item.store_id,
                        total = productTotal,
                        // tax_amount = PRE-discount tax, matching the online API's tax_amount field.
                        tax_amount = CalculationHelper.round2(productTaxPreDisc).toPlainString(),
                        tax = productTaxRate,
                        taxrate = productTaxRate.toString(),
                        discount = productDiscountAmountInt,   // AMOUNT (e.g. 15 for 5% on 300)
                        price_inclusive_tax = priceIncTaxItems
                    )
                )
            }
        }

        // Cart-level aggregate with spot discount + mixed-tax-safe tax total
        val cart = CalculationHelper.computeCart(
            lines = allLineInputs,
            isInclusive = isInclusive,
            spotDiscountPercent = spotDiscountPercent
        )

        Log.d(
            TAG,
            "incl=$isInclusive spot=$spotDiscountPercent% -> " +
                "subTotal(base)=${cart.subTotal}, itemDiscAmt=${cart.itemDiscountTotal}, " +
                "baseAfterItem=${cart.subTotalAfterItemDiscount}, spotAmt=${cart.spotDiscountAmount}, " +
                "baseAfterSpot=${cart.subTotalAfterSpot}, tax=${cart.taxAmount}, grand=${cart.grandTotal}"
        )

        // For is_inclusive=true the server's sub_total is the tax-exclusive base AFTER all
        // discounts (item discount + spot), i.e. grand_total - tax = cart.subTotalAfterSpot.
        // For is_inclusive=false the server's sub_total is the pre-discount tax-exclusive base
        // (cart.subTotal); the per-item discount is then shown as a separate field
        // (subtotal_after_discount). UI layers that want to display "subtotal after discount"
        // for the user should read `sub_total_after_discount` (or the
        // `subTotalAfterItemDiscount` field on the cart) instead of `sub_total` so we keep
        // server-bound data semantics intact.
        val subTotalRounded = CalculationHelper.round2(
            if (isInclusive) cart.subTotalAfterSpot else cart.subTotal
        ).toDouble()
        val taxRounded = CalculationHelper.round2(cart.taxAmount)
        val subTotalAfterDiscountRounded = CalculationHelper.round2(cart.subTotalAfterItemDiscount).toDouble()
        val spotAmountRounded = CalculationHelper.round2(cart.spotDiscountAmount)
        val grandTotalRounded = CalculationHelper.round2(cart.grandTotal)

        return PosAddToCartRes(
            data = cartResDataList,
            discount_amount = CalculationHelper.round2(cart.itemDiscountTotal).toDouble(),
            grand_total = grandTotalRounded.toPlainString(),
            message = "Offline calculation",
            status = 1,
            sub_total = subTotalRounded,
            sub_total_after_discount = subTotalAfterDiscountRounded,
            tax = "@${maxTaxRate}%",
            tax_amount = taxRounded.toPlainString(),
            spot_discount_percentage = spotDiscountPercent.toString(),
            spot_discount_amount = spotAmountRounded.toPlainString()
        )
    }
}
