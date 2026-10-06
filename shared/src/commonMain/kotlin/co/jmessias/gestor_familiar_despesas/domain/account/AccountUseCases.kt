package co.jmessias.gestor_familiar_despesas.domain.account

import co.jmessias.gestor_familiar_despesas.domain.model.EntityMetadataProvider

class ListAccountsUseCase(
    private val repository: AccountRepository,
) {
    suspend fun execute(familyId: String): Result<List<Account>> {
        if (familyId.isBlank()) return Result.failure(IllegalArgumentException("Family ID cannot be blank"))
        if (!repository.familyExists(familyId)) return Result.failure(NoSuchElementException("Family not found"))
        return Result.success(repository.listByFamily(familyId))
    }
}

class CreateAccountUseCase(
    private val repository: AccountRepository,
    private val metadataProvider: EntityMetadataProvider,
) {
    suspend fun execute(
        familyId: String,
        name: String,
        type: AccountType,
        institutionName: String? = null,
    ): Result<Account> {
        val normalizedName = name.trim()
        if (familyId.isBlank()) return Result.failure(IllegalArgumentException("Family ID cannot be blank"))
        if (normalizedName.isEmpty()) return Result.failure(IllegalArgumentException("Account name cannot be blank"))
        if (!repository.familyExists(familyId)) return Result.failure(NoSuchElementException("Family not found"))

        val now = metadataProvider.nowUtc()
        val account = Account(
            id = metadataProvider.newId(),
            familyId = familyId,
            name = normalizedName,
            type = type,
            institutionName = institutionName?.trim()?.takeIf(String::isNotEmpty),
            isActive = true,
            createdAt = now,
            updatedAt = now,
        )
        repository.insert(account)
        return Result.success(account)
    }
}

class DeactivateAccountUseCase(
    private val repository: AccountRepository,
    private val metadataProvider: EntityMetadataProvider,
) {
    suspend fun execute(familyId: String, accountId: String): Result<Boolean> {
        if (familyId.isBlank() || accountId.isBlank()) {
            return Result.failure(IllegalArgumentException("Family and account IDs cannot be blank"))
        }
        if (!repository.familyExists(familyId)) return Result.failure(NoSuchElementException("Family not found"))
        return Result.success(repository.deactivate(familyId, accountId, metadataProvider.nowUtc()))
    }
}
