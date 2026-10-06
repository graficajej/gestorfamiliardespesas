package co.jmessias.gestor_familiar_despesas.domain.obligation

import co.jmessias.gestor_familiar_despesas.domain.money.Money

private fun sumPayments(payments: List<PaymentAdjustment>): Money = payments.fold(Money.ZERO) { acc, it ->
    acc + it.paidAmount
}

private fun sumAdjustments(payments: List<PaymentAdjustment>, selector: (PaymentAdjustment) -> Money): Money = payments.fold(Money.ZERO) { acc, payment ->
    acc + selector(payment)
}

data class PaymentAdjustment(
    val paidAmount: Money,
    val interestAmount: Money = Money.ZERO,
    val fineAmount: Money = Money.ZERO,
    val discountAmount: Money = Money.ZERO,
) {
    init {
        require(paidAmount > Money.ZERO) { "Payment amount must be positive" }
    }
}

data class ExpenseLedger(
    val obligationAmount: Money,
    val dueDate: kotlinx.datetime.LocalDate,
    val today: kotlinx.datetime.LocalDate,
    val payments: List<PaymentAdjustment> = emptyList(),
    val cancelled: Boolean = false,
) {
    val paidAmount: Money
        get() = sumPayments(payments)

    val interestAmount: Money
        get() = sumAdjustments(payments) { it.interestAmount }

    val fineAmount: Money
        get() = sumAdjustments(payments) { it.fineAmount }

    val discountAmount: Money
        get() = sumAdjustments(payments) { it.discountAmount }

    val balance: Money
        get() = obligationAmount + interestAmount + fineAmount - discountAmount - paidAmount

    val status: ExpenseStatus
        get() = ExpenseStatusResolver.resolve(balance, dueDate, today, cancelled)

    fun canAccept(payment: PaymentAdjustment): Boolean {
        if (cancelled) return false
        if (payment.paidAmount <= Money.ZERO) return false
        return try {
            val projectedBalance = obligationAmount +
                (interestAmount + payment.interestAmount) +
                (fineAmount + payment.fineAmount) -
                (discountAmount + payment.discountAmount) -
                (paidAmount + payment.paidAmount)
            projectedBalance >= Money.ZERO
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    fun registerPayment(payment: PaymentAdjustment): ExpenseLedger {
        require(!cancelled) { "Cancelled obligation cannot receive payment" }
        require(canAccept(payment)) { "Payment exceeds the current balance" }
        return copy(payments = payments + payment)
    }
}
