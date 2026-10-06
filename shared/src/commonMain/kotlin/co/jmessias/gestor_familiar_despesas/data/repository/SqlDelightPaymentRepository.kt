package co.jmessias.gestor_familiar_despesas.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import co.jmessias.gestor_familiar_despesas.db.AppDatabase
import co.jmessias.gestor_familiar_despesas.domain.money.Money
import co.jmessias.gestor_familiar_despesas.domain.obligation.ExpenseLedger
import co.jmessias.gestor_familiar_despesas.domain.obligation.ExpenseStatus
import co.jmessias.gestor_familiar_despesas.domain.obligation.PaymentAdjustment
import co.jmessias.gestor_familiar_despesas.domain.payment.Payment
import co.jmessias.gestor_familiar_despesas.domain.payment.PaymentMethod
import co.jmessias.gestor_familiar_despesas.domain.payment.PaymentRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate

class SqlDelightPaymentRepository(
    private val database: AppDatabase,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : PaymentRepository {
    private val queries = database.appDatabaseQueries

    override suspend fun userBelongsToFamily(familyId: String, userId: String): Boolean =
        withContext(dispatcher) {
            queries.selectUserBelongsToFamily(family_id = familyId, user_id = userId).executeAsOne()
        }

    override suspend fun activeAccountBelongsToFamily(familyId: String, accountId: String): Boolean =
        withContext(dispatcher) {
            queries.selectActiveAccountBelongsToFamily(
                family_id = familyId,
                account_id = accountId,
            ).executeAsOne()
        }

    override suspend fun findLedger(
        familyId: String,
        expenseId: String,
        today: LocalDate,
    ): ExpenseLedger? = withContext(dispatcher) {
        var ledger: ExpenseLedger? = null
        database.transaction {
            queries.refreshExpenseStatuses(today = today.toString())
            ledger = queries.selectExpensePaymentLedger(
                family_id = familyId,
                expense_id = expenseId,
            ).executeAsOneOrNull()?.toLedger(today)
        }
        ledger
    }

    override fun observePayments(familyId: String, expenseId: String): Flow<List<Payment>> =
        queries.selectPaymentsByExpense(family_id = familyId, expense_id = expenseId)
            .asFlow()
            .mapToList(dispatcher)
            .map { rows -> rows.map(::toDomain) }

    override suspend fun recordPayment(payment: Payment, today: LocalDate): Boolean = withContext(dispatcher) {
        var recorded = false
        database.transaction {
            queries.refreshExpenseStatuses(today = today.toString())
            val belongs = queries.selectUserBelongsToFamily(
                family_id = payment.familyId,
                user_id = payment.createdBy,
            ).executeAsOne()
            val accountIsValid = payment.accountId == null ||
                queries.selectActiveAccountBelongsToFamily(
                    family_id = payment.familyId,
                    account_id = payment.accountId,
                ).executeAsOne()
            val ledger = queries.selectExpensePaymentLedger(
                family_id = payment.familyId,
                expense_id = payment.expenseId,
            ).executeAsOneOrNull()?.toLedger(today)
            val adjustment = PaymentAdjustment(
                paidAmount = payment.paidAmount,
                interestAmount = payment.interestAmount,
                fineAmount = payment.fineAmount,
                discountAmount = payment.discountAmount,
            )
            if (belongs && accountIsValid && ledger != null && ledger.canAccept(adjustment)) {
                queries.insertPayment(
                    id = payment.id,
                    family_id = payment.familyId,
                    expense_id = payment.expenseId,
                    paid_amount_in_cents = payment.paidAmount.cents,
                    paid_date = payment.paidDate.toString(),
                    payment_method = payment.method.name,
                    account_id = payment.accountId,
                    interest_amount_in_cents = payment.interestAmount.cents,
                    fine_amount_in_cents = payment.fineAmount.cents,
                    discount_amount_in_cents = payment.discountAmount.cents,
                    notes = payment.notes,
                    created_by = payment.createdBy,
                    created_at = payment.createdAt,
                    updated_at = payment.updatedAt,
                )
                queries.refreshExpenseStatuses(today = today.toString())
                recorded = true
            }
        }
        recorded
    }

    override suspend fun refreshStatuses(today: LocalDate) = withContext(dispatcher) {
        database.transaction {
            queries.refreshExpenseStatuses(today = today.toString())
        }
        Unit
    }

    private fun co.jmessias.gestorfamiliardespesas.db.SelectExpensePaymentLedger.toLedger(today: LocalDate) =
        ExpenseLedger(
            obligationAmount = Money.ofCents(obligation_amount_in_cents),
            dueDate = LocalDate.parse(due_date),
            today = today,
            payments = if (paid_amount_in_cents > 0L) {
                listOf(
                    PaymentAdjustment(
                        paidAmount = Money.ofCents(paid_amount_in_cents),
                        interestAmount = Money.ofCents(interest_amount_in_cents),
                        fineAmount = Money.ofCents(fine_amount_in_cents),
                        discountAmount = Money.ofCents(discount_amount_in_cents),
                    )
                )
            } else {
                emptyList()
            },
            cancelled = status == ExpenseStatus.CANCELADA.name,
        )

    private fun toDomain(row: co.jmessias.gestorfamiliardespesas.db.SelectPaymentsByExpense) = Payment(
        id = row.id,
        familyId = row.family_id,
        expenseId = row.expense_id,
        paidAmount = Money.ofCents(row.paid_amount_in_cents),
        paidDate = LocalDate.parse(row.paid_date),
        method = PaymentMethod.valueOf(row.payment_method),
        accountId = row.account_id,
        interestAmount = Money.ofCents(row.interest_amount_in_cents),
        fineAmount = Money.ofCents(row.fine_amount_in_cents),
        discountAmount = Money.ofCents(row.discount_amount_in_cents),
        notes = row.notes,
        createdBy = row.created_by,
        createdAt = row.created_at,
        updatedAt = row.updated_at,
    )
}
