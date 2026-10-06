package co.jmessias.gestor_familiar_despesas.domain.expense

import kotlinx.coroutines.flow.Flow

interface ExpenseRepository {
    suspend fun familyExists(familyId: String): Boolean
    suspend fun userBelongsToFamily(familyId: String, userId: String): Boolean
    suspend fun activeCategoryBelongsToFamily(familyId: String, categoryId: String): Boolean
    suspend fun activeAccountBelongsToFamily(familyId: String, accountId: String): Boolean
    fun observeOneTimeExpenses(familyId: String): Flow<List<Expense>>
    suspend fun findOneTimeExpense(familyId: String, expenseId: String): Expense?
    suspend fun hasPayments(expenseId: String): Boolean
    suspend fun insert(expense: Expense)
    suspend fun updateUnpaidOneTimeExpense(expense: Expense): Boolean
    suspend fun cancelUnpaidOneTimeExpense(familyId: String, expenseId: String, updatedBy: String, updatedAt: String): Boolean
}
