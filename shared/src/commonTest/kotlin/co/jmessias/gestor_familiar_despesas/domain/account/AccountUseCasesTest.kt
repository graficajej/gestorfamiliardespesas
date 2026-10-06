package co.jmessias.gestor_familiar_despesas.domain.account

import co.jmessias.gestor_familiar_despesas.domain.model.EntityMetadataProvider
import kotlinx.datetime.LocalDate
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AccountUseCasesTest {
    private val repository = InMemoryAccountRepository()
    private val metadata = FixedAccountMetadataProvider()

    @Test
    fun `create normalizes names and preserves selected account type`() = runTest {
        repository.families += "family-1"

        val result = CreateAccountUseCase(repository, metadata)
            .execute("family-1", "  Conta principal ", AccountType.BANK, "  Banco X ")

        assertTrue(result.isSuccess)
        val account = result.getOrThrow()
        assertEquals("Conta principal", account.name)
        assertEquals(AccountType.BANK, account.type)
        assertEquals("Banco X", account.institutionName)
        assertTrue(account.isActive)
    }

    @Test
    fun `create rejects blank names and unknown families`() = runTest {
        val useCase = CreateAccountUseCase(repository, metadata)
        assertTrue(useCase.execute("family-1", " ", AccountType.CASH).isFailure)
        assertTrue(useCase.execute("missing", "Carteira", AccountType.CASH).isFailure)
    }

    @Test
    fun `deactivation preserves the account in family listing`() = runTest {
        repository.families += "family-1"
        val account = CreateAccountUseCase(repository, metadata)
            .execute("family-1", "Dinheiro", AccountType.CASH).getOrThrow()

        assertTrue(DeactivateAccountUseCase(repository, metadata).execute("family-1", account.id).getOrThrow())
        assertFalse(ListAccountsUseCase(repository).execute("family-1").getOrThrow().single().isActive)
    }
}

private class InMemoryAccountRepository : AccountRepository {
    val families = mutableSetOf<String>()
    private val accounts = mutableListOf<Account>()

    override suspend fun familyExists(familyId: String): Boolean = familyId in families

    override suspend fun listByFamily(familyId: String): List<Account> =
        accounts.filter { it.familyId == familyId }.sortedBy { it.name }

    override suspend fun insert(account: Account) {
        accounts += account
    }

    override suspend fun deactivate(familyId: String, accountId: String, updatedAt: String): Boolean {
        val index = accounts.indexOfFirst { it.familyId == familyId && it.id == accountId && it.isActive }
        if (index < 0) return false
        accounts[index] = accounts[index].copy(isActive = false, updatedAt = updatedAt)
        return true
    }
}

private class FixedAccountMetadataProvider : EntityMetadataProvider {
    private var nextId = 0

    override fun newId(): String = "account-${++nextId}"

    override fun nowUtc(): String = "2026-10-06T12:00:00Z"

    override fun today(): LocalDate = LocalDate(2026, 10, 6)
}
