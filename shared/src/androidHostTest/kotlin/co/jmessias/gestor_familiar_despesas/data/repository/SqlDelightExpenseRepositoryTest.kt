package co.jmessias.gestor_familiar_despesas.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import co.jmessias.gestor_familiar_despesas.db.AppDatabase
import co.jmessias.gestor_familiar_despesas.domain.expense.Expense
import co.jmessias.gestor_familiar_despesas.domain.expense.ExpenseType
import co.jmessias.gestor_familiar_despesas.domain.money.Money
import co.jmessias.gestor_familiar_despesas.domain.obligation.ExpenseStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SqlDelightExpenseRepositoryTest {

    @Test
    fun `one-time expense round trip update cancellation and live observation`() = runBlocking {
        val (driver, database) = createDatabase()
        try {
            val repository = SqlDelightExpenseRepository(database)
            val expense = expense()
            assertTrue(repository.familyExists(expense.familyId))
            assertTrue(repository.userBelongsToFamily(expense.familyId, expense.createdBy))
            assertTrue(repository.activeCategoryBelongsToFamily(expense.familyId, "category-1"))
            assertTrue(repository.activeAccountBelongsToFamily(expense.familyId, "account-1"))

            val observed = repository.observeOneTimeExpenses(expense.familyId)
            assertTrue(observed.first().isEmpty())
            repository.insert(expense)
            assertEquals(expense, observed.first().single())
            assertNotNull(repository.findOneTimeExpense(expense.familyId, expense.id))

            val updated = expense.copy(description = "Refeição", updatedAt = updatedTimestamp)
            assertTrue(repository.updateUnpaidOneTimeExpense(updated))
            assertEquals("Refeição", observed.first().single().description)

            assertTrue(
                repository.cancelUnpaidOneTimeExpense(
                    familyId = expense.familyId,
                    expenseId = expense.id,
                    updatedBy = expense.updatedBy,
                    updatedAt = updatedTimestamp,
                )
            )
            assertEquals(ExpenseStatus.CANCELADA, observed.first().single().status)
        } finally {
            driver.close()
        }
    }

    @Test
    fun `cancelled history remains and invalid family references are rejected`() = runBlocking {
        val (driver, database) = createDatabase()
        try {
            val repository = SqlDelightExpenseRepository(database)
            assertFalse(repository.familyExists("missing"))
            assertFalse(repository.userBelongsToFamily("family-1", "missing"))
            assertFalse(repository.activeCategoryBelongsToFamily("family-1", "missing"))
            assertFalse(repository.activeAccountBelongsToFamily("family-1", "missing"))

            assertFailsWith<Exception> { repository.insert(expense(categoryId = "missing")) }
            val expense = expense(categoryId = null, accountId = null)
            repository.insert(expense)
            database.appDatabaseQueries.insertPayment(
                id = "payment-1",
                family_id = expense.familyId,
                expense_id = expense.id,
                paid_amount_in_cents = 100L,
                paid_date = "2026-10-06",
                payment_method = "PIX",
                account_id = null,
                interest_amount_in_cents = 0L,
                fine_amount_in_cents = 0L,
                discount_amount_in_cents = 0L,
                notes = null,
                created_by = expense.createdBy,
                created_at = timestamp,
                updated_at = timestamp,
            )
            assertTrue(repository.hasPayments(expense.id))
            assertFalse(
                repository.cancelUnpaidOneTimeExpense(
                    expense.familyId,
                    expense.id,
                    expense.updatedBy,
                    updatedTimestamp,
                )
            )
            assertNotNull(repository.findOneTimeExpense(expense.familyId, expense.id))
            assertNull(repository.findOneTimeExpense("other-family", expense.id))
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

    private fun expense(
        categoryId: String? = "category-1",
        accountId: String? = "account-1",
    ) = Expense(
        id = "expense-1",
        familyId = "family-1",
        categoryId = categoryId,
        accountId = accountId,
        description = "Mercado",
        amount = Money.ofCents(1250),
        dueDate = LocalDate(2026, 10, 15),
        type = ExpenseType.ONE_TIME,
        status = ExpenseStatus.A_VENCER,
        notes = null,
        recurrenceId = null,
        createdBy = "user-1",
        updatedBy = "user-1",
        createdAt = timestamp,
        updatedAt = timestamp,
        deletedAt = null,
    )

    private companion object {
        const val timestamp = "2026-10-06T12:00:00Z"
        const val updatedTimestamp = "2026-10-06T13:00:00Z"
    }
}
