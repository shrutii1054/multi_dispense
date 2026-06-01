package com.retailone.pos.utils

import java.text.NumberFormat
import java.util.Locale

object FunUtils {

   // clickitem.pack_product_description.trim().contains("loose oil", ignoreCase = true)

    //Imp in ReturnsaleItemAdapter  product_id will replace with category_id

    fun isLooseOil(catId: Int, desc: String): Boolean {
        // Only return true if the "loose_oil" feature is enabled AND description contains "loose oil"
        if (!FeatureManager.isEnabled("loose oile sale")) return false

        return desc.trim().contains("loose oil", ignoreCase = true)
    }

    fun DtoInt(input: Double): Int {
        return input.toInt()
    }

    // Convert Double to Double with 2 decimal places
    fun DtoDouble(input: Double): Double {
        return String.format("%.2f", input).toDouble()
    }

//    fun DtoString(input: Double): String {
//        return if (input % 1 == 0.0) {
//            input.toInt().toString()  // No decimal part, return as integer
//        } else {
//            String.format("%.2f", input)  // Return with 2 decimal places
//        }
//    }

    fun DtoString(input: Double): String {
        return if (input % 1 == 0.0) {
            input.toInt().toString()  // No decimal part, return as integer
        } else {
            // Format with two decimal places, but conditionally remove second digit if it's zero
            val formatted = String.format("%.2f", input)
            if (formatted.endsWith(".00")) {
                formatted.substring(0, formatted.indexOf("."))
            } else if (formatted.endsWith("0")) {
                formatted.substring(0, formatted.length - 1)
            } else {
                formatted
            }
        }
    }

    /**
     * Parses user-facing numeric input for the current (or given) locale.
     * Handles comma decimals (e.g. 20359288,01), dot decimals, and European thousands (50.000,00).
     */
    fun parseLocaleNumber(input: String, locale: Locale = Locale.getDefault()): Double {
        val trimmed = input.trim()
        if (trimmed.isEmpty() || trimmed == "." || trimmed == ",") return 0.0

        // Avoid corrupting values produced by Double.toString() (e.g. 2.0408887E7)
        if (trimmed.contains('e', ignoreCase = true)) {
            return trimmed.toDoubleOrNull() ?: 0.0
        }

        try {
            val format = NumberFormat.getNumberInstance(locale)
            format.isParseIntegerOnly = false
            format.parse(trimmed)?.toDouble()?.let { return it }
        } catch (_: Exception) {
        }

        var cleaned = trimmed.replace(Regex("[^0-9.,\\-]"), "")
        if (cleaned.isEmpty()) return 0.0

        when {
            cleaned.contains(",") && cleaned.contains(".") -> {
                cleaned = if (cleaned.lastIndexOf(",") > cleaned.lastIndexOf(".")) {
                    cleaned.replace(".", "").replace(",", ".")
                } else {
                    cleaned.replace(",", "")
                }
            }
            cleaned.contains(",") -> cleaned = cleaned.replace(",", ".")
        }
        return cleaned.toDoubleOrNull() ?: 0.0
    }

    fun stringToDouble(input: String): Double = parseLocaleNumber(input)




    fun formatPrintPrice(numberString: String): String? {
        return try {
            // Remove commas from the string
            val sanitizedNumberString = numberString.replace(",", "")

            // Parse the string to a floating-point number
            val number = sanitizedNumberString.toFloat()

            // Check if the number has any decimal part
            if (number % 1 == 0f) {
                // If there's no decimal part (both digits after the decimal are zero), return as an integer
                number.toInt().toString()
            } else {
                // Else return the number with two decimal places
                String.format("%.2f", number)
            }
        } catch (e: NumberFormatException) {
            // Handle invalid number format (if the input string isn't a valid number)
            "--"
        }
    }



}