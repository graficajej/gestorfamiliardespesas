package co.jmessias.gestor_familiar_despesas

import androidx.compose.ui.window.ComposeUIViewController
import co.jmessias.gestor_familiar_despesas.composition.ManagementViewModelFactory
import co.jmessias.gestor_familiar_despesas.db.AppDatabase
import co.jmessias.gestor_familiar_despesas.database.IosDatabaseFactory

fun MainViewController() = ComposeUIViewController {
    App {
        ManagementViewModelFactory.create(AppDatabase(IosDatabaseFactory().create()))
    }
}