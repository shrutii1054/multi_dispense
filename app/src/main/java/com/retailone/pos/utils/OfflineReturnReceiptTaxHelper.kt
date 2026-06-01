package com.retailone.pos.utils

import com.retailone.pos.models.PosSalesDetailsModel.TaxDetails
import com.retailone.pos.models.PosSalesDetailsModel.TaxSummary
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Builds tax code + tax_summery for offline return receipt printing only.
 * Resolves codes from cached invoice [tax_summery] when available (matches org tax master:
 * code A/B/C/D, name e.g. "A-EX", "B-18.00%", "VAT 18%").
 */
object OfflineReturnReceiptTaxHelper {

    data class TaxConfigEntry(
        val code: String,
        val displayName: String,
        val ratePercent: Double
    )

    fun resolveForRate(
        taxRatePercent: Double,
        invoiceTaxSummery: List<TaxSummary>?
    ): TaxConfigEntry {
        val catalog = catalogFromInvoiceTaxSummery(invoiceTaxSummery)
        if (catalog.isNotEmpty()) {
            val exact = catalog.firstOrNull { abs(it.ratePercent - taxRatePercent) < 0.01 }
            if (exact != null) return exact
            val nearest = catalog.minByOrNull { abs(it.ratePercent - taxRatePercent) }
            if (nearest != null) return nearest
        }
        return defaultEntryForRate(taxRatePercent)
    }

    fun toTaxDetails(entry: TaxConfigEntry): TaxDetails = TaxDetails(
        id = null,
        name = entry.displayName,
        amount = entry.ratePercent.toString(),
        type = null,
        organization_id = null,
        deleted_at = null,
        status = null,
        created_at = null,
        updated_at = null,
        code = entry.code
    )

    /**
     * Groups return lines by tax code and runs [CalculationHelper.computeCart] per group
     * (same spot % as the return) so tax_summery matches online receipt layout.
     */
    fun buildTaxSummeryForReturn(
        linesByCode: Map<String, List<CalculationHelper.LineInput>>,
        taxConfigByCode: Map<String, TaxConfigEntry>,
        isInclusive: Boolean,
        spotDiscountPercent: Double,
        negateForRefund: (Double) -> Double
    ): List<TaxSummary> {
        if (linesByCode.isEmpty()) return emptyList()

        return linesByCode.entries
            .sortedBy { it.key }
            .mapNotNull { (code, inputs) ->
                if (inputs.isEmpty()) return@mapNotNull null
                val config = taxConfigByCode[code] ?: defaultEntryForRate(0.0).copy(code = code)
                val cart = CalculationHelper.computeCart(
                    lines = inputs,
                    isInclusive = isInclusive,
                    spotDiscountPercent = spotDiscountPercent
                )
                val taxable = if (isInclusive) {
                    cart.subTotalAfterSpot.toDouble()
                } else {
                    cart.subTotalAfterSpot.toDouble()
                }
                TaxSummary(
                    code = code,
                    code_name = config.displayName,
                    rate = config.ratePercent.roundToInt(),
                    taxable_value = negateForRefund(taxable),
                    tax_amount = negateForRefund(cart.taxAmount.toDouble()),
                    gross_total = negateForRefund(cart.grandTotal.toDouble())
                )
            }
    }

    private fun catalogFromInvoiceTaxSummery(summery: List<TaxSummary>?): List<TaxConfigEntry> {
        return summery.orEmpty().mapNotNull { row ->
            val code = row.code?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val rate = row.rate?.toDouble()
                ?: parseRateFromCodeName(row.code_name)
                ?: 0.0
            val name = row.code_name?.trim()?.takeIf { it.isNotEmpty() }
                ?: defaultEntryForRate(rate).displayName
            TaxConfigEntry(code = code, displayName = name, ratePercent = rate)
        }.distinctBy { it.code }
    }

    private fun parseRateFromCodeName(codeName: String?): Double? {
        if (codeName.isNullOrBlank()) return null
        val percentMatch = Regex("(\\d+(?:\\.\\d+)?)\\s*%").find(codeName)
        if (percentMatch != null) {
            return percentMatch.groupValues[1].toDoubleOrNull()
        }
        return null
    }

    private fun defaultEntryForRate(taxRatePercent: Double): TaxConfigEntry {
        return if (taxRatePercent <= 0.0) {
            TaxConfigEntry(
                code = "A",
                displayName = "Exempt",
                ratePercent = 0.0
            )
        } else {
            val rateInt = taxRatePercent.roundToInt()
            TaxConfigEntry(
                code = "B",
                displayName = "B - $rateInt%",
                ratePercent = taxRatePercent
            )
        }
    }
}
