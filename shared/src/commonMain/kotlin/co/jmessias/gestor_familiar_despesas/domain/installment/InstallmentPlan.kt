package co.jmessias.gestor_familiar_despesas.domain.installment

import co.jmessias.gestor_familiar_despesas.domain.expense.Expense
import co.jmessias.gestor_familiar_despesas.domain.money.Money
import kotlinx.datetime.LocalDate

data class InstallmentPlan(
    val id: String,
    val familyId: String,
    val description: String,
    val totalAmount: Money,
    val installmentCount: Int,
    val firstDueDate: LocalDate,
    val categoryId: String?,
    val accountId: String?,
    val createdBy: String,
    val createdAt: String,
    val updatedAt: String,
)

data class Installment(
    val id: String,
    val planId: String,
    val expense: Expense,
    val number: Int,
    val totalInstallments: Int,
    val createdAt: String,
    val updatedAt: String,
)

data class InstallmentPlanWithInstallments(
    val plan: InstallmentPlan,
    val installments: List<Installment>,
)
