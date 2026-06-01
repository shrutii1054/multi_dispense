package com.retailone.pos.utils

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Centralized calculation helper for POS cart, Return flow and SalesPaymentDetails view.
 *
 * Single source of truth for how the `is_inclusive` flag affects subtotal / tax / discount /
 * grand total. Everything in the app that calculates these values on the client side should
 * funnel through here so that the OFFLINE cart, Return summary card, Return item card and
 * the SalesPaymentDetails screen all produce consistent numbers.
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 * SERVER-MATCHING FORMULAS
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 *   INCLUSIVE (is_inclusive = true) — retail price ALREADY contains tax:
 *     gross        = retail * qty                  (the inclusive price, tax embedded)
 *     base         = gross / (1 + tax%)            (tax-exclusive portion, shown as sub_total)
 *     tax_in_price = gross - base                  (informational; already in the price)
 *
 *     Item discount is applied on the GROSS (inclusive) amount:
 *       gross_after_disc = max(0, gross - item_discount)
 *       base_after_disc  = gross_after_disc / (1 + tax%)
 *       tax_after_disc   = gross_after_disc - base_after_disc  (informational)
 *       line_total       = gross_after_disc                    (grand contribution; no extra tax)
 *
 *     Cart-level spot discount is applied on the GROSS amount after item discounts:
 *       spot_amt         = Σ(gross_after_item_disc) * spotPct / 100
 *       grand_total      = Σ(gross_after_item_disc) - spot_amt  (tax already embedded)
 *       tax_shown        = grand_total * rate / (1 + rate)       (informational)
 *       sub_total        = Σ(base)                               (tax-exclusive, display only)
 *
 *   EXCLUSIVE (is_inclusive = false) — retail price is the tax-exclusive base:
 *     base         = retail * qty
 *     tax          = base * tax%
 *     gross        = base + tax
 *
 *     Item discount applied on the tax-exclusive base:
 *       base_after_disc  = max(0, base - item_discount)
 *       tax_after_disc   = base_after_disc * tax%
 *       line_total       = base_after_disc + tax_after_disc
 *
 *     Cart-level spot discount applied on the tax-exclusive base after item discounts.
 *     Tax is recomputed on the discounted base per line to handle mixed tax rates:
 *       spot_amt         = Σ(base_after_item_disc) * spotPct / 100
 *       base_after_spot  = Σ(base_after_item_disc) - spot_amt
 *       tax_after_spot   = Σ per line (base_after_item_disc * (1 - spotPct/100) * tax%)
 *       grand_total      = base_after_spot + tax_after_spot
 */
object CalculationHelper {

    // ---------------- core data holders ----------------

    data class LineInput(
        val retailPrice: Double,
        val quantity: Double,
        val taxRate: Double,
        val discount: Double = 0.0
    )

    data class LineResult(
        val base: BigDecimal,               // tax-exclusive base, before item discount
        val tax: BigDecimal,                // tax corresponding to `base` (informational for inclusive)
        val baseAfterDiscount: BigDecimal,  // tax-exclusive base after item discount
        val taxAfterDiscount: BigDecimal,   // tax on baseAfterDiscount (informational for inclusive)
        val total: BigDecimal,              // gross_after_disc (inclusive) or base+tax after disc (exclusive)
        val gross: BigDecimal,              // retail * qty (inclusive price)
        val grossAfterDiscount: BigDecimal  // gross after item discount (key for inclusive cart total)
    )

    data class CartResult(
        val subTotal: BigDecimal,                // tax-exclusive base sum, before any discount (display)
        val itemDiscountTotal: BigDecimal,       // sum of per-item discounts (as given)
        val subTotalAfterItemDiscount: BigDecimal, // tax-exclusive base after item discounts
        val spotDiscountAmount: BigDecimal,      // spot discount amount
        val subTotalAfterSpot: BigDecimal,       // tax-exclusive base after spot (exclusive) or gross_after_spot (inclusive)
        val taxAmount: BigDecimal,               // tax on amount after all discounts
        val grandTotal: BigDecimal               // final amount payable
    )

    // ---------------- per-line helpers ----------------

