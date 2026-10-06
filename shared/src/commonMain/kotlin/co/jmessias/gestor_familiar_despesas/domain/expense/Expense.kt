package co.jmessias.gestor_familiar_despesas.domain.expense

import co.jmessias.gestor_familiar_despesas.domain.money.Money
import co.jmessias.gestor_familiar_despesas.domain.obligation.ExpenseStatus
import kotlinx.datetime.LocalDate

enum class ExpenseType {
    ONE_TIME,
    RECURRING,
    INSTALLMENT,
}

data class Expense(
    val id: String,
    val familyId: String,
    val categoryId: String?,
    val accountId: String?,
    val description: String,
    val amount: Money,
    val dueDate: LocalDate,
    val type: ExpenseType,
    val status: ExpenseStatus,
    val notes: String?,
    val recurrenceId: String?,
    val createdBy: String,
    val updatedBy: String,
    val createdAt: String,
    val updatedAt: String,
    val deletedAt: String?,
)
