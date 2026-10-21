package app.ticktodo.data.auth

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.util.Base64
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import app.ticktodo.core.AppLog
import app.ticktodo.data.remote.FirebaseProvider
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Google sign-in through Android Credential Manager, exchanged for a Firebase
 * session. The Google password is never seen by the app; only a short-lived
 * ID token is passed to Firebase, which verifies it server-side.
 */
@Singleton
class FirebaseAuthRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val firebase: FirebaseProvider,
) : AuthRepository {

    private val credentialManager by lazy { CredentialManager.create(context) }
    // Firebase restores the signed-in user synchronously, so the first value is already final.
    private val _state = MutableStateFlow(
        if (firebase.isConfigured) firebase.auth.currentUser.toState() else AuthState.SignedOut,
    )
    override val state: StateFlow<AuthState> = _state.asStateFlow()
    override val isConfigured: Boolean get() = firebase.isConfigured

    init {
        if (firebase.isConfigured) {
            firebase.auth.addAuthStateListener { auth -> _state.value = auth.currentUser.toState() }
        }
    }

    override fun currentUid(): String? =
        if (firebase.isConfigured) firebase.auth.currentUser?.uid else null

    override suspend fun signIn(activity: Activity): AuthResult {
        if (!firebase.isConfigured) return AuthResult.NotConfigured
        return when (val token = requestGoogleCredential(activity)) {
            is TokenResult.Ok -> try {
                val user = firebase.auth.signInWithCredential(token.credential).await().user
                    ?: return AuthResult.Failed
                AuthResult.Success(user.toProfile())
            } catch (e: FirebaseNetworkException) {
                AppLog.w("sign-in network failure", e)
                AuthResult.Network
            } catch (e: Exception) {
                AppLog.w("firebase sign-in failed", e)
                AuthResult.Failed
            }
            is TokenResult.Error -> token.result
        }
    }

    override suspend fun reauthenticate(activity: Activity): AuthResult {
        if (!firebase.isConfigured) return AuthResult.NotConfigured
        val user = firebase.auth.currentUser ?: return AuthResult.Failed
        return when (val token = requestGoogleCredential(activity)) {
            is TokenResult.Ok -> try {
                user.reauthenticate(token.credential).await()
                AuthResult.Success(user.toProfile())
            } catch (e: FirebaseAuthInvalidUserException) {
                // Firebase reports a mismatched account as an invalid user.
                AppLog.w("reauthentication with a different account", e)
                AuthResult.WrongAccount
            } catch (e: FirebaseNetworkException) {
                AuthResult.Network
            } catch (e: Exception) {
                AppLog.w("reauthentication failed", e)
                AuthResult.Failed
            }
            is TokenResult.Error -> token.result
        }
    }

    override suspend fun signOut() {
        if (!firebase.isConfigured) return
        firebase.auth.signOut()
        try {
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
        } catch (e: Exception) {
            AppLog.w("could not clear credential state", e)
        }
    }

    override suspend fun deleteCurrentUser() {
        val user = firebase.auth.currentUser ?: return
        user.delete().await()
        signOut()
    }

    private sealed interface TokenResult {
        data class Ok(val credential: AuthCredential) : TokenResult
        data class Error(val result: AuthResult) : TokenResult
    }

    private suspend fun requestGoogleCredential(activity: Activity): TokenResult {
        val clientId = webClientId() ?: return TokenResult.Error(AuthResult.NotConfigured)
        val nonce = newNonce()
        val option = GetSignInWithGoogleOption.Builder(clientId).setNonce(nonce).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        return try {
            val credential = credentialManager.getCredential(activity, request).credential
            if (credential !is CustomCredential ||
                credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                return TokenResult.Error(AuthResult.Failed)
            }
            val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
            if (nonceOf(idToken) != nonce) {
                AppLog.w("ID token nonce mismatch")
                return TokenResult.Error(AuthResult.Failed)
            }
            TokenResult.Ok(GoogleAuthProvider.getCredential(idToken, null))
        } catch (e: GetCredentialCancellationException) {
            TokenResult.Error(AuthResult.Cancelled)
        } catch (e: NoCredentialException) {
            TokenResult.Error(AuthResult.NoAccount)
        } catch (e: GetCredentialException) {
            AppLog.w("credential request failed", e)
            TokenResult.Error(AuthResult.Failed)
        } catch (e: Exception) {
            AppLog.w("unexpected credential response", e)
            TokenResult.Error(AuthResult.Failed)
        }
    }

    /**
     * The OAuth web client ID is generated into resources by the google-services
     * plugin. It is looked up by name so the project compiles without that file.
     */
    @SuppressLint("DiscouragedApi")
    private fun webClientId(): String? {
        val id = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        return if (id == 0) null else context.getString(id).takeIf { it.isNotBlank() }
    }

    private fun newNonce(): String {
        val bytes = ByteArray(32).also(SecureRandom()::nextBytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    private fun FirebaseUser?.toState(): AuthState = if (this == null) AuthState.SignedOut else AuthState.SignedIn(toProfile())

    private fun FirebaseUser.toProfile() = UserProfile(uid, displayName, email)

    companion object {
        /** Reads the `nonce` claim from a JWT payload. Signature checks are done by Firebase. */
        internal fun nonceOf(idToken: String): String? = try {
            val payload = idToken.split('.').getOrNull(1) ?: return null
            val json = String(Base64.decode(payload, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP))
            Json.parseToJsonElement(json).jsonObject["nonce"]?.jsonPrimitive?.content
        } catch (e: Exception) {
            null
        }
    }
}
