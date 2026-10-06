package co.jmessias.gestor_familiar_despesas.domain.expense

import co.jmessias.gestor_familiar_despesas.domain.model.EntityMetadataProvider
import co.jmessias.gestor_familiar_despesas.domain.money.Money
import co.jmessias.gestor_familiar_despesas.domain.obligation.ExpenseStatus
import co.jmessias.gestor_familiar_despesas.domain.obligation.ExpenseStatusResolver
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

data class OneTimeExpenseInput(
    val familyId: String,
    val userId: String,
    val categoryId: String?,
    val accountId: String?,
    val description: String,
    val amount: Money,
    val dueDate: LocalDate,
    val notes: String?,
)

class ObserveOneTimeExpensesUseCase(
    private val repository: ExpenseRepository,
) {
    suspend fun execute(familyId: String): Result<Flow<List<Expense>>> {
        if (familyId.isBlank()) return Result.failure(IllegalArgumentException("Family ID cannot be blank"))
        if (!repository.familyExists(familyId)) return Result.failure(NoSuchElementException("Family not found"))
        return Result.success(repository.observeOneTimeExpenses(familyId))
    }
}

class CreateOneTimeExpenseUseCase(
    private val repository: ExpenseRepository,
    private val metadataProvider: EntityMetadataProvider,
) {
    suspend fun execute(input: OneTimeExpenseInput): Result<Expense> {
        val description = input.description.trim()
        if (input.familyId.isBlank() || input.userId.isBlank()) {
            return Result.failure(IllegalArgumentException("Family and user IDs are required"))
        }
        if (description.isEmpty()) return Result.failure(IllegalArgumentException("Expense description cannot be blank"))
        if (input.amount <= Money.ZERO) return Result.failure(IllegalArgumentException("Expense amount must be positive"))
        if (!repository.familyExists(input.familyId)) return Result.failure(NoSuchElementException("Family not found"))
        if (!repository.userBelongsToFamily(input.familyId, input.userId)) {
            return Result.failure(IllegalArgumentException("User does not belong to the active family"))
        }
        if (input.categoryId != null &&
            !repository.activeCategoryBelongsToFamily(input.familyId, input.categoryId)
        ) {
            return Result.failure(IllegalArgumentException("Category must be active and belong to the active family"))
        }
        if (input.accountId != null &&
            !repository.activeAccountBelongsToFamily(input.familyId, input.accountId)
        ) {
            return Result.failure(IllegalArgumentException("Account must be active and belong to the active family"))
        }

        val now = metadataProvider.nowUtc()
        val expense = Expense(
            id = metadataProvider.newId(),
            familyId = input.familyId,
            categoryId = input.categoryId,
            accountId = input.accountId,
            description = description,
            amount = input.amount,
            dueDate = input.dueDate,
            type = ExpenseType.ONE_TIME,
            status = ExpenseStatusResolver.resolve(input.amount, input.dueDate, metadataProvider.today()),
            notes = input.notes?.trim()?.takeIf(String::isNotEmpty),
            recurrenceId = null,
            createdBy = input.userId,
            updatedBy = input.userId,
            createdAt = now,
            updatedAt = now,
            deletedAt = null,
        )
        repository.insert(expense)
        return Result.success(expense)
    }
}

class UpdateOneTimeExpenseUseCase(
    private val repository: ExpenseRepository,
    private val metadataProvider: EntityMetadataProvider,
) {
    suspend fun execute(
        familyId: String,
        expenseId: String,
        updatedBy: String,
        categoryId: String?,
        accountId: String?,
        description: String,
        amount: Money,
        dueDate: LocalDate,
        notes: String?,
    ): Result<Expense> {
        val normalizedDescription = description.trim()
        if (familyId.isBlank() || expenseId.isBlank() || updatedBy.isBlank()) {
            return Result.failure(IllegalArgumentException("Family, expense, and user IDs are required"))
        }
        if (normalizedDescription.isEmpty()) {
            return Result.failure(IllegalArgumentException("Expense description cannot be blank"))
        }
        if (amount <= Money.ZERO) return Result.failure(IllegalArgumentException("Expense amount must be positive"))
        if (!repository.familyExists(familyId)) return Result.failure(NoSuchElementException("Family not found"))
        if (!repository.userBelongsToFamily(familyId, updatedBy)) {
            return Result.failure(IllegalArgumentException("User does not belong to the active family"))
        }
        val existing = repository.findOneTimeExpense(familyId, expenseId)
            ?: return Result.failure(NoSuchElementException("One-time expense not found"))
        if (existing.status == ExpenseStatus.CANCELADA || existing.status == ExpenseStatus.PAGA) {
            return Result.failure(IllegalStateException("Paid or cancelled expense cannot be edited"))
        }
        if (repository.hasPayments(expenseId)) {
            return Result.failure(IllegalStateException("Expense with payments cannot be edited"))
        }
        if (categoryId != null && !repository.activeCategoryBelongsToFamily(familyId, categoryId)) {
            return Result.failure(IllegalArgumentException("Category must be active and belong to the active family"))
        }
        if (accountId != null && !repository.activeAccountBelongsToFamily(familyId, accountId)) {
            return Result.failure(IllegalArgumentException("Account must be active and belong to the active family"))
        }

        val updated = existing.copy(
            categoryId = categoryId,
            accountId = accountId,
            description = normalizedDescription,
            amount = amount,
            dueDate = dueDate,
            status = ExpenseStatusResolver.resolve(amount, dueDate, metadataProvider.today()),
            notes = notes?.trim()?.takeIf(String::isNotEmpty),
            updatedBy = updatedBy,
            updatedAt = metadataProvider.nowUtc(),
        )
        if (!repository.updateUnpaidOneTimeExpense(updated)) {
            return Result.failure(IllegalStateException("Expense could not be updated; it may have payments or be terminal"))
        }
        return Result.success(updated)
    }
}

class CancelOneTimeExpenseUseCase(
    private val repository: ExpenseRepository,
    private val metadataProvider: EntityMetadataProvider,
) {
    suspend fun execute(familyId: String, expenseId: String, updatedBy: String): Result<Boolean> {
        if (familyId.isBlank() || expenseId.isBlank() || updatedBy.isBlank()) {
            return Result.failure(IllegalArgumentException("Family, expense, and user IDs are required"))
        }
        if (!repository.familyExists(familyId)) return Result.failure(NoSuchElementException("Family not found"))
        if (!repository.userBelongsToFamily(familyId, updatedBy)) {
            return Result.failure(IllegalArgumentException("User does not belong to the active family"))
        }
        val expense = repository.findOneTimeExpense(familyId, expenseId)
            ?: return Result.failure(NoSuchElementException("One-time expense not found"))
        if (expense.status == ExpenseStatus.PAGA || expense.status == ExpenseStatus.CANCELADA) {
            return Result.failure(IllegalStateException("Paid or cancelled expense cannot be cancelled"))
        }
        if (repository.hasPayments(expenseId)) {
            return Result.failure(IllegalStateException("Expense with payments cannot be cancelled"))
        }
        val cancelled = repository.cancelUnpaidOneTimeExpense(
            familyId = familyId,
            expenseId = expenseId,
            updatedBy = updatedBy,
            updatedAt = metadataProvider.nowUtc(),
        )
        return if (cancelled) Result.success(true)
        else Result.failure(IllegalStateException("Expense could not be cancelled; it may have payments or be terminal"))
    }
}
