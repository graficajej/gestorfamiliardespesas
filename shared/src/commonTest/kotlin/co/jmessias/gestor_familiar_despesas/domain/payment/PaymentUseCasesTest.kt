package co.jmessias.gestor_familiar_despesas.domain.payment

import co.jmessias.gestor_familiar_despesas.domain.model.EntityMetadataProvider
import co.jmessias.gestor_familiar_despesas.domain.money.Money
import co.jmessias.gestor_familiar_despesas.domain.obligation.ExpenseLedger
import co.jmessias.gestor_familiar_despesas.domain.obligation.PaymentAdjustment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PaymentUseCasesTest {
    private val repository = FakePaymentRepository()
    private val metadata = PaymentMetadata()

    @Test
    fun `recording partial then full payment preserves the obligation and settles balance`() = runTest {
        val useCase = RecordPaymentUseCase(repository, metadata)

        val partial = useCase.execute(input(paidAmount = Money.ofCents(300))).getOrThrow()

        assertEquals(300L, partial.paidAmount.cents)
        assertEquals("payment-1", partial.id)
        assertEquals(700L, repository.ledger!!.balance.cents)

        useCase.execute(input(paidAmount = Money.ofCents(700))).getOrThrow()

        assertEquals(1000L, repository.ledger!!.obligationAmount.cents)
        assertEquals(Money.ZERO, repository.ledger!!.balance)
        assertTrue(repository.ledger!!.canAccept(PaymentAdjustment(paidAmount = Money.ofCents(1))).not())
    }

    @Test
    fun `cancelled expenses overpayments and invalid references are rejected`() = runTest {
        val useCase = RecordPaymentUseCase(repository, metadata)
        assertTrue(useCase.execute(input(paidAmount = Money.ofCents(1001))).isFailure)

        repository.ledger = repository.ledger!!.copy(cancelled = true)
        assertTrue(useCase.execute(input()).isFailure)

        repository.ledger = repository.ledger!!.copy(cancelled = false)
        repository.member = false
        assertTrue(useCase.execute(input()).isFailure)
        repository.member = true
        repository.activeAccount = false
        assertTrue(useCase.execute(input(accountId = "account-1")).isFailure)
        assertFalse(repository.recordedPayments.isNotEmpty())
    }

    private fun input(
        paidAmount: Money = Money.ofCents(100),
        accountId: String? = null,
    ) = RecordPaymentInput(
        familyId = "family-1",
        expenseId = "expense-1",
        userId = "user-1",
        paidAmount = paidAmount,
        paidDate = LocalDate(2026, 10, 6),
        method = PaymentMethod.PIX,
        accountId = accountId,
    )
}

private class FakePaymentRepository : PaymentRepository {
    var member = true
    var activeAccount = true
    var ledger: ExpenseLedger? = ExpenseLedger(
        obligationAmount = Money.ofCents(1000),
        dueDate = LocalDate(2026, 10, 15),
        today = LocalDate(2026, 10, 6),
    )
    val recordedPayments = mutableListOf<Payment>()

    override suspend fun userBelongsToFamily(familyId: String, userId: String): Boolean = member

    override suspend fun activeAccountBelongsToFamily(familyId: String, accountId: String): Boolean = activeAccount

    override suspend fun findLedger(familyId: String, expenseId: String, today: LocalDate): ExpenseLedger? =
        ledger?.copy(today = today)

    override fun observePayments(familyId: String, expenseId: String): Flow<List<Payment>> =
        flowOf(recordedPayments.toList())

    override suspend fun recordPayment(payment: Payment, today: LocalDate): Boolean {
        val current = ledger ?: return false
        val updated = current.registerPayment(
            PaymentAdjustment(
                paidAmount = payment.paidAmount,
                interestAmount = payment.interestAmount,
                fineAmount = payment.fineAmount,
                discountAmount = payment.discountAmount,
            )
        )
        ledger = updated
        recordedPayments += payment
        return true
    }

    override suspend fun refreshStatuses(today: LocalDate) = Unit
}

private class PaymentMetadata : EntityMetadataProvider {
    private var nextId = 0

    override fun newId(): String = "payment-${++nextId}"

    override fun nowUtc(): String = "2026-10-06T12:00:00Z"

    override fun today(): LocalDate = LocalDate(2026, 10, 6)
}
