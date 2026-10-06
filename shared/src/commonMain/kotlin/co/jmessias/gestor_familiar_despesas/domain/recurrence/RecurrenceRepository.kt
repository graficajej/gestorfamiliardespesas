package co.jmessias.gestor_familiar_despesas.domain.recurrence

import co.jmessias.gestor_familiar_despesas.domain.expense.Expense
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

interface RecurrenceRepository {
    suspend fun userBelongsToFamily(familyId: String, userId: String): Boolean
    suspend fun activeCategoryBelongsToFamily(familyId: String, categoryId: String): Boolean
    suspend fun activeAccountBelongsToFamily(familyId: String, accountId: String): Boolean
    suspend fun findActive(familyId: String, recurrenceId: String): Recurrence?
    suspend fun insert(recurrence: Recurrence)
    suspend fun updateActive(recurrence: Recurrence): Boolean
    suspend fun cancelActive(familyId: String, recurrenceId: String, updatedAt: String): Boolean
    fun observeRecurrences(familyId: String): Flow<List<RecurrenceWithOccurrences>>
    suspend fun listActive(familyId: String): List<Recurrence>
    suspend fun insertGeneratedOccurrences(occurrences: List<Expense>): Int
}
