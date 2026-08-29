package dev.stan.yotsuba.domain.model

/**
 * One file the user picked to import into the vault: an opaque content URI and the name to
 * file it under. The URI is a string rather than an `android.net.Uri` so the domain layer
 * keeps no platform types; whoever resolves it knows how.
 */
data class ImportSource(val uri: String, val displayName: String)
