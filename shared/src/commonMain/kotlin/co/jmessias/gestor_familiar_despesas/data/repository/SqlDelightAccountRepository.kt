package co.jmessias.gestor_familiar_despesas.data.repository

import co.jmessias.gestor_familiar_despesas.db.AppDatabase
import co.jmessias.gestor_familiar_despesas.domain.account.Account
import co.jmessias.gestor_familiar_despesas.domain.account.AccountRepository
import co.jmessias.gestor_familiar_despesas.domain.account.AccountType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SqlDelightAccountRepository(
    private val database: AppDatabase,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : AccountRepository {
    private val queries = database.appDatabaseQueries

    override suspend fun familyExists(familyId: String): Boolean = withContext(dispatcher) {
        queries.selectFamilyExists(family_id = familyId).executeAsOne()
    }

    override suspend fun listByFamily(familyId: String): List<Account> = withContext(dispatcher) {
        queries.selectAccountsByFamily(family_id = familyId).executeAsList().map { row ->
            Account(
                id = row.id,
                familyId = row.family_id,
                name = row.name,
                type = AccountType.valueOf(row.type),
                institutionName = row.institution_name,
                isActive = row.is_active == 1L,
                createdAt = row.created_at,
                updatedAt = row.updated_at,
            )
        }
    }

    override suspend fun insert(account: Account) = withContext(dispatcher) {
        queries.insertAccount(
            id = account.id,
            family_id = account.familyId,
            name = account.name,
            type = account.type.name,
            institution_name = account.institutionName,
            is_active = if (account.isActive) 1L else 0L,
            created_at = account.createdAt,
            updated_at = account.updatedAt,
        )
        Unit
    }

    override suspend fun deactivate(familyId: String, accountId: String, updatedAt: String): Boolean =
        withContext(dispatcher) {
            queries.deactivateAccount(
                family_id = familyId,
                id = accountId,
                updated_at = updatedAt,
            )
            queries.selectAccountInactive(family_id = familyId, id = accountId).executeAsOne()
        }
}
