package co.jmessias.gestor_familiar_despesas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import co.jmessias.gestor_familiar_despesas.presentation.management.ManagementViewModel
import co.jmessias.gestor_familiar_despesas.domain.expense.Expense
import co.jmessias.gestor_familiar_despesas.domain.payment.PaymentMethod
import co.jmessias.gestor_familiar_despesas.domain.obligation.ExpenseStatus
import co.jmessias.gestor_familiar_despesas.presentation.management.OneTimeExpenseDraft
import co.jmessias.gestor_familiar_despesas.presentation.management.PaymentDraft
import co.jmessias.gestor_familiar_despesas.presentation.management.InstallmentPlanDraft
import co.jmessias.gestor_familiar_despesas.presentation.management.RecurrenceDraft
import co.jmessias.gestor_familiar_despesas.domain.recurrence.RecurrenceFrequency
import co.jmessias.gestor_familiar_despesas.presentation.management.ManagementSection
import co.jmessias.gestor_familiar_despesas.presentation.management.ManagementUiState

@Composable
fun App(createViewModel: () -> ManagementViewModel) {
    val factory = remember(createViewModel) {
        viewModelFactory {
            initializer { createViewModel() }
        }
    }
    val viewModel: ManagementViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsState()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.onAppResumed()
    }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            when (val current = state) {
                ManagementUiState.Loading -> LoadingScreen()
                is ManagementUiState.Setup -> SetupScreen(
                    state = current,
                    onNamesChanged = viewModel::updateSetupNames,
                    onSubmit = viewModel::createOwnerFamily,
                )
                is ManagementUiState.Workspace -> WorkspaceScreen(
                    state = current,
                    onSectionSelected = viewModel::selectSection,
                    onCreateCategory = viewModel::createCategory,
                    onDeactivateCategory = viewModel::deactivateCategory,
                    onExpenseDraftChanged = viewModel::updateExpenseDraft,
                    onSaveExpense = viewModel::saveOneTimeExpense,
                    onEditExpense = viewModel::editExpense,
                    onCancelExpense = viewModel::cancelOneTimeExpense,
                    onClearExpenseDraft = viewModel::clearExpenseDraft,
                    onPayExpense = viewModel::openPayment,
                    onPaymentDraftChanged = viewModel::updatePaymentDraft,
                    onSavePayment = viewModel::savePayment,
                    onClosePayment = viewModel::closePayment,
                    onInstallmentDraftChanged = viewModel::updateInstallmentPlanDraft,
                    onCreateInstallmentPlan = viewModel::createInstallmentPlan,
                    onRecurrenceDraftChanged = viewModel::updateRecurrenceDraft,
                    onSaveRecurrence = viewModel::saveRecurrence,
                    onEditRecurrence = viewModel::editRecurrence,
                    onCancelRecurrence = viewModel::cancelRecurrence,
                    onClearRecurrenceDraft = viewModel::clearRecurrenceDraft,
                )
            }
        }
    }
}

@Composable
private fun LoadingScreen() {
    Column(
        modifier = Modifier.fillMaxSize().safeContentPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text("Carregando dados locais…")
    }
}

@Composable
private fun SetupScreen(
    state: ManagementUiState.Setup,
    onNamesChanged: (String, String) -> Unit,
    onSubmit: () -> Unit,
) {
    var ownerName by remember { mutableStateOf(state.ownerName) }
    var familyName by remember { mutableStateOf(state.familyName) }

    Column(
        modifier = Modifier.fillMaxSize().safeContentPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Vamos configurar sua família", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text("Esses dados ficam salvos neste dispositivo.")
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = ownerName,
            onValueChange = {
                ownerName = it
                onNamesChanged(ownerName, familyName)
            },
            label = { Text("Seu nome") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = familyName,
            onValueChange = {
                familyName = it
                onNamesChanged(ownerName, familyName)
            },
            label = { Text("Nome da família") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        state.errorMessage?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onSubmit,
            enabled = !state.isSaving,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.isSaving) {
                CircularProgressIndicator()
            } else {
                Text("Continuar")
            }
        }
    }
}

