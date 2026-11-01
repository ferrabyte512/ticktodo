package app.ticktodo.di

import android.content.Context
import androidx.room.Room
import app.ticktodo.core.Clock
import app.ticktodo.core.SystemClock
import app.ticktodo.data.auth.AuthRepository
import app.ticktodo.data.auth.AuthUserSession
import app.ticktodo.data.auth.FirebaseAuthRepository
import app.ticktodo.data.auth.UserSession
import app.ticktodo.data.local.AppDatabase
import app.ticktodo.data.remote.FirestoreRemoteStore
import app.ticktodo.data.remote.RemoteStore
import app.ticktodo.data.repository.TaskChangeEffects
import app.ticktodo.ads.AdsGateway
import app.ticktodo.ads.DisabledAds
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineExceptionHandler
import app.ticktodo.core.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

/** Scope for work that must outlive a screen, such as finishing a write after the user navigates away. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    @ApplicationScope
    fun applicationScope(): CoroutineScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default +
            // Background work (sync requests, alarm re-arming, widget updates) must never take the process down.
            CoroutineExceptionHandler { _, e -> AppLog.e("background work failed", e) },
    )

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            // Schema changes must ship a Migration; never silently drop user data.
            .build()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class BindingsModule {
    @Binds abstract fun clock(impl: SystemClock): Clock
    @Binds abstract fun auth(impl: FirebaseAuthRepository): AuthRepository
    @Binds abstract fun session(impl: AuthUserSession): UserSession
    @Binds abstract fun remote(impl: FirestoreRemoteStore): RemoteStore
    @Binds abstract fun effects(impl: DefaultTaskChangeEffects): TaskChangeEffects
    @Binds abstract fun ads(impl: DisabledAds): AdsGateway
}
