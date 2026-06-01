package com.retailone.pos.utils

import android.content.Context
import com.retailone.pos.R
import java.util.Calendar
import java.util.Date
import java.util.Locale

object LocalizationUtils {

    fun getLocalizedMonthShort(context: Context, monthIndex: Int): String {
        val monthKeys = arrayOf(
            R.string.jan, R.string.feb, R.string.mar, R.string.apr,
            R.string.may_short, R.string.jun, R.string.jul, R.string.aug,
            R.string.sep, R.string.oct, R.string.nov, R.string.dec
        )
        val index = monthIndex.coerceIn(0, 11)
        return context.getString(monthKeys[index])
    }

    /** Maps API month names (often English) to localized month + year for display. */
    fun formatLocalizedMonthYear(context: Context, monthName: String?, year: String?): String {
        val index = parseMonthNameToIndex(monthName)
        val localizedMonth = if (index >= 0) {
            getLocalizedMonthFull(context, index)
        } else {
            monthName?.trim().orEmpty()
        }
        val yearPart = year?.trim().orEmpty()
        return if (yearPart.isEmpty()) localizedMonth else "$localizedMonth $yearPart"
    }

    private fun parseMonthNameToIndex(monthName: String?): Int {
        return when (monthName?.trim()?.lowercase(Locale.getDefault())) {
            "january", "jan" -> 0
            "february", "feb" -> 1
            "march", "mar" -> 2
            "april", "apr" -> 3
            "may" -> 4
            "june", "jun" -> 5
            "july", "jul" -> 6
            "august", "aug" -> 7
            "september", "sep" -> 8
            "october", "oct" -> 9
            "november", "nov" -> 10
            "december", "dec" -> 11
            else -> -1
        }
    }

    fun getLocalizedMonthFull(context: Context, monthIndex: Int): String {
        val monthKeys = arrayOf(
            R.string.january, R.string.february, R.string.march, R.string.april,
            R.string.may, R.string.june, R.string.july, R.string.august,
            R.string.september, R.string.october, R.string.november, R.string.december
        )
        val index = monthIndex.coerceIn(0, 11)
        return context.getString(monthKeys[index])
    }

    fun getLocalizedStatus(context: Context, backendValue: String): String {
        return when (backendValue.lowercase().trim()) {
            "store stock", "store_stock" -> context.getString(R.string.status_store_stock)
            "damaged" -> context.getString(R.string.damaged_label)
            "good" -> context.getString(R.string.good_label)
            "expired" -> context.getString(R.string.expired_label)
            "pending" -> context.getString(R.string.status_pending)
            "approved" -> context.getString(R.string.status_approved)
            "cancelled" -> context.getString(R.string.status_cancelled)
            "dispatched" -> context.getString(R.string.status_dispatched)
            "received" -> context.getString(R.string.status_received)
            "rejected" -> context.getString(R.string.status_rejected)
            "returnable" -> context.getString(R.string.returnable_label)
            "replaceable" -> context.getString(R.string.replaceable_label)
            "returned" -> context.getString(R.string.returned_label)
            "replaced" -> context.getString(R.string.replaced_label)
            "defective" -> context.getString(R.string.defective_label)
            "return approved", "return_approved" -> context.getString(R.string.status_return_approved)
            "no sell", "no_sell", "nosell" -> context.getString(R.string.no_sell_label)
            "excessive stock", "excessive_stock", "excessivestock" -> context.getString(R.string.excessive_stock_label)
            "others" -> context.getString(R.string.others_label)
            "extra stock", "extra_stock", "extrastock", "extra" -> context.getString(R.string.extra_stock_label)
            else -> backendValue
        }
    }

    /** Receipt/display dates using app locale month names (not device default). */
    fun formatReceiptDisplayDate(context: Context, date: Date): String {
        val cal = Calendar.getInstance()
        cal.time = date
        val day = cal.get(Calendar.DAY_OF_MONTH)
        val month = getLocalizedMonthShort(context, cal.get(Calendar.MONTH))
        val year = cal.get(Calendar.YEAR)
        return String.format(Locale.getDefault(), "%02d-%s-%04d", day, month, year)
    }

