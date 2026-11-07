package ua.moneytracking

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

class FinanceCoreTest {
    @Test fun selectedMonthUsesLocalBoundariesAndExcludesTransfersAndCorrections() {
        val month = YearMonth.of(2026, 11)
        val zone = ZoneId.of("Europe/Kyiv")
        val start = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val entries = listOf(
            Entry(1, "income", 10000, 1, time = start - 1),
            Entry(2, "income", 125050, 1, time = start, incomeSource = "salary", sender = "Компанія"),
            Entry(3, "expense", 35050, 1, time = end - 1),
            Entry(4, "income", 99900, 1, time = end),
            Entry(5, "transfer", 20000, 1, targetId = 2, time = start),
            Entry(6, "correction", -50000, 1, time = start),
            Entry(7, "income", 20000, 2, time = start)
        )
        assertEquals(MonthlyTotals(income = 145050, expense = 35050), monthlyTotals(entries, month, zone))
        assertEquals(MonthlyTotals(income = 125050, expense = 35050), monthlyTotals(entries, month, zone, 1))
        assertEquals(MonthlyTotals(income = 20000), monthlyTotals(entries, month, zone, 2))
        assertEquals(MonthlyTotals(income = 99900), monthlyTotals(entries, month.plusMonths(1), zone, 1))
    }

    @Test fun daylightSavingMonthAndLeapYearHaveCorrectInclusiveExclusiveLimits() {
        val zone = ZoneId.of("Europe/Kyiv")
        val october = YearMonth.of(2026, 10)
        val events = listOf(
            Entry(1, "expense", 100, 1, time = Instant.parse("2026-10-24T23:30:00Z").toEpochMilli()),
            Entry(2, "expense", 200, 1, time = Instant.parse("2026-10-25T01:30:00Z").toEpochMilli()),
            Entry(3, "expense", 400, 1, time = Instant.parse("2026-10-31T22:00:00Z").toEpochMilli())
        )
        assertEquals(300L, monthlyTotals(events, october, zone).expense)
        assertEquals(400L, monthlyTotals(events, october.plusMonths(1), zone).expense)
        val leap = YearMonth.of(2024, 2)
        val leapIncome = Entry(kind = "income", amount = 100, accountId = 1, time = leap.atEndOfMonth().atTime(23, 59).atZone(zone).toInstant().toEpochMilli())
        assertEquals(100L, monthlyTotals(listOf(leapIncome), leap, zone).income)
        assertEquals(0L, monthlyTotals(listOf(leapIncome), leap.plusMonths(1), zone).income)
    }

    @Test fun balanceIncludesSignedCorrectionsAndRaisesOnOverflow() {
        val account = Account(1, "Картка", 100000, palette[0])
        val plus = Entry(kind = "correction", amount = 20000, accountId = 1, time = 0)
        val minus = plus.copy(amount = -50000)
        assertEquals(70000L, balance(account, listOf(plus, minus)))
        assertEquals(0L, delta(plus, 2))
        assertThrows(ArithmeticException::class.java) { balance(account.copy(opening = Long.MAX_VALUE), listOf(plus)) }
    }

    @Test fun incomeLabelsAndDecimalFormattingRemainUnambiguous() {
        assertEquals("Зарплата", incomeSourceLabel("salary"))
        assertEquals("Від іншої людини", incomeSourceLabel("person"))
        assertEquals("Інше надходження", incomeSourceLabel("other"))
        assertEquals("Інше надходження", incomeSourceLabel("legacy"))
        assertEquals(-125050L, parseMoney("-1 250,50"))
        assertEquals(MAX_MONEY, parseMoney("9999999999,99"))
        assertNull(parseMoney("10000000000"))
        assertNull(parseMoney("1,001"))
        assertEquals("−1 250,50 грн".replace('−', '-'), money(-125050))
    }
}
