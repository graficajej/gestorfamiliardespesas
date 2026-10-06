package co.jmessias.gestor_familiar_despesas.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import co.jmessias.gestor_familiar_despesas.db.AppDatabase
import co.jmessias.gestor_familiar_despesas.domain.expense.Expense
import co.jmessias.gestor_familiar_despesas.domain.expense.ExpenseType
import co.jmessias.gestor_familiar_despesas.domain.installment.CreateInstallmentPlanInput
import co.jmessias.gestor_familiar_despesas.domain.installment.CreateInstallmentPlanUseCase
import co.jmessias.gestor_familiar_despesas.domain.installment.Installment
import co.jmessias.gestor_familiar_despesas.domain.installment.InstallmentPlan
import co.jmessias.gestor_familiar_despesas.domain.money.Money
import co.jmessias.gestor_familiar_despesas.domain.obligation.ExpenseStatus
import co.jmessias.gestor_familiar_despesas.domain.model.EntityMetadataProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SqlDelightInstallmentRepositoryTest {

    @Test
    fun `plan and installment expenses persist atomically and observe from linked expenses`() = runBlocking {
        val (driver, database) = createDatabase()
        try {
            val repository = SqlDelightInstallmentRepository(database)
            val useCase = CreateInstallmentPlanUseCase(repository, InstallmentTestMetadata())
            val plans = repository.observePlans("family-1")
            assertTrue(plans.first().isEmpty())

            val created = useCase.execute(
                CreateInstallmentPlanInput(
                    familyId = "family-1",
                    userId = "user-1",
                    description = "Notebook",
                    totalAmount = Money.ofCents(10_000),
                    installmentCount = 3,
                    firstDueDate = LocalDate(2026, 10, 31),
                    categoryId = "category-1",
                    accountId = "account-1",
                )
            ).getOrThrow()

            val observed = plans.first().single()
            assertEquals(created.plan, observed.plan)
            assertEquals(3, observed.installments.size)
            assertEquals(listOf(3334L, 3333L, 3333L), observed.installments.map { it.expense.amount.cents })
            assertEquals(10_000L, observed.installments.sumOf { it.expense.amount.cents })
            assertEquals(
                listOf(LocalDate(2026, 10, 31), LocalDate(2026, 11, 30), LocalDate(2026, 12, 31)),
                observed.installments.map { it.expense.dueDate },
            )
            assertTrue(observed.installments.all { it.expense.type == ExpenseType.INSTALLMENT })
            assertTrue(observed.installments.all { it.expense.status == ExpenseStatus.A_VENCER })
        } finally {
            driver.close()
        }
    }

    @Test
    fun `failed installment insert rolls back plan expense and position`() = runBlocking {
        val (driver, database) = createDatabase()
        try {
            val repository = SqlDelightInstallmentRepository(database)
            val now = "2026-10-06T12:00:00Z"
            val plan = InstallmentPlan(
                id = "plan-1",
                familyId = "family-1",
                description = "Compra",
                totalAmount = Money.ofCents(200),
                installmentCount = 2,
                firstDueDate = LocalDate(2026, 10, 15),
                categoryId = null,
                accountId = null,
                createdBy = "user-1",
                createdAt = now,
                updatedAt = now,
            )
            val expense = Expense(
                id = "duplicate-expense",
                familyId = "family-1",
                categoryId = null,
                accountId = null,
                description = "Compra (1/2)",
                amount = Money.ofCents(100),
                dueDate = LocalDate(2026, 10, 15),
                type = ExpenseType.INSTALLMENT,
                status = ExpenseStatus.A_VENCER,
                notes = null,
                recurrenceId = null,
                createdBy = "user-1",
                updatedBy = "user-1",
                createdAt = now,
                updatedAt = now,
                deletedAt = null,
            )
            val installments = listOf(
                Installment("position-1", "plan-1", expense, 1, 2, now, now),
                Installment("position-2", "plan-1", expense, 2, 2, now, now),
            )

            assertFailsWith<Exception> { repository.createPlan(plan, installments) }
            assertTrue(repository.observePlans("family-1").first().isEmpty())
            assertEquals(0L, database.appDatabaseQueries.countInstallmentPlansByFamily("family-1").executeAsOne())
        } finally {
            driver.close()
        }
    }

    private fun createDatabase(): Pair<JdbcSqliteDriver, AppDatabase> {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        AppDatabase.Schema.create(driver)
        driver.execute(null, "PRAGMA foreign_keys = ON", 0)
        val database = AppDatabase(driver)
        val queries = database.appDatabaseQueries
        queries.insertUser("user-1", "Ana", null, timestamp, timestamp)
        queries.insertFamily("family-1", "Família", timestamp, timestamp)
        queries.insertFamilyMember("member-1", "family-1", "user-1", "OWNER", timestamp, timestamp)
        queries.insertCategory("category-1", "family-1", "Casa", null, 1L, timestamp, timestamp)
        queries.insertAccount("account-1", "family-1", "Banco", "BANK", null, 1L, timestamp, timestamp)
        return driver to database
    }

    private companion object {
        const val timestamp = "2026-10-06T12:00:00Z"
    }
}

private class InstallmentTestMetadata : EntityMetadataProvider {
    private var nextId = 0

    override fun newId(): String = "generated-${++nextId}"

    override fun nowUtc(): String = "2026-10-06T12:00:00Z"

    override fun today(): LocalDate = LocalDate(2026, 10, 6)
}
