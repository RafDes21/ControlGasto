package com.controlgasto.app.data.repository

import com.controlgasto.app.data.local.dao.ExpenseDao
import com.controlgasto.app.data.local.entity.toDomain
import com.controlgasto.app.data.local.entity.toEntity
import com.controlgasto.app.data.remote.FirestoreExpenseSource
import com.controlgasto.app.domain.model.Expense
import com.controlgasto.app.domain.repository.CardClosingPeriodRepository
import com.controlgasto.app.domain.repository.ExpenseRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExpenseRepositoryImpl @Inject constructor(
    private val dao: ExpenseDao,
    private val firestoreSource: FirestoreExpenseSource,
    private val firebaseAuth: FirebaseAuth,
    private val cardClosingPeriodRepository: CardClosingPeriodRepository
) : ExpenseRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val uid get() = firebaseAuth.currentUser?.uid

    private val authStateFlow: Flow<String?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { auth -> trySend(auth.currentUser?.uid) }
        firebaseAuth.addAuthStateListener(listener)
        awaitClose { firebaseAuth.removeAuthStateListener(listener) }
    }.distinctUntilChanged()

    init {
        scope.launch {
            authStateFlow.collect { uid ->
                if (uid != null) migrateRoomToFirestore()
            }
        }
    }

    override fun getExpenses(): Flow<List<Expense>> =
        authStateFlow.flatMapLatest { uid ->
            if (uid != null) firestoreSource.getExpenses(uid)
            else dao.getAll().map { it.map { e -> e.toDomain() } }
        }

    override fun getCreditExpensesByDueMonth(monthStart: Long, monthEnd: Long): Flow<List<Expense>> =
        authStateFlow.flatMapLatest { uid ->
            if (uid != null) {
                combine(
                    firestoreSource.getExpenses(uid),
                    cardClosingPeriodRepository.getPeriodsForDueDateInMonthFlow(monthStart, monthEnd)
                ) { expenses, periods ->
                    val periodKeys = periods.map { "${it.cardId}:${it.month}" }.toSet()
                    expenses.filter { expense ->
                        if (expense.cardId == null || expense.billingPeriod == null) return@filter false
                        if ("${expense.cardId}:${expense.billingPeriod}" in periodKeys) return@filter true
                        if (expense.isInstallment) {
                            val parts = expense.billingPeriod!!.split("-")
                            val bpYear = parts[0].toIntOrNull() ?: return@filter false
                            val bpMonth = parts[1].toIntOrNull() ?: return@filter false
                            val bpTotal = bpYear * 12 + bpMonth
                            for (n in 1 until expense.totalInstallments) {
                                val st = bpTotal + n
                                val sy = (st - 1) / 12
                                val sm = (st - 1) % 12 + 1
                                if ("${expense.cardId}:${"%04d".format(sy)}-${"%02d".format(sm)}" in periodKeys) return@filter true
                            }
                        }
                        false
                    }
                }
            } else {
                combine(
                    dao.getCreditExpensesByDueMonth(monthStart, monthEnd),
                    dao.getCardInstallmentExpenses(),
                    cardClosingPeriodRepository.getPeriodsForDueDateInMonthFlow(monthStart, monthEnd)
                ) { firstMonth, allCardInstallments, periods ->
                    val firstMonthIds = firstMonth.map { it.id }.toSet()
                    val periodKeys = periods.map { "${it.cardId}:${it.month}" }.toSet()
                    val additional = allCardInstallments.filter { entity ->
                        if (entity.id in firstMonthIds || entity.billingPeriod == null) return@filter false
                        val parts = entity.billingPeriod!!.split("-")
                        val bpYear = parts[0].toIntOrNull() ?: return@filter false
                        val bpMonth = parts[1].toIntOrNull() ?: return@filter false
                        val bpTotal = bpYear * 12 + bpMonth
                        for (n in 1 until entity.totalInstallments) {
                            val st = bpTotal + n
                            val sy = (st - 1) / 12
                            val sm = (st - 1) % 12 + 1
                            if ("${entity.cardId}:${"%04d".format(sy)}-${"%02d".format(sm)}" in periodKeys) return@filter true
                        }
                        false
                    }
                    (firstMonth.map { it.toDomain() } + additional.map { it.toDomain() })
                }
            }
        }

    override fun getExpensesByMonth(year: String, month: String): Flow<List<Expense>> =
        authStateFlow.flatMapLatest { uid ->
            if (uid != null) {
                firestoreSource.getExpenses(uid).map { list ->
                    filterExpensesForMonth(list, year, month)
                }
            } else {
                combine(
                    dao.getByMonth(year, month),
                    dao.getInstallmentExpenses(),
                    dao.getRecurringExpenses()
                ) { regular, installments, recurring ->
                    val regularDomain = regular.map { it.toDomain() }
                    val installmentDomain = installments
                        .filter { it.cardId == null && isInstallmentInMonth(it.date, it.totalInstallments, year, month) }
                        .map { it.toDomain() }
                    val recurringDomain = recurring
                        .filter { isRecurringInMonth(it.date, it.durationMonths, year, month) }
                        .map { it.toDomain() }
                    (regularDomain + installmentDomain + recurringDomain).sortedByDescending { it.date }
                }
            }
        }

    override suspend fun addExpense(expense: Expense) {
        val currentUid = uid
        if (currentUid != null) firestoreSource.addExpense(currentUid, expense)
        else dao.insert(expense.toEntity())
    }

    override suspend fun updateExpense(expense: Expense) {
        val currentUid = uid
        if (currentUid != null) firestoreSource.updateExpense(currentUid, expense)
        else dao.update(expense.toEntity())
    }

    override suspend fun deleteExpense(expense: Expense) {
        val currentUid = uid
        if (currentUid != null) firestoreSource.deleteExpense(currentUid, expense)
        else dao.delete(expense.toEntity())
    }

    override suspend fun getExpenseById(id: String): Expense? {
        val currentUid = uid
        return if (currentUid != null) firestoreSource.getExpenseById(currentUid, id)
        else dao.getById(id)?.toDomain()
    }

    override suspend fun downgradeExpenses(deletedCardIds: Set<String>) {
        val currentUid = uid ?: return
        runCatching {
            val firestoreExpenses = firestoreSource.getExpensesOnce(currentUid)
            firestoreExpenses
                .filter { it.cardId == null || it.cardId !in deletedCardIds }
                .forEach { dao.insert(it.toEntity()) }
            firestoreSource.deleteAllExpenses(currentUid)
        }
    }

    override suspend fun deleteExpensesByCardId(cardId: String) {
        val currentUid = uid
        if (currentUid != null) firestoreSource.deleteByCardId(currentUid, cardId)
        else dao.deleteByCardId(cardId)
    }

    private suspend fun migrateRoomToFirestore() {
        val currentUid = uid ?: return
        val roomExpenses = dao.getAllOnce()
        if (roomExpenses.isEmpty()) return
        runCatching {
            val existing = firestoreSource.getExpensesOnce(currentUid)
            roomExpenses.forEach { entity ->
                if (existing.none { it.id == entity.id }) {
                    firestoreSource.addExpense(currentUid, entity.toDomain())
                }
            }
            dao.deleteAll()
        }
    }

    private fun filterExpensesForMonth(list: List<Expense>, year: String, month: String): List<Expense> =
        list.filter { expense ->
            when {
                expense.isRecurring -> isRecurringInMonth(expense.date, expense.durationMonths, year, month)
                expense.isInstallment && expense.cardId == null -> isInstallmentInMonth(expense.date, expense.totalInstallments, year, month)
                expense.isInstallment -> false  // card installments handled via getCreditExpensesByDueMonth
                else -> {
                    val cal = Calendar.getInstance().apply { timeInMillis = expense.date }
                    String.format("%04d", cal.get(Calendar.YEAR)) == year &&
                    String.format("%02d", cal.get(Calendar.MONTH) + 1) == month
                }
            }
        }

    private fun isInstallmentInMonth(date: Long, totalInstallments: Int, year: String, month: String): Boolean {
        val cal = Calendar.getInstance().apply { timeInMillis = date }
        val startTotal = cal.get(Calendar.YEAR) * 12 + (cal.get(Calendar.MONTH) + 1)
        val endTotal = startTotal + totalInstallments - 1
        val targetTotal = year.toInt() * 12 + month.toInt()
        return targetTotal in startTotal..endTotal
    }

    private fun isRecurringInMonth(date: Long, durationMonths: Int, year: String, month: String): Boolean {
        val cal = Calendar.getInstance().apply { timeInMillis = date }
        val startTotal = cal.get(Calendar.YEAR) * 12 + (cal.get(Calendar.MONTH) + 1)
        val targetTotal = year.toInt() * 12 + month.toInt()
        if (targetTotal < startTotal) return false
        if (durationMonths <= 0) return true
        return targetTotal <= startTotal + durationMonths - 1
    }
}
