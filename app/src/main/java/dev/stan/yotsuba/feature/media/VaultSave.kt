package dev.stan.yotsuba.feature.media

import android.content.Context

/**
 * The vault lives on shared storage, so every save is gated on all-files access. Both the
 * viewer and the thread list save, and they must not drift on what happens when it is
 * missing — so the check lives here rather than in each screen.
 */
inline fun saveToVault(
    context: Context,
    hasAccess: Boolean,
    onAccessNeeded: () -> Unit,
    save: () -> Unit,
) {
    if (hasAccess) {
        save()
    } else {
        requestAllFilesAccess(context)
        onAccessNeeded()
    }
}
