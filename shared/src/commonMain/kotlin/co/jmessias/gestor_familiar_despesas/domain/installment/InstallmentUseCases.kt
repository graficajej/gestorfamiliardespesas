package co.jmessias.gestor_familiar_despesas.domain.installment

import co.jmessias.gestor_familiar_despesas.domain.expense.Expense
import co.jmessias.gestor_familiar_despesas.domain.expense.ExpenseType
import co.jmessias.gestor_familiar_despesas.domain.model.EntityMetadataProvider
import co.jmessias.gestor_familiar_despesas.domain.money.Money
import co.jmessias.gestor_familiar_despesas.domain.obligation.ExpenseStatusResolver
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

data class CreateInstallmentPlanInput(
    val familyId: String,
    val userId: String,
    val description: String,
    val totalAmount: Money,
    val installmentCount: Int,
    val firstDueDate: LocalDate,
    val categoryId: String?,
    val accountId: String?,
)

class CreateInstallmentPlanUseCase(
    private val repository: InstallmentRepository,
    private val metadataProvider: EntityMetadataProvider,
) {
    suspend fun execute(input: CreateInstallmentPlanInput): Result<InstallmentPlanWithInstallments> {
        val description = input.description.trim()
        if (input.familyId.isBlank() || input.userId.isBlank()) {
            return Result.failure(IllegalArgumentException("Família e usuário são obrigatórios."))
        }
        if (description.isEmpty()) {
            return Result.failure(IllegalArgumentException("Informe a descrição do parcelamento."))
        }
        if (input.totalAmount <= Money.ZERO) {
            return Result.failure(IllegalArgumentException("O valor total deve ser maior que zero."))
        }
        if (input.installmentCount <= 0 || input.installmentCount.toLong() > input.totalAmount.cents) {
            return Result.failure(IllegalArgumentException("A quantidade de parcelas deve permitir ao menos um centavo em cada parcela."))
        }
        if (!repository.userBelongsToFamily(input.familyId, input.userId)) {
            return Result.failure(IllegalArgumentException("Usuário não pertence à família selecionada."))
        }
        if (input.categoryId != null &&
            !repository.activeCategoryBelongsToFamily(input.familyId, input.categoryId)
        ) {
            return Result.failure(IllegalArgumentException("A categoria deve estar ativa e pertencer à família selecionada."))
        }
        if (input.accountId != null &&
            !repository.activeAccountBelongsToFamily(input.familyId, input.accountId)
        ) {
            return Result.failure(IllegalArgumentException("A conta deve estar ativa e pertencer à família selecionada."))
        }

        val now = metadataProvider.nowUtc()
        val plan = InstallmentPlan(
            id = metadataProvider.newId(),
            familyId = input.familyId,
            description = description,
            totalAmount = input.totalAmount,
            installmentCount = input.installmentCount,
            firstDueDate = input.firstDueDate,
            categoryId = input.categoryId,
            accountId = input.accountId,
            createdBy = input.userId,
            createdAt = now,
            updatedAt = now,
        )
        val baseAmount = input.totalAmount.cents / input.installmentCount
        val remainder = input.totalAmount.cents % input.installmentCount
        val today = metadataProvider.today()
        val installments = (1..input.installmentCount).map { number ->
            val installmentAmount = baseAmount + if (number <= remainder) 1 else 0
            val dueDate = monthlyDueDate(input.firstDueDate, number - 1)
            val expenseId = metadataProvider.newId()
            val expense = Expense(
                id = expenseId,
                familyId = input.familyId,
                categoryId = input.categoryId,
                accountId = input.accountId,
                description = "$description ($number/${input.installmentCount})",
                amount = Money.ofCents(installmentAmount),
                dueDate = dueDate,
                type = ExpenseType.INSTALLMENT,
                status = ExpenseStatusResolver.resolve(
                    Money.ofCents(installmentAmount),
                    dueDate,
                    today,
                ),
                notes = null,
                recurrenceId = null,
                createdBy = input.userId,
                updatedBy = input.userId,
                createdAt = now,
                updatedAt = now,
                deletedAt = null,
            )
            Installment(
                id = metadataProvider.newId(),
                planId = plan.id,
                expense = expense,
                number = number,
                totalInstallments = input.installmentCount,
                createdAt = now,
                updatedAt = now,
            )
        }
        if (installments.sumOf { it.expense.amount.cents } != input.totalAmount.cents) {
            return Result.failure(IllegalStateException("A soma das parcelas não corresponde ao valor total."))
        }

        repository.createPlan(plan, installments)
        return Result.success(InstallmentPlanWithInstallments(plan, installments))
    }
}

class ObserveInstallmentPlansUseCase(
    private val repository: InstallmentRepository,
) {
    fun execute(familyId: String): Flow<List<InstallmentPlanWithInstallments>> =
        repository.observePlans(familyId)
}

private fun monthlyDueDate(firstDueDate: LocalDate, monthOffset: Int): LocalDate {
    val monthIndex = firstDueDate.year.toLong() * 12L + firstDueDate.month.ordinal + monthOffset
    val year = (monthIndex / 12L).toInt()
    val monthNumber = (monthIndex % 12L).toInt() + 1
    val isLeapYear = year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)
    val daysInMonth = when (monthNumber) {
        2 -> if (isLeapYear) 29 else 28
        4, 6, 9, 11 -> 30
        else -> 31
    }
    return LocalDate(year, monthNumber, minOf(firstDueDate.day, daysInMonth))
}
