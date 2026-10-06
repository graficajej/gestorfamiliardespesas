package co.jmessias.gestor_familiar_despesas.database

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import co.jmessias.gestor_familiar_despesas.db.AppDatabase

class AndroidDatabaseFactory(private val context: Context) {
    fun create(): SqlDriver {
        val driver = AndroidSqliteDriver(
            schema = AppDatabase.Schema,
            context = context,
            name = "gestorfamiliardespesas.db",
        )
        driver.execute(null, "PRAGMA foreign_keys = ON", 0)
        return driver
    }
}
