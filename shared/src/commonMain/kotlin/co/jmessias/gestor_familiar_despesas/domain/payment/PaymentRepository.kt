package co.jmessias.gestor_familiar_despesas.domain.payment

import co.jmessias.gestor_familiar_despesas.domain.obligation.ExpenseLedger
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

interface PaymentRepository {
    suspend fun userBelongsToFamily(familyId: String, userId: String): Boolean
    suspend fun activeAccountBelongsToFamily(familyId: String, accountId: String): Boolean
    suspend fun findLedger(familyId: String, expenseId: String, today: LocalDate): ExpenseLedger?
    fun observePayments(familyId: String, expenseId: String): Flow<List<Payment>>
    suspend fun recordPayment(payment: Payment, today: LocalDate): Boolean
    suspend fun refreshStatuses(today: LocalDate)
}
