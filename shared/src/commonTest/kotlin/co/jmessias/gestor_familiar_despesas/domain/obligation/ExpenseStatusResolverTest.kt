package co.jmessias.gestor_familiar_despesas.domain.obligation

import co.jmessias.gestor_familiar_despesas.domain.money.Money
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class ExpenseStatusResolverTest {

    @Test
    fun `zero balance is marked as paid`() {
        val date = LocalDate(2026, 10, 6)
        val status = ExpenseStatusResolver.resolve(Money.ZERO, date, date)
        assertEquals(ExpenseStatus.PAGA, status)
    }

    @Test
    fun `positive balance with past due date is overdue`() {
        val dueDate = LocalDate(2026, 10, 5)
        val today = LocalDate(2026, 10, 6)
        val status = ExpenseStatusResolver.resolve(Money.ofCents(100), dueDate, today)
        assertEquals(ExpenseStatus.VENCIDA, status)
    }

    @Test
    fun `positive balance with future due date is open`() {
        val dueDate = LocalDate(2026, 10, 7)
        val today = LocalDate(2026, 10, 6)
        val status = ExpenseStatusResolver.resolve(Money.ofCents(100), dueDate, today)
        assertEquals(ExpenseStatus.A_VENCER, status)
    }

    @Test
    fun `cancelled obligation is terminal`() {
        val dueDate = LocalDate(2026, 10, 7)
        val today = LocalDate(2026, 10, 6)
        val status = ExpenseStatusResolver.resolve(Money.ofCents(100), dueDate, today, cancelled = true)
        assertEquals(ExpenseStatus.CANCELADA, status)
    }
}
