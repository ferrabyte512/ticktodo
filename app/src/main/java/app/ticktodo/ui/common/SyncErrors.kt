package app.ticktodo.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.ticktodo.BuildConfig
import app.ticktodo.R
import app.ticktodo.sync.SyncError

/** One-line, user-facing summary of a sync problem. */
@Composable
fun syncErrorTitle(error: SyncError): String = stringResource(
    when (error) {
        SyncError.ACCESS_DENIED -> R.string.sync_err_access_title
        SyncError.SIGNED_OUT -> R.string.sync_err_signed_out_title
        SyncError.DATABASE_MISSING -> R.string.sync_err_db_title
        SyncError.BUSY -> R.string.sync_err_busy_title
        SyncError.CHANGES_REFUSED -> R.string.sync_err_refused_title
        SyncError.OTHER -> R.string.sync_err_other_title
    },
)

/** What the user can do. Debug builds add the developer-side fix. */
@Composable
fun syncErrorHelp(error: SyncError): String {
    val user = stringResource(
        when (error) {
            SyncError.ACCESS_DENIED -> R.string.sync_err_access_body
            SyncError.SIGNED_OUT -> R.string.sync_err_signed_out_body
            SyncError.DATABASE_MISSING -> R.string.sync_err_db_body
            SyncError.BUSY -> R.string.sync_err_busy_body
            SyncError.CHANGES_REFUSED -> R.string.sync_err_refused_body
            SyncError.OTHER -> R.string.sync_err_other_body
        },
    )
    if (!BuildConfig.DEBUG) return user
    val dev = when (error) {
        SyncError.ACCESS_DENIED -> stringResource(R.string.sync_err_access_dev)
        SyncError.DATABASE_MISSING -> stringResource(R.string.sync_err_db_dev)
        SyncError.CHANGES_REFUSED -> stringResource(R.string.sync_err_refused_dev)
        else -> null
    }
    return if (dev == null) user else "$user\n\n$dev"
}
