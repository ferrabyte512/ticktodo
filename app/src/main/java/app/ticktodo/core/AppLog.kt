package app.ticktodo.core

import android.util.Log
import app.ticktodo.BuildConfig

/**
 * Logging wrapper. Rules:
 *  - Never pass task titles, notes, tags, e-mail addresses or tokens here.
 *  - Debug messages are dropped in release builds (and stripped by R8).
 *  - Warnings/errors log only the exception type, not its message, in release,
 *    because some exception messages echo request data.
 */
object AppLog {
    private const val TAG = "TickToDo"

    fun d(message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message)
    }

    fun w(message: String, error: Throwable? = null) {
        Log.w(TAG, if (BuildConfig.DEBUG) "$message ${error ?: ""}" else "$message ${error?.javaClass?.simpleName ?: ""}")
    }

    fun e(message: String, error: Throwable? = null) {
        Log.e(TAG, if (BuildConfig.DEBUG) "$message ${error ?: ""}" else "$message ${error?.javaClass?.simpleName ?: ""}")
    }
}
