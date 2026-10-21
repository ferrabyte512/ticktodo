package app.ticktodo.ui.common

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import app.ticktodo.core.Clock
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Connectivity @Inject constructor(@ApplicationContext private val context: Context) {

    /** Emits whether the device has a validated internet connection. */
    val isOnline: Flow<Boolean> = callbackFlow {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        fun current(): Boolean = cm.getNetworkCapabilities(cm.activeNetwork)
            ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { trySend(current()) }
            override fun onLost(network: Network) { trySend(current()) }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) { trySend(current()) }
        }
        trySend(current())
        cm.registerDefaultNetworkCallback(callback)
        awaitClose { cm.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()
}

/** Emits today's date and again just after each midnight, so "Today" lists roll over. */
@Singleton
class DayTicker @Inject constructor(private val clock: Clock) {
    val today: Flow<LocalDate> = flow {
        while (true) {
            val today = clock.today()
            emit(today)
            val nextMidnight = today.plusDays(1).atStartOfDay(clock.zone()).toInstant().toEpochMilli()
            delay((nextMidnight - clock.now()).coerceIn(1_000, ChronoUnit.HOURS.duration.toMillis()) + 500)
        }
    }.distinctUntilChanged()
}
