# ADR-0001 — The vault lives on shared storage, described by sidecars

**Status:** accepted

## Context

Saved media is the only thing in this app a user would genuinely mourn. Kept in
the app sandbox it would be invisible to every other app and destroyed by an
uninstall — including the uninstall required to move from a debug-signed build
to a properly signed one.

## Decision

Files go to `/sdcard/Yotsuba/<board>/<threadNo> - <subject>/`, one directory per
thread, each carrying a `meta.json` sidecar describing every file in it: origin
URL, post number, dimensions, size, saved-at.

The sidecars are the source of truth. The Room table is a cache of them, and
`rescan()` rebuilds it by walking the tree for `meta.json` and reading nothing
else.

## Consequences

- The media survives uninstalling the app, and a fresh install recovers its
  whole index from disk. This has been exercised: 193 files across 23 sidecars
  came back intact through a signature-forced uninstall/reinstall.
- Rescan is manual, in the Vault tab. A fresh install shows an empty vault until
  it is pressed — which looks exactly like data loss and is not.
- A file with no sidecar entry is invisible to the app even though it is on
  disk. Anything that writes into the vault must update the sidecar in the same
  breath.
- The app needs `MANAGE_EXTERNAL_STORAGE`, a permission with real weight, and
  without it the vault reads as empty rather than as inaccessible.
