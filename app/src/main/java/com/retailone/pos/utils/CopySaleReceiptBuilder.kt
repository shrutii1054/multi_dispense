package com.retailone.pos.utils

import com.retailone.pos.models.PosSalesDetailsModel.SaleReceiptRes
import com.retailone.pos.models.SalesPaymentModel.SalesDetails.SalesDetailsData
import com.retailone.pos.models.SalesPaymentModel.SalesDetails.SalesItem
import kotlin.math.roundToInt

/**
 * Builds [SaleReceiptRes] for copy-sale receipt printing when offline or as API fallback.
 */
object CopySaleReceiptBuilder {

    fun fromSalesDetails(
        sale: SalesDetailsData,
        tpinNo: String?
    ): SaleReceiptRes {
        val taxCodeForRate: (Int) -> String = { rate ->
            if (rate <= 0) "A" else "B"
        }

        val receiptItems = sale.sales_items.map { si ->
            val rate = si.tax
            val code = taxCodeForRate(rate)
            SaleReceiptRes.SalesItem(
                batchno = null,
                quantity = si.quantity,
                tax_inclusive_price = si.retail_price,
                retail_price = si.retail_price,
                distribution_pack_id = si.distribution_pack_id.toString(),
                distribution_pack_name = si.distribution_pack_name,
                product_id = si.product_id.toString(),
                product_name = si.product_name,
                total_amount = si.total_amount,
                discounted_total = si.total_amount,
                uom = si.distribution_pack?.product_description,
                whole_sale_price = si.whole_sale_price.toString(),
                tax_details = SaleReceiptRes.ItemTax(
                    id = null,
                    name = if (rate <= 0) "$code - Zero/Exempt" else "$code - $rate%",
                    amount = rate.toString(),
                    type = null,
                    organization_id = sale.store_details.organization_id,
                    deleted_at = null,
                    status = null,
                    created_at = null,
                    updated_at = null,
                    code = code
                ),
                discount_rate = if (si.discount > 0 && si.total_amount > 0) {
                    -(si.discount.toDouble() / si.total_amount * 100.0)
                } else {
                    0.0
                }
            )
        }

        val taxSummery = sale.sales_items
            .groupBy { taxCodeForRate(it.tax) }
            .map { (code, items) ->
                val rate = items.firstOrNull()?.tax?.toDouble() ?: 0.0
                val taxable = items.sumOf { it.sub_total }
                val taxAmt = items.sumOf { it.tax_amount }
                val gross = items.sumOf { it.total_amount }
                val codeName = if (rate <= 0.0) "$code - Zero/Exempt" else "$code - ${rate.roundToInt()}%"
                SaleReceiptRes.TaxSummery(
                    code = code,
                    rate = rate,
                    code_name = codeName,
                    taxable_value = taxable,
                    tax_amount = taxAmt,
                    gross_total = gross
                )
            }
            .sortedBy { it.code }

        val store = sale.store_details
        return SaleReceiptRes(
            status = 1,
            message = "Offline Copy Sale",
            data = SaleReceiptRes.Data(
                store = SaleReceiptRes.Store(
                    id = store.id,
                    store_name = store.store_name,
                    station_code = store.station_code,
                    address = store.address,
                    organization_id = store.organization_id,
                    ho_manager_id = null,
                    cluster_id = null,
                    phone_no = store.phone_no,
                    logo = store.logo,
                    logo_image_name = null,
                    latitude = null,
                    longitude = null,
                    induction_date = null,
                    location = store.location,
                    internal_data = null,
                    petty_cash_opening_balance = null,
                    deleted_at = null,
                    created_at = null,
                    updated_at = null,
                    status = null,
                    exposure_limit = null,
                    branch_code = null,
                    device_serial_no = null
                ),
                store_manager_id = sale.store_manager_id.toString(),
                payment_type = sale.payment_type,
                invoice_id = sale.invoice_id,
                sub_total = sale.sub_total.toString(),
                tax = sale.tax.toString(),
                tax_amount = sale.tax_amount.toString(),
                discount_amount = sale.discount_amount.toString(),
                subtotal_after_discount = sale.subtotal_after_discount.toString(),
                grand_total = sale.grand_total.toString(),
                amount_tendered = sale.amount_tendered.toString(),
                salesItem = receiptItems,
                tax_summery = taxSummery,
                purchase_date_time = sale.created_at,
                customer_name = sale.customer?.customer_name,
                customer_mob_no = sale.customer?.customer_mob_no,
                vat_no = null,
                tpin_no = tpinNo,
                buyers_tpin = null,
                buyers_vat_no = null,
                tax_ex = "0.00",
                ej_no = null,
                ej_activation_date = null,
                sdc_id = null,
                receipt_no = null,
                internal_data = null,
                receipt_sign = null,
                warning = null,
                rcptType = "C",
                vsdc_reciept = null,
                spot_discount_percentage = sale.spot_discount_percentage,
                spot_discount_amount = sale.spot_discount_amount
            )
        )
    }
}
