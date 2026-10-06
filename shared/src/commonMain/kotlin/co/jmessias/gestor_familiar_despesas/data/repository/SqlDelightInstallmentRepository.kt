package co.jmessias.gestor_familiar_despesas.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import co.jmessias.gestor_familiar_despesas.db.AppDatabase
import co.jmessias.gestor_familiar_despesas.domain.expense.Expense
import co.jmessias.gestor_familiar_despesas.domain.expense.ExpenseType
import co.jmessias.gestor_familiar_despesas.domain.installment.Installment
import co.jmessias.gestor_familiar_despesas.domain.installment.InstallmentPlan
import co.jmessias.gestor_familiar_despesas.domain.installment.InstallmentPlanWithInstallments
import co.jmessias.gestor_familiar_despesas.domain.installment.InstallmentRepository
import co.jmessias.gestor_familiar_despesas.domain.money.Money
import co.jmessias.gestor_familiar_despesas.domain.obligation.ExpenseStatus
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate

class SqlDelightInstallmentRepository(
    private val database: AppDatabase,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : InstallmentRepository {
    private val queries = database.appDatabaseQueries

    override suspend fun userBelongsToFamily(familyId: String, userId: String): Boolean =
        withContext(dispatcher) {
            queries.selectUserBelongsToFamily(family_id = familyId, user_id = userId).executeAsOne()
        }

    override suspend fun activeCategoryBelongsToFamily(familyId: String, categoryId: String): Boolean =
        withContext(dispatcher) {
            queries.selectActiveCategoryBelongsToFamily(
                family_id = familyId,
                category_id = categoryId,
            ).executeAsOne()
        }

    override suspend fun activeAccountBelongsToFamily(familyId: String, accountId: String): Boolean =
        withContext(dispatcher) {
            queries.selectActiveAccountBelongsToFamily(
                family_id = familyId,
                account_id = accountId,
            ).executeAsOne()
        }

    override suspend fun createPlan(
        plan: InstallmentPlan,
        installments: List<Installment>,
    ) = withContext(dispatcher) {
        require(installments.size == plan.installmentCount)
        require(installments.withIndex().all { (index, item) -> item.number == index + 1 })
        require(installments.sumOf { it.expense.amount.cents } == plan.totalAmount.cents)
        require(
            installments.all {
                it.planId == plan.id &&
                    it.totalInstallments == plan.installmentCount &&
                    it.expense.familyId == plan.familyId &&
                    it.expense.type == ExpenseType.INSTALLMENT &&
                    it.expense.createdBy == plan.createdBy
            }
        )
        database.transaction {
            require(
                queries.selectUserBelongsToFamily(
                    family_id = plan.familyId,
                    user_id = plan.createdBy,
                ).executeAsOne()
            ) { "Usuário não pertence à família selecionada." }
            if (plan.categoryId != null) {
                require(
                    queries.selectActiveCategoryBelongsToFamily(
                        family_id = plan.familyId,
                        category_id = plan.categoryId,
                    ).executeAsOne()
                ) { "A categoria deve estar ativa e pertencer à família selecionada." }
            }
            if (plan.accountId != null) {
                require(
                    queries.selectActiveAccountBelongsToFamily(
                        family_id = plan.familyId,
                        account_id = plan.accountId,
                    ).executeAsOne()
                ) { "A conta deve estar ativa e pertencer à família selecionada." }
            }
            queries.insertInstallmentPlan(
                id = plan.id,
                family_id = plan.familyId,
                description = plan.description,
                total_amount_in_cents = plan.totalAmount.cents,
                installment_count = plan.installmentCount.toLong(),
                first_due_date = plan.firstDueDate.toString(),
                category_id = plan.categoryId,
                account_id = plan.accountId,
                created_by = plan.createdBy,
                created_at = plan.createdAt,
                updated_at = plan.updatedAt,
            )
            installments.forEach { installment ->
                val expense = installment.expense
                queries.insertInstallmentExpense(
                    id = expense.id,
                    family_id = expense.familyId,
                    category_id = expense.categoryId,
                    account_id = expense.accountId,
                    description = expense.description,
                    amount_in_cents = expense.amount.cents,
                    due_date = expense.dueDate.toString(),
                    status = expense.status.name,
                    notes = expense.notes,
                    created_by = expense.createdBy,
                    updated_by = expense.updatedBy,
                    created_at = expense.createdAt,
                    updated_at = expense.updatedAt,
                )
                queries.insertInstallment(
                    id = installment.id,
                    installment_plan_id = installment.planId,
                    expense_id = expense.id,
                    number = installment.number.toLong(),
                    total_installments = installment.totalInstallments.toLong(),
                    created_at = installment.createdAt,
                    updated_at = installment.updatedAt,
                )
            }
        }
        Unit
    }

    override fun observePlans(familyId: String): Flow<List<InstallmentPlanWithInstallments>> =
        queries.selectInstallmentPlansByFamily(family_id = familyId)
            .asFlow()
            .mapToList(dispatcher)
            .map { rows ->
                rows.groupBy { it.plan_id }.values.map { planRows ->
                    val row = planRows.first()
                    InstallmentPlanWithInstallments(
                        plan = InstallmentPlan(
                            id = row.plan_id,
                            familyId = row.plan_family_id,
                            description = row.plan_description,
                            totalAmount = Money.ofCents(row.plan_total_amount_in_cents),
                            installmentCount = row.plan_installment_count.toInt(),
                            firstDueDate = LocalDate.parse(row.plan_first_due_date),
                            categoryId = row.plan_category_id,
                            accountId = row.plan_account_id,
                            createdBy = row.plan_created_by,
                            createdAt = row.plan_created_at,
                            updatedAt = row.plan_updated_at,
                        ),
                        installments = planRows.map { installmentRow ->
                            Installment(
                                id = installmentRow.installment_id,
                                planId = installmentRow.plan_id,
                                expense = Expense(
                                    id = installmentRow.expense_id,
                                    familyId = installmentRow.expense_family_id,
                                    categoryId = installmentRow.expense_category_id,
                                    accountId = installmentRow.expense_account_id,
                                    description = installmentRow.expense_description,
                                    amount = Money.ofCents(installmentRow.expense_amount_in_cents),
                                    dueDate = LocalDate.parse(installmentRow.expense_due_date),
                                    type = ExpenseType.valueOf(installmentRow.expense_type),
                                    status = ExpenseStatus.valueOf(installmentRow.expense_status),
                                    notes = installmentRow.expense_notes,
                                    recurrenceId = installmentRow.expense_recurrence_id,
                                    createdBy = installmentRow.expense_created_by,
                                    updatedBy = installmentRow.expense_updated_by,
                                    createdAt = installmentRow.expense_created_at,
                                    updatedAt = installmentRow.expense_updated_at,
                                    deletedAt = installmentRow.expense_deleted_at,
                                ),
                                number = installmentRow.installment_number.toInt(),
                                totalInstallments = installmentRow.installment_total.toInt(),
                                createdAt = installmentRow.installment_created_at,
                                updatedAt = installmentRow.installment_updated_at,
                            )
                        },
                    )
                }
            }
}