    /**
     * Maps known English UI/API messages to the current app locale.
     * Backend payloads are unchanged; call this only for display (Toast, dialogs).
     */
    fun localizeUserMessage(context: Context, message: String?): String {
        if (message.isNullOrBlank()) return message ?: ""
        val normalized = message.trim().lowercase()
        return when {
            normalized == "please select any reason for return" ||
                normalized == "please select a return reason" ->
                context.getString(R.string.select_return_reason_error)

            normalized == "please select any reason for replacement" ->
                context.getString(R.string.select_replace_reason_error)

            normalized == "hold" || normalized == "on hold" ->
                context.getString(R.string.hold_button)

            normalized == "product on-hold" || normalized == "product on hold" ->
                context.getString(R.string.product_on_hold)

            normalized == "something went wrong" ||
                normalized == "something went wrong, try again" ||
                normalized == "something went wrong ,try again" ||
                normalized == "something went yes wrong" ->
                context.getString(R.string.something_went_wrong_short)

            normalized.startsWith("something went wrong:") -> {
                val detail = message.substringAfter(":", "").trim()
                context.getString(
                    R.string.something_went_wrong_msg,
                    detail.ifEmpty { context.getString(R.string.error_unknown) }
                )
            }

            normalized.contains("something went") && normalized.contains("wrong") ->
                context.getString(R.string.something_went_wrong_short)

            normalized.contains("something pos went") && normalized.contains("wrong") ->
                context.getString(R.string.something_went_wrong_short)

            normalized == "expense saved successfully" ->
                context.getString(R.string.expense_saved_successfully)

            normalized == "required" ->
                context.getString(R.string.field_required)

            normalized == "invalid number" ->
                context.getString(R.string.invalid_number)

            normalized == "cannot be negative" ->
                context.getString(R.string.cannot_be_negative)

            normalized.startsWith("cannot exceed max") -> {
                val max = message.substringAfter("(", "").substringBefore(")", "").trim()
                context.getString(R.string.bottle_qty_exceed_max, max.ifEmpty { "0" })
            }

            normalized.startsWith("cannot exceed purchased") -> {
                val qty = message.substringAfter("(", "").substringBefore(")", "").trim()
                context.getString(R.string.cannot_exceed_purchased_qty, qty.ifEmpty { "0" })
            }

            normalized.startsWith("failed to fetch data, try again") -> {
                val code = Regex("\\((\\d+)\\)").find(message)?.groupValues?.getOrNull(1)
                if (code != null) {
                    context.getString(R.string.failed_to_fetch_data_with_code, code)
                } else {
                    context.getString(R.string.failed_to_fetch_data_try_again)
                }
            }

            normalized == "sale successful" -> context.getString(R.string.sale_successful)

            normalized == "purchase request submitted." ||
                normalized == "purchase request submitted" ->
                context.getString(R.string.purchase_request_submitted)

            normalized.contains("invoice cancelled") && normalized.contains("reversal") ->
                context.getString(R.string.invoice_cancelled_success)

            normalized.contains("return processed") && normalized.contains("success") ->
                context.getString(R.string.return_processed_successfully)

            normalized == "items replaced successfully" ||
                normalized == "items replaced successfully." ->
                context.getString(R.string.items_replaced_successfully)

            normalized == "replaced successfully" ||
                normalized == "replaced successfully." ->
                context.getString(R.string.replaced_successfully)

            normalized.contains("replaced") && normalized.contains("success") &&
                !normalized.contains("items") ->
                context.getString(R.string.replaced_successfully)

            // ── Data fetch errors ──────────────────────────────────────────────
            normalized == "failed to fetch data, try again" ||
                normalized == "failed to fetch data,try again" ||
                normalized == "failed to fetch data , try again" ->
                context.getString(R.string.failed_to_fetch_data_try_again)

            normalized.startsWith("failed to fetch stock") ->
                context.getString(R.string.failed_to_fetch_stock_list)

            normalized == "failed to load receipt types" ->
                context.getString(R.string.failed_load_receipt_types)

            normalized == "sale details not loaded yet" ->
                context.getString(R.string.sale_details_not_loaded)

            normalized == "store id not available" ->
                context.getString(R.string.store_id_not_available)

            // ── Customer search (POS) ──────────────────────────────────────────
            normalized == "enter valid mobile no or tin" ||
                normalized == "enter a valid mobile no or tin" ->
                context.getString(R.string.enter_valid_mobile_or_tin)

            normalized == "customer does not exist." ||
                normalized == "customer does not exist" ->
                context.getString(R.string.customer_does_not_exist)

            normalized.startsWith("customer not found in offline") ->
                context.getString(R.string.customer_not_found_offline)

            normalized.contains("no data found") && normalized.contains("criteria") ->
                context.getString(R.string.no_data_found_for_criteria)

            normalized == "customer not found" ->
                context.getString(R.string.customer_not_found)

            // ── POS cart / product ─────────────────────────────────────────────
            normalized == "scanned product currrently unavailable" ||
                normalized == "scanned product currently unavailable" ->
                context.getString(R.string.scanned_product_unavailable)

            normalized == "please add atleast one item" ||
                normalized == "please add at least one item" ->
                context.getString(R.string.please_add_at_least_one_item)

            normalized.contains("quantity") && normalized.contains("zero or empty") ||
                normalized.contains("quantity of") && normalized.contains("zero or empty") ->
                context.getString(R.string.quantity_cannot_be_zero_or_empty)

            normalized == "please dispanse all loose item" ||
                normalized == "please dispense all loose items" ->
                context.getString(R.string.please_dispense_all_loose_items)

            normalized == "item already added to cart" ->
                context.getString(R.string.item_already_in_cart)

            normalized == "out of stock - cannot add to cart" ->
                context.getString(R.string.out_of_stock_cannot_add)

            normalized == "can't update the dispensed item" ->
                context.getString(R.string.cant_update_dispensed_item)

            normalized.contains("dispensing was not successful") ->
                context.getString(R.string.dispensing_not_successful)

            normalized == "please sell the dispensed item first" ->
                context.getString(R.string.please_sell_dispensed_item)

            normalized == "camera permission is required" ->
                context.getString(R.string.toast_camera_permission_required)

            // ── Invoice / Return / Replace ─────────────────────────────────────
            normalized == "no invoice found" ->
                context.getString(R.string.no_invoice_found)

            normalized == "enter a valid invoice id" ->
                context.getString(R.string.enter_valid_invoice_id)

            normalized == "you haven't return anything" ||
                normalized == "you haven't returned anything" ->
                context.getString(R.string.you_havent_returned_anything)

            normalized == "return reason not found, try after sometime" ->
                context.getString(R.string.return_reason_not_found)

            normalized == "replace reason not found, try after sometime" ->
                context.getString(R.string.replace_reason_not_found)

            normalized == "nothing to replace" ->
                context.getString(R.string.nothing_to_replace)

            normalized == "no items saved for return." ||
                normalized == "no items saved for return" ->
                context.getString(R.string.no_items_saved_for_return)

            normalized == "failed to queue return request" ->
                context.getString(R.string.failed_queue_return_request)

            normalized == "failed to queue replace request" ->
                context.getString(R.string.failed_queue_replace)

            normalized.contains("invoice has already been returned and cannot be returned again") ->
                context.getString(R.string.invoice_already_returned_cannot_again)

            normalized == "no sales found" ->
                context.getString(R.string.no_sales_found)

            // ── Date filter ────────────────────────────────────────────────────
            normalized == "please select from date first" ->
                context.getString(R.string.please_select_from_date_first)

            normalized == "please select from date" ->
                context.getString(R.string.please_select_from_date)

            normalized == "please select to date" ->
                context.getString(R.string.please_select_to_date)

            // ── Store / device info ────────────────────────────────────────────
            normalized == "couldn't fetch store info" ||
                normalized.startsWith("could't fetch store info") ->
                context.getString(R.string.couldnt_fetch_store_info)

            normalized == "couldn't fetch store manager info" ||
                normalized.startsWith("could't fetch store manager info") ||
                normalized == "coudn't fetch store manager info" ->
                context.getString(R.string.couldnt_fetch_store_manager_info)

            normalized == "couldn't fetch device id" ->
                context.getString(R.string.couldnt_fetch_device_id)

            // ── Sync ───────────────────────────────────────────────────────────
            normalized == "some items failed to sync. please try again." ->
                context.getString(R.string.sync_failed)

            // ── CashUp ─────────────────────────────────────────────────────────
            normalized == "cashup failed ,try again later" ||
                normalized == "cashup failed, try again later" ->
                context.getString(R.string.cashup_failed)

            normalized == "please enter valid cash amount" ->
                context.getString(R.string.please_enter_valid_cash_amount)

            normalized == "please enter a valid pettycash amount" ->
                context.getString(R.string.please_enter_valid_pettycash_amount)

            normalized == "please enter valid transfer amount to bank" ->
                context.getString(R.string.please_enter_valid_transfer_amount)

            normalized == "please enter valid credit card amount" ->
                context.getString(R.string.please_enter_valid_credit_card_amount)

            normalized == "please enter valid debit card amount" ->
                context.getString(R.string.please_enter_valid_debit_card_amount)

            normalized == "please enter valid m-money amount" ->
                context.getString(R.string.please_enter_valid_mmoney_amount)

            normalized == "please verify the bank mobile no" ->
                context.getString(R.string.please_verify_bank_mobile)

            normalized == "please enter 6 digit otp" ->
                context.getString(R.string.please_enter_6_digit_otp)

            normalized == "actual cash amount can't be more than expected cash" ->
                context.getString(R.string.actual_cash_exceeds_expected)

            normalized == "actual petty cash amount can't be more than expected cash" ->
                context.getString(R.string.actual_pettycash_exceeds_expected)

            normalized == "actual m money cash amount can't be more than expected m money" ->
                context.getString(R.string.actual_mmoney_exceeds_expected)

            normalized == "actual m money amount can't more than expected" ->
                context.getString(R.string.actual_mmoney_exceeds_expected_short)

            normalized == "transfer amount cannot exceed cash + m-money." ->
                context.getString(R.string.transfer_exceeds_cash_mmoney)

            normalized == "can't transfer an amount greater than expected cash." ->
                context.getString(R.string.transfer_exceeds_expected_cash)

            // ── Offline data ───────────────────────────────────────────────────
            normalized == "no offline data available. please connect to the internet." ||
                normalized == "no offline data available. please connect to internet." ->
                context.getString(R.string.no_offline_sales_data)

            // ── Feature flags ──────────────────────────────────────────────────
            normalized == "this feature is not enabled for your organization" ->
                context.getString(R.string.feature_not_enabled_org)

            // ── Attendance ─────────────────────────────────────────────────────
            normalized == "attendance list not found" ->
                context.getString(R.string.attendance_list_not_found)

            // ── Material receiving ──────────────────────────────────────────────
            normalized == "stn upload failed" ->
                context.getString(R.string.stn_upload_failed)

            normalized == "please receive all the materials" ->
                context.getString(R.string.receive_all_materials)

            normalized == "please receive at least one item" ->
                context.getString(R.string.receive_at_least_one)

            normalized == "all received item can't be empty or zero" ->
                context.getString(R.string.received_item_not_empty)

            normalized == "please enter vehicle number" ->
                context.getString(R.string.enter_vehicle_number)

            normalized == "please enter driver name" ->
                context.getString(R.string.enter_driver_name)

            normalized == "please scan stn" ->
                context.getString(R.string.scan_stn_msg)

            // ── Expense ────────────────────────────────────────────────────────
            normalized == "offline mode: expense view only" ->
                context.getString(R.string.expense_offline_view_only)

            normalized == "no history found" ->
                context.getString(R.string.no_history_found)

            normalized == "invoice upload failed" ->
                context.getString(R.string.invoice_upload_failed)

            normalized == "only jpeg, jpg, and png files are allowed" ->
                context.getString(R.string.only_jpeg_png_allowed)

            normalized == "file size should be less than 300 kb" ->
                context.getString(R.string.file_size_limit_300kb)

            // ── PIN / OTP (ForgotPin) ───────────────────────────────────────────
            normalized == "please enter valid pin" ->
                context.getString(R.string.enter_valid_pin)

            normalized == "invalid otp" ->
                context.getString(R.string.invalid_otp)

            normalized == "confirm pin must be same as new pin" ->
                context.getString(R.string.confirm_pin_mismatch)

            normalized == "please enter valid mobile no" ||
                normalized == "please enter valid mobile no." ->
                context.getString(R.string.enter_valid_mobile)

            // ── POS details / customer info ────────────────────────────────────
            normalized == "please enter customer name" ||
                normalized == "please enter your customer name" ->
                context.getString(R.string.please_enter_customer_name)

            normalized == "please enter customer tin number" ->
                context.getString(R.string.please_enter_customer_tin)

            normalized == "please enter valid mobile number" ||
                normalized == "please enter valid mobile number." ->
                context.getString(R.string.please_enter_valid_mobile_number)

            normalized == "please enter valid tin number" ->
                context.getString(R.string.please_enter_valid_tin_number)

            normalized == "please enter customer mobile number" ->
                context.getString(R.string.please_enter_customer_mobile)

            normalized == "please enter payment type" ->
                context.getString(R.string.please_enter_payment_type)

            normalized == "please select receipt type" ->
                context.getString(R.string.please_select_receipt_type)

            normalized == "please enter valid invoice number" ->
                context.getString(R.string.please_enter_valid_invoice_number)

            normalized.contains("invoice number is already used") ->
                context.getString(R.string.invoice_number_already_used)

            normalized.contains("no valid items to sell") ->
                context.getString(R.string.no_valid_items_to_sell)

            normalized.contains("customer does not exist in offline") ->
                context.getString(R.string.customer_not_in_offline_records)

            normalized == "customer already exists!" ||
                normalized == "customer already exists" ->
                context.getString(R.string.customer_already_exists)

            normalized == "no data to return" ->
                context.getString(R.string.no_data_to_return)

            normalized == "all items are already refunded." ||
                normalized == "all items are already refunded" ->
                context.getString(R.string.all_items_refunded)

            normalized == "this invoice has already been replaced." ||
                normalized == "this invoice has already been replaced" ->
                context.getString(R.string.invoice_already_replaced)

            normalized == "insufficient expected amount for cash up" ->
                context.getString(R.string.insufficient_expected_cash_up)

            normalized == "error parsing date" ->
                context.getString(R.string.error_parsing_date)

            normalized.startsWith("submit failed:") -> {
                val detail = message.substringAfter(":", "").trim()
                context.getString(R.string.submit_failed_msg, detail.ifEmpty { context.getString(R.string.error_unknown) })
            }

            // ── Invoice already returned (dynamic reason) ──────────────────────
            normalized.startsWith("this invoice has already been returned. reason:") -> {
                val reason = message.substringAfter("Reason:", "").substringAfter("reason:", "").trim()
                context.getString(R.string.invoice_already_returned_with_reason, reason)
            }

            // ── OTP ────────────────────────────────────────────────────────────
            normalized == "otp not matched" ||
                normalized == "otp does not match" ||
                normalized == "otp mismatch" ->
                context.getString(R.string.otp_not_matched)

            normalized == "otp matched" ||
                normalized == "otp verified" ||
                normalized == "otp match" ->
                context.getString(R.string.otp_matched)

            // ── Offline messages ───────────────────────────────────────────────
            normalized == "loaded from offline cache" ||
                normalized.startsWith("loaded from offline") ->
                context.getString(R.string.loaded_from_offline_cache)

            normalized.startsWith("no receipt types cached") ->
                context.getString(R.string.no_receipt_types_cached)

            normalized.startsWith("api failed") && normalized.contains("offline") ->
                context.getString(R.string.api_failed_offline_cache)

            normalized.startsWith("network error") && normalized.contains("offline") ->
                context.getString(R.string.network_error_offline_cache)

            normalized == "offline login success" ||
                normalized == "offline login successful" ->
                context.getString(R.string.offline_login_successful)

            normalized.contains("failed to save expense") ->
                context.getString(R.string.failed_to_save_expense)

            normalized.contains("failed to upload invoice") ->
                context.getString(R.string.failed_to_upload_invoice_try_again)

            normalized.contains("no offline profile data") ->
                context.getString(R.string.no_offline_profile_data)

            normalized.contains("no offline attendance data") ->
                context.getString(R.string.no_offline_attendance_data)

            // ── Expense validation ─────────────────────────────────────────────
            normalized == "please select expense type" ||
                normalized == "please enter a valid expence type" ||
                normalized == "please enter a expence type" ||
                normalized == "please enter valid expense type" ->
                context.getString(R.string.please_select_expense_type)

            normalized == "please enter valid expense amount" ->
                context.getString(R.string.please_enter_valid_expense_amount)

            normalized == "please enter a valid party name" ||
                normalized == "please select party name" ->
                context.getString(R.string.please_enter_valid_party_name)

            normalized == "please enter a valid sdc no" ||
                normalized == "please enter a valid sdc number" ->
                context.getString(R.string.please_enter_valid_sdc_no)

            normalized == "please enter a valid remarks" ||
                normalized == "please enter valid remarks" ->
                context.getString(R.string.please_enter_valid_remarks)

            normalized == "expence can't be zero" ||
                normalized == "expense can't be zero" ->
                context.getString(R.string.expense_cannot_be_zero)

            normalized == "please enter a valid vat amount" ||
                normalized == "please enter valid vat amount" ->
                context.getString(R.string.please_enter_valid_vat_amount)

            normalized.contains("vat amount") && normalized.contains("expense amount") ->
                context.getString(R.string.vat_exceeds_expense_amount)

            normalized.contains("can't expence more than") ||
                normalized.contains("can't expense more than") ->
                context.getString(R.string.expense_exceeds_pettycash)

            normalized.contains("sdc number should") && normalized.contains("12") ->
                context.getString(R.string.sdc_number_12_digits)

            normalized.contains("first 3") && normalized.contains("charecter") ||
                normalized.contains("first 3") && normalized.contains("character") ->
                context.getString(R.string.sdc_first_3_chars)

            normalized.contains("last 9") && normalized.contains("number") ->
                context.getString(R.string.sdc_last_9_numbers)

            normalized == "error creating image file" ->
                context.getString(R.string.error_creating_image_file)

            normalized == "please select invoice image" ->
                context.getString(R.string.please_select_invoice_image)

            // ── PIN ────────────────────────────────────────────────────────────
            normalized == "please enter 6 digit pin" ->
                context.getString(R.string.please_enter_6_digit_pin)

            // ── Inventory / Stock ──────────────────────────────────────────────
            normalized == "product quantity can't be empty or zero" ||
                normalized == "product quantity can't be empty or zero." ->
                context.getString(R.string.product_qty_empty_or_zero)

            normalized.contains("add at least one product") ||
                normalized.contains("add atleast one product") ->
                context.getString(R.string.please_add_product_to_cart)

            normalized == "please select atleast one product" ||
                normalized == "please select at least one product" ->
                context.getString(R.string.select_at_least_one_product)

            // ── Misc ───────────────────────────────────────────────────────────
            normalized == "please,try again later.." ||
                normalized == "please, try again later" ->
                context.getString(R.string.something_went_wrong_short)

            else -> message
        }
    }

    fun getLocalizedApiMessage(context: Context, message: String?): String =
        localizeUserMessage(context, message)

    /**
     * Maps backend payment type values (always English) to the current app locale label.
     * The raw API value is preserved for any network calls — only call this for display.
     */
    fun localizePaymentType(context: Context, apiValue: String?): String {
        return when (apiValue?.trim()?.lowercase()) {
            "cash"        -> context.getString(R.string.cash_label)
            "m-money"     -> context.getString(R.string.mmoney_label)
            "credit card",
            "credit"      -> context.getString(R.string.credit_card_label)
            "debit card",
            "debit"       -> context.getString(R.string.debit_card_label)
            "petty cash"  -> context.getString(R.string.petty_cash_label)
            else          -> apiValue ?: ""
        }
    }
}
