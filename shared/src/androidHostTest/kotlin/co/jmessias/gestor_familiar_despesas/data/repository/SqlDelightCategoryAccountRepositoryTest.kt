package co.jmessias.gestor_familiar_despesas.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import co.jmessias.gestor_familiar_despesas.db.AppDatabase
import co.jmessias.gestor_familiar_despesas.domain.account.Account
import co.jmessias.gestor_familiar_despesas.domain.account.AccountType
import co.jmessias.gestor_familiar_despesas.domain.category.Category
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SqlDelightCategoryAccountRepositoryTest {

    @Test
    fun `category repository creates lists and deactivates without deleting`() = runBlocking {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            AppDatabase.Schema.create(driver)
            driver.execute(null, "PRAGMA foreign_keys = ON", 0)
            val database = AppDatabase(driver)
            database.appDatabaseQueries.insertFamily("family-1", "Família", timestamp, timestamp)
            val repository = SqlDelightCategoryRepository(database)
            val category = Category("category-1", "family-1", "Moradia", null, true, timestamp, timestamp)

            assertTrue(repository.insert(category))
            assertFailsWith<Exception> {
                repository.insert(category.copy(id = "category-invalid", familyId = "missing"))
            }
            assertFalse(repository.insert(category.copy(id = "category-2")))
            assertEquals(category, repository.listByFamily("family-1").single())
            assertTrue(repository.deactivate("family-1", category.id, updatedTimestamp))
            assertFalse(repository.listByFamily("family-1").single().isActive)
            assertEquals(updatedTimestamp, repository.listByFamily("family-1").single().updatedAt)
            assertFalse(repository.deactivate("other-family", category.id, updatedTimestamp))
        } finally {
            driver.close()
        }
    }

    @Test
    fun `account repository creates lists and deactivates within its family`() = runBlocking {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            AppDatabase.Schema.create(driver)
            driver.execute(null, "PRAGMA foreign_keys = ON", 0)
            val database = AppDatabase(driver)
            database.appDatabaseQueries.insertFamily("family-1", "Família", timestamp, timestamp)
            val repository = SqlDelightAccountRepository(database)
            val account = Account(
                id = "account-1",
                familyId = "family-1",
                name = "Conta corrente",
                type = AccountType.BANK,
                institutionName = "Banco",
                isActive = true,
                createdAt = timestamp,
                updatedAt = timestamp,
            )

            repository.insert(account)

            assertTrue(repository.familyExists("family-1"))
            assertFalse(repository.familyExists("missing"))
            assertEquals(account, repository.listByFamily("family-1").single())
            assertFalse(repository.deactivate("other-family", account.id, updatedTimestamp))
            assertTrue(repository.deactivate("family-1", account.id, updatedTimestamp))
            assertFalse(repository.listByFamily("family-1").single().isActive)
            assertNull(repository.listByFamily("missing").firstOrNull())
        } finally {
            driver.close()
        }
    }

    private companion object {
        const val timestamp = "2026-10-06T12:00:00Z"
        const val updatedTimestamp = "2026-10-06T13:00:00Z"
    }
}
