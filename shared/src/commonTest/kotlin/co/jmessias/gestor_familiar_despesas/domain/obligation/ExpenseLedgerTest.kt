package co.jmessias.gestor_familiar_despesas.domain.obligation

import co.jmessias.gestor_familiar_despesas.domain.money.Money
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ExpenseLedgerTest {

    @Test
    fun `zero payments leaves the amount untouched`() {
        val ledger = ExpenseLedger(
            obligationAmount = Money.ofCents(1500),
            dueDate = LocalDate(2026, 10, 6),
            today = LocalDate(2026, 10, 6),
        )

        assertEquals(1500L, ledger.balance.cents)
        assertEquals(ExpenseStatus.A_VENCER, ledger.status)
    }

    @Test
    fun `partial payment reduces the balance`() {
        val ledger = ExpenseLedger(
            obligationAmount = Money.ofCents(1000),
            dueDate = LocalDate(2026, 10, 8),
            today = LocalDate(2026, 10, 6),
        )

        val updated = ledger.registerPayment(PaymentAdjustment(paidAmount = Money.ofCents(300)))

        assertEquals(700L, updated.balance.cents)
        assertEquals(ExpenseStatus.A_VENCER, updated.status)
    }

    @Test
    fun `payments above the balance are rejected`() {
        val ledger = ExpenseLedger(
            obligationAmount = Money.ofCents(500),
            dueDate = LocalDate(2026, 10, 8),
            today = LocalDate(2026, 10, 6),
        )

        assertFailsWith<IllegalArgumentException> {
            ledger.registerPayment(PaymentAdjustment(paidAmount = Money.ofCents(600)))
        }
    }

    @Test
    fun `cancelled obligations cannot receive payments`() {
        val ledger = ExpenseLedger(
            obligationAmount = Money.ofCents(500),
            dueDate = LocalDate(2026, 10, 8),
            today = LocalDate(2026, 10, 6),
            cancelled = true,
        )

        assertFailsWith<IllegalArgumentException> {
            ledger.registerPayment(PaymentAdjustment(paidAmount = Money.ofCents(50)))
        }
    }

    @Test
    fun `balance with interest and discounts is calculated correctly`() {
        val ledger = ExpenseLedger(
            obligationAmount = Money.ofCents(1000),
            dueDate = LocalDate(2026, 10, 8),
            today = LocalDate(2026, 10, 6),
            payments = listOf(
                PaymentAdjustment(
                    paidAmount = Money.ofCents(200),
                    interestAmount = Money.ofCents(50),
                    fineAmount = Money.ofCents(25),
                    discountAmount = Money.ofCents(10),
                )
            )
        )

        assertEquals(865L, ledger.balance.cents)
    }
}
