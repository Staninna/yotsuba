package dev.stan.yotsuba.core.designsystem

import androidx.annotation.StringRes
import dev.stan.yotsuba.R
import dev.stan.yotsuba.domain.model.VaultError

/** The one user-facing label per vault failure, shared by every screen that reports one. */
val VaultError.labelRes: Int
    @StringRes get() = when (this) {
        VaultError.NoAccess -> R.string.vault_error_no_access
        VaultError.NotFound -> R.string.vault_error_not_found
        is VaultError.Io -> R.string.vault_error_io
    }
