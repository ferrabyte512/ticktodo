package app.ticktodo.data.auth

import android.app.Activity
import kotlinx.coroutines.flow.StateFlow

data class UserProfile(
    val uid: String,
    val displayName: String?,
    val email: String?,
)

sealed interface AuthState {
    data object Loading : AuthState
    data object SignedOut : AuthState
    data class SignedIn(val user: UserProfile) : AuthState
}

sealed interface AuthResult {
    data class Success(val user: UserProfile) : AuthResult
    data object Cancelled : AuthResult
    /** No Google account on the device, or none the user can pick. */
    data object NoAccount : AuthResult
    data object NotConfigured : AuthResult
    data object Network : AuthResult
    /** Re-authentication picked a different account than the signed-in one. */
    data object WrongAccount : AuthResult
    data object Failed : AuthResult
}

interface AuthRepository {
    val state: StateFlow<AuthState>
    val isConfigured: Boolean

    fun currentUid(): String?

    /** Shows the Google account picker and signs in to Firebase. */
    suspend fun signIn(activity: Activity): AuthResult

    /** Asks the user to confirm their Google account again; needed before deleting the account. */
    suspend fun reauthenticate(activity: Activity): AuthResult

    suspend fun signOut()

    /** Deletes the Firebase Auth user. Call [reauthenticate] first. */
    suspend fun deleteCurrentUser()
}
