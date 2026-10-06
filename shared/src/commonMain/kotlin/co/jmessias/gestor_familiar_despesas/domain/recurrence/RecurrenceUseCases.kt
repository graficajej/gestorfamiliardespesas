package co.jmessias.gestor_familiar_despesas.domain.recurrence

import co.jmessias.gestor_familiar_despesas.domain.expense.Expense
import co.jmessias.gestor_familiar_despesas.domain.expense.ExpenseType
import co.jmessias.gestor_familiar_despesas.domain.model.EntityMetadataProvider
import co.jmessias.gestor_familiar_despesas.domain.money.Money
import co.jmessias.gestor_familiar_despesas.domain.obligation.ExpenseStatusResolver
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

data class RecurrenceInput(
    val familyId: String,
    val userId: String,
    val description: String,
    val amount: Money,
    val categoryId: String?,
    val accountId: String?,
    val frequency: RecurrenceFrequency,
    val startDate: LocalDate,
    val dueDay: Int?,
)

class ObserveRecurrencesUseCase(
    private val repository: RecurrenceRepository,
) {
    fun execute(familyId: String): Flow<List<RecurrenceWithOccurrences>> =
        repository.observeRecurrences(familyId)
}

class CreateRecurrenceUseCase(
    private val repository: RecurrenceRepository,
    private val metadataProvider: EntityMetadataProvider,
) {
    suspend fun execute(input: RecurrenceInput): Result<Recurrence> {
        val error = validateRecurrenceInput(repository, input)
        if (error != null) return Result.failure(error)
        val now = metadataProvider.nowUtc()
        val recurrence = Recurrence(
            id = metadataProvider.newId(),
            familyId = input.familyId,
            description = input.description.trim(),
            amount = input.amount,
            categoryId = input.categoryId,
            accountId = input.accountId,
            frequency = input.frequency,
            startDate = input.startDate,
            dueDay = input.dueDay,
            isActive = true,
            createdBy = input.userId,
            createdAt = now,
            updatedAt = now,
        )
        repository.insert(recurrence)
        return Result.success(recurrence)
    }
}

class UpdateRecurrenceUseCase(
    private val repository: RecurrenceRepository,
    private val metadataProvider: EntityMetadataProvider,
) {
    suspend fun execute(recurrenceId: String, input: RecurrenceInput): Result<Recurrence> {
        if (recurrenceId.isBlank()) return Result.failure(IllegalArgumentException("Informe uma recorrência."))
        val existing = repository.findActive(input.familyId, recurrenceId)
            ?: return Result.failure(NoSuchElementException("Recorrência ativa não encontrada."))
        val validated = validateRecurrenceInput(repository, input)
        if (validated != null) return Result.failure(validated)
        val updated = existing.copy(
            description = input.description.trim(),
            amount = input.amount,
            categoryId = input.categoryId,
            accountId = input.accountId,
            frequency = input.frequency,
            startDate = input.startDate,
            dueDay = input.dueDay,
            updatedAt = metadataProvider.nowUtc(),
        )
        if (!repository.updateActive(updated)) {
            return Result.failure(IllegalStateException("Recorrência não pôde ser atualizada; ela pode ter sido cancelada."))
        }
        return Result.success(updated)
    }
}

class CancelRecurrenceUseCase(
    private val repository: RecurrenceRepository,
    private val metadataProvider: EntityMetadataProvider,
) {
    suspend fun execute(familyId: String, recurrenceId: String, userId: String): Result<Boolean> {
        if (familyId.isBlank() || recurrenceId.isBlank() || userId.isBlank()) {
            return Result.failure(IllegalArgumentException("Família, recorrência e usuário são obrigatórios."))
        }
        if (!repository.userBelongsToFamily(familyId, userId)) {
            return Result.failure(IllegalArgumentException("Usuário não pertence à família selecionada."))
        }
        if (repository.findActive(familyId, recurrenceId) == null) {
            return Result.failure(NoSuchElementException("Recorrência ativa não encontrada."))
        }
        val cancelled = repository.cancelActive(familyId, recurrenceId, metadataProvider.nowUtc())
        return if (cancelled) Result.success(true)
        else Result.failure(IllegalStateException("Recorrência não pôde ser cancelada."))
    }
}

