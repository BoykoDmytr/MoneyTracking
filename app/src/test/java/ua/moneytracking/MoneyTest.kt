package ua.moneytracking
import org.junit.Assert.*
import org.junit.Test
class MoneyTest {
    @Test fun moneyParsingIsExact() {
        assertEquals(125050L, parseMoney("1 250,50"))
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
}
