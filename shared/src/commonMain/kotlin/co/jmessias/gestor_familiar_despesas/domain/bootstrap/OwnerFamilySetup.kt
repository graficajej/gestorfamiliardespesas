package co.jmessias.gestor_familiar_despesas.domain.bootstrap

import co.jmessias.gestor_familiar_despesas.domain.model.EntityMetadataProvider

data class OwnerFamilySeed(
    val ownerName: String,
    val familyName: String,
)

data class OwnerFamilySetup(
    val userId: String,
    val familyId: String,
    val ownerName: String,
    val familyName: String,
)

interface FamilyBootstrapRepository {
    suspend fun loadOwnerFamily(): OwnerFamilySetup?
    suspend fun saveOwnerFamily(setup: OwnerFamilySetup)
}

class LoadOwnerFamilyUseCase(
    private val repository: FamilyBootstrapRepository,
) {
    suspend fun execute(): Result<OwnerFamilySetup?> = Result.success(repository.loadOwnerFamily())
}

class CreateOwnerFamilyUseCase(
    private val repository: FamilyBootstrapRepository,
    private val metadataProvider: EntityMetadataProvider,
) {
    suspend fun execute(seed: OwnerFamilySeed): Result<OwnerFamilySetup> {
        val normalizedOwnerName = seed.ownerName.trim()
        val normalizedFamilyName = seed.familyName.trim()

        if (normalizedOwnerName.isEmpty()) {
            return Result.failure(IllegalArgumentException("Owner name cannot be blank"))
        }
        if (normalizedFamilyName.isEmpty()) {
            return Result.failure(IllegalArgumentException("Family name cannot be blank"))
        }

        val setup = OwnerFamilySetup(
                userId = metadataProvider.newId(),
                familyId = metadataProvider.newId(),
                ownerName = normalizedOwnerName,
                familyName = normalizedFamilyName,
            )
        repository.saveOwnerFamily(setup)
        return Result.success(setup)
    }
}
