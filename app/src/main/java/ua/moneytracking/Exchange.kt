package ua.moneytracking

import java.math.BigDecimal
import java.math.RoundingMode

fun validExchangeRate(rawRate: String): Boolean = try {
    val text = rawRate.trim().replace(',', '.')
    Regex("\\d{1,12}(\\.\\d{1,18})?").matches(text) && BigDecimal(text).signum() > 0
} catch (_: NumberFormatException) { false }

/** Rate is units of the recipient currency for one unit of the source currency. */
fun exchangeAmount(amount: Long?, rawRate: String): Long? = try {
    val text = rawRate.trim().replace(',', '.')
    if (amount == null || amount <= 0 || !validExchangeRate(text)) null
    else BigDecimal(text).takeIf { it.signum() > 0 }?.multiply(BigDecimal.valueOf(amount))
        ?.setScale(0, RoundingMode.HALF_UP)?.longValueExact()?.takeIf { it in 1..MAX_MONEY }
} catch (_: ArithmeticException) { null }

fun exchangeRate(entry: Entry?): String = if (entry?.targetAmount != null && entry.amount > 0) {
    BigDecimal.valueOf(entry.targetAmount).divide(BigDecimal.valueOf(entry.amount), 18, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
} else ""