    fun computeLine(
        retailPrice: Double,
        quantity: Double,
        taxRate: Double,
        isInclusive: Boolean,
        itemDiscount: Double = 0.0
    ): LineResult {
        val retail = BigDecimal.valueOf(retailPrice.coerceAtLeast(0.0))
        val qty = BigDecimal.valueOf(quantity.coerceAtLeast(0.0))
        val safeRate = taxRate.coerceAtLeast(0.0)
        val rateFrac = BigDecimal.valueOf(safeRate / 100.0)
        val one = BigDecimal.ONE
        val gross = retail.multiply(qty)  // retail * qty (inclusive price for inclusive mode)
        val disc = BigDecimal.valueOf(itemDiscount.coerceAtLeast(0.0))

        val base: BigDecimal
        val tax: BigDecimal
        val baseAfterDisc: BigDecimal
        val taxAfterDisc: BigDecimal
        val total: BigDecimal
        val grossAfterDisc: BigDecimal

        if (isInclusive) {
            // For inclusive: retail price already contains tax.
            // sub_total (base) is the tax-exclusive portion, used for display only.
            base = if (safeRate > 0.0) {
                gross.divide(one.add(rateFrac), 10, RoundingMode.HALF_UP)
            } else {
                gross
            }
            tax = gross.subtract(base)  // informational: tax embedded in gross

            // Item discount is applied on the GROSS (inclusive) amount — matching server behaviour.
            grossAfterDisc = gross.subtract(disc).max(BigDecimal.ZERO)
            baseAfterDisc = if (safeRate > 0.0) {
                grossAfterDisc.divide(one.add(rateFrac), 10, RoundingMode.HALF_UP)
            } else {
                grossAfterDisc
            }
            taxAfterDisc = grossAfterDisc.subtract(baseAfterDisc)  // informational

            // For inclusive mode, the line total IS the gross-after-discount.
            // No extra tax is added because it is already embedded in the price.
            total = grossAfterDisc

        } else {
            // For exclusive: retail price is the tax-exclusive base.
            base = gross
            tax = if (safeRate > 0.0) base.multiply(rateFrac) else BigDecimal.ZERO

            // Item discount applied on the tax-exclusive base.
            baseAfterDisc = base.subtract(disc).max(BigDecimal.ZERO)
            taxAfterDisc = if (safeRate > 0.0) baseAfterDisc.multiply(rateFrac) else BigDecimal.ZERO
            grossAfterDisc = baseAfterDisc.add(taxAfterDisc)
            total = grossAfterDisc
        }

        return LineResult(
            base = base,
            tax = tax,
            baseAfterDiscount = baseAfterDisc,
            taxAfterDiscount = taxAfterDisc,
            total = total,
            gross = gross,
            grossAfterDiscount = grossAfterDisc
        )
    }

    // ---------------- cart-level aggregate ----------------

    fun computeCart(
        lines: List<LineInput>,
        isInclusive: Boolean,
        spotDiscountPercent: Double = 0.0
    ): CartResult {
        var totalBase = BigDecimal.ZERO           // Σ(tax-exclusive base before discount)
        var totalItemDiscount = BigDecimal.ZERO   // Σ(item discounts as given)
        var totalBaseAfterItem = BigDecimal.ZERO  // Σ(tax-exclusive base after item disc)
        var totalGrossAfterItem = BigDecimal.ZERO // Σ(gross after item disc) — for inclusive

        // Per-line data for mixed-tax-safe spot recalculation
        // Stores (grossAfterItemDisc or baseAfterItemDisc, rateFrac) per line
        val perLine = mutableListOf<Triple<BigDecimal, BigDecimal, Boolean>>()

        lines.forEach { ln ->
            val r = computeLine(ln.retailPrice, ln.quantity, ln.taxRate, isInclusive, ln.discount)
            totalBase = totalBase.add(r.base)
            totalBaseAfterItem = totalBaseAfterItem.add(r.baseAfterDiscount)
            totalGrossAfterItem = totalGrossAfterItem.add(r.grossAfterDiscount)
            totalItemDiscount = totalItemDiscount.add(BigDecimal.valueOf(ln.discount.coerceAtLeast(0.0)))
            perLine.add(
                Triple(
                    r.grossAfterDiscount,  // we always store grossAfterDiscount
                    BigDecimal.valueOf(ln.taxRate.coerceAtLeast(0.0) / 100.0),
                    isInclusive
                )
            )
        }

        val spotPct = spotDiscountPercent.coerceAtLeast(0.0)
        val spotFrac = BigDecimal.valueOf(spotPct / 100.0)
        val spotComplement = BigDecimal.ONE.subtract(spotFrac)

        val grandTotal: BigDecimal
        val taxAfterSpot: BigDecimal
        val spotAmt: BigDecimal
        val baseAfterSpot: BigDecimal

        if (isInclusive) {
            // Inclusive: spot discount is applied on the gross amount after item discounts.
            // Tax is embedded — grand total = gross_after_spot (no tax added on top).
            spotAmt = totalGrossAfterItem.multiply(spotFrac)
            val grossAfterSpot = totalGrossAfterItem.subtract(spotAmt).max(BigDecimal.ZERO)
            grandTotal = grossAfterSpot

            // Tax is informational: back-calculate from the inclusive grand total.
            // For mixed tax rates, recompute per-line.
            var taxSum = BigDecimal.ZERO
            val one = BigDecimal.ONE
            perLine.forEach { (lineGrossAfterItem, rateFrac, _) ->
                if (rateFrac > BigDecimal.ZERO) {
                    val lineGrossAfterSpot = lineGrossAfterItem.multiply(spotComplement)
                    // tax = gross_after_spot * rate / (1 + rate)
                    taxSum = taxSum.add(
                        lineGrossAfterSpot.multiply(rateFrac)
                            .divide(one.add(rateFrac), 10, RoundingMode.HALF_UP)
                    )
                }
            }
            taxAfterSpot = taxSum

            // baseAfterSpot is the tax-exclusive equivalent of grossAfterSpot (for display)
            baseAfterSpot = grandTotal.subtract(taxAfterSpot)

        } else {
            // Exclusive: spot discount applied on tax-exclusive base after item discounts.
            // Tax recomputed per-line on discounted base.
            spotAmt = totalBaseAfterItem.multiply(spotFrac)
            baseAfterSpot = totalBaseAfterItem.subtract(spotAmt).max(BigDecimal.ZERO)

            var taxSum = BigDecimal.ZERO
            perLine.forEach { (lineGrossAfterItem, rateFrac, _) ->
                // For exclusive, grossAfterDisc = baseAfterDisc + tax, so baseAfterDisc = grossAfterDisc / (1+rate)
                // But we stored grossAfterDiscount, which for exclusive = baseAfterDisc + tax.
                // Recompute: lineBaseAfterDisc * (1 - spotPct/100) * rate
                // We need baseAfterItem per line. Back-calc from grossAfterItem (exclusive):
                //   baseAfterItem = grossAfterItem / (1 + rate)  [when rate > 0]
                //   baseAfterItem = grossAfterItem               [when rate == 0]
                val lineBaseAfterItem = if (rateFrac > BigDecimal.ZERO) {
                    lineGrossAfterItem.divide(
                        BigDecimal.ONE.add(rateFrac), 10, RoundingMode.HALF_UP
                    )
                } else {
                    lineGrossAfterItem
                }
                val lineBaseAfterSpot = lineBaseAfterItem.multiply(spotComplement)
                taxSum = taxSum.add(lineBaseAfterSpot.multiply(rateFrac))
            }
            taxAfterSpot = taxSum
            grandTotal = baseAfterSpot.add(taxAfterSpot)
        }

        return CartResult(
            subTotal = totalBase,
            itemDiscountTotal = totalItemDiscount,
            subTotalAfterItemDiscount = totalBaseAfterItem,
            spotDiscountAmount = spotAmt,
            subTotalAfterSpot = baseAfterSpot,
            taxAmount = taxAfterSpot,
            grandTotal = grandTotal
        )
    }

    // ---------------- rounding helpers ----------------

    fun round2(v: BigDecimal): BigDecimal = v.setScale(2, RoundingMode.HALF_UP)
    fun round0(v: BigDecimal): BigDecimal = v.setScale(0, RoundingMode.HALF_UP)

    fun round2(v: Double): Double = BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).toDouble()
    fun round0(v: Double): Double = BigDecimal.valueOf(v).setScale(0, RoundingMode.HALF_UP).toDouble()
}
