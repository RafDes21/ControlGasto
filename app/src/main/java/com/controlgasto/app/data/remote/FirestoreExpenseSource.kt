package com.controlgasto.app.data.remote

import com.controlgasto.app.domain.model.Expense
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreExpenseSource @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    private fun userExpenses(uid: String) =
        firestore.collection("users").document(uid).collection("expenses")

    fun getExpenses(uid: String): Flow<List<Expense>> = callbackFlow {
        val listener = userExpenses(uid)
            .orderBy("date", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                val list = snapshot?.documents?.mapNotNull { doc ->
                    runCatching { doc.toExpense() }.getOrNull()
                } ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    suspend fun addExpense(uid: String, expense: Expense) {
        userExpenses(uid).document(expense.id).set(expense.toMap()).await()
    }

    suspend fun updateExpense(uid: String, expense: Expense) {
        userExpenses(uid).document(expense.id).set(expense.toMap()).await()
    }

    suspend fun deleteExpense(uid: String, expense: Expense) {
        userExpenses(uid).document(expense.id).delete().await()
    }

    suspend fun getExpenseById(uid: String, id: String): Expense? {
        val doc = userExpenses(uid).document(id).get().await()
        return if (doc.exists()) doc.toExpense() else null
    }

    suspend fun getExpensesOnce(uid: String): List<Expense> =
        userExpenses(uid).get().await().documents.mapNotNull { doc ->
            runCatching { doc.toExpense() }.getOrNull()
        }

    suspend fun deleteAllExpenses(uid: String) {
        userExpenses(uid).get().await().documents.forEach { it.reference.delete().await() }
    }

    suspend fun deleteByCardId(uid: String, cardId: String) {
        userExpenses(uid)
            .whereEqualTo("cardId", cardId)
            .get().await()
            .documents.forEach { it.reference.delete().await() }
    }

    private fun com.google.firebase.firestore.DocumentSnapshot.toExpense() = Expense(
        id = id,
        title = getString("title") ?: "",
        amount = getDouble("amount") ?: 0.0,
        categoryId = getString("categoryId") ?: "",
        date = getLong("date") ?: 0L,
        note = getString("note") ?: "",
        cardId = getString("cardId"),
        totalInstallments = (getLong("totalInstallments") ?: 1L).toInt(),
        installmentDueDay = (getLong("installmentDueDay") ?: 0L).toInt(),
        isRecurring = getBoolean("isRecurring") ?: false,
        durationMonths = (getLong("durationMonths") ?: 0L).toInt(),
        expenseMonth = getString("expenseMonth") ?: "",
        billingPeriod = getString("billingPeriod")
    )

    private fun Expense.toMap() = mapOf(
        "title" to title,
        "amount" to amount,
        "categoryId" to categoryId,
        "date" to date,
        "note" to note,
        "cardId" to cardId,
        "totalInstallments" to totalInstallments,
        "installmentDueDay" to installmentDueDay,
        "isRecurring" to isRecurring,
        "durationMonths" to durationMonths,
        "expenseMonth" to expenseMonth,
        "billingPeriod" to billingPeriod
    )
}
