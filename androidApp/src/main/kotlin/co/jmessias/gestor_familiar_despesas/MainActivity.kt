package co.jmessias.gestor_familiar_despesas

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import co.jmessias.gestor_familiar_despesas.composition.ManagementViewModelFactory
import co.jmessias.gestor_familiar_despesas.db.AppDatabase
import co.jmessias.gestor_familiar_despesas.database.AndroidDatabaseFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            App {
                ManagementViewModelFactory.create(
                    AppDatabase(AndroidDatabaseFactory(applicationContext).create())
                )
            }
        }
    }
}
