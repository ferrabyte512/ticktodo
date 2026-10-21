package app.ticktodo.ads

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Integration point for advertising. Version 1 ships with ads disabled and no
 * ads SDK in the build. docs/ads-integration.md describes how to add AdMob
 * with the User Messaging Platform consent flow behind this interface.
 *
 * Placement rules (enforced by where [AdSlot] is used, not by this code):
 * never on the task editor, quick add, reminders, sign-in or settings screens,
 * and never next to complete/delete controls.
 */
interface AdsGateway {
    val enabled: Boolean

    /** Called once at start-up; an implementation gathers consent before loading ads. */
    suspend fun initialize() {}
}

@Singleton
class DisabledAds @Inject constructor() : AdsGateway {
    override val enabled: Boolean = false
}

enum class AdPlacement { BROWSE_FOOTER }

/** Renders an ad for [placement] when ads are enabled; renders nothing in this build. */
@Composable
fun AdSlot(gateway: AdsGateway, placement: AdPlacement, modifier: Modifier = Modifier) {
    if (!gateway.enabled) return
    // AdMob banner view goes here once the SDK is added (see docs/ads-integration.md).
}
