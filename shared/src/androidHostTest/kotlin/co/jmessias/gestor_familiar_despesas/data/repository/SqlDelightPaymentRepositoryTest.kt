package co.jmessias.gestor_familiar_despesas.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import co.jmessias.gestor_familiar_despesas.db.AppDatabase
import co.jmessias.gestor_familiar_despesas.domain.expense.Expense
import co.jmessias.gestor_familiar_despesas.domain.expense.ExpenseType
import co.jmessias.gestor_familiar_despesas.domain.money.Money
import co.jmessias.gestor_familiar_despesas.domain.obligation.ExpenseStatus
import co.jmessias.gestor_familiar_despesas.domain.payment.Payment
import co.jmessias.gestor_familiar_despesas.domain.payment.PaymentMethod
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SqlDelightPaymentRepositoryTest {

    @Test
    fun `payments are transactional partial balances status changes and terminal states persist`() = runBlocking {
        val (driver, database) = createDatabase()
        try {
            val expenseRepository = SqlDelightExpenseRepository(database)
            val paymentRepository = SqlDelightPaymentRepository(database)
            val expense = expense(dueDate = LocalDate(2026, 10, 6))
            expenseRepository.insert(expense)

            paymentRepository.refreshStatuses(LocalDate(2026, 10, 5))
            assertEquals(ExpenseStatus.A_VENCER, expenseRepository.observeOneTimeExpenses(expense.familyId).first().single().status)
            paymentRepository.refreshStatuses(LocalDate(2026, 10, 6))
            assertEquals(ExpenseStatus.A_VENCER, expenseRepository.observeOneTimeExpenses(expense.familyId).first().single().status)
            paymentRepository.refreshStatuses(LocalDate(2026, 10, 7))
            assertEquals(ExpenseStatus.VENCIDA, expenseRepository.observeOneTimeExpenses(expense.familyId).first().single().status)

            assertTrue(paymentRepository.recordPayment(payment(expense, "payment-1", 300), LocalDate(2026, 10, 7)))
            val partialLedger = assertNotNull(
                paymentRepository.findLedger(expense.familyId, expense.id, LocalDate(2026, 10, 7))
            )
            assertEquals(700L, partialLedger.balance.cents)
            assertEquals(ExpenseStatus.VENCIDA, partialLedger.status)
            assertEquals(1, paymentRepository.observePayments(expense.familyId, expense.id).first().size)

            assertFalse(paymentRepository.recordPayment(payment(expense, "payment-over", 701), LocalDate(2026, 10, 7)))
            assertEquals(1, paymentRepository.observePayments(expense.familyId, expense.id).first().size)
            assertTrue(paymentRepository.recordPayment(payment(expense, "payment-2", 700), LocalDate(2026, 10, 7)))
            assertEquals(
                ExpenseStatus.PAGA,
                paymentRepository.findLedger(expense.familyId, expense.id, LocalDate(2026, 10, 7))?.status,
            )
            assertEquals(ExpenseStatus.PAGA, expenseRepository.observeOneTimeExpenses(expense.familyId).first().single().status)

            val cancelled = expense.copy(id = "expense-cancelled")
            expenseRepository.insert(cancelled)
            assertTrue(
                expenseRepository.cancelUnpaidOneTimeExpense(
                    cancelled.familyId,
                    cancelled.id,
                    cancelled.updatedBy,
                    updatedTimestamp,
                )
            )
            assertFalse(paymentRepository.recordPayment(payment(cancelled, "payment-cancelled", 100), LocalDate(2026, 10, 7)))
        } finally {
            driver.close()
        }
    }

    @Test
    fun `payment transaction rejects a user or account outside the family`() = runBlocking {
        val (driver, database) = createDatabase()
        try {
            val expenseRepository = SqlDelightExpenseRepository(database)
            val paymentRepository = SqlDelightPaymentRepository(database)
            val expense = expense(dueDate = LocalDate(2026, 10, 15))
            expenseRepository.insert(expense)

            assertFalse(
                paymentRepository.recordPayment(
                    payment(expense, "payment-invalid-user", 100).copy(createdBy = "missing-user"),
                    LocalDate(2026, 10, 6),
                )
            )
            assertFalse(
                paymentRepository.recordPayment(
                    payment(expense, "payment-invalid-account", 100).copy(accountId = "missing-account"),
                    LocalDate(2026, 10, 6),
                )
            )
            assertTrue(paymentRepository.observePayments(expense.familyId, expense.id).first().isEmpty())
        } finally {
            driver.close()
        }
    }

    private fun createDatabase(): Pair<JdbcSqliteDriver, AppDatabase> {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        AppDatabase.Schema.create(driver)
        driver.execute(null, "PRAGMA foreign_keys = ON", 0)
        val database = AppDatabase(driver)
        val queries = database.appDatabaseQueries
        queries.insertUser("user-1", "Ana", null, timestamp, timestamp)
        queries.insertFamily("family-1", "Família", timestamp, timestamp)
        queries.insertFamilyMember("member-1", "family-1", "user-1", "OWNER", timestamp, timestamp)
        queries.insertAccount("account-1", "family-1", "Banco", "BANK", null, 1L, timestamp, timestamp)
        return driver to database
    }

    private fun expense(dueDate: LocalDate) = Expense(
        id = "expense-1",
        familyId = "family-1",
        categoryId = null,
        accountId = "account-1",
        description = "Mercado",
        amount = Money.ofCents(1000),
        dueDate = dueDate,
        type = ExpenseType.ONE_TIME,
        status = ExpenseStatus.A_VENCER,
        notes = null,
        recurrenceId = null,
        createdBy = "user-1",
        updatedBy = "user-1",
        createdAt = timestamp,
        updatedAt = timestamp,
        deletedAt = null,
    )

    private fun payment(expense: Expense, id: String, cents: Long) = Payment(
        id = id,
        familyId = expense.familyId,
        expenseId = expense.id,
        paidAmount = Money.ofCents(cents),
        paidDate = LocalDate(2026, 10, 7),
        method = PaymentMethod.PIX,
        accountId = "account-1",
        interestAmount = Money.ZERO,
        fineAmount = Money.ZERO,
        discountAmount = Money.ZERO,
        notes = null,
        createdBy = "user-1",
        createdAt = timestamp,
        updatedAt = timestamp,
    )

    private companion object {
        const val timestamp = "2026-10-06T12:00:00Z"
        const val updatedTimestamp = "2026-10-06T13:00:00Z"
    }
}
