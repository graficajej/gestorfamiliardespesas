package co.jmessias.gestor_familiar_despesas.data.bootstrap

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import co.jmessias.gestor_familiar_despesas.db.AppDatabase
import co.jmessias.gestor_familiar_despesas.domain.bootstrap.OwnerFamilySetup
import co.jmessias.gestor_familiar_despesas.domain.model.EntityMetadataProvider
import kotlinx.datetime.LocalDate
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SqlDelightFamilyBootstrapRepositoryTest {

    @Test
    fun `owner and family setup is committed as a unit`() = runBlocking {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            AppDatabase.Schema.create(driver)
            driver.execute(null, "PRAGMA foreign_keys = ON", 0)
            val database = AppDatabase(driver)
            val repository = SqlDelightFamilyBootstrapRepository(database, FixedMetadataProvider())

            val setup = OwnerFamilySetup("user-1", "family-1", "Ana", "Família")
            repository.saveOwnerFamily(setup)
            assertTrue(database.appDatabaseQueries.selectUserExists("user-1").executeAsOne())
            assertTrue(database.appDatabaseQueries.selectFamilyExists("family-1").executeAsOne())
            assertTrue(repository.loadOwnerFamily() == setup)

            database.appDatabaseQueries.insertFamily("taken-family", "Existente", timestamp, timestamp)
            var failed = false
            try {
                repository.saveOwnerFamily(OwnerFamilySetup("user-2", "taken-family", "Bruno", "Duplicada"))
            } catch (_: Exception) {
                failed = true
            }

            assertTrue(failed)
            assertFalse(database.appDatabaseQueries.selectUserExists("user-2").executeAsOne())
        } finally {
            driver.close()
        }
    }

    private class FixedMetadataProvider : EntityMetadataProvider {
        private var nextId = 0

        override fun newId(): String = "member-${++nextId}"

        override fun nowUtc(): String = timestamp

        override fun today(): LocalDate = LocalDate(2026, 10, 6)
    }

    private companion object {
        const val timestamp = "2026-10-06T12:00:00Z"
    }
}
