package co.jmessias.gestor_familiar_despesas.domain.category

interface CategoryRepository {
    suspend fun familyExists(familyId: String): Boolean
    suspend fun listByFamily(familyId: String): List<Category>
    suspend fun findByName(familyId: String, name: String): Category?
    suspend fun insert(category: Category): Boolean
    suspend fun deactivate(familyId: String, categoryId: String, updatedAt: String): Boolean
}
