package co.jmessias.gestor_familiar_despesas.domain.payment

import co.jmessias.gestor_familiar_despesas.domain.model.EntityMetadataProvider
import co.jmessias.gestor_familiar_despesas.domain.money.Money
import co.jmessias.gestor_familiar_despesas.domain.obligation.ExpenseLedger
import co.jmessias.gestor_familiar_despesas.domain.obligation.PaymentAdjustment
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

data class RecordPaymentInput(
    val familyId: String,
    val expenseId: String,
    val userId: String,
    val paidAmount: Money,
    val paidDate: LocalDate,
    val method: PaymentMethod,
    val accountId: String? = null,
    val interestAmount: Money = Money.ZERO,
    val fineAmount: Money = Money.ZERO,
    val discountAmount: Money = Money.ZERO,
    val notes: String? = null,
)

class GetExpenseLedgerUseCase(
    private val repository: PaymentRepository,
    private val metadataProvider: EntityMetadataProvider,
) {
    suspend fun execute(familyId: String, expenseId: String): Result<ExpenseLedger> {
        if (familyId.isBlank() || expenseId.isBlank()) {
            return Result.failure(IllegalArgumentException("Família e despesa são obrigatórias."))
        }
        val ledger = repository.findLedger(familyId, expenseId, metadataProvider.today())
            ?: return Result.failure(NoSuchElementException("Despesa não encontrada na família selecionada."))
        return Result.success(ledger)
    }
}

class ObservePaymentsUseCase(
    private val repository: PaymentRepository,
) {
    fun execute(familyId: String, expenseId: String): Flow<List<Payment>> =
        repository.observePayments(familyId, expenseId)
}

class RecordPaymentUseCase(
    private val repository: PaymentRepository,
    private val metadataProvider: EntityMetadataProvider,
) {
    suspend fun execute(input: RecordPaymentInput): Result<Payment> {
        if (input.familyId.isBlank() || input.expenseId.isBlank() || input.userId.isBlank()) {
            return Result.failure(IllegalArgumentException("Família, despesa e usuário são obrigatórios."))
        }
        if (input.paidAmount <= Money.ZERO) {
            return Result.failure(IllegalArgumentException("O valor do pagamento deve ser maior que zero."))
        }
        if (!repository.userBelongsToFamily(input.familyId, input.userId)) {
            return Result.failure(IllegalArgumentException("Usuário não pertence à família selecionada."))
        }
        if (input.accountId != null &&
            !repository.activeAccountBelongsToFamily(input.familyId, input.accountId)
        ) {
            return Result.failure(IllegalArgumentException("A conta deve estar ativa e pertencer à família selecionada."))
        }

        val today = metadataProvider.today()
        val ledger = repository.findLedger(input.familyId, input.expenseId, today)
            ?: return Result.failure(NoSuchElementException("Despesa não encontrada na família selecionada."))
        if (ledger.cancelled) return Result.failure(IllegalStateException("Despesas canceladas não podem receber pagamentos."))
        val adjustment = PaymentAdjustment(
            paidAmount = input.paidAmount,
            interestAmount = input.interestAmount,
            fineAmount = input.fineAmount,
            discountAmount = input.discountAmount,
        )
        if (!ledger.canAccept(adjustment)) {
            return Result.failure(IllegalArgumentException("O valor do pagamento excede o saldo atual."))
        }

        val now = metadataProvider.nowUtc()
        val payment = Payment(
            id = metadataProvider.newId(),
            familyId = input.familyId,
            expenseId = input.expenseId,
            paidAmount = input.paidAmount,
            paidDate = input.paidDate,
            method = input.method,
            accountId = input.accountId,
            interestAmount = input.interestAmount,
            fineAmount = input.fineAmount,
            discountAmount = input.discountAmount,
            notes = input.notes?.trim()?.takeIf(String::isNotEmpty),
            createdBy = input.userId,
            createdAt = now,
            updatedAt = now,
        )
        if (!repository.recordPayment(payment, today)) {
            return Result.failure(IllegalStateException("O pagamento não foi registrado; o saldo da despesa pode ter sido alterado."))
        }
        return Result.success(payment)
    }
}

class RefreshExpenseStatusesUseCase(
    private val repository: PaymentRepository,
    private val metadataProvider: EntityMetadataProvider,
) {
    suspend fun execute() = repository.refreshStatuses(metadataProvider.today())
}
