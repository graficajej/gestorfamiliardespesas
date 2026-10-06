package co.jmessias.gestor_familiar_despesas.domain.account

enum class AccountType {
    BANK,
    CASH,
    DIGITAL_WALLET,
    CREDIT_CARD,
    OTHER,
}

data class Account(
    val id: String,
    val familyId: String,
    val name: String,
    val type: AccountType,
    val institutionName: String?,
    val isActive: Boolean,
    val createdAt: String,
    val updatedAt: String,
)
