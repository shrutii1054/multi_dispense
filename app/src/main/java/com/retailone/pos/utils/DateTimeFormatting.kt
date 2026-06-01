package com.retailone.pos.utils

import android.content.Context
import android.util.Log
import com.retailone.pos.R
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class DateTimeFormatting {

    companion object {

        private fun resolveTimezone(zone: String): String = when (zone) {
            "IST" -> "Asia/Kolkata"
            "CAT" -> "Africa/Lusaka"
            else -> "Africa/Lusaka"
        }

        private fun normalizeIsoInput(inputDate: String): String {
            var normalized = inputDate.trim()
            if (normalized.contains(".") && normalized.endsWith("Z")) {
                val dotIndex = normalized.indexOf(".")
                val zIndex = normalized.indexOf("Z")
                if (zIndex - dotIndex > 4) {
                    normalized = normalized.substring(0, dotIndex + 4) + "Z"
                }
            }
            return normalized
        }

        /**
         * Tries common API, offline DB, and display date formats (online + offline).
         */
        private fun parseFlexibleDate(inputDate: String, zone: String): Date? {
            if (inputDate.isBlank()) return null

            val storeTz = TimeZone.getTimeZone(resolveTimezone(zone))
            val normalized = normalizeIsoInput(inputDate)

            val patterns = listOf(
                Triple("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ENGLISH, TimeZone.getTimeZone("UTC")),
                Triple("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ENGLISH, TimeZone.getTimeZone("UTC")),
                Triple("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.ENGLISH, TimeZone.getTimeZone("UTC")),
                Triple("yyyy-MM-dd HH:mm:ss", Locale.getDefault(), storeTz),
                Triple("dd-MMM-yyyy hh:mm a", Locale.ENGLISH, storeTz),
                Triple("dd-MMM-yyyy hh:mm a", Locale.getDefault(), storeTz),
                Triple("yyyy-MM-dd", Locale.getDefault(), storeTz),
                Triple("dd-MMM-yyyy", Locale.ENGLISH, storeTz),
            )

            for ((pattern, locale, tz) in patterns) {
                try {
                    val format = SimpleDateFormat(pattern, locale)
                    format.timeZone = tz
                    format.isLenient = false
                    val candidate = if (pattern.contains("'Z'")) normalized else inputDate.trim()
                    format.parse(candidate)?.let { return it }
                } catch (_: ParseException) {
                }
            }
            return null
        }

        private fun formatDisplay(context: Context?, date: Date, zone: String): String {
            val storeTz = TimeZone.getTimeZone(resolveTimezone(zone))
            val cal = Calendar.getInstance(storeTz)
            cal.time = date

            val day = cal.get(Calendar.DAY_OF_MONTH)
            val month = if (context != null) {
                LocalizationUtils.getLocalizedMonthShort(context, cal.get(Calendar.MONTH)).lowercase(Locale.getDefault())
            } else {
                SimpleDateFormat("MMM", Locale.getDefault()).format(date).lowercase(Locale.getDefault())
            }

            val timeFormat = SimpleDateFormat(" [hh:mma]", Locale.getDefault())
            timeFormat.timeZone = storeTz
            return "${day}${month}.${timeFormat.format(date)}"
        }

        private fun formatOrError(
            context: Context?,
            inputDate: String,
            zone: String,
            dispatchDateOnly: Boolean = false
        ): String {
            if (inputDate.isBlank()) return ""
            val parsed = parseFlexibleDate(inputDate, zone) ?: run {
                Log.e("DateTimeFormatting", "Error parsing date: $inputDate")
                return context?.getString(R.string.error_parsing_date)
                    ?: "Error parsing date"
            }
            return if (dispatchDateOnly) {
                val cal = Calendar.getInstance(TimeZone.getTimeZone(resolveTimezone(zone)))
                cal.time = parsed
                val day = cal.get(Calendar.DAY_OF_MONTH)
                val month = if (context != null) {
                    LocalizationUtils.getLocalizedMonthShort(context, cal.get(Calendar.MONTH))
                        .lowercase(Locale.getDefault())
                } else {
                    SimpleDateFormat("MMM", Locale.getDefault()).format(parsed).lowercase(Locale.getDefault())
                }
                "${day}${month}"
            } else {
                formatDisplay(context, parsed, zone)
            }
        }

        @JvmOverloads
        fun formatOrderdate(inputDate: String, zone: String, context: Context? = null): String =
            formatOrError(context, inputDate, zone)

        @JvmOverloads
        fun formatApprovedate(inputDate: String, zone: String, context: Context? = null): String =
            formatOrError(context, inputDate, zone)

        @JvmOverloads
        fun formatReceivedate(inputDate: String, zone: String, context: Context? = null): String =
            formatOrError(context, inputDate, zone)

        @JvmOverloads
        fun formatDispatchDate(inputDate: String, timezone: String, context: Context? = null): String =
            formatOrError(context, inputDate, timezone, dispatchDateOnly = true)

        @JvmOverloads
        fun formatGlobalTime(inputDate: String, zone: String, context: Context? = null): String =
            formatOrError(context, inputDate, zone)

        @JvmOverloads
        fun formatSaleReturndate(inputDate: String, zone: String, context: Context? = null): String =
            formatOrError(context, inputDate, zone)

        @JvmOverloads
        fun formatReturndate(inputDate: String, zone: String, context: Context? = null): String =
            formatOrError(context, inputDate, zone)
    }
}