class GenerateRecurrenceWindowUseCase(
    private val repository: RecurrenceRepository,
    private val metadataProvider: EntityMetadataProvider,
) {
    suspend fun execute(familyId: String, fromInclusive: LocalDate, toExclusive: LocalDate): Result<Int> {
        if (familyId.isBlank()) return Result.failure(IllegalArgumentException("Informe a família."))
        if (toExclusive <= fromInclusive) {
            return Result.failure(IllegalArgumentException("O fim da janela deve ser posterior ao início."))
        }
        val today = metadataProvider.today()
        val now = metadataProvider.nowUtc()
        val occurrences = mutableListOf<Expense>()
        repository.listActive(familyId).forEach { recurrence ->
            scheduledDates(recurrence, fromInclusive, toExclusive).forEach { dueDate ->
                occurrences += Expense(
                    id = metadataProvider.newId(),
                    familyId = recurrence.familyId,
                    categoryId = recurrence.categoryId,
                    accountId = recurrence.accountId,
                    description = recurrence.description,
                    amount = recurrence.amount,
                    dueDate = dueDate,
                    type = ExpenseType.RECURRING,
                    status = ExpenseStatusResolver.resolve(recurrence.amount, dueDate, today),
                    notes = null,
                    recurrenceId = recurrence.id,
                    createdBy = recurrence.createdBy,
                    updatedBy = recurrence.createdBy,
                    createdAt = now,
                    updatedAt = now,
                    deletedAt = null,
                )
            }
        }
        return Result.success(repository.insertGeneratedOccurrences(occurrences))
    }
}

private fun scheduledDates(
    recurrence: Recurrence,
    fromInclusive: LocalDate,
    toExclusive: LocalDate,
): List<LocalDate> {
    val dates = mutableListOf<LocalDate>()
    val startMonthIndex = recurrence.startDate.year.toLong() * 12L + recurrence.startDate.month.ordinal
    val fromMonthIndex = fromInclusive.year.toLong() * 12L + fromInclusive.month.ordinal
    val monthsBeforeWindow = fromMonthIndex - startMonthIndex
    var period = if (monthsBeforeWindow <= 0L) {
        0L
    } else {
        (monthsBeforeWindow + recurrence.frequency.monthInterval - 1) /
            recurrence.frequency.monthInterval
    }
    while (true) {
        val monthIndex = startMonthIndex + period * recurrence.frequency.monthInterval
        val year = (monthIndex / 12L).toInt()
        val monthNumber = (monthIndex % 12L).toInt() + 1
        val monthDate = LocalDate(year, monthNumber, 1)
        val dueDay = recurrence.dueDay ?: recurrence.startDate.day
        val dueDate = LocalDate(year, monthNumber, minOf(dueDay, monthDate.daysInMonth()))

        if (dueDate >= recurrence.startDate && dueDate >= fromInclusive && dueDate < toExclusive) {
            dates += dueDate
        }
        if (dueDate >= toExclusive || monthDate >= toExclusive) break
        period++
    }
    return dates
}

private fun LocalDate.daysInMonth(): Int {
    val leapYear = year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)
    return when (month.ordinal + 1) {
        2 -> if (leapYear) 29 else 28
        4, 6, 9, 11 -> 30
        else -> 31
    }
}

private suspend fun validateRecurrenceInput(
    repository: RecurrenceRepository,
    input: RecurrenceInput,
): Throwable? {
    if (input.familyId.isBlank() || input.userId.isBlank()) {
        return IllegalArgumentException("Família e usuário são obrigatórios.")
    }
    if (input.description.isBlank()) return IllegalArgumentException("Informe a descrição da recorrência.")
    if (input.amount <= Money.ZERO) return IllegalArgumentException("O valor deve ser maior que zero.")
    if (input.dueDay != null && input.dueDay !in 1..31) {
        return IllegalArgumentException("O dia de vencimento deve estar entre 1 e 31.")
    }
    if (!repository.userBelongsToFamily(input.familyId, input.userId)) {
        return IllegalArgumentException("Usuário não pertence à família selecionada.")
    }
    if (input.categoryId != null && !repository.activeCategoryBelongsToFamily(input.familyId, input.categoryId)) {
        return IllegalArgumentException("A categoria deve estar ativa e pertencer à família selecionada.")
    }
    if (input.accountId != null && !repository.activeAccountBelongsToFamily(input.familyId, input.accountId)) {
        return IllegalArgumentException("A conta deve estar ativa e pertencer à família selecionada.")
    }
    return null
}
