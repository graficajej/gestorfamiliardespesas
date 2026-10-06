package co.jmessias.gestor_familiar_despesas.domain.category

import co.jmessias.gestor_familiar_despesas.domain.model.EntityMetadataProvider
import kotlinx.datetime.LocalDate
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CategoryUseCasesTest {
    private val repository = InMemoryCategoryRepository()
    private val metadata = FixedMetadataProvider()

    @Test
    fun `create trims category name and icon`() = runTest {
        repository.families += "family-1"

        val result = CreateCategoryUseCase(repository, metadata)
            .execute("family-1", "  Moradia  ", "  home  ")

        assertTrue(result.isSuccess)
        val category = result.getOrThrow()
        assertEquals("Moradia", category.name)
        assertEquals("home", category.icon)
        assertEquals("id-1", category.id)
        assertEquals("2026-10-06T12:00:00Z", category.createdAt)
    }

    @Test
    fun `create rejects blank names and unknown families`() = runTest {
        assertTrue(CreateCategoryUseCase(repository, metadata).execute("family-1", " ").isFailure)
        assertTrue(CreateCategoryUseCase(repository, metadata).execute("missing", "Moradia").isFailure)
    }

    @Test
    fun `category names are unique within a family`() = runTest {
        repository.families += "family-1"
        val useCase = CreateCategoryUseCase(repository, metadata)
        assertTrue(useCase.execute("family-1", "Moradia").isSuccess)
        assertTrue(useCase.execute("family-1", "Moradia").isFailure)
    }

    @Test
    fun `list includes inactive categories and deactivate is family scoped`() = runTest {
        repository.families += listOf("family-1", "family-2")
        val category = CreateCategoryUseCase(repository, metadata).execute("family-1", "Lazer").getOrThrow()

        assertFalse(DeactivateCategoryUseCase(repository, metadata).execute("family-2", category.id).getOrThrow())
        assertTrue(DeactivateCategoryUseCase(repository, metadata).execute("family-1", category.id).getOrThrow())
        assertFalse(ListCategoriesUseCase(repository).execute("family-1").getOrThrow().single().isActive)
    }
}

private class InMemoryCategoryRepository : CategoryRepository {
    val families = mutableSetOf<String>()
    private val categories = mutableListOf<Category>()

    override suspend fun familyExists(familyId: String): Boolean = familyId in families

    override suspend fun listByFamily(familyId: String): List<Category> =
        categories.filter { it.familyId == familyId }.sortedBy { it.name }

    override suspend fun findByName(familyId: String, name: String): Category? =
        categories.firstOrNull { it.familyId == familyId && it.name == name }

    override suspend fun insert(category: Category): Boolean {
        if (findByName(category.familyId, category.name) != null) return false
        categories += category
        return true
    }

    override suspend fun deactivate(familyId: String, categoryId: String, updatedAt: String): Boolean {
        val index = categories.indexOfFirst { it.familyId == familyId && it.id == categoryId && it.isActive }
        if (index < 0) return false
        categories[index] = categories[index].copy(isActive = false, updatedAt = updatedAt)
        return true
    }
}

private class FixedMetadataProvider : EntityMetadataProvider {
    private var nextId = 0

    override fun newId(): String = "id-${++nextId}"

    override fun nowUtc(): String = "2026-10-06T12:00:00Z"

    override fun today(): LocalDate = LocalDate(2026, 10, 6)
}
