package ua.moneytracking

import org.junit.Assert.*
import org.junit.Test
import java.time.YearMonth

class CurrencyTest {
    @Test fun currencyFormattingRetainsExactCentsAndUkrainianSeparators() {
        assertEquals("1 250,50 $", money(125050, "USD"))
        assertEquals("−1 250,50 €".replace('−', '-'), money(-125050, "EUR"))
        assertEquals("1 250 $", compactMoney(125000, "USD"))
        assertEquals("0 €", compactMoney(0, "EUR"))
        assertEquals("1 250 грн", compactMoney(125000))
    }

    @Test fun exchangeUsesDecimalArithmeticAndRoundsRecipientCents() {
        assertEquals(412550L, exchangeAmount(10000, "41,255"))
        assertEquals(93L, exchangeAmount(101, "0.925"))
        assertEquals(1L, exchangeAmount(1, "0.5"))
        listOf("0", "-1", "NaN", "1e3", "Infinity", "", "1..2", "99999999999").forEach { assertNull(it, exchangeAmount(100, it)) }
        assertNull(exchangeAmount(null, "1"))
        assertNull(exchangeAmount(0, "1"))
        assertNull(exchangeAmount(1, "0.001"))
        assertNull(exchangeAmount(MAX_MONEY, "2"))
    }

    @Test fun derivingRateNeverChangesAnEditedTransferByOneCent() {
        val amounts = listOf(1L, 3L, 101L, 999999999999L, 839572834753L)
        amounts.forEach { from -> amounts.forEach { to ->
            val entry = Entry(kind = "transfer", amount = from, accountId = 1, targetId = 2, time = 1, targetAmount = to)
            assertEquals("$from → $to", to, exchangeAmount(from, exchangeRate(entry)))
        } }
    }

    @Test fun reportsNeverMixDifferentCurrenciesOrCountForeignExchangeAsIncome() {
        val month = YearMonth.now()
        val now = System.currentTimeMillis()
        val state = FinanceState(accounts = listOf(
            Account(1, "UAH", 0, 0), Account(2, "USD", 0, 0, currency = "USD"), Account(3, "EUR", 0, 0, currency = "EUR")
        ), entries = listOf(
            Entry(1, "income", 100000, 1, time = now), Entry(2, "income", 20000, 2, time = now),
            Entry(3, "expense", 3000, 3, time = now), Entry(4, "transfer", 10000, 2, 1, time = now, targetAmount = 410000),
            Entry(5, "correction", 12345, 2, time = now)
        ))
        assertEquals(listOf("UAH", "USD", "EUR"), state.currencies())
        assertEquals(MonthlyTotals(100000, 0), monthlyTotals(state.entriesInCurrency("UAH"), month))
        assertEquals(MonthlyTotals(20000, 0), monthlyTotals(state.entriesInCurrency("USD"), month))
        assertEquals(MonthlyTotals(0, 3000), monthlyTotals(state.entriesInCurrency("EUR"), month))
        assertEquals(510000L, balance(state.accounts[0], state.entries))
        assertEquals(22345L, balance(state.accounts[1], state.entries))
        assertEquals(-3000L, balance(state.accounts[2], state.entries))
    }
}
