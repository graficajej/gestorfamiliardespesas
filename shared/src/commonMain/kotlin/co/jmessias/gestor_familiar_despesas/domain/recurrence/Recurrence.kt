package co.jmessias.gestor_familiar_despesas.domain.recurrence

import co.jmessias.gestor_familiar_despesas.domain.expense.Expense
import co.jmessias.gestor_familiar_despesas.domain.money.Money
import kotlinx.datetime.LocalDate

enum class RecurrenceFrequency(val monthInterval: Int) {
    MONTHLY(1),
    QUARTERLY(3),
    YEARLY(12),
}

data class Recurrence(
    val id: String,
    val familyId: String,
    val description: String,
    val amount: Money,
    val categoryId: String?,
    val accountId: String?,
    val frequency: RecurrenceFrequency,
    val startDate: LocalDate,
    val dueDay: Int?,
    val isActive: Boolean,
    val createdBy: String,
    val createdAt: String,
    val updatedAt: String,
)

data class RecurrenceWithOccurrences(
    val recurrence: Recurrence,
    val occurrences: List<Expense>,
)
