package co.jmessias.gestor_familiar_despesas.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import co.jmessias.gestor_familiar_despesas.db.AppDatabase

class IosDatabaseFactory {
    fun create(): SqlDriver {
        val driver = NativeSqliteDriver(
            schema = AppDatabase.Schema,
            databaseName = "gestorfamiliardespesas.db",
        )
        driver.execute(null, "PRAGMA foreign_keys = ON", 0)
        return driver
    }
}
