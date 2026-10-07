package co.jmessias.gestor_familiar_despesas.presentation.management

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.jmessias.gestor_familiar_despesas.domain.bootstrap.FamilyBootstrapRepository
import co.jmessias.gestor_familiar_despesas.domain.bootstrap.CreateOwnerFamilyUseCase
import co.jmessias.gestor_familiar_despesas.domain.bootstrap.LoadOwnerFamilyUseCase
import co.jmessias.gestor_familiar_despesas.domain.bootstrap.OwnerFamilySeed
import co.jmessias.gestor_familiar_despesas.domain.bootstrap.OwnerFamilySetup
import co.jmessias.gestor_familiar_despesas.domain.category.Category
import co.jmessias.gestor_familiar_despesas.domain.category.CategoryRepository
import co.jmessias.gestor_familiar_despesas.domain.category.CreateCategoryUseCase
import co.jmessias.gestor_familiar_despesas.domain.category.DeactivateCategoryUseCase
import co.jmessias.gestor_familiar_despesas.domain.category.ListCategoriesUseCase
import co.jmessias.gestor_familiar_despesas.domain.expense.ExpenseRepository
import co.jmessias.gestor_familiar_despesas.domain.expense.CancelOneTimeExpenseUseCase
import co.jmessias.gestor_familiar_despesas.domain.expense.CreateOneTimeExpenseUseCase
import co.jmessias.gestor_familiar_despesas.domain.expense.Expense
import co.jmessias.gestor_familiar_despesas.domain.expense.ObserveOneTimeExpensesUseCase
import co.jmessias.gestor_familiar_despesas.domain.expense.OneTimeExpenseInput
import co.jmessias.gestor_familiar_despesas.domain.expense.UpdateOneTimeExpenseUseCase
import co.jmessias.gestor_familiar_despesas.domain.installment.CreateInstallmentPlanInput
import co.jmessias.gestor_familiar_despesas.domain.installment.CreateInstallmentPlanUseCase
import co.jmessias.gestor_familiar_despesas.domain.installment.InstallmentRepository
import co.jmessias.gestor_familiar_despesas.domain.installment.InstallmentPlanWithInstallments
import co.jmessias.gestor_familiar_despesas.domain.installment.ObserveInstallmentPlansUseCase
import co.jmessias.gestor_familiar_despesas.domain.model.EntityMetadataProvider
import co.jmessias.gestor_familiar_despesas.domain.model.SystemEntityMetadataProvider
import co.jmessias.gestor_familiar_despesas.domain.money.Money
import co.jmessias.gestor_familiar_despesas.domain.obligation.ExpenseLedger
import co.jmessias.gestor_familiar_despesas.domain.payment.GetExpenseLedgerUseCase
import co.jmessias.gestor_familiar_despesas.domain.payment.ObservePaymentsUseCase
import co.jmessias.gestor_familiar_despesas.domain.payment.Payment
import co.jmessias.gestor_familiar_despesas.domain.payment.PaymentRepository
import co.jmessias.gestor_familiar_despesas.domain.payment.PaymentMethod
import co.jmessias.gestor_familiar_despesas.domain.payment.RecordPaymentInput
import co.jmessias.gestor_familiar_despesas.domain.payment.RecordPaymentUseCase
import co.jmessias.gestor_familiar_despesas.domain.payment.RefreshExpenseStatusesUseCase
import co.jmessias.gestor_familiar_despesas.domain.recurrence.CancelRecurrenceUseCase
import co.jmessias.gestor_familiar_despesas.domain.recurrence.CreateRecurrenceUseCase
import co.jmessias.gestor_familiar_despesas.domain.recurrence.GenerateRecurrenceWindowUseCase
import co.jmessias.gestor_familiar_despesas.domain.recurrence.ObserveRecurrencesUseCase
import co.jmessias.gestor_familiar_despesas.domain.recurrence.Recurrence
import co.jmessias.gestor_familiar_despesas.domain.recurrence.RecurrenceFrequency
import co.jmessias.gestor_familiar_despesas.domain.recurrence.RecurrenceInput
import co.jmessias.gestor_familiar_despesas.domain.recurrence.RecurrenceRepository
import co.jmessias.gestor_familiar_despesas.domain.recurrence.RecurrenceWithOccurrences
import co.jmessias.gestor_familiar_despesas.domain.recurrence.UpdateRecurrenceUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

