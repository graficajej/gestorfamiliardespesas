package co.jmessias.gestor_familiar_despesas.domain.installment

import kotlinx.coroutines.flow.Flow

interface InstallmentRepository {
    suspend fun userBelongsToFamily(familyId: String, userId: String): Boolean
    suspend fun activeCategoryBelongsToFamily(familyId: String, categoryId: String): Boolean
    suspend fun activeAccountBelongsToFamily(familyId: String, accountId: String): Boolean
    suspend fun createPlan(
        plan: InstallmentPlan,
        installments: List<Installment>,
    )
    fun observePlans(familyId: String): Flow<List<InstallmentPlanWithInstallments>>
}
