package co.jmessias.gestor_familiar_despesas.domain.recurrence

import co.jmessias.gestor_familiar_despesas.domain.expense.Expense
import co.jmessias.gestor_familiar_despesas.domain.model.EntityMetadataProvider
import co.jmessias.gestor_familiar_despesas.domain.money.Money
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RecurrenceUseCasesTest {

    @Test
    fun `monthly dates clamp short months and restore original day`() = runTest {
        val repository = FakeRecurrenceRepository(recurrences = listOf(recurrence(startDate = LocalDate(2024, 1, 31))))

        generate(repository, LocalDate(2024, 1, 1), LocalDate(2024, 5, 1))

        assertEquals(
            listOf(
                LocalDate(2024, 1, 31),
                LocalDate(2024, 2, 29),
                LocalDate(2024, 3, 31),
                LocalDate(2024, 4, 30),
            ),
            repository.generated.map { it.dueDate },
        )
    }

    @Test
    fun `quarterly and yearly frequencies preserve their calendar anchor`() = runTest {
        val quarterly = FakeRecurrenceRepository(
            recurrences = listOf(
                recurrence(
                    id = "quarterly",
                    startDate = LocalDate(2024, 1, 31),
                    frequency = RecurrenceFrequency.QUARTERLY,
                )
            )
        )
        generate(quarterly, LocalDate(2024, 1, 1), LocalDate(2025, 1, 1))
        assertEquals(
            listOf(
                LocalDate(2024, 1, 31),
                LocalDate(2024, 4, 30),
                LocalDate(2024, 7, 31),
                LocalDate(2024, 10, 31),
            ),
            quarterly.generated.map { it.dueDate },
        )

        val yearly = FakeRecurrenceRepository(
            recurrences = listOf(
                recurrence(
                    id = "yearly",
                    startDate = LocalDate(2024, 2, 29),
                    frequency = RecurrenceFrequency.YEARLY,
                )
            )
        )
        generate(yearly, LocalDate(2024, 1, 1), LocalDate(2028, 1, 1))
        assertEquals(
            listOf(
                LocalDate(2024, 2, 29),
                LocalDate(2025, 2, 28),
                LocalDate(2026, 2, 28),
                LocalDate(2027, 2, 28),
            ),
            yearly.generated.map { it.dueDate },
        )
    }

    @Test
    fun `window is inclusive at start exclusive at end and respects recurrence start date`() = runTest {
        val repository = FakeRecurrenceRepository(
            recurrences = listOf(
                recurrence(
                    startDate = LocalDate(2025, 1, 20),
                    dueDay = 5,
                )
            )
        )

        generate(repository, LocalDate(2025, 2, 5), LocalDate(2025, 4, 5))

        assertEquals(
            listOf(LocalDate(2025, 2, 5), LocalDate(2025, 3, 5)),
            repository.generated.map { it.dueDate },
        )
    }

    @Test
    fun `old recurrence starts calculation close to requested window and generation is idempotent`() = runTest {
        val repository = FakeRecurrenceRepository(
            recurrences = listOf(recurrence(startDate = LocalDate(1900, 1, 1), dueDay = 31))
        )

        val firstCount = generate(repository, LocalDate(2026, 1, 1), LocalDate(2026, 4, 1))
        val secondCount = generate(repository, LocalDate(2026, 1, 1), LocalDate(2026, 4, 1))

        assertEquals(3, firstCount)
        assertEquals(0, secondCount)
        assertEquals(
            listOf(LocalDate(2026, 1, 31), LocalDate(2026, 2, 28), LocalDate(2026, 3, 31)),
            repository.generated.map { it.dueDate },
        )
    }

    @Test
    fun `invalid amount due day and window are rejected`() = runTest {
        val repository = FakeRecurrenceRepository()
        val useCase = CreateRecurrenceUseCase(repository, RecurrenceMetadata())
        val validInput = RecurrenceInput(
            familyId = "family-1",
            userId = "user-1",
            description = "Conta",
            amount = Money.ofCents(1_000),
            categoryId = null,
            accountId = null,
            frequency = RecurrenceFrequency.MONTHLY,
            startDate = LocalDate(2026, 1, 1),
            dueDay = null,
        )
        assertTrue(useCase.execute(validInput.copy(amount = Money.ZERO)).isFailure)
        assertTrue(useCase.execute(validInput.copy(dueDay = 32)).isFailure)
        repository.member = false
        assertTrue(useCase.execute(validInput).isFailure)
        assertTrue(
            GenerateRecurrenceWindowUseCase(repository, RecurrenceMetadata())
                .execute("family-1", LocalDate(2026, 2, 1), LocalDate(2026, 2, 1))
                .isFailure
        )
        assertTrue(repository.created.isEmpty())
        assertTrue(repository.generated.isEmpty())
    }

    private suspend fun generate(
        repository: FakeRecurrenceRepository,
        fromInclusive: LocalDate,
        toExclusive: LocalDate,
    ): Int = GenerateRecurrenceWindowUseCase(repository, RecurrenceMetadata())
        .execute("family-1", fromInclusive, toExclusive)
        .getOrThrow()
}

private fun recurrence(
    id: String = "recurrence-1",
    startDate: LocalDate,
    frequency: RecurrenceFrequency = RecurrenceFrequency.MONTHLY,
    dueDay: Int? = null,
) = Recurrence(
    id = id,
    familyId = "family-1",
    description = "Conta",
    amount = Money.ofCents(1_000),
    categoryId = null,
    accountId = null,
    frequency = frequency,
    startDate = startDate,
    dueDay = dueDay,
    isActive = true,
    createdBy = "user-1",
    createdAt = "2026-01-01T00:00:00Z",
    updatedAt = "2026-01-01T00:00:00Z",
)

private class FakeRecurrenceRepository(
    private val recurrences: List<Recurrence> = emptyList(),
) : RecurrenceRepository {
    var member = true
    val created = mutableListOf<Recurrence>()
    val generated = mutableListOf<Expense>()
    private val inserted = mutableSetOf<Pair<String, LocalDate>>()

    override suspend fun userBelongsToFamily(familyId: String, userId: String) = member
    override suspend fun activeCategoryBelongsToFamily(familyId: String, categoryId: String) = true
    override suspend fun activeAccountBelongsToFamily(familyId: String, accountId: String) = true
    override suspend fun findActive(familyId: String, recurrenceId: String): Recurrence? =
        recurrences.firstOrNull { it.familyId == familyId && it.id == recurrenceId && it.isActive }
    override suspend fun insert(recurrence: Recurrence) {
        created += recurrence
    }
    override suspend fun updateActive(recurrence: Recurrence) = true
    override suspend fun cancelActive(familyId: String, recurrenceId: String, updatedAt: String) = true
    override fun observeRecurrences(familyId: String): Flow<List<RecurrenceWithOccurrences>> = flowOf(emptyList())
    override suspend fun listActive(familyId: String): List<Recurrence> =
        recurrences.filter { it.familyId == familyId && it.isActive }

    override suspend fun insertGeneratedOccurrences(occurrences: List<Expense>): Int {
        val newOccurrences = occurrences.filter { expense ->
            inserted.add(requireNotNull(expense.recurrenceId) to expense.dueDate)
        }
        generated += newOccurrences
        return newOccurrences.size
    }
}

private class RecurrenceMetadata : EntityMetadataProvider {
    private var nextId = 0

    override fun newId(): String = "generated-${++nextId}"
    override fun nowUtc(): String = "2026-01-01T00:00:00Z"
    override fun today(): LocalDate = LocalDate(2026, 1, 1)
}
