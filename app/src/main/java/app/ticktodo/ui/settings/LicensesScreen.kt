package app.ticktodo.ui.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.ticktodo.R

/** Kept in sync with docs/third-party-licenses.md. */
private val NOTICES = listOf(
    "AndroidX (Core, Activity, Lifecycle, Navigation, Room, WorkManager, DataStore, Glance, Credentials, Compose)" to "Apache License 2.0",
    "Material Components for Compose and Material Icons" to "Apache License 2.0",
    "Kotlin standard library, kotlinx.coroutines, kotlinx.serialization" to "Apache License 2.0",
    "Dagger / Hilt" to "Apache License 2.0",
    "Firebase Android SDK (Auth, Firestore, App Check, Analytics)" to "Apache License 2.0",
    "Google Identity Services (googleid)" to "Apache License 2.0",
    "Google Play services libraries" to "Android Software Development Kit License",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicensesScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_licenses)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item {
                Text(
                    stringResource(R.string.licenses_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
            }
            items(NOTICES) { (name, license) ->
                ListItem(headlineContent = { Text(name) }, supportingContent = { Text(license) })
            }
            item {
                Text(
                    stringResource(R.string.licenses_apache_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}
