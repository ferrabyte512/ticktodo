package app.ticktodo.widget

import android.content.Context
import app.ticktodo.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Refreshes every placed widget after data or focus changes. */
@Singleton
class WidgetUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val scope: CoroutineScope,
) {
    fun update() {
        scope.launch {
            try {
                updateAllWidgets(context)
            } catch (e: Exception) {
                // No widget placed, or the launcher is unavailable; nothing to update.
            }
        }
    }
}
