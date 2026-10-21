package app.ticktodo.data.remote

import android.content.Context
import app.ticktodo.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lazily hands out Firebase clients. Nothing touches Firebase until the app is
 * known to be configured, so a checkout without google-services.json still runs.
 */
@Singleton
class FirebaseProvider @Inject constructor(@ApplicationContext private val context: Context) {

    val isConfigured: Boolean by lazy {
        BuildConfig.FIREBASE_CONFIGURED && FirebaseApp.getApps(context).isNotEmpty()
    }

    val auth: FirebaseAuth by lazy {
        check(isConfigured) { "Firebase is not configured" }
        FirebaseAuth.getInstance().also {
            if (BuildConfig.USE_FIREBASE_EMULATOR) it.useEmulator(BuildConfig.EMULATOR_HOST, 9099)
        }
    }

    val firestore: FirebaseFirestore by lazy {
        check(isConfigured) { "Firebase is not configured" }
        FirebaseFirestore.getInstance().also { fs ->
            if (BuildConfig.USE_FIREBASE_EMULATOR) fs.useEmulator(BuildConfig.EMULATOR_HOST, 8080)
            // Room is the offline store. Firestore's own disk cache is turned off so
            // task data is not stored twice and sync behaviour stays predictable.
            fs.firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
                .build()
        }
    }
}
