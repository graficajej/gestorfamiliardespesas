package co.jmessias.gestor_familiar_despesas.data.repository

import co.jmessias.gestor_familiar_despesas.db.AppDatabase
import co.jmessias.gestor_familiar_despesas.domain.category.Category
import co.jmessias.gestor_familiar_despesas.domain.category.CategoryRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SqlDelightCategoryRepository(
    private val database: AppDatabase,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : CategoryRepository {
    private val queries = database.appDatabaseQueries

    override suspend fun familyExists(familyId: String): Boolean = withContext(dispatcher) {
        queries.selectFamilyExists(family_id = familyId).executeAsOne()
    }

    override suspend fun listByFamily(familyId: String): List<Category> = withContext(dispatcher) {
        queries.selectCategoriesByFamily(family_id = familyId).executeAsList().map { row ->
            Category(
                id = row.id,
                familyId = row.family_id,
                name = row.name,
                icon = row.icon,
                isActive = row.is_active == 1L,
                createdAt = row.created_at,
                updatedAt = row.updated_at,
            )
        }
    }

    override suspend fun findByName(familyId: String, name: String): Category? = withContext(dispatcher) {
        queries.selectCategoryByName(family_id = familyId, name = name).executeAsOneOrNull()?.let { row ->
            Category(
                id = row.id,
                familyId = row.family_id,
                name = row.name,
                icon = row.icon,
                isActive = row.is_active == 1L,
                createdAt = row.created_at,
                updatedAt = row.updated_at,
            )
        }
    }

    override suspend fun insert(category: Category): Boolean = withContext(dispatcher) {
        queries.insertCategory(
            id = category.id,
            family_id = category.familyId,
            name = category.name,
            icon = category.icon,
            is_active = if (category.isActive) 1L else 0L,
            created_at = category.createdAt,
            updated_at = category.updatedAt,
        )
        queries.selectCategoryByName(family_id = category.familyId, name = category.name)
            .executeAsOneOrNull()?.id == category.id
    }

    override suspend fun deactivate(familyId: String, categoryId: String, updatedAt: String): Boolean =
        withContext(dispatcher) {
            queries.deactivateCategory(
                family_id = familyId,
                id = categoryId,
                updated_at = updatedAt,
            )
            queries.selectCategoryInactive(family_id = familyId, id = categoryId).executeAsOne()
        }
}
