package co.jmessias.gestor_familiar_despesas.presentation.management

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import co.jmessias.gestor_familiar_despesas.composition.ManagementViewModelFactory
import co.jmessias.gestor_familiar_despesas.data.repository.SqlDelightRecurrenceRepository
import co.jmessias.gestor_familiar_despesas.db.AppDatabase
import co.jmessias.gestor_familiar_despesas.domain.model.SystemEntityMetadataProvider
import co.jmessias.gestor_familiar_despesas.domain.obligation.ExpenseStatus
import co.jmessias.gestor_familiar_despesas.domain.payment.PaymentMethod
import co.jmessias.gestor_familiar_despesas.domain.recurrence.GenerateRecurrenceWindowUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ManagementViewModelTest {

    @Test
    fun `one time expense flow validates input creates edits and cancels`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            AppDatabase.Schema.create(driver)
            val viewModel = ManagementViewModelFactory.create(AppDatabase(driver), dispatcher)
            awaitState(viewModel) { it is ManagementUiState.Setup }
            viewModel.updateSetupNames("Ana", "Família Silva")
            viewModel.createOwnerFamily()
            awaitState(viewModel) { it is ManagementUiState.Workspace && !it.isLoading }

            viewModel.selectSection(ManagementSection.EXPENSES)
            awaitState(viewModel) { it is ManagementUiState.Workspace && !it.isLoading }
            viewModel.updateExpenseDraft(
                OneTimeExpenseDraft(
                    description = "Conta de luz",
                    amountText = "125,90",
                    dueDateText = "data-inválida",
                )
            )
            viewModel.saveOneTimeExpense()
            val invalidDateState = assertIs<ManagementUiState.Workspace>(viewModel.state.value)
            assertEquals("Informe uma data válida no formato AAAA-MM-DD.", invalidDateState.errorMessage)

            viewModel.updateExpenseDraft(
                invalidDateState.expenseDraft.copy(dueDateText = "2025-03-15")
            )
            viewModel.saveOneTimeExpense()
            val created = assertIs<ManagementUiState.Workspace>(
                awaitState(viewModel) {
                    it is ManagementUiState.Workspace && it.expenses.any { expense -> expense.description == "Conta de luz" }
                }
            ).expenses.single()
            assertEquals(12_590L, created.amount.cents)

            viewModel.editExpense(created)
            val editingState = assertIs<ManagementUiState.Workspace>(viewModel.state.value)
            viewModel.updateExpenseDraft(
                editingState.expenseDraft.copy(description = "Energia elétrica", amountText = "130,00")
            )
            viewModel.saveOneTimeExpense()
            val updated = assertIs<ManagementUiState.Workspace>(
                awaitState(viewModel) {
                    it is ManagementUiState.Workspace &&
                        it.expenses.any { expense -> expense.description == "Energia elétrica" }
                }
            ).expenses.single()
            assertEquals(created.id, updated.id)
            assertEquals(13_000L, updated.amount.cents)

            viewModel.cancelOneTimeExpense(updated.id)
            val cancelled = assertIs<ManagementUiState.Workspace>(
                awaitState(viewModel) {
                    it is ManagementUiState.Workspace &&
                        it.expenses.singleOrNull()?.status == ExpenseStatus.CANCELADA
                }
            ).expenses.single()
            assertEquals(ExpenseStatus.CANCELADA, cancelled.status)
        } finally {
            driver.close()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `amount parser converts decimal input to cents without floating point`() {
        assertEquals(12_590L, parseAmountInCents("125,90"))
        assertEquals(5_000L, parseAmountInCents("50"))
        assertEquals(null, parseAmountInCents("1,234"))
        assertEquals(null, parseAmountInCents("-1,00"))
        assertEquals(null, parseAmountInCents("999999999999999999999999"))
    }

    @Test
    fun `recurrence creates future expenses and edits and cancellation preserve generated history`() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            runTest {
                val dispatcher = StandardTestDispatcher(testScheduler)
                Dispatchers.setMain(dispatcher)
                val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
                try {
                    AppDatabase.Schema.create(driver)
                    val viewModel = ManagementViewModelFactory.create(AppDatabase(driver), dispatcher)
                    awaitState(viewModel) { it is ManagementUiState.Setup }
                    viewModel.updateSetupNames("Ana", "Família Silva")
                    viewModel.createOwnerFamily()
                    awaitState(viewModel) { it is ManagementUiState.Workspace && !it.isLoading }
                    viewModel.selectSection(ManagementSection.RECURRENCES)
                    awaitState(viewModel) { it is ManagementUiState.Workspace && !it.isLoading }

                    viewModel.updateRecurrenceDraft(
                        RecurrenceDraft(
                            description = "Internet",
                            amountText = "100,00",
                            startDateText = java.time.LocalDate.now().toString(),
                        )
                    )
                    viewModel.saveRecurrence()
                    val created = assertIs<ManagementUiState.Workspace>(
                        awaitState(viewModel) {
                            it is ManagementUiState.Workspace &&
                                it.recurrences.singleOrNull()?.occurrences?.size == 12
                        }
                    ).recurrences.single()
                    assertEquals(10_000L, created.occurrences.first().amount.cents)

                    viewModel.selectSection(ManagementSection.EXPENSES)
                    awaitState(viewModel) { it is ManagementUiState.Workspace && !it.isLoading }
                    viewModel.selectSection(ManagementSection.RECURRENCES)
                    val repeatedGeneration = assertIs<ManagementUiState.Workspace>(
                        awaitState(viewModel) {
                            it is ManagementUiState.Workspace &&
                                !it.isLoading &&
                                it.recurrences.singleOrNull()?.occurrences?.size == 12
                        }
                    ).recurrences.single()
                    assertEquals(12, repeatedGeneration.occurrences.size)

                    viewModel.editRecurrence(created.recurrence)
                    val editing = assertIs<ManagementUiState.Workspace>(viewModel.state.value)
                    viewModel.updateRecurrenceDraft(editing.recurrenceDraft.copy(amountText = "125,00"))
                    viewModel.saveRecurrence()
                    val updated = assertIs<ManagementUiState.Workspace>(
                        awaitState(viewModel) {
                            it is ManagementUiState.Workspace &&
                                it.recurrences.singleOrNull()?.recurrence?.amount?.cents == 12_500L
                        }
                    ).recurrences.single()
                    assertTrue(updated.occurrences.all { it.amount.cents == 10_000L })

                    viewModel.cancelRecurrence(updated.recurrence.id)
                    val cancelled = assertIs<ManagementUiState.Workspace>(
                        awaitState(viewModel) {
                            it is ManagementUiState.Workspace &&
                                it.recurrences.singleOrNull()?.recurrence?.isActive == false &&
                                it.recurrenceMessage?.startsWith("Regra cancelada") == true
                        }
                    ).recurrences.single()
                    assertEquals(12, cancelled.occurrences.size)

                    val futureStart = java.time.LocalDate.now().plusYears(3).withDayOfMonth(1)
                    val repository = SqlDelightRecurrenceRepository(AppDatabase(driver), dispatcher)
                    val futureGeneration = GenerateRecurrenceWindowUseCase(
                        repository,
                        SystemEntityMetadataProvider,
                    ).execute(
                        cancelled.recurrence.familyId,
                        LocalDate.parse(futureStart.toString()),
                        LocalDate.parse(futureStart.plusMonths(1).toString()),
                    )
                    assertEquals(0, futureGeneration.getOrThrow())
                } finally {
                    driver.close()
                }
            }
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `payment flow shows balance and registers partial and full payments`() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            runTest {
                val dispatcher = StandardTestDispatcher(testScheduler)
                Dispatchers.setMain(dispatcher)
                val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
                try {
                    AppDatabase.Schema.create(driver)
                    val viewModel = ManagementViewModelFactory.create(AppDatabase(driver), dispatcher)
                    awaitState(viewModel) { it is ManagementUiState.Setup }
                    viewModel.updateSetupNames("Ana", "Família Silva")
                    viewModel.createOwnerFamily()
                    awaitState(viewModel) { it is ManagementUiState.Workspace && !it.isLoading }
                    viewModel.selectSection(ManagementSection.EXPENSES)
                    awaitState(viewModel) { it is ManagementUiState.Workspace && !it.isLoading }
                    viewModel.updateExpenseDraft(
                        OneTimeExpenseDraft(
                            description = "Aluguel",
                            amountText = "100,00",
                            dueDateText = "2026-10-15",
                        )
                    )
                    viewModel.saveOneTimeExpense()
                    val expense = assertIs<ManagementUiState.Workspace>(
                        awaitState(viewModel) {
                            it is ManagementUiState.Workspace && it.expenses.any { item -> item.description == "Aluguel" }
                        }
                    ).expenses.single()

                    viewModel.openPayment(expense)
                    val paymentReady = assertIs<ManagementUiState.Workspace>(
                        awaitState(viewModel) { it is ManagementUiState.Workspace && it.paymentLedger != null }
                    )
                    assertEquals(10_000L, paymentReady.paymentLedger?.balance?.cents)

                    viewModel.updatePaymentDraft(
                        paymentReady.paymentDraft.copy(
                            paidAmountText = "25,00",
                            method = PaymentMethod.PIX,
                        )
                    )
                    viewModel.savePayment()
                    val partial = assertIs<ManagementUiState.Workspace>(
                        awaitState(viewModel) {
                            it is ManagementUiState.Workspace && it.payments.size == 1 &&
                                it.paymentLedger?.balance?.cents == 7_500L
                        }
                    )
                    assertEquals(ExpenseStatus.A_VENCER, partial.expenses.single().status)

                    viewModel.updatePaymentDraft(partial.paymentDraft.copy(paidAmountText = "75,00"))
                    viewModel.savePayment()
                    val settled = assertIs<ManagementUiState.Workspace>(
                        awaitState(viewModel) {
                            it is ManagementUiState.Workspace && it.payments.size == 2 &&
                                it.paymentLedger?.balance == co.jmessias.gestor_familiar_despesas.domain.money.Money.ZERO
                        }
                    )
                    assertEquals(ExpenseStatus.PAGA, settled.expenses.single().status)
                    assertEquals("Pagamento registrado.", settled.paymentMessage)
                } finally {
                    driver.close()
                }
            }
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `installment plan screen creates and observes generated expenses`() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            runTest {
                val dispatcher = StandardTestDispatcher(testScheduler)
                Dispatchers.setMain(dispatcher)
                val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
                try {
                    AppDatabase.Schema.create(driver)
                    val viewModel = ManagementViewModelFactory.create(AppDatabase(driver), dispatcher)
                    awaitState(viewModel) { it is ManagementUiState.Setup }
                    viewModel.updateSetupNames("Ana", "Família Silva")
                    viewModel.createOwnerFamily()
                    awaitState(viewModel) { it is ManagementUiState.Workspace && !it.isLoading }
                    viewModel.selectSection(ManagementSection.INSTALLMENTS)
                    awaitState(viewModel) { it is ManagementUiState.Workspace && !it.isLoading }

                    viewModel.updateInstallmentPlanDraft(
                        InstallmentPlanDraft(
                            description = "Sofá",
                            totalAmountText = "100,00",
                            installmentCountText = "3",
                            firstDueDateText = "2026-10-31",
                        )
                    )
                    viewModel.createInstallmentPlan()
                    val workspace = assertIs<ManagementUiState.Workspace>(
                        awaitState(viewModel) {
                            it is ManagementUiState.Workspace && it.installmentPlans.isNotEmpty()
                        }
                    )
                    val createdPlan = workspace.installmentPlans.single()
                    assertEquals(10_000L, createdPlan.plan.totalAmount.cents)
                    assertEquals(listOf(3334L, 3333L, 3333L), createdPlan.installments.map { it.expense.amount.cents })
                    assertEquals(
                        listOf(ExpenseStatus.A_VENCER, ExpenseStatus.A_VENCER, ExpenseStatus.A_VENCER),
                        createdPlan.installments.map { it.expense.status },
                    )
                } finally {
                    driver.close()
                }
            }
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `setup and category management flow persists and refreshes state`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            AppDatabase.Schema.create(driver)
            val viewModel = ManagementViewModelFactory.create(AppDatabase(driver), dispatcher)
            awaitState(viewModel) { it is ManagementUiState.Setup }

            viewModel.updateSetupNames("Ana", "Família Silva")
            viewModel.createOwnerFamily()
            val workspace = assertIs<ManagementUiState.Workspace>(
                awaitState(viewModel) { it is ManagementUiState.Workspace && !it.isLoading }
            )
            assertEquals("Família Silva", workspace.family.familyName)
            assertFalse(workspace.isLoading)

            viewModel.createCategory("Moradia", "")
            val categoryWorkspace = assertIs<ManagementUiState.Workspace>(
                awaitState(viewModel) {
                    it is ManagementUiState.Workspace && it.categories.any { category -> category.name == "Moradia" }
                }
            )
            assertEquals("Moradia", categoryWorkspace.categories.single().name)

            viewModel.deactivateCategory(categoryWorkspace.categories.single().id)
            assertFalse(
                assertIs<ManagementUiState.Workspace>(
                    awaitState(viewModel) {
                        it is ManagementUiState.Workspace &&
                            it.categories.any { category -> !category.isActive }
                    }
                ).categories.single().isActive
            )

            assertEquals(
                listOf(
                    ManagementSection.CATEGORIES,
                    ManagementSection.EXPENSES,
                    ManagementSection.INSTALLMENTS,
                    ManagementSection.RECURRENCES,
                ),
                ManagementSection.entries,
            )
        } finally {
            driver.close()
            Dispatchers.resetMain()
        }
    }

    private suspend fun awaitState(
        viewModel: ManagementViewModel,
        predicate: (ManagementUiState) -> Boolean,
    ): ManagementUiState = withTimeout(5_000) {
        viewModel.state.first(predicate)
    }
}
