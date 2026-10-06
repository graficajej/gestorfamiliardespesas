package co.jmessias.gestor_familiar_despesas.domain.category

data class Category(
    val id: String,
    val familyId: String,
    val name: String,
    val icon: String?,
    val isActive: Boolean,
    val createdAt: String,
    val updatedAt: String,
)