enum class ManagementSection(val title: String) {
    CATEGORIES("Categorias"),
    EXPENSES("Despesas"),
    INSTALLMENTS("Parcelas"),
    RECURRENCES("Recorrências"),
}

data class RecurrenceDraft(
    val editingRecurrenceId: String? = null,
    val description: String = "",
    val amountText: String = "",
    val frequency: RecurrenceFrequency = RecurrenceFrequency.MONTHLY,
    val startDateText: String = "",
    val dueDayText: String = "",
    val categoryId: String? = null,
    val accountId: String? = null,
)

data class InstallmentPlanDraft(
    val description: String = "",
    val totalAmountText: String = "",
    val installmentCountText: String = "",
    val firstDueDateText: String = "",
)

data class OneTimeExpenseDraft(
    val editingExpenseId: String? = null,
    val description: String = "",
    val amountText: String = "",
    val dueDateText: String = "",
    val categoryId: String? = null,
    val accountId: String? = null,
    val notes: String = "",
)

data class PaymentDraft(
    val paidAmountText: String = "",
    val paidDateText: String = "",
    val method: PaymentMethod = PaymentMethod.PIX,
    val accountId: String? = null,
    val notes: String = "",
)

fun parseAmountInCents(input: String): Long? {
    val normalized = input.trim().replace(',', '.')
    if (!normalized.matches(Regex("""\d+(\.\d{1,2})?"""))) return null
    val parts = normalized.split('.')
    val whole = parts[0].toLongOrNull() ?: return null
    if (whole > Long.MAX_VALUE / 100) return null
    val fractional = parts.getOrElse(1) { "" }.padEnd(2, '0').toLongOrNull() ?: 0L
    val cents = whole * 100
    if (cents > Long.MAX_VALUE - fractional) return null
    return cents + fractional
}

sealed interface ManagementUiState {
    data object Loading : ManagementUiState

    data class Setup(
        val ownerName: String = "",
        val familyName: String = "",
        val isSaving: Boolean = false,
        val errorMessage: String? = null,
    ) : ManagementUiState

    data class Workspace(
        val family: OwnerFamilySetup,
        val section: ManagementSection = ManagementSection.CATEGORIES,
        val categories: List<Category> = emptyList(),
        val expenses: List<Expense> = emptyList(),
        val expenseDraft: OneTimeExpenseDraft = OneTimeExpenseDraft(),
        val installmentPlans: List<InstallmentPlanWithInstallments> = emptyList(),
        val installmentPlanDraft: InstallmentPlanDraft = InstallmentPlanDraft(),
        val recurrences: List<RecurrenceWithOccurrences> = emptyList(),
        val recurrenceDraft: RecurrenceDraft = RecurrenceDraft(),
        val recurrenceMessage: String? = null,
        val paymentExpense: Expense? = null,
        val paymentLedger: ExpenseLedger? = null,
        val paymentDraft: PaymentDraft = PaymentDraft(),
        val payments: List<Payment> = emptyList(),
        val isLoadingPayment: Boolean = false,
        val paymentMessage: String? = null,
        val isLoading: Boolean = false,
        val isSaving: Boolean = false,
        val errorMessage: String? = null,
    ) : ManagementUiState
}

