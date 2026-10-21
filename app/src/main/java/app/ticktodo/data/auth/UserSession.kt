package app.ticktodo.data.auth

import app.ticktodo.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

class NotSignedInException : IllegalStateException("No signed-in user")

/** The uid every local query and remote call is scoped to. Null when signed out. */
interface UserSession {
    val uid: StateFlow<String?>
    fun requireUid(): String = uid.value ?: throw NotSignedInException()
}

@Singleton
class AuthUserSession @Inject constructor(
    auth: AuthRepository,
    @ApplicationScope scope: CoroutineScope,
) : UserSession {
    override val uid: StateFlow<String?> = auth.state
        .filterNot { it is AuthState.Loading }
        .map { (it as? AuthState.SignedIn)?.user?.uid }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, auth.currentUid())
}
