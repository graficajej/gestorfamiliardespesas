package co.jmessias.gestor_familiar_despesas.domain.expense

import co.jmessias.gestor_familiar_despesas.domain.model.EntityMetadataProvider
import co.jmessias.gestor_familiar_despesas.domain.money.Money
import co.jmessias.gestor_familiar_despesas.domain.obligation.ExpenseStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExpenseUseCasesTest {
    private val repository = FakeExpenseRepository()
    private val metadata = FixedMetadataProvider()

    @Test
    fun `create validates input family membership and references`() = runTest {
        repository.families += "family-1"
        repository.members += "family-1" to "user-1"
        repository.categories += "family-1" to "category-1"
        repository.accounts += "family-1" to "account-1"

        val result = CreateOneTimeExpenseUseCase(repository, metadata).execute(input())

        assertTrue(result.isSuccess)
        val expense = result.getOrThrow()
        assertEquals("Conta de luz", expense.description)
        assertEquals(1250L, expense.amount.cents)
        assertEquals(ExpenseStatus.VENCIDA, expense.status)
        assertEquals("expense-1", expense.id)
        assertEquals("user-1", expense.createdBy)
        assertTrue(CreateOneTimeExpenseUseCase(repository, metadata).execute(input(amount = Money.ZERO)).isFailure)
        assertTrue(CreateOneTimeExpenseUseCase(repository, metadata).execute(input(accountId = "other")).isFailure)
    }

    @Test
    fun `update preserves identity and cancellation retains terminal history`() = runTest {
        repository.families += "family-1"
        repository.members += "family-1" to "user-1"
        repository.categories += "family-1" to "category-1"
        repository.accounts += "family-1" to "account-1"
        val created = CreateOneTimeExpenseUseCase(repository, metadata).execute(input()).getOrThrow()

        val updated = UpdateOneTimeExpenseUseCase(repository, metadata).execute(
            familyId = created.familyId,
            expenseId = created.id,
            updatedBy = "user-1",
            categoryId = null,
            accountId = null,
            description = "Internet",
            amount = Money.ofCents(2000),
            dueDate = LocalDate(2026, 10, 7),
            notes = "  ajustada ",
        ).getOrThrow()
        assertEquals(created.id, updated.id)
        assertEquals("Internet", updated.description)
        assertEquals("ajustada", updated.notes)
        assertEquals(ExpenseStatus.A_VENCER, updated.status)

        assertTrue(CancelOneTimeExpenseUseCase(repository, metadata)
            .execute(updated.familyId, updated.id, "user-1").getOrThrow())
        assertEquals(ExpenseStatus.CANCELADA, repository.expenses.single().status)
        assertFalse(ObserveOneTimeExpensesUseCase(repository).execute("family-1").getOrThrow().first().isEmpty())
    }

    @Test
    fun `expenses with payments cannot be edited or cancelled`() = runTest {
        repository.families += "family-1"
        repository.members += "family-1" to "user-1"
        repository.categories += "family-1" to "category-1"
        repository.accounts += "family-1" to "account-1"
        val expense = CreateOneTimeExpenseUseCase(repository, metadata).execute(input()).getOrThrow()
        repository.paidExpenseIds += expense.id

        assertTrue(
            UpdateOneTimeExpenseUseCase(repository, metadata).execute(
                familyId = expense.familyId,
                expenseId = expense.id,
                updatedBy = "user-1",
                categoryId = null,
                accountId = null,
                description = "Alterada",
                amount = expense.amount,
                dueDate = expense.dueDate,
                notes = null,
            ).isFailure
        )
        assertTrue(CancelOneTimeExpenseUseCase(repository, metadata)
            .execute(expense.familyId, expense.id, "user-1").isFailure)
    }

    private fun input(
        amount: Money = Money.ofCents(1250),
        accountId: String? = "account-1",
    ) = OneTimeExpenseInput(
        familyId = "family-1",
        userId = "user-1",
        categoryId = "category-1",
        accountId = accountId,
        description = "  Conta de luz ",
        amount = amount,
        dueDate = LocalDate(2026, 10, 5),
        notes = "  outubro  ",
    )
}

private class FakeExpenseRepository : ExpenseRepository {
    val families = mutableSetOf<String>()
    val members = mutableSetOf<Pair<String, String>>()
    val categories = mutableSetOf<Pair<String, String>>()
    val accounts = mutableSetOf<Pair<String, String>>()
    val paidExpenseIds = mutableSetOf<String>()
    val expenses = mutableListOf<Expense>()

    override suspend fun familyExists(familyId: String): Boolean = familyId in families

    override suspend fun userBelongsToFamily(familyId: String, userId: String): Boolean =
        familyId to userId in members

    override suspend fun activeCategoryBelongsToFamily(familyId: String, categoryId: String): Boolean =
        familyId to categoryId in categories

    override suspend fun activeAccountBelongsToFamily(familyId: String, accountId: String): Boolean =
        familyId to accountId in accounts

    override fun observeOneTimeExpenses(familyId: String): Flow<List<Expense>> =
        flowOf(expenses.filter { it.familyId == familyId && it.type == ExpenseType.ONE_TIME })

    override suspend fun findOneTimeExpense(familyId: String, expenseId: String): Expense? =
        expenses.firstOrNull { it.familyId == familyId && it.id == expenseId && it.type == ExpenseType.ONE_TIME }

    override suspend fun hasPayments(expenseId: String): Boolean = expenseId in paidExpenseIds

    override suspend fun insert(expense: Expense) {
        expenses += expense
    }

    override suspend fun updateUnpaidOneTimeExpense(expense: Expense): Boolean {
        if (hasPayments(expense.id)) return false
        val index = expenses.indexOfFirst { it.id == expense.id && it.familyId == expense.familyId }
        if (index < 0) return false
        expenses[index] = expense
        return true
    }

    override suspend fun cancelUnpaidOneTimeExpense(
        familyId: String,
        expenseId: String,
        updatedBy: String,
        updatedAt: String,
    ): Boolean {
        if (hasPayments(expenseId)) return false
        val index = expenses.indexOfFirst { it.id == expenseId && it.familyId == familyId }
        if (index < 0) return false
        expenses[index] = expenses[index].copy(
            status = ExpenseStatus.CANCELADA,
            updatedBy = updatedBy,
            updatedAt = updatedAt,
        )
        return true
    }
}

private class FixedMetadataProvider : EntityMetadataProvider {
    private var nextId = 0

    override fun newId(): String = "expense-${++nextId}"

    override fun nowUtc(): String = "2026-10-06T12:00:00Z"

    override fun today(): LocalDate = LocalDate(2026, 10, 6)
}
