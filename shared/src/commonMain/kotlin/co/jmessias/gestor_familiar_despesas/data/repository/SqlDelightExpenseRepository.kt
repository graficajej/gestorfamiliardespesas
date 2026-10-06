package co.jmessias.gestor_familiar_despesas.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import co.jmessias.gestor_familiar_despesas.db.AppDatabase
import co.jmessias.gestor_familiar_despesas.domain.expense.Expense
import co.jmessias.gestor_familiar_despesas.domain.expense.ExpenseRepository
import co.jmessias.gestor_familiar_despesas.domain.expense.ExpenseType
import co.jmessias.gestor_familiar_despesas.domain.money.Money
import co.jmessias.gestor_familiar_despesas.domain.obligation.ExpenseStatus
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate

class SqlDelightExpenseRepository(
    private val database: AppDatabase,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ExpenseRepository {
    private val queries = database.appDatabaseQueries

    override suspend fun familyExists(familyId: String): Boolean = withContext(dispatcher) {
        queries.selectFamilyExists(family_id = familyId).executeAsOne()
    }

    override suspend fun userBelongsToFamily(familyId: String, userId: String): Boolean = withContext(dispatcher) {
        queries.selectUserBelongsToFamily(family_id = familyId, user_id = userId).executeAsOne()
    }

    override suspend fun activeCategoryBelongsToFamily(familyId: String, categoryId: String): Boolean =
        withContext(dispatcher) {
            queries.selectActiveCategoryBelongsToFamily(
                family_id = familyId,
                category_id = categoryId,
            ).executeAsOne()
        }

    override suspend fun activeAccountBelongsToFamily(familyId: String, accountId: String): Boolean =
        withContext(dispatcher) {
            queries.selectActiveAccountBelongsToFamily(
                family_id = familyId,
                account_id = accountId,
            ).executeAsOne()
        }

    override fun observeOneTimeExpenses(familyId: String): Flow<List<Expense>> =
        queries.selectOneTimeExpensesByFamily(family_id = familyId)
            .asFlow()
            .mapToList(dispatcher)
            .map { rows -> rows.map(::toDomain) }

    override suspend fun findOneTimeExpense(familyId: String, expenseId: String): Expense? =
        withContext(dispatcher) {
            queries.selectOneTimeExpense(family_id = familyId, id = expenseId)
                .executeAsOneOrNull()
                ?.let(::toDomain)
        }

    override suspend fun hasPayments(expenseId: String): Boolean = withContext(dispatcher) {
        queries.selectExpenseHasPayments(expense_id = expenseId).executeAsOne()
    }

    override suspend fun insert(expense: Expense) = withContext(dispatcher) {
        queries.insertOneTimeExpense(
            id = expense.id,
            family_id = expense.familyId,
            category_id = expense.categoryId,
            account_id = expense.accountId,
            description = expense.description,
            amount_in_cents = expense.amount.cents,
            due_date = expense.dueDate.toString(),
            status = expense.status.name,
            notes = expense.notes,
            created_by = expense.createdBy,
            updated_by = expense.updatedBy,
            created_at = expense.createdAt,
            updated_at = expense.updatedAt,
        )
        Unit
    }

    override suspend fun updateUnpaidOneTimeExpense(expense: Expense): Boolean = withContext(dispatcher) {
        queries.updateOneTimeExpense(
            category_id = expense.categoryId,
            account_id = expense.accountId,
            description = expense.description,
            amount_in_cents = expense.amount.cents,
            due_date = expense.dueDate.toString(),
            status = expense.status.name,
            notes = expense.notes,
            updated_by = expense.updatedBy,
            updated_at = expense.updatedAt,
            family_id = expense.familyId,
            id = expense.id,
        )
        queries.selectOneTimeExpense(family_id = expense.familyId, id = expense.id)
            .executeAsOneOrNull()
            ?.let { row -> row.updated_at == expense.updatedAt && row.description == expense.description } == true
    }

    override suspend fun cancelUnpaidOneTimeExpense(
        familyId: String,
        expenseId: String,
        updatedBy: String,
        updatedAt: String,
    ): Boolean = withContext(dispatcher) {
        queries.cancelOneTimeExpense(
            updated_by = updatedBy,
            updated_at = updatedAt,
            family_id = familyId,
            id = expenseId,
        )
        queries.selectOneTimeExpense(family_id = familyId, id = expenseId)
            .executeAsOneOrNull()
            ?.status == ExpenseStatus.CANCELADA.name
    }

    private fun toDomain(row: co.jmessias.gestorfamiliardespesas.db.SelectOneTimeExpensesByFamily): Expense =
        toDomain(
            id = row.id,
            familyId = row.family_id,
            categoryId = row.category_id,
            accountId = row.account_id,
            description = row.description,
            amountInCents = row.amount_in_cents,
            dueDate = row.due_date,
            type = row.type,
            status = row.status,
            notes = row.notes,
            recurrenceId = row.recurrence_id,
            createdBy = row.created_by,
            updatedBy = row.updated_by,
            createdAt = row.created_at,
            updatedAt = row.updated_at,
            deletedAt = row.deleted_at,
        )

    private fun toDomain(row: co.jmessias.gestorfamiliardespesas.db.SelectOneTimeExpense): Expense =
        toDomain(
            id = row.id,
            familyId = row.family_id,
            categoryId = row.category_id,
            accountId = row.account_id,
            description = row.description,
            amountInCents = row.amount_in_cents,
            dueDate = row.due_date,
            type = row.type,
            status = row.status,
            notes = row.notes,
            recurrenceId = row.recurrence_id,
            createdBy = row.created_by,
            updatedBy = row.updated_by,
            createdAt = row.created_at,
            updatedAt = row.updated_at,
            deletedAt = row.deleted_at,
        )

    private fun toDomain(
        id: String,
        familyId: String,
        categoryId: String?,
        accountId: String?,
        description: String,
        amountInCents: Long,
        dueDate: String,
        type: String,
        status: String,
        notes: String?,
        recurrenceId: String?,
        createdBy: String,
        updatedBy: String,
        createdAt: String,
        updatedAt: String,
        deletedAt: String?,
    ): Expense = Expense(
        id = id,
        familyId = familyId,
        categoryId = categoryId,
        accountId = accountId,
        description = description,
        amount = Money.ofCents(amountInCents),
        dueDate = LocalDate.parse(dueDate),
        type = ExpenseType.valueOf(type),
        status = ExpenseStatus.valueOf(status),
        notes = notes,
        recurrenceId = recurrenceId,
        createdBy = createdBy,
        updatedBy = updatedBy,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
    )
}
