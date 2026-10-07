package co.jmessias.gestor_familiar_despesas.composition

import co.jmessias.gestor_familiar_despesas.data.bootstrap.SqlDelightFamilyBootstrapRepository
import co.jmessias.gestor_familiar_despesas.data.repository.SqlDelightCategoryRepository
import co.jmessias.gestor_familiar_despesas.data.repository.SqlDelightExpenseRepository
import co.jmessias.gestor_familiar_despesas.data.repository.SqlDelightInstallmentRepository
import co.jmessias.gestor_familiar_despesas.data.repository.SqlDelightPaymentRepository
import co.jmessias.gestor_familiar_despesas.data.repository.SqlDelightRecurrenceRepository
import co.jmessias.gestor_familiar_despesas.db.AppDatabase
import co.jmessias.gestor_familiar_despesas.presentation.management.ManagementViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

object ManagementViewModelFactory {
    fun create(
        database: AppDatabase,
        dispatcher: CoroutineDispatcher = Dispatchers.Default,
    ) = ManagementViewModel(
        bootstrapRepository = SqlDelightFamilyBootstrapRepository(database, dispatcher = dispatcher),
        categoryRepository = SqlDelightCategoryRepository(database, dispatcher),
        expenseRepository = SqlDelightExpenseRepository(database, dispatcher),
        paymentRepository = SqlDelightPaymentRepository(database, dispatcher),
        installmentRepository = SqlDelightInstallmentRepository(database, dispatcher),
        recurrenceRepository = SqlDelightRecurrenceRepository(database, dispatcher),
    )
}
