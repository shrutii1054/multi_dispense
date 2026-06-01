//package com.retailone.pos.utils
//
//import android.util.Log
//import com.retailone.pos.models.ReturnSalesItemModel.ReturnSaleResModel.ReturnSaleRes
//import com.retailone.pos.models.ReturnSalesItemModel.ReturnSaleResModel.ReturnedItem
//import java.text.SimpleDateFormat
//import java.util.Locale
//
///**
// * Logcat-only preview of the RRA return/refund receipt layout (no printer paper needed).
// * Filter Logcat by tag: [ReturnReceiptPreview]
// */
//object ReturnReceiptLogPreview {
//
//    private const val TAG = "ReturnReceiptPreview"
//    private const val WIDTH = 34
//    private const val SEP = "----------------------------------"
//
//    @JvmStatic
//    fun log(res: ReturnSaleRes?, currency: String?) {
//        val cur = currency?.takeIf { it.isNotBlank() } ?: "—"
//        val data = res?.data
//        if (data == null) {
//            Log.w(TAG, "No return receipt data — cannot preview")
//            return
//        }
//
//        val lines = mutableListOf<String>()
//        fun emit(text: String = "") {
//            lines.add(text)
//        }
//        fun emitRow(label: String, value: String) {
//            emit(label + padLeft(value, WIDTH - label.length))
//        }
//        fun emitCenter(text: String) {
//            val t = text.trim()
//            if (t.length >= WIDTH) {
//                emit(t)
//            } else {
//                val pad = (WIDTH - t.length) / 2
//                emit(" ".repeat(pad.coerceAtLeast(0)) + t)
//            }
//        }
//
//        val rcptType = data.rcptType?.trim().orEmpty()
//        val isProforma = rcptType.equals("P", true) || rcptType.equals("Proforma", true)
//        val source = if (res.message?.contains("Offline", ignoreCase = true) == true) "OFFLINE" else "ONLINE/API"
//
//        emit("╔══════════════════════════════════╗")
//        emit("║   RETURN RECEIPT PREVIEW ($source) ║")
//        emit("╚══════════════════════════════════╝")
//        emitCenter("*** START OF LEGAL RECEIPT ***")
//        emitCenter("CIS Version : 1.0.1")
//        emit("")
//        data.store?.store_name?.let { emitCenter(it.uppercase(Locale.getDefault())) }
//        data.store?.address?.let { emitCenter(it.uppercase(Locale.getDefault())) }
//        emitCenter("TIN NO:${data.tpin_no.orEmpty()}")
//        emit(SEP)
//
//        when {
//            rcptType.equals("P", true) || rcptType.equals("Proforma", true) -> emitCenter("PROFORMA")
//            rcptType.equals("T", true) || rcptType.equals("Training", true) -> emitCenter("TRAINING MODE")
//            rcptType.equals("C", true) || rcptType.equals("Copy", true) -> emitCenter("COPY")
//        }
//        emit(SEP)
//        emitCenter("REFUND")
//        val ogRcpt = data.ogRcpt_no?.let { if (it.isNaN() || it.isInfinite()) "0" else it.toLong().toString() } ?: "0"
//        emitRow("Ref. Normal Receipt:", ogRcpt)
//        emit(SEP)
//        emitCenter("REFUND IS APPROVED ONLY FOR ORIGINAL SALES RECEIPT")
//        emit(SEP)
//        emit("")
//        emitRow("BUYER'S NAME:", data.customer_name.orEmpty())
//        emitRow("BUYER'S TIN:", data.buyers_tpin.orEmpty())
//        emitRow("BUYER'S CONTACT:", data.customer_mob_no.orEmpty())
//        emit(SEP)
//        emit("")
//
//        val items = data.returned_items.orEmpty()
//        if (items.isEmpty()) {
//            emit("(no returned items)")
//        } else {
//            items.forEach { item ->
//                emit(item.product_name.orEmpty())
//                emit(formatItemLine(item))
//                val discount = item.discount
//                val totalAmount = item.total_amount
//                if (discount != null && discount > 0 && totalAmount != null && totalAmount > 0) {
//                    val pct = (discount / totalAmount) * 100.0
//                    val discountText = "discount -${FunUtils.DtoString(pct)}%"
//                    val finalAmt = FunUtils.formatPrintPrice((totalAmount - discount).toString()).orEmpty()
//                    emitRow(discountText, finalAmt)
//                }
//                emit("")
//            }
//        }
//
//        emit(SEP)
//        if (isProforma || rcptType.equals("T", true) || rcptType.equals("Training", true) ||
//            rcptType.equals("C", true) || rcptType.equals("Copy", true)
//        ) {
//            emitCenter("THIS IS NOT AN OFFICIAL RECEIPT")
//            emit(SEP)
//        }
//
//        val grandFormatted = FunUtils.formatPrintPrice(data.grand_total?.toString()).orEmpty() ?: ""
//        emitRow("TOTAL($cur):", grandFormatted)
//
//        val taxSummary = data.tax_summery.orEmpty()
//        if (taxSummary.isNotEmpty()) {
//            taxSummary.forEach { row ->
//                val code = row.code
//                if (code != null) {
//                    val taxLabel = "TOTAL ${row.code_name.orEmpty()}"
//                    val taxable = FunUtils.formatPrintPrice(row.taxable_value?.toString()).orEmpty()
//                    emitRow(taxLabel, taxable)
//                }
//            }
//            taxSummary.forEach { row ->
//                val code = row.code
//                val taxAmt = row.tax_amount
//                if (taxAmt != null && taxAmt != 0.0 && code != null) {
//                    emitRow("TOTAL TAX $code", FunUtils.formatPrintPrice(taxAmt.toString()).orEmpty())
//                }
//            }
//            val headerTax = data.tax_amount
//            if (headerTax != null && headerTax != 0.0) {
//                emitRow(
//                    "TOTAL TAX AMOUNT",
//                    FunUtils.formatPrintPrice(headerTax.toString()).orEmpty()
//                )
//            }
//        } else {
//            emit("(tax_summery empty — tax block will not print)")
//        }
//
//        emit("")
//        emit(SEP)
//
//        if (!isProforma) {
//            emitRow("CASH:", grandFormatted)
//            emitRow("Items:", items.size.toString())
//            emit(SEP)
//        }
//
//        emitCenter("SDC INFORMATION")
//        val vsdc = data.vsdc_reciept?.firstOrNull()
//        val (retDate, retTime) = formatReturnDateTime(data.returned_date)
//        if (retDate.isNotEmpty() || retTime.isNotEmpty()) {
//            emit("DATE: $retDate${" ".repeat((WIDTH - "DATE: $retDate".length - "TIME: $retTime".length).coerceAtLeast(1))}TIME: $retTime")
//        }
//        emitRow("SDC ID:", vsdc?.sdcId.orEmpty())
//        val receiptTypeCode = resolveReceiptTypeCode(rcptType)
//        val sdcRcpt = "${data.returned_invoice_id.orEmpty()}/${vsdc?.totRcptNo ?: ""} $receiptTypeCode R".trim()
//        emitRow("RECEIPT NUMBER:", sdcRcpt)
//        if (!isProforma && !rcptType.equals("T", true) && !rcptType.equals("Training", true)) {
//            emitCenter("INTERNAL DATA:  ${vsdc?.intrlData.orEmpty()}")
//            emitCenter("RECEIPT SIGNATURE:  ${vsdc?.rcptSign.orEmpty()}")
//            if (!vsdc?.qrCodeUrl.isNullOrBlank()) {
//                emitCenter("[QR CODE]")
//            }
//        }
//        emit(SEP)
//        emitRow("RECEIPT NUMBER:", data.returned_invoice_id.orEmpty())
//        if (retDate.isNotEmpty() || retTime.isNotEmpty()) {
//            emit("DATE: $retDate${" ".repeat((WIDTH - "DATE: $retDate".length - "TIME: $retTime".length).coerceAtLeast(1))}TIME: $retTime")
//        }
//        emitRow("MRC NO.:.", vsdc?.mrcNo?.let { "$it." } ?: ".")
//        emit(SEP)
//        emitCenter("THANK YOU")
//        emitCenter("*** END ***")
//        emit("")
//        emit("── raw totals (debug) ──")
//        emit("  total=${data.total}  sub_total=${data.sub_total}  tax_amount=${data.tax_amount}")
//        emit("  grand_total=${data.grand_total}  subtal=${data.subtal}")
//        emit("╔══════════════════════════════════╗")
//        emit("║      END RETURN RECEIPT PREVIEW   ║")
//        emit("╚══════════════════════════════════╝")
//
//        Log.i(TAG, "Filter Logcat by tag: $TAG")
//        Log.i(TAG, "Invoice: ${data.returned_invoice_id} | source: $source | items: ${items.size}")
//        lines.forEach { line ->
//            Log.i(TAG, line)
//        }
//    }
//
//    private fun formatItemLine(item: ReturnedItem): String {
//        val taxCode = item.tax_details?.code.orEmpty()
//        val rateCol = "${FunUtils.formatPrintPrice(item.retail_price.toString())}x"
//        val qtyCol = FunUtils.DtoString(item.return_quantity.toDouble())
//        val amountCol = "-${FunUtils.formatPrintPrice(item.total_amount.toString())}$taxCode"
//        val totalWidth = WIDTH
//        val rightColWidth = 10
//        val midColWidth = 8
//        val leftColWidth = totalWidth - midColWidth - rightColWidth
//        return String.format(
//            Locale.US,
//            "%-${leftColWidth}s%${midColWidth}s%${rightColWidth}s",
//            rateCol,
//            qtyCol,
//            amountCol
//        )
//    }
//
//    private fun padLeft(value: String, width: Int): String {
//        val v = value.ifEmpty { "" }
//        return if (v.length >= width) v else " ".repeat(width - v.length) + v
//    }
//
//    private fun resolveReceiptTypeCode(rcptType: String): String = when {
//        rcptType.equals("N", true) || rcptType.equals("Normal", true) -> "N"
//        rcptType.equals("P", true) || rcptType.equals("Proforma", true) -> "P"
//        rcptType.equals("T", true) || rcptType.equals("Training", true) -> "T"
//        rcptType.equals("C", true) || rcptType.equals("Copy", true) -> "C"
//        rcptType.isNotEmpty() -> rcptType
//        else -> "N"
//    }
//
//    private fun formatReturnDateTime(raw: String?): Pair<String, String> {
//        if (raw.isNullOrBlank()) return "" to ""
//        val formats = arrayOf(
//            "yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'",
//            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
//            "yyyy-MM-dd'T'HH:mm:ss'Z'",
//            "yyyy-MM-dd HH:mm:ss"
//        )
//        for (fmt in formats) {
//            try {
//                val sdf = SimpleDateFormat(fmt, Locale.getDefault())
//                sdf.isLenient = false
//                val parsed = sdf.parse(raw) ?: continue
//                val date = SimpleDateFormat("dd-MMM-yyyy", Locale.getDefault()).format(parsed)
//                val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(parsed)
//                return date to time
//            } catch (_: Exception) {
//            }
//        }
//        return raw to ""
//    }
//}
