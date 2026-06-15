package com.controlgasto.app.data.repository

import com.controlgasto.app.core.UserPreferences
import com.controlgasto.app.domain.model.User
import com.controlgasto.app.domain.repository.AuthRepository
import com.controlgasto.app.domain.repository.CardClosingPeriodRepository
import com.controlgasto.app.domain.repository.CategoryRepository
import com.controlgasto.app.domain.repository.CreditCardRepository
import com.controlgasto.app.domain.repository.ExpenseRepository
import com.controlgasto.app.domain.repository.MonthlyIncomeRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val prefs: UserPreferences,
    private val categoryRepository: CategoryRepository,
    private val creditCardRepository: CreditCardRepository,
    private val cardClosingPeriodRepository: CardClosingPeriodRepository,
    private val expenseRepository: ExpenseRepository,
    private val incomeRepository: MonthlyIncomeRepository
) : AuthRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var proListenerJob: Job? = null

    private val _currentUser = MutableStateFlow<User?>(null)
    override val currentUser: Flow<User?> = _currentUser.asStateFlow()

    init {
        firebaseAuth.currentUser?.let { fbUser ->
            _currentUser.value = User(
                uid = fbUser.uid,
                email = fbUser.email ?: "",
                displayName = fbUser.displayName ?: "",
                photoUrl = fbUser.photoUrl?.toString() ?: ""
            )
            startProModeListener(fbUser.uid)
        }
    }

    override suspend fun login(email: String, password: String): Result<User> {
        return runCatching {
            val result = firebaseAuth.signInWithEmailAndPassword(email, password).await()
            val fbUser = result.user!!
            val user = User(
                uid = fbUser.uid,
                email = fbUser.email ?: "",
                displayName = fbUser.displayName ?: "",
                photoUrl = fbUser.photoUrl?.toString() ?: ""
            )
            _currentUser.value = user
            prefs.saveUserSession(user.uid, user.email, user.displayName)
            ensureUserDocument(fbUser.uid, fbUser.email ?: "")
            startProModeListener(fbUser.uid)
            user
        }
    }

    override suspend fun register(email: String, password: String): Result<User> {
        return runCatching {
            val result = firebaseAuth.createUserWithEmailAndPassword(email, password).await()
            val fbUser = result.user!!
            // Cierra sesión — el usuario debe loguearse manualmente
            firebaseAuth.signOut()
            runCatching {
                firestore.collection("users").document(fbUser.uid).set(
                    mapOf(
                        "email" to fbUser.email,
                        "displayName" to "",
                        "photoUrl" to "",
                        "isPro" to false,
                        "createdAt" to System.currentTimeMillis()
                    )
                ).await()
            }
            User(uid = fbUser.uid, email = fbUser.email ?: "", isPro = false)
        }
    }

    override suspend fun logout() {
        proListenerJob?.cancel()
        proListenerJob = null
        // Sincronizar todo a Room y limpiar Firestore ANTES de cerrar sesión
        categoryRepository.syncToRoomOnLogout()
        expenseRepository.syncToRoomOnLogout()
        creditCardRepository.syncToRoomOnLogout()
        cardClosingPeriodRepository.syncToRoomOnLogout()
        incomeRepository.syncToRoomOnLogout()
        firebaseAuth.signOut()
        _currentUser.value = null
        prefs.clearUserSession()
        prefs.setProMode(false)
    }

    override fun isLoggedIn(): Boolean = firebaseAuth.currentUser != null

    // Escucha en tiempo real isPro y proExpiresAt de Firestore
    private fun startProModeListener(uid: String) {
        proListenerJob?.cancel()
        proListenerJob = scope.launch {
            val registration = firestore.collection("users").document(uid)
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null) return@addSnapshotListener
                    val expiresAt = snapshot.getLong("proExpiresAt") ?: 0L
                    scope.launch {
                        if (expiresAt > 0L) {
                            prefs.setProExpiresAt(expiresAt)
                        } else {
                            // Fallback al campo booleano para compatibilidad
                            val isPro = snapshot.getBoolean("isPro") ?: false
                            prefs.setProMode(isPro)
                        }
                    }
                }
            try {
                kotlinx.coroutines.awaitCancellation()
            } finally {
                registration.remove()
            }
        }
    }

    override suspend fun updateProStatus(isPro: Boolean) {
        val uid = firebaseAuth.currentUser?.uid ?: return
        runCatching {
            firestore.collection("users").document(uid)
                .update("isPro", isPro)
                .await()
        }
    }

    override suspend fun updateProExpiry(expiresAtMs: Long) {
        val uid = firebaseAuth.currentUser?.uid ?: return
        runCatching {
            firestore.collection("users").document(uid)
                .update(
                    mapOf(
                        "proExpiresAt" to expiresAtMs,
                        "isPro" to (expiresAtMs > System.currentTimeMillis())
                    )
                ).await()
        }
    }

    private suspend fun ensureUserDocument(uid: String, email: String) {
        runCatching {
            val ref = firestore.collection("users").document(uid)
            val doc = ref.get().await()
            if (!doc.exists()) {
                ref.set(
                    mapOf(
                        "email" to email,
                        "displayName" to "",
                        "photoUrl" to "",
                        "isPro" to false,
                        "createdAt" to System.currentTimeMillis()
                    )
                ).await()
            }
        }
    }
}
