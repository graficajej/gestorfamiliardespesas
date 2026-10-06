package co.jmessias.gestor_familiar_despesas.domain.category

import co.jmessias.gestor_familiar_despesas.domain.model.EntityMetadataProvider

class ListCategoriesUseCase(
    private val repository: CategoryRepository,
) {
    suspend fun execute(familyId: String): Result<List<Category>> {
        if (familyId.isBlank()) return Result.failure(IllegalArgumentException("Family ID cannot be blank"))
        if (!repository.familyExists(familyId)) return Result.failure(NoSuchElementException("Family not found"))
        return Result.success(repository.listByFamily(familyId))
    }
}

class CreateCategoryUseCase(
    private val repository: CategoryRepository,
    private val metadataProvider: EntityMetadataProvider,
) {
    suspend fun execute(familyId: String, name: String, icon: String? = null): Result<Category> {
        val normalizedName = name.trim()
        if (familyId.isBlank()) return Result.failure(IllegalArgumentException("Family ID cannot be blank"))
        if (normalizedName.isEmpty()) return Result.failure(IllegalArgumentException("Category name cannot be blank"))
        if (!repository.familyExists(familyId)) return Result.failure(NoSuchElementException("Family not found"))
        if (repository.findByName(familyId, normalizedName) != null) {
            return Result.failure(IllegalArgumentException("Category name already exists in this family"))
        }

        val now = metadataProvider.nowUtc()
        val category = Category(
            id = metadataProvider.newId(),
            familyId = familyId,
            name = normalizedName,
            icon = icon?.trim()?.takeIf(String::isNotEmpty),
            isActive = true,
            createdAt = now,
            updatedAt = now,
        )
        if (!repository.insert(category)) {
            return Result.failure(IllegalArgumentException("Category name already exists in this family"))
        }
        return Result.success(category)
    }
}

class DeactivateCategoryUseCase(
    private val repository: CategoryRepository,
    private val metadataProvider: EntityMetadataProvider,
) {
    suspend fun execute(familyId: String, categoryId: String): Result<Boolean> {
        if (familyId.isBlank() || categoryId.isBlank()) {
            return Result.failure(IllegalArgumentException("Family and category IDs cannot be blank"))
        }
        if (!repository.familyExists(familyId)) return Result.failure(NoSuchElementException("Family not found"))
        return Result.success(repository.deactivate(familyId, categoryId, metadataProvider.nowUtc()))
    }
}