@Composable
private fun WorkspaceScreen(
    state: ManagementUiState.Workspace,
    onSectionSelected: (ManagementSection) -> Unit,
    onCreateCategory: (String, String) -> Unit,
    onDeactivateCategory: (String) -> Unit,
    onExpenseDraftChanged: (OneTimeExpenseDraft) -> Unit,
    onSaveExpense: () -> Unit,
    onEditExpense: (Expense) -> Unit,
    onCancelExpense: (String) -> Unit,
    onClearExpenseDraft: () -> Unit,
    onPayExpense: (Expense) -> Unit,
    onPaymentDraftChanged: (PaymentDraft) -> Unit,
    onSavePayment: () -> Unit,
    onClosePayment: () -> Unit,
    onInstallmentDraftChanged: (InstallmentPlanDraft) -> Unit,
    onCreateInstallmentPlan: () -> Unit,
    onRecurrenceDraftChanged: (RecurrenceDraft) -> Unit,
    onSaveRecurrence: () -> Unit,
    onEditRecurrence: (co.jmessias.gestor_familiar_despesas.domain.recurrence.Recurrence) -> Unit,
    onCancelRecurrence: (String) -> Unit,
    onClearRecurrenceDraft: () -> Unit,
) {
    Scaffold { contentPadding ->
        Column(
            modifier = Modifier.fillMaxSize().safeContentPadding().padding(contentPadding),
        ) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                Text("Gestor familiar", style = MaterialTheme.typography.headlineSmall)
                Text("${state.family.ownerName} · ${state.family.familyName}")
            }
            PrimaryTabRow(selectedTabIndex = state.section.ordinal) {
                ManagementSection.entries.forEach { section ->
                    Tab(
                        selected = state.section == section,
                        onClick = { onSectionSelected(section) },
                        text = {
                            Text(
                                text = section.title,
                                maxLines = 1,
                                softWrap = false,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    letterSpacing = 0.sp,
                                ),
                            )
                        },
                    )
                }
            }
            state.errorMessage?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
            if (state.isLoading) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else {
                when (state.section) {
                    ManagementSection.CATEGORIES -> CategoryPanel(state, onCreateCategory, onDeactivateCategory)
                    ManagementSection.EXPENSES -> ExpensePanel(
                        state = state,
                        onDraftChanged = onExpenseDraftChanged,
                        onSave = onSaveExpense,
                        onEdit = onEditExpense,
                        onCancel = onCancelExpense,
                        onClearDraft = onClearExpenseDraft,
                        onPay = onPayExpense,
                    )
                    ManagementSection.INSTALLMENTS -> InstallmentPlanPanel(
                        state = state,
                        onDraftChanged = onInstallmentDraftChanged,
                        onCreate = onCreateInstallmentPlan,
                    )
                    ManagementSection.RECURRENCES -> RecurrencePanel(
                        state = state,
                        onDraftChanged = onRecurrenceDraftChanged,
                        onSave = onSaveRecurrence,
                        onEdit = onEditRecurrence,
                        onCancel = onCancelRecurrence,
                        onClearDraft = onClearRecurrenceDraft,
                        onPayExpense = onPayExpense,
                    )
                }
            }
        }
        state.paymentExpense?.let {
            PaymentDialog(
                state = state,
                onDraftChanged = onPaymentDraftChanged,
                onSave = onSavePayment,
                onDismiss = onClosePayment,
            )
        }
    }
}

