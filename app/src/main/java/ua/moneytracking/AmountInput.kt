package ua.moneytracking

import java.math.BigDecimal
import java.math.RoundingMode

/** Exact decimal keypad arithmetic; no binary floating point enters stored amounts. */
object AmountInput {
    fun key(expression: String, key: String): String {
        if (key == "⌫") return expression.dropLast(1)
        if (key == "C") return ""
        if (key in listOf("+", "−", "×", "÷")) {
            if (expression.isBlank()) return expression
            val base = expression.trimEnd('+', '−', '×', '÷')
            val value = evaluate(base) ?: return expression
            return editableMoney(value).trimEnd('0').trimEnd('.') + key
        }
        if (key == ",") {
            val current = expression.split('+', '−', '×', '÷').last()
            return if (',' in current || '.' in current) expression else expression + if (current.isEmpty()) "0," else ","
        }
        if (key.length != 1 || !key[0].isDigit()) return expression
        val current = expression.split('+', '−', '×', '÷').last()
        if (current.substringBefore(',').substringBefore('.').length >= 10 && ',' !in current && '.' !in current) return expression
        val decimal = current.indexOfFirst { it == ',' || it == '.' }
        if (decimal >= 0 && current.length - decimal > 2) return expression
        return if (current == "0") expression.dropLast(1) + key else expression + key
    }
    fun evaluate(expression: String): Long? = try {
        val normalized = expression.replace(',', '.')
        val match = Regex("(-?\\d{1,10}(?:\\.\\d{0,2})?)(?:([+−×÷])(\\d{1,10}(?:\\.\\d{0,2})?))?").matchEntire(normalized)
        if (match == null) null else {
            val left = BigDecimal(match.groupValues[1])
            val right = match.groupValues[3].takeIf { it.isNotEmpty() }?.let { BigDecimal(it) }
            val result = if (right == null) left else when (match.groupValues[2]) {
                "+" -> left + right
                "−" -> left - right
                "×" -> left * right
                else -> left.divide(right, 2, RoundingMode.HALF_UP)
            }
            parseMoney(result.setScale(2, RoundingMode.HALF_UP).toPlainString())
        }
    } catch (_: ArithmeticException) { null }
}

fun compactMoney(value: Long, currency: String = "UAH"): String {
    val unit = currencyUnit(currency)
    return money(value, currency).removeSuffix(",00 $unit") + if (value % 100L == 0L) " $unit" else ""
}
