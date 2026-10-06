package co.jmessias.gestor_familiar_despesas.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import co.jmessias.gestor_familiar_despesas.db.AppDatabase
import co.jmessias.gestor_familiar_despesas.domain.expense.Expense
import co.jmessias.gestor_familiar_despesas.domain.expense.ExpenseType
import co.jmessias.gestor_familiar_despesas.domain.money.Money
import co.jmessias.gestor_familiar_despesas.domain.obligation.ExpenseStatus
import co.jmessias.gestor_familiar_despesas.domain.recurrence.Recurrence
import co.jmessias.gestor_familiar_despesas.domain.recurrence.RecurrenceFrequency
import co.jmessias.gestor_familiar_despesas.domain.recurrence.RecurrenceRepository
import co.jmessias.gestor_familiar_despesas.domain.recurrence.RecurrenceWithOccurrences
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate

class SqlDelightRecurrenceRepository(
    private val database: AppDatabase,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : RecurrenceRepository {
    private val queries = database.appDatabaseQueries

    override suspend fun userBelongsToFamily(familyId: String, userId: String): Boolean =
        withContext(dispatcher) {
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

    override suspend fun findActive(familyId: String, recurrenceId: String): Recurrence? =
        withContext(dispatcher) {
            queries.selectActiveRecurrencesByFamily(family_id = familyId)
                .executeAsList()
                .firstOrNull { it.id == recurrenceId }
                ?.toDomain()
        }

    override suspend fun insert(recurrence: Recurrence) = withContext(dispatcher) {
        database.transaction {
            validateFamilyReferences(recurrence)
            queries.insertRecurrence(
                id = recurrence.id,
                family_id = recurrence.familyId,
                description = recurrence.description,
                amount_in_cents = recurrence.amount.cents,
                category_id = recurrence.categoryId,
                account_id = recurrence.accountId,
                frequency = recurrence.frequency.name,
                start_date = recurrence.startDate.toString(),
                due_day = recurrence.dueDay?.toLong(),
                created_by = recurrence.createdBy,
                created_at = recurrence.createdAt,
                updated_at = recurrence.updatedAt,
            )
        }
        Unit
    }

    override suspend fun updateActive(recurrence: Recurrence): Boolean = withContext(dispatcher) {
        var updated = false
        database.transaction {
            val isActive = queries.selectActiveRecurrence(
                family_id = recurrence.familyId,
                id = recurrence.id,
            ).executeAsOneOrNull() != null
            if (isActive) {
                validateFamilyReferences(recurrence)
                queries.updateActiveRecurrence(
                    description = recurrence.description,
                    amount_in_cents = recurrence.amount.cents,
                    category_id = recurrence.categoryId,
                    account_id = recurrence.accountId,
                    frequency = recurrence.frequency.name,
                    start_date = recurrence.startDate.toString(),
                    due_day = recurrence.dueDay?.toLong(),
                    updated_at = recurrence.updatedAt,
                    family_id = recurrence.familyId,
                    id = recurrence.id,
                )
                updated = true
            }
        }
        updated
    }

    override suspend fun cancelActive(familyId: String, recurrenceId: String, updatedAt: String): Boolean =
        withContext(dispatcher) {
            var cancelled = false
            database.transaction {
                val wasActive = queries.selectActiveRecurrence(
                    family_id = familyId,
                    id = recurrenceId,
                ).executeAsOneOrNull() != null
                queries.cancelActiveRecurrence(
                    updated_at = updatedAt,
                    family_id = familyId,
                    id = recurrenceId,
                )
                cancelled = wasActive && queries.selectActiveRecurrence(
                    family_id = familyId,
                    id = recurrenceId,
                ).executeAsOneOrNull() == null
            }
            cancelled
        }

    override fun observeRecurrences(familyId: String): Flow<List<RecurrenceWithOccurrences>> {
        val recurrenceFlow = queries.selectRecurrencesByFamily(family_id = familyId)
            .asFlow()
            .mapToList(dispatcher)
            .map { rows -> rows.map { it.toDomain() } }
        val expenseFlow = queries.selectRecurrenceExpensesByFamily(family_id = familyId)
            .asFlow()
            .mapToList(dispatcher)
            .map { rows -> rows.map(::toExpense) }
        return combine(recurrenceFlow, expenseFlow) { recurrences, expenses ->
            recurrences.map { recurrence ->
                RecurrenceWithOccurrences(
                    recurrence = recurrence,
                    occurrences = expenses.filter { it.recurrenceId == recurrence.id },
                )
            }
        }
    }

    override suspend fun listActive(familyId: String): List<Recurrence> = withContext(dispatcher) {
        queries.selectActiveRecurrencesByFamily(family_id = familyId)
            .executeAsList()
            .map { it.toDomain() }
    }

    override suspend fun insertGeneratedOccurrences(occurrences: List<Expense>): Int = withContext(dispatcher) {
        var inserted = 0
        database.transaction {
            occurrences.forEach { expense ->
                val recurrenceId = requireNotNull(expense.recurrenceId)
                val exists = queries.selectRecurrenceOccurrenceExists(
                    recurrence_id = recurrenceId,
                    due_date = expense.dueDate.toString(),
                ).executeAsOne()
                if (!exists) {
                    queries.insertGeneratedRecurrenceExpense(
                        id = expense.id,
                        recurrence_id = recurrenceId,
                        family_id = expense.familyId,
                        due_date = expense.dueDate.toString(),
                        status = expense.status.name,
                        created_at = expense.createdAt,
                        updated_at = expense.updatedAt,
                    )
                    val insertedForDate = queries.selectRecurrenceOccurrenceExists(
                        recurrence_id = recurrenceId,
                        due_date = expense.dueDate.toString(),
                    ).executeAsOne()
                    if (insertedForDate) inserted++
                }
            }
        }
        inserted
    }

    private fun validateFamilyReferences(recurrence: Recurrence) {
        require(
            queries.selectUserBelongsToFamily(
                family_id = recurrence.familyId,
                user_id = recurrence.createdBy,
            ).executeAsOne()
        ) { "Usuário não pertence à família selecionada." }
        if (recurrence.categoryId != null) {
            require(
                queries.selectActiveCategoryBelongsToFamily(
                    family_id = recurrence.familyId,
                    category_id = recurrence.categoryId,
                ).executeAsOne()
            ) { "A categoria deve estar ativa e pertencer à família selecionada." }
        }
        if (recurrence.accountId != null) {
            require(
                queries.selectActiveAccountBelongsToFamily(
                    family_id = recurrence.familyId,
                    account_id = recurrence.accountId,
                ).executeAsOne()
            ) { "A conta deve estar ativa e pertencer à família selecionada." }
        }
    }

    private fun co.jmessias.gestorfamiliardespesas.db.SelectRecurrencesByFamily.toDomain() =
        Recurrence(
            id = id,
            familyId = family_id,
            description = description,
            amount = Money.ofCents(amount_in_cents),
            categoryId = category_id,
            accountId = account_id,
            frequency = RecurrenceFrequency.valueOf(frequency),
            startDate = LocalDate.parse(start_date),
            dueDay = due_day?.toInt(),
            isActive = is_active == 1L,
            createdBy = created_by,
            createdAt = created_at,
            updatedAt = updated_at,
        )

    private fun co.jmessias.gestorfamiliardespesas.db.SelectActiveRecurrencesByFamily.toDomain() =
        Recurrence(
            id = id,
            familyId = family_id,
            description = description,
            amount = Money.ofCents(amount_in_cents),
            categoryId = category_id,
            accountId = account_id,
            frequency = RecurrenceFrequency.valueOf(frequency),
            startDate = LocalDate.parse(start_date),
            dueDay = due_day?.toInt(),
            isActive = is_active == 1L,
            createdBy = created_by,
            createdAt = created_at,
            updatedAt = updated_at,
        )

    private fun toExpense(row: co.jmessias.gestorfamiliardespesas.db.SelectRecurrenceExpensesByFamily) =
        Expense(
            id = row.id,
            familyId = row.family_id,
            categoryId = row.category_id,
            accountId = row.account_id,
            description = row.description,
            amount = Money.ofCents(row.amount_in_cents),
            dueDate = LocalDate.parse(row.due_date),
            type = ExpenseType.valueOf(row.type),
            status = ExpenseStatus.valueOf(row.status),
            notes = row.notes,
            recurrenceId = row.recurrence_id,
            createdBy = row.created_by,
            updatedBy = row.updated_by,
            createdAt = row.created_at,
            updatedAt = row.updated_at,
            deletedAt = row.deleted_at,
        )
}
