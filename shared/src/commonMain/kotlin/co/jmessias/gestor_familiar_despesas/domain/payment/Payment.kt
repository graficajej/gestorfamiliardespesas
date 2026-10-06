package co.jmessias.gestor_familiar_despesas.domain.payment

import co.jmessias.gestor_familiar_despesas.domain.money.Money
import kotlinx.datetime.LocalDate

enum class PaymentMethod {
    PIX,
    CASH,
    DEBIT_CARD,
    CREDIT_CARD,
    BANK_TRANSFER,
    BOLETO,
    OTHER,
}

data class Payment(
    val id: String,
    val familyId: String,
    val expenseId: String,
    val paidAmount: Money,
    val paidDate: LocalDate,
    val method: PaymentMethod,
    val accountId: String?,
    val interestAmount: Money,
    val fineAmount: Money,
    val discountAmount: Money,
    val notes: String?,
    val createdBy: String,
    val createdAt: String,
    val updatedAt: String,
)
