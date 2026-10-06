package co.jmessias.gestor_familiar_despesas.domain.account

interface AccountRepository {
    suspend fun familyExists(familyId: String): Boolean
    suspend fun listByFamily(familyId: String): List<Account>
    suspend fun insert(account: Account)
    suspend fun deactivate(familyId: String, accountId: String, updatedAt: String): Boolean
}
