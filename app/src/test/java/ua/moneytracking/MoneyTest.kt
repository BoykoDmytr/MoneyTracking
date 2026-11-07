package ua.moneytracking
import org.junit.Assert.*
import org.junit.Test
class MoneyTest {
    @Test fun moneyParsingIsExact() {
        assertEquals(125050L, parseMoney("1 250,50"))
        assertEquals(125050L, parseMoney("1\u202f250,50"))
        assertEquals(-125050L, parseMoney("−1\u00a0250,50"))
        assertEquals(10L, parseMoney("0.10"))
        assertNull(parseMoney("1.999")); assertNull(parseMoney("NaN")); assertNull(parseMoney("999999999999999999"))
        assertEquals("1 250,50 грн", money(125050))
    }
    @Test fun editsDeletionAndTransfersRecalculate() {
        val a = Account(1, "Картка", 100000, 0)
        val b = Account(2, "Готівка", 0, 0)
        val expense = Entry(1, "expense", 35000, 1, time = 0)
        val transfer = Entry(2, "transfer", 20000, 1, 2, time = 0)
        assertEquals(45000L, balance(a, listOf(expense, transfer)))
        assertEquals(20000L, balance(b, listOf(expense, transfer)))
        assertEquals(65000L, balance(a, listOf(expense, transfer)) + balance(b, listOf(expense, transfer)))
        assertEquals(70000L, balance(a, listOf(expense.copy(amount = 10000), transfer)))
        assertEquals(80000L, balance(a, listOf(transfer)))
        assertEquals(100000L, balance(a, emptyList()))
    }
    @Test fun keypadUsesExactDecimalArithmeticAndLimitsPrecision() {
        var input = ""
        listOf("0", ",", "1", "+", "0", ",", "2").forEach { input = AmountInput.key(input, it) }
        assertEquals(30L, AmountInput.evaluate(input))
        assertEquals(125050L, AmountInput.evaluate("1250,50"))
        assertEquals(333L, AmountInput.evaluate("10÷3"))
        assertNull(AmountInput.evaluate("10÷0"))
        assertNull(AmountInput.evaluate("10+"))
        assertEquals("1,23", AmountInput.key("1,23", "4"))
        assertEquals("1,2", AmountInput.key("1,23", "⌫"))
        assertEquals("", AmountInput.key("1,23", "C"))
        assertEquals("0,", AmountInput.key("", ","))
        assertEquals("0,50", AmountInput.key("0,50", ","))
        assertEquals("5×", AmountInput.key("2+3", "×"))
        val negativeIntermediate = AmountInput.key("10−20", "+")
        assertEquals("-10+", negativeIntermediate)
        assertEquals(2000L, AmountInput.evaluate(negativeIntermediate + "30"))
        assertEquals(-1000L, AmountInput.evaluate("10−20"))
        assertEquals("637 грн", compactMoney(63700))
        assertEquals("637,50 грн", compactMoney(63750))
    }
}
