package app.ticktodo.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/** Time source; replaced in tests so date-based logic is deterministic. */
interface Clock {
    fun now(): Long
    fun zone(): ZoneId
    fun today(): LocalDate = Instant.ofEpochMilli(now()).atZone(zone()).toLocalDate()
}

@Singleton
class SystemClock @Inject constructor() : Clock {
    override fun now(): Long = System.currentTimeMillis()
    override fun zone(): ZoneId = ZoneId.systemDefault()
}
