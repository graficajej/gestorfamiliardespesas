package co.jmessias.gestor_familiar_despesas.domain.obligation

import co.jmessias.gestor_familiar_despesas.domain.money.Money
import kotlinx.datetime.LocalDate

enum class ExpenseStatus {
    A_VENCER,
    VENCIDA,
    PAGA,
    CANCELADA,
}

object ExpenseStatusResolver {
    fun resolve(balance: Money, dueDate: LocalDate, today: LocalDate, cancelled: Boolean = false): ExpenseStatus {
        if (cancelled) return ExpenseStatus.CANCELADA
        if (balance == Money.ZERO) return ExpenseStatus.PAGA
        if (dueDate < today) return ExpenseStatus.VENCIDA
        return ExpenseStatus.A_VENCER
    }
}