class ManagementViewModel(
    bootstrapRepository: FamilyBootstrapRepository,
    categoryRepository: CategoryRepository,
    expenseRepository: ExpenseRepository,
    paymentRepository: PaymentRepository,
    installmentRepository: InstallmentRepository,
    recurrenceRepository: RecurrenceRepository,
    private val metadataProvider: EntityMetadataProvider = SystemEntityMetadataProvider,
) : ViewModel() {
    private val createFamily = CreateOwnerFamilyUseCase(bootstrapRepository, metadataProvider)
    private val loadFamily = LoadOwnerFamilyUseCase(bootstrapRepository)
    private val listCategories = ListCategoriesUseCase(categoryRepository)
    private val createCategory = CreateCategoryUseCase(categoryRepository, metadataProvider)
    private val deactivateCategory = DeactivateCategoryUseCase(categoryRepository, metadataProvider)
    private val observeExpenses = ObserveOneTimeExpensesUseCase(expenseRepository)
    private val createExpense = CreateOneTimeExpenseUseCase(expenseRepository, metadataProvider)
    private val updateExpense = UpdateOneTimeExpenseUseCase(expenseRepository, metadataProvider)
    private val cancelExpense = CancelOneTimeExpenseUseCase(expenseRepository, metadataProvider)
    private val getExpenseLedger = GetExpenseLedgerUseCase(paymentRepository, metadataProvider)
    private val observePayments = ObservePaymentsUseCase(paymentRepository)
    private val recordPayment = RecordPaymentUseCase(paymentRepository, metadataProvider)
    private val refreshExpenseStatuses = RefreshExpenseStatusesUseCase(paymentRepository, metadataProvider)
    private val createInstallmentPlanUseCase = CreateInstallmentPlanUseCase(installmentRepository, metadataProvider)
    private val observeInstallmentPlansUseCase = ObserveInstallmentPlansUseCase(installmentRepository)
    private val createRecurrence = CreateRecurrenceUseCase(recurrenceRepository, metadataProvider)
    private val updateRecurrence = UpdateRecurrenceUseCase(recurrenceRepository, metadataProvider)
    private val cancelRecurrence = CancelRecurrenceUseCase(recurrenceRepository, metadataProvider)
    private val observeRecurrences = ObserveRecurrencesUseCase(recurrenceRepository)
    private val generateRecurrenceWindow = GenerateRecurrenceWindowUseCase(recurrenceRepository, metadataProvider)

    private val mutableState = MutableStateFlow<ManagementUiState>(ManagementUiState.Loading)
    val state: StateFlow<ManagementUiState> = mutableState.asStateFlow()
    private var expensesObservationJob: Job? = null
    private var paymentsObservationJob: Job? = null
    private var installmentPlansObservationJob: Job? = null
    private var recurrencesObservationJob: Job? = null

    init {
        loadInitialState()
    }

    fun updateSetupNames(ownerName: String, familyName: String) {
        val current = mutableState.value as? ManagementUiState.Setup ?: return
        mutableState.value = current.copy(ownerName = ownerName, familyName = familyName, errorMessage = null)
    }

    fun createOwnerFamily() {
        val current = mutableState.value as? ManagementUiState.Setup ?: return
        if (current.isSaving) return
        mutableState.value = current.copy(isSaving = true, errorMessage = null)

        viewModelScope.launch {
            try {
                val result = createFamily.execute(OwnerFamilySeed(current.ownerName, current.familyName))
                result.fold(
                    onSuccess = { family ->
                        mutableState.value = ManagementUiState.Workspace(family, isLoading = true)
                        refreshSection()
                    },
                    onFailure = { error ->
                        mutableState.value = current.copy(
                            isSaving = false,
                            errorMessage = error.toPresentationMessage("Não foi possível validar os dados."),
                        )
                    },
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                mutableState.value = current.copy(
                    isSaving = false,
                    errorMessage = error.toPresentationMessage("Não foi possível salvar a configuração."),
                )
            }
        }
    }

    fun selectSection(section: ManagementSection) {
        val current = mutableState.value as? ManagementUiState.Workspace ?: return
        if (current.section == section) return
        mutableState.value = current.copy(section = section, isLoading = true, errorMessage = null)
        current.section.takeIf { it == ManagementSection.INSTALLMENTS }?.let {
            installmentPlansObservationJob?.cancel()
        }
        current.section.takeIf { it == ManagementSection.RECURRENCES }?.let {
            recurrencesObservationJob?.cancel()
        }
        refreshSection()
    }

    fun onAppResumed() {
        viewModelScope.launch {
            try {
                refreshExpenseStatuses.execute()
                val familyId = (mutableState.value as? ManagementUiState.Workspace)?.family?.familyId
                if (familyId != null) generateNextTwelveMonths(familyId)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                val message = error.toPresentationMessage("Não foi possível atualizar os status das despesas.")
                when (val current = mutableState.value) {
                    is ManagementUiState.Setup -> mutableState.value = current.copy(errorMessage = message)
                    is ManagementUiState.Workspace -> mutableState.value = current.copy(errorMessage = message)
                    ManagementUiState.Loading -> Unit
                }
            }
        }
    }

    fun createCategory(name: String, icon: String) {
        val current = mutableState.value as? ManagementUiState.Workspace ?: return
        if (current.isSaving) return
        mutableState.value = current.copy(isSaving = true, errorMessage = null)
        viewModelScope.launch {
            try {
                val result = createCategory.execute(current.family.familyId, name, icon)
                result.fold(
                    onSuccess = { loadCategories() },
                    onFailure = {
                        error -> updateWorkspace {
                            it.copy(
                                isSaving = false,
                                errorMessage = error.toPresentationMessage("Não foi possível salvar a categoria."),
                            )
                        }
                    },
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                updateWorkspace {
                    it.copy(isSaving = false, errorMessage = error.toPresentationMessage("Não foi possível salvar a categoria."))
                }
            }
        }
    }

    fun deactivateCategory(categoryId: String) {
        val current = mutableState.value as? ManagementUiState.Workspace ?: return
        viewModelScope.launch {
            try {
                deactivateCategory.execute(current.family.familyId, categoryId).getOrThrow()
                loadCategories()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                updateWorkspace {
                    it.copy(errorMessage = error.toPresentationMessage("Não foi possível desativar a categoria."))
                }
            }
        }
    }

    fun updateExpenseDraft(draft: OneTimeExpenseDraft) {
        updateWorkspace { it.copy(expenseDraft = draft, errorMessage = null) }
    }

    fun editExpense(expense: Expense) {
        updateWorkspace {
            it.copy(
                expenseDraft = OneTimeExpenseDraft(
                    editingExpenseId = expense.id,
                    description = expense.description,
                    amountText = formatAmountForInput(expense.amount.cents),
                    dueDateText = expense.dueDate.toString(),
                    categoryId = expense.categoryId,
                    accountId = expense.accountId,
                    notes = expense.notes.orEmpty(),
                ),
                errorMessage = null,
            )
        }
    }

    fun clearExpenseDraft() {
        updateWorkspace { it.copy(expenseDraft = OneTimeExpenseDraft(), errorMessage = null) }
    }

    fun saveOneTimeExpense() {
        val current = mutableState.value as? ManagementUiState.Workspace ?: return
        if (current.isSaving) return
        val draft = current.expenseDraft
        val amountCents = parseAmountInCents(draft.amountText)
        if (amountCents == null || amountCents <= 0) {
            updateWorkspace { it.copy(errorMessage = "Informe um valor maior que zero, com até duas casas decimais.") }
            return
        }
        val dueDate = try {
            LocalDate.parse(draft.dueDateText.trim())
        } catch (_: IllegalArgumentException) {
            updateWorkspace { it.copy(errorMessage = "Informe uma data válida no formato AAAA-MM-DD.") }
            return
        }

        mutableState.value = current.copy(isSaving = true, errorMessage = null)
        viewModelScope.launch {
            try {
                val result = if (draft.editingExpenseId == null) {
                    createExpense.execute(
                        OneTimeExpenseInput(
                            familyId = current.family.familyId,
                            userId = current.family.userId,
                            categoryId = draft.categoryId,
                            accountId = draft.accountId,
                            description = draft.description,
                            amount = Money.ofCents(amountCents),
                            dueDate = dueDate,
                            notes = draft.notes,
                        )
                    )
                } else {
                    updateExpense.execute(
                        familyId = current.family.familyId,
                        expenseId = draft.editingExpenseId,
                        updatedBy = current.family.userId,
                        categoryId = draft.categoryId,
                        accountId = draft.accountId,
                        description = draft.description,
                        amount = Money.ofCents(amountCents),
                        dueDate = dueDate,
                        notes = draft.notes,
                    )
                }
                result.fold(
                    onSuccess = {
                        updateWorkspace { it.copy(isSaving = false, expenseDraft = OneTimeExpenseDraft()) }
                    },
                    onFailure = { error ->
                        updateWorkspace {
                            it.copy(
                                isSaving = false,
                                errorMessage = error.toPresentationMessage("Não foi possível salvar a despesa."),
                            )
                        }
                    },
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                updateWorkspace {
                    it.copy(isSaving = false, errorMessage = error.toPresentationMessage("Não foi possível salvar a despesa."))
                }
            }
        }
    }

    fun cancelOneTimeExpense(expenseId: String) {
        val current = mutableState.value as? ManagementUiState.Workspace ?: return
        viewModelScope.launch {
            try {
                cancelExpense.execute(current.family.familyId, expenseId, current.family.userId).getOrThrow()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                updateWorkspace {
                    it.copy(errorMessage = error.toPresentationMessage("Não foi possível cancelar a despesa."))
                }
            }
        }
    }

    fun openPayment(expense: Expense) {
        val current = mutableState.value as? ManagementUiState.Workspace ?: return
        paymentsObservationJob?.cancel()
        updateWorkspace {
            it.copy(
                paymentExpense = expense,
                paymentLedger = null,
                paymentDraft = PaymentDraft(paidDateText = metadataProvider.today().toString()),
                payments = emptyList(),
                isLoadingPayment = true,
                paymentMessage = null,
                errorMessage = null,
            )
        }
        viewModelScope.launch {
            try {
                val ledger = getExpenseLedger.execute(current.family.familyId, expense.id).getOrThrow()
                updateWorkspace {
                    it.copy(
                        paymentLedger = ledger,
                        paymentDraft = it.paymentDraft.copy(paidAmountText = formatAmountForInput(ledger.balance.cents)),
                        isLoadingPayment = false,
                    )
                }
                paymentsObservationJob = viewModelScope.launch {
                    observePayments.execute(current.family.familyId, expense.id).collect { payments ->
                        updateWorkspace { it.copy(payments = payments) }
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                updateWorkspace {
                    it.copy(
                        isLoadingPayment = false,
                        errorMessage = error.toPresentationMessage("Não foi possível carregar o saldo da despesa."),
                    )
                }
            }
        }
    }

    fun updatePaymentDraft(draft: PaymentDraft) {
        updateWorkspace { it.copy(paymentDraft = draft, errorMessage = null, paymentMessage = null) }
    }

    fun closePayment() {
        paymentsObservationJob?.cancel()
        updateWorkspace {
            it.copy(
                paymentExpense = null,
                paymentLedger = null,
                paymentDraft = PaymentDraft(),
                payments = emptyList(),
                isLoadingPayment = false,
                paymentMessage = null,
                errorMessage = null,
            )
        }
    }

    fun savePayment() {
        val current = mutableState.value as? ManagementUiState.Workspace ?: return
        val expense = current.paymentExpense ?: return
        if (current.isSaving || current.isLoadingPayment) return
        val amountCents = parseAmountInCents(current.paymentDraft.paidAmountText)
        if (amountCents == null || amountCents <= 0) {
            updateWorkspace { it.copy(errorMessage = "Informe um pagamento maior que zero, com até duas casas decimais.") }
            return
        }
        val paidDate = try {
            LocalDate.parse(current.paymentDraft.paidDateText.trim())
        } catch (_: IllegalArgumentException) {
            updateWorkspace { it.copy(errorMessage = "Informe uma data de pagamento válida no formato AAAA-MM-DD.") }
            return
        }

        updateWorkspace { it.copy(isSaving = true, errorMessage = null, paymentMessage = null) }
        viewModelScope.launch {
            try {
                recordPayment.execute(
                    RecordPaymentInput(
                        familyId = current.family.familyId,
                        expenseId = expense.id,
                        userId = current.family.userId,
                        paidAmount = Money.ofCents(amountCents),
                        paidDate = paidDate,
                        method = current.paymentDraft.method,
                        accountId = current.paymentDraft.accountId,
                        notes = current.paymentDraft.notes,
                    )
                ).getOrThrow()
                val ledger = getExpenseLedger.execute(current.family.familyId, expense.id).getOrThrow()
                updateWorkspace {
                    it.copy(
                        isSaving = false,
                        paymentLedger = ledger,
                        paymentDraft = it.paymentDraft.copy(paidAmountText = formatAmountForInput(ledger.balance.cents)),
                        paymentMessage = "Pagamento registrado.",
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                updateWorkspace {
                    it.copy(
                        isSaving = false,
                        errorMessage = error.toPresentationMessage("Não foi possível registrar o pagamento."),
                    )
                }
            }
        }
    }

    fun updateInstallmentPlanDraft(draft: InstallmentPlanDraft) {
        updateWorkspace { it.copy(installmentPlanDraft = draft, errorMessage = null) }
    }

    fun createInstallmentPlan() {
        val current = mutableState.value as? ManagementUiState.Workspace ?: return
        if (current.isSaving) return
        val totalCents = parseAmountInCents(current.installmentPlanDraft.totalAmountText)
        if (totalCents == null || totalCents <= 0) {
            updateWorkspace { it.copy(errorMessage = "Informe um valor total maior que zero, com até duas casas decimais.") }
            return
        }
        val count = current.installmentPlanDraft.installmentCountText.trim().toIntOrNull()
        if (count == null || count <= 0) {
            updateWorkspace { it.copy(errorMessage = "Informe uma quantidade de parcelas válida.") }
            return
        }
        val firstDueDate = try {
            LocalDate.parse(current.installmentPlanDraft.firstDueDateText.trim())
        } catch (_: IllegalArgumentException) {
            updateWorkspace { it.copy(errorMessage = "Informe a primeira data de vencimento no formato AAAA-MM-DD.") }
            return
        }

        updateWorkspace { it.copy(isSaving = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                createInstallmentPlanUseCase.execute(
                    CreateInstallmentPlanInput(
                        familyId = current.family.familyId,
                        userId = current.family.userId,
                        description = current.installmentPlanDraft.description,
                        totalAmount = Money.ofCents(totalCents),
                        installmentCount = count,
                        firstDueDate = firstDueDate,
                        categoryId = null,
                        accountId = null,
                    )
                ).getOrThrow()
                updateWorkspace {
                    it.copy(
                        isSaving = false,
                        installmentPlanDraft = InstallmentPlanDraft(),
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                updateWorkspace {
                    it.copy(
                        isSaving = false,
                        errorMessage = error.toPresentationMessage("Não foi possível criar o parcelamento."),
                    )
                }
            }
        }
    }

    fun updateRecurrenceDraft(draft: RecurrenceDraft) {
        updateWorkspace { it.copy(recurrenceDraft = draft, errorMessage = null, recurrenceMessage = null) }
    }

    fun editRecurrence(recurrence: Recurrence) {
        updateWorkspace {
            it.copy(
                recurrenceDraft = RecurrenceDraft(
                    editingRecurrenceId = recurrence.id,
                    description = recurrence.description,
                    amountText = formatAmountForInput(recurrence.amount.cents),
                    frequency = recurrence.frequency,
                    startDateText = recurrence.startDate.toString(),
                    dueDayText = recurrence.dueDay?.toString().orEmpty(),
                    categoryId = recurrence.categoryId,
                    accountId = recurrence.accountId,
                ),
                errorMessage = null,
                recurrenceMessage = null,
            )
        }
    }

    fun clearRecurrenceDraft() {
        updateWorkspace { it.copy(recurrenceDraft = RecurrenceDraft(), errorMessage = null) }
    }

    fun saveRecurrence() {
        val current = mutableState.value as? ManagementUiState.Workspace ?: return
        if (current.isSaving) return
        val draft = current.recurrenceDraft
        val amountCents = parseAmountInCents(draft.amountText)
        if (amountCents == null || amountCents <= 0) {
            updateWorkspace { it.copy(errorMessage = "Informe um valor maior que zero, com até duas casas decimais.") }
            return
        }
        val startDate = try {
            LocalDate.parse(draft.startDateText.trim())
        } catch (_: IllegalArgumentException) {
            updateWorkspace { it.copy(errorMessage = "Informe uma data inicial válida no formato AAAA-MM-DD.") }
            return
        }
        val dueDay = if (draft.dueDayText.isBlank()) null else draft.dueDayText.trim().toIntOrNull()
        if (draft.dueDayText.isNotBlank() && (dueDay == null || dueDay !in 1..31)) {
            updateWorkspace { it.copy(errorMessage = "O dia de vencimento deve estar entre 1 e 31.") }
            return
        }
        val input = RecurrenceInput(
            familyId = current.family.familyId,
            userId = current.family.userId,
            description = draft.description,
            amount = Money.ofCents(amountCents),
            categoryId = draft.categoryId,
            accountId = draft.accountId,
            frequency = draft.frequency,
            startDate = startDate,
            dueDay = dueDay,
        )
        updateWorkspace { it.copy(isSaving = true, errorMessage = null, recurrenceMessage = null) }
        viewModelScope.launch {
            try {
                if (draft.editingRecurrenceId == null) {
                    createRecurrence.execute(input).getOrThrow()
                } else {
                    updateRecurrence.execute(draft.editingRecurrenceId, input).getOrThrow()
                }
                generateNextTwelveMonths(current.family.familyId)
                updateWorkspace {
                    it.copy(
                        isSaving = false,
                        recurrenceDraft = RecurrenceDraft(),
                        recurrenceMessage = "Regra recorrente salva.",
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                updateWorkspace {
                    it.copy(
                        isSaving = false,
                        errorMessage = error.toPresentationMessage("Não foi possível salvar a recorrência."),
                    )
                }
            }
        }
    }

    fun cancelRecurrence(recurrenceId: String) {
        val current = mutableState.value as? ManagementUiState.Workspace ?: return
        viewModelScope.launch {
            try {
                cancelRecurrence.execute(
                    familyId = current.family.familyId,
                    recurrenceId = recurrenceId,
                    userId = current.family.userId,
                ).getOrThrow()
                updateWorkspace { it.copy(recurrenceMessage = "Regra cancelada; ocorrências existentes foram preservadas.") }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                updateWorkspace {
                    it.copy(errorMessage = error.toPresentationMessage("Não foi possível cancelar a recorrência."))
                }
            }
        }
    }

    private fun loadInitialState() {
        viewModelScope.launch {
            try {
                loadFamily.execute().fold(
                    onSuccess = { family ->
                        if (family == null) {
                            mutableState.value = ManagementUiState.Setup()
                        } else {
                            mutableState.value = ManagementUiState.Workspace(family, isLoading = true)
                            refreshSection()
                        }
                    },
                    onFailure = { error ->
                        mutableState.value = ManagementUiState.Setup(
                            errorMessage = error.toPresentationMessage("Não foi possível carregar a família."),
                        )
                    },
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                mutableState.value = ManagementUiState.Setup(
                    errorMessage = error.toPresentationMessage("Não foi possível carregar a configuração."),
                )
            }
        }
    }

    private fun refreshSection() {
        when ((mutableState.value as? ManagementUiState.Workspace)?.section) {
            ManagementSection.CATEGORIES -> loadCategories()
            ManagementSection.EXPENSES -> observeExpenses()
            ManagementSection.INSTALLMENTS -> observeInstallmentPlans()
            ManagementSection.RECURRENCES -> observeRecurrenceRules()
            null -> Unit
        }
    }

    private fun observeRecurrenceRules() {
        val current = mutableState.value as? ManagementUiState.Workspace ?: return
        recurrencesObservationJob?.cancel()
        recurrencesObservationJob = viewModelScope.launch {
            try {
                val categories = listCategories.execute(current.family.familyId).getOrThrow()
                generateNextTwelveMonths(current.family.familyId)
                observeRecurrences.execute(current.family.familyId).collect { recurrences ->
                    updateWorkspace {
                        it.copy(
                            categories = categories,
                            recurrences = recurrences,
                            isLoading = false,
                            isSaving = false,
                            errorMessage = null,
                        )
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                updateWorkspace {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.toPresentationMessage("Não foi possível carregar recorrências."),
                    )
                }
            }
        }
    }

    private suspend fun generateNextTwelveMonths(familyId: String) {
        val from = metadataProvider.today()
        val through = addMonths(from, 12)
        generateRecurrenceWindow.execute(familyId, from, through).getOrThrow()
    }

    private fun observeInstallmentPlans() {
        val current = mutableState.value as? ManagementUiState.Workspace ?: return
        installmentPlansObservationJob?.cancel()
        installmentPlansObservationJob = viewModelScope.launch {
            try {
                observeInstallmentPlansUseCase.execute(current.family.familyId).collect { plans ->
                    updateWorkspace {
                        it.copy(installmentPlans = plans, isLoading = false, isSaving = false, errorMessage = null)
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                updateWorkspace {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.toPresentationMessage("Não foi possível carregar parcelamentos."),
                    )
                }
            }
        }
    }

    private fun observeExpenses() {
        val current = mutableState.value as? ManagementUiState.Workspace ?: return
        expensesObservationJob?.cancel()
        expensesObservationJob = viewModelScope.launch {
            try {
                refreshExpenseStatuses.execute()
                val flow = observeExpenses.execute(current.family.familyId).getOrThrow()
                flow.collect { expenses ->
                    updateWorkspace {
                        it.copy(expenses = expenses, isLoading = false, isSaving = false, errorMessage = null)
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                updateWorkspace {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.toPresentationMessage("Não foi possível carregar despesas."),
                    )
                }
            }
        }
    }

    private fun loadCategories() {
        val current = mutableState.value as? ManagementUiState.Workspace ?: return
        viewModelScope.launch {
            try {
                val categories = listCategories.execute(current.family.familyId).getOrThrow()
                updateWorkspace { it.copy(categories = categories, isLoading = false, isSaving = false, errorMessage = null) }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                updateWorkspace {
                    it.copy(
                        isLoading = false,
                        isSaving = false,
                        errorMessage = error.toPresentationMessage("Não foi possível carregar categorias."),
                    )
                }
            }
        }
    }

    private inline fun updateWorkspace(transform: (ManagementUiState.Workspace) -> ManagementUiState.Workspace) {
        val current = mutableState.value as? ManagementUiState.Workspace ?: return
        mutableState.value = transform(current)
    }

    private fun formatAmountForInput(cents: Long): String {
        val whole = cents / 100
        val fractional = (cents % 100).toString().padStart(2, '0')
        return "$whole,$fractional"
    }

    private fun addMonths(date: LocalDate, count: Int): LocalDate {
        val monthIndex = date.year.toLong() * 12L + date.month.ordinal + count
        val year = (monthIndex / 12L).toInt()
        val month = (monthIndex % 12L).toInt() + 1
        val leapYear = year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)
        val daysInMonth = when (month) {
            2 -> if (leapYear) 29 else 28
            4, 6, 9, 11 -> 30
            else -> 31
        }
        return LocalDate(year, month, minOf(date.day, daysInMonth))
    }
}

internal fun Throwable.toPresentationMessage(fallback: String): String = when (this) {
    is IllegalArgumentException, is NoSuchElementException ->
        message?.takeIf(String::isNotBlank) ?: fallback
    else -> fallback
}
