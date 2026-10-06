package co.jmessias.gestor_familiar_despesas.domain.installment

import co.jmessias.gestor_familiar_despesas.domain.model.EntityMetadataProvider
import co.jmessias.gestor_familiar_despesas.domain.money.Money
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InstallmentUseCasesTest {

    @Test
    fun `create splits total exactly and clamps monthly dates from original day`() = runTest {
        val repository = FakeInstallmentRepository()
        val useCase = CreateInstallmentPlanUseCase(repository, InstallmentMetadata())

        val result = useCase.execute(
            CreateInstallmentPlanInput(
                familyId = "family-1",
                userId = "user-1",
                description = "Notebook",
                totalAmount = Money.ofCents(100),
                installmentCount = 3,
                firstDueDate = LocalDate(2024, 1, 31),
                categoryId = null,
                accountId = null,
            )
        ).getOrThrow()

        assertEquals(listOf(34L, 33L, 33L), result.installments.map { it.expense.amount.cents })
        assertEquals(100L, result.installments.sumOf { it.expense.amount.cents })
        assertEquals(
            listOf(LocalDate(2024, 1, 31), LocalDate(2024, 2, 29), LocalDate(2024, 3, 31)),
            result.installments.map { it.expense.dueDate },
        )
        assertEquals(listOf(1, 2, 3), result.installments.map { it.number })
        assertEquals(1, repository.createdPlans.size)
    }

    @Test
    fun `invalid installment counts amounts and family references are rejected`() = runTest {
        val repository = FakeInstallmentRepository()
        val useCase = CreateInstallmentPlanUseCase(repository, InstallmentMetadata())
        val valid = CreateInstallmentPlanInput(
            familyId = "family-1",
            userId = "user-1",
            description = "Compra",
            totalAmount = Money.ofCents(2),
            installmentCount = 2,
            firstDueDate = LocalDate(2026, 10, 6),
            categoryId = null,
            accountId = null,
        )

        assertTrue(useCase.execute(valid.copy(totalAmount = Money.ZERO)).isFailure)
        assertTrue(useCase.execute(valid.copy(installmentCount = 3)).isFailure)
        assertTrue(useCase.execute(valid.copy(description = "  ")).isFailure)
        repository.member = false
        assertTrue(useCase.execute(valid).isFailure)
        assertTrue(repository.createdPlans.isEmpty())
    }
}

private class FakeInstallmentRepository : InstallmentRepository {
    var member = true
    val createdPlans = mutableListOf<InstallmentPlanWithInstallments>()

    override suspend fun userBelongsToFamily(familyId: String, userId: String): Boolean = member

    override suspend fun activeCategoryBelongsToFamily(familyId: String, categoryId: String): Boolean = true

    override suspend fun activeAccountBelongsToFamily(familyId: String, accountId: String): Boolean = true

    override suspend fun createPlan(plan: InstallmentPlan, installments: List<Installment>) {
        createdPlans += InstallmentPlanWithInstallments(plan, installments)
    }

    override fun observePlans(familyId: String): Flow<List<InstallmentPlanWithInstallments>> =
        flowOf(createdPlans.filter { it.plan.familyId == familyId })
}

private class InstallmentMetadata : EntityMetadataProvider {
    private var nextId = 0

    override fun newId(): String = "id-${++nextId}"

    override fun nowUtc(): String = "2026-10-06T12:00:00Z"

    override fun today(): LocalDate = LocalDate(2026, 10, 6)
}