@Composable
private fun RecurrencePanel(
    state: ManagementUiState.Workspace,
    onDraftChanged: (RecurrenceDraft) -> Unit,
    onSave: () -> Unit,
    onEdit: (co.jmessias.gestor_familiar_despesas.domain.recurrence.Recurrence) -> Unit,
    onCancel: (String) -> Unit,
    onClearDraft: () -> Unit,
    onPayExpense: (Expense) -> Unit,
) {
    val draft = state.recurrenceDraft
    var frequencyMenuExpanded by remember { mutableStateOf(false) }
    var categoryMenuExpanded by remember { mutableStateOf(false) }
    var recurrenceToCancel by remember { mutableStateOf<String?>(null) }
    val activeCategories = state.categories.filter { it.isActive }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = draft.description,
            onValueChange = { onDraftChanged(draft.copy(description = it)) },
            label = { Text("Descrição") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = draft.amountText,
            onValueChange = { onDraftChanged(draft.copy(amountText = it)) },
            label = { Text("Valor por ocorrência (R$)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedButton(
            onClick = { frequencyMenuExpanded = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Frequência: ${draft.frequency.label()}")
        }
        DropdownMenu(
            expanded = frequencyMenuExpanded,
            onDismissRequest = { frequencyMenuExpanded = false },
        ) {
            RecurrenceFrequency.entries.forEach { frequency ->
                DropdownMenuItem(
                    text = { Text(frequency.label()) },
                    onClick = {
                        onDraftChanged(draft.copy(frequency = frequency))
                        frequencyMenuExpanded = false
                    },
                )
            }
        }
        OutlinedTextField(
            value = draft.startDateText,
            onValueChange = { onDraftChanged(draft.copy(startDateText = it)) },
            label = { Text("Início (AAAA-MM-DD)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = draft.dueDayText,
            onValueChange = { onDraftChanged(draft.copy(dueDayText = it)) },
            label = { Text("Dia do vencimento (opcional, 1–31)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedButton(
            onClick = { categoryMenuExpanded = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Categoria: ${activeCategories.firstOrNull { it.id == draft.categoryId }?.name ?: "Nenhuma"}")
        }
        DropdownMenu(expanded = categoryMenuExpanded, onDismissRequest = { categoryMenuExpanded = false }) {
            DropdownMenuItem(
                text = { Text("Nenhuma") },
                onClick = {
                    onDraftChanged(draft.copy(categoryId = null))
                    categoryMenuExpanded = false
                },
            )
            activeCategories.forEach { category ->
                DropdownMenuItem(
                    text = { Text(category.name) },
                    onClick = {
                        onDraftChanged(draft.copy(categoryId = category.id))
                        categoryMenuExpanded = false
                    },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onSave,
                enabled = !state.isSaving,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    when {
                        state.isSaving -> "Salvando…"
                        draft.editingRecurrenceId != null -> "Salvar alterações"
                        else -> "Criar recorrência"
                    }
                )
            }
            if (draft.editingRecurrenceId != null) {
                OutlinedButton(onClick = onClearDraft, enabled = !state.isSaving) {
                    Text("Cancelar edição")
                }
            }
        }
        state.recurrenceMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        if (state.recurrences.isEmpty()) {
            Text("Ainda não há regras recorrentes.")
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.recurrences, key = { it.recurrence.id }) { item ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(
                            Modifier.fillMaxWidth().padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(item.recurrence.description, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "R$ ${formatCents(item.recurrence.amount.cents)} · ${item.recurrence.frequency.label()} · " +
                                    (if (item.recurrence.isActive) "Ativa" else "Cancelada")
                            )
                            if (item.recurrence.isActive) {
                                Row {
                                    TextButton(onClick = { onEdit(item.recurrence) }) { Text("Editar") }
                                    TextButton(onClick = { recurrenceToCancel = item.recurrence.id }) { Text("Cancelar regra") }
                                }
                            }
                            if (item.occurrences.isEmpty()) {
                                Text("Nenhuma ocorrência gerada.")
                            } else {
                                item.occurrences.forEach { expense ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            "${expense.dueDate} · R$ ${formatCents(expense.amount.cents)} · ${expense.status.label()}",
                                            modifier = Modifier.weight(1f),
                                        )
                                        if (expense.status != ExpenseStatus.PAGA && expense.status != ExpenseStatus.CANCELADA) {
                                            TextButton(onClick = { onPayExpense(expense) }) { Text("Pagar") }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    recurrenceToCancel?.let { recurrenceId ->
        AlertDialog(
            onDismissRequest = { recurrenceToCancel = null },
            title = { Text("Cancelar recorrência?") },
            text = { Text("As ocorrências já geradas serão preservadas; nenhuma nova será criada.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onCancel(recurrenceId)
                        recurrenceToCancel = null
                    }
                ) { Text("Cancelar regra") }
            },
            dismissButton = {
                TextButton(onClick = { recurrenceToCancel = null }) { Text("Voltar") }
            },
        )
    }
}

private fun RecurrenceFrequency.label(): String = when (this) {
    RecurrenceFrequency.MONTHLY -> "Mensal"
    RecurrenceFrequency.QUARTERLY -> "Trimestral"
    RecurrenceFrequency.YEARLY -> "Anual"
}

@Composable
private fun InstallmentPlanPanel(
    state: ManagementUiState.Workspace,
    onDraftChanged: (InstallmentPlanDraft) -> Unit,
    onCreate: () -> Unit,
) {
    val draft = state.installmentPlanDraft

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = draft.description,
            onValueChange = { onDraftChanged(draft.copy(description = it)) },
            label = { Text("Descrição da compra") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = draft.totalAmountText,
            onValueChange = { onDraftChanged(draft.copy(totalAmountText = it)) },
            label = { Text("Valor total (R$)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = draft.installmentCountText,
            onValueChange = { onDraftChanged(draft.copy(installmentCountText = it)) },
            label = { Text("Quantidade de parcelas") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = draft.firstDueDateText,
            onValueChange = { onDraftChanged(draft.copy(firstDueDateText = it)) },
            label = { Text("Primeiro vencimento (AAAA-MM-DD)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = onCreate,
            enabled = !state.isSaving,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (state.isSaving) "Salvando…" else "Criar parcelamento")
        }
        Spacer(Modifier.height(4.dp))
        if (state.installmentPlans.isEmpty()) {
            Text("Ainda não há parcelamentos.")
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.installmentPlans, key = { it.plan.id }) { planWithInstallments ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(
                            Modifier.fillMaxWidth().padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(planWithInstallments.plan.description, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Total R$ ${formatCents(planWithInstallments.plan.totalAmount.cents)} · " +
                                    "${planWithInstallments.installments.size} parcelas"
                            )
                            planWithInstallments.installments.forEach { installment ->
                                Text(
                                    "${installment.number}/${installment.totalInstallments} · " +
                                        "R$ ${formatCents(installment.expense.amount.cents)} · " +
                                        "vence ${installment.expense.dueDate} · ${installment.expense.status.label()}"
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpensePanel(
    state: ManagementUiState.Workspace,
    onDraftChanged: (OneTimeExpenseDraft) -> Unit,
    onSave: () -> Unit,
    onEdit: (Expense) -> Unit,
    onCancel: (String) -> Unit,
    onClearDraft: () -> Unit,
    onPay: (Expense) -> Unit,
) {
            val draft = state.expenseDraft
            var categoryMenuExpanded by remember { mutableStateOf(false) }
            var expenseToCancel by remember { mutableStateOf<Expense?>(null) }
            val activeCategories = state.categories.filter { it.isActive }

            Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = draft.description,
                    onValueChange = { onDraftChanged(draft.copy(description = it)) },
                    label = { Text("Descrição") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = draft.amountText,
                    onValueChange = { onDraftChanged(draft.copy(amountText = it)) },
                    label = { Text("Valor (R$)") },
                    placeholder = { Text("0,00") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = draft.dueDateText,
                    onValueChange = { onDraftChanged(draft.copy(dueDateText = it)) },
                    label = { Text("Vencimento (AAAA-MM-DD)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = draft.notes,
                    onValueChange = { onDraftChanged(draft.copy(notes = it)) },
                    label = { Text("Observações (opcional)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { categoryMenuExpanded = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Categoria: ${activeCategories.firstOrNull { it.id == draft.categoryId }?.name ?: "Nenhuma"}")
                }
                DropdownMenu(
                    expanded = categoryMenuExpanded,
                    onDismissRequest = { categoryMenuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Nenhuma") },
                        onClick = {
                            onDraftChanged(draft.copy(categoryId = null))
                            categoryMenuExpanded = false
                        },
                    )
                    activeCategories.forEach { category ->
                        DropdownMenuItem(
                            text = { Text(category.name) },
                            onClick = {
                                onDraftChanged(draft.copy(categoryId = category.id))
                                categoryMenuExpanded = false
                            },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onSave,
                        enabled = !state.isSaving,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            when {
                                state.isSaving -> "Salvando…"
                                draft.editingExpenseId != null -> "Salvar alterações"
                                else -> "Adicionar despesa"
                            }
                        )
                    }
                    if (draft.editingExpenseId != null) {
                        OutlinedButton(onClick = onClearDraft, enabled = !state.isSaving) {
                            Text("Cancelar edição")
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                if (state.expenses.isEmpty()) {
                    Text("Ainda não há despesas avulsas.")
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(state.expenses, key = { it.id }) { expense ->
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                                    Text(expense.description, style = MaterialTheme.typography.titleMedium)
                                    Text("R$ ${formatCents(expense.amount.cents)} · vence ${expense.dueDate}")
                                    Text(expense.status.label())
                                    if (expense.status != ExpenseStatus.PAGA && expense.status != ExpenseStatus.CANCELADA) {
                                        Row {
                                            TextButton(onClick = { onEdit(expense) }) { Text("Editar") }
                                            TextButton(onClick = { expenseToCancel = expense }) { Text("Cancelar despesa") }
                                        }
                                        TextButton(onClick = { onPay(expense) }) { Text("Registrar pagamento") }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            expenseToCancel?.let { expense ->
                AlertDialog(
                    onDismissRequest = { expenseToCancel = null },
                    title = { Text("Cancelar despesa?") },
                    text = { Text("“${expense.description}” permanecerá no histórico como cancelada.") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                onCancel(expense.id)
                                expenseToCancel = null
                            }
                        ) { Text("Cancelar despesa") }
                    },
                    dismissButton = {
                        TextButton(onClick = { expenseToCancel = null }) { Text("Voltar") }
                    },
                )
            }
        }

private fun formatCents(cents: Long): String {
    val whole = cents / 100
    val fractional = (cents % 100).toString().padStart(2, '0')
    return "$whole,$fractional"
}

private fun ExpenseStatus.label(): String = when (this) {
    ExpenseStatus.A_VENCER -> "A vencer"
    ExpenseStatus.VENCIDA -> "Vencida"
    ExpenseStatus.PAGA -> "Paga"
    ExpenseStatus.CANCELADA -> "Cancelada"
}

@Composable
private fun PaymentDialog(
    state: ManagementUiState.Workspace,
    onDraftChanged: (PaymentDraft) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    val expense = state.paymentExpense ?: return
    val draft = state.paymentDraft
    var methodMenuExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pagamento: ${expense.description}") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (state.isLoadingPayment) {
                    CircularProgressIndicator()
                } else {
                    state.paymentLedger?.let { ledger ->
                        Text("Saldo atual: R$ ${formatCents(ledger.balance.cents)}")
                    }
                    if (state.payments.isNotEmpty()) {
                        Text("Pagamentos registrados: ${state.payments.size}")
                        state.payments.forEach { payment ->
                            Text("${payment.paidDate} · R$ ${formatCents(payment.paidAmount.cents)} · ${payment.method.label()}")
                        }
                    }
                    OutlinedTextField(
                        value = draft.paidAmountText,
                        onValueChange = { onDraftChanged(draft.copy(paidAmountText = it)) },
                        label = { Text("Valor pago (R$)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = draft.paidDateText,
                        onValueChange = { onDraftChanged(draft.copy(paidDateText = it)) },
                        label = { Text("Data do pagamento (AAAA-MM-DD)") },
                        singleLine = true,
                    )
                    OutlinedButton(
                        onClick = { methodMenuExpanded = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Forma: ${draft.method.label()}")
                    }
                    DropdownMenu(
                        expanded = methodMenuExpanded,
                        onDismissRequest = { methodMenuExpanded = false },
                    ) {
                        PaymentMethod.entries.forEach { method ->
                            DropdownMenuItem(
                                text = { Text(method.label()) },
                                onClick = {
                                    onDraftChanged(draft.copy(method = method))
                                    methodMenuExpanded = false
                                },
                            )
                        }
                    }
                    OutlinedTextField(
                        value = draft.notes,
                        onValueChange = { onDraftChanged(draft.copy(notes = it)) },
                        label = { Text("Observações (opcional)") },
                    )
                    state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    state.paymentMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onSave,
                enabled = !state.isLoadingPayment && !state.isSaving &&
                    state.paymentLedger?.balance?.let { it > co.jmessias.gestor_familiar_despesas.domain.money.Money.ZERO } == true,
            ) {
                Text(if (state.isSaving) "Salvando…" else "Registrar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Fechar") }
        },
    )
}

private fun PaymentMethod.label(): String = when (this) {
    PaymentMethod.PIX -> "PIX"
    PaymentMethod.CASH -> "Dinheiro"
    PaymentMethod.DEBIT_CARD -> "Cartão de débito"
    PaymentMethod.CREDIT_CARD -> "Cartão de crédito"
    PaymentMethod.BANK_TRANSFER -> "Transferência"
    PaymentMethod.BOLETO -> "Boleto"
    PaymentMethod.OTHER -> "Outro"
}

@Composable
private fun CategoryPanel(
    state: ManagementUiState.Workspace,
    onCreateCategory: (String, String) -> Unit,
    onDeactivateCategory: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var icon by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Nome da categoria") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = icon,
            onValueChange = { icon = it },
            label = { Text("Ícone (opcional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                onCreateCategory(name, icon)
            },
            enabled = !state.isSaving,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (state.isSaving) "Salvando…" else "Adicionar categoria")
        }
        Spacer(Modifier.height(12.dp))
        if (state.categories.isEmpty()) {
            Text("Ainda não há categorias. Cadastre a primeira acima.")
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.categories, key = { it.id }) { category ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(category.name, style = MaterialTheme.typography.titleMedium)
                                Text(if (category.isActive) "Ativa" else "Inativa")
                            }
                            if (category.isActive) {
                                TextButton(onClick = { onDeactivateCategory(category.id) }) {
                                    Text("Desativar")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
