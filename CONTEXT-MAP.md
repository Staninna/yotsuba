# Yotsuba context map

The package layout is layered (`core` / `domain` / `data` / `feature`), so the
contexts below cut across it rather than mapping one-to-one onto directories.
Each names where its vocabulary is defined.

| Context | Lives in | Owns |
|---|---|---|
| **Browsing** | `feature/{home,boards,catalog,thread}`, `core/text`, `core/network` | Boards, catalogs, threads, posts, post HTML becoming something renderable, and the archive fallthrough (`ArchiveHosts`) |
| **Filters** | `feature/settings` (filters section), `domain/model` | Rules that hide or stub posts before they reach a screen |
| **Media** | `feature/media`, `core/media` | The viewer shared by thread and vault, video stills, reverse image search and its uploads |
| **Vault** | `core/vault`, `core/dedup`, `data/repository/MediaVault*`, `data/repository/Vault*`, `feature/vault` | Saved media on shared storage, its directory shape, sidecars, index, trash, dedup and stats |
| **Library** | `feature/threads`, `feature/{bookmarks,history}`, `core/work`, `core/widget` | What the user has kept, read, or hidden; the background refresh, its notifications and the widget |
| **Backup** | `core/backup`, `data/repository/BackupRepositoryImpl` | Bookmarks, hidden threads and settings written to the vault folder and merged back in |
| **Lock** | `core/lock`, `feature/lock` | Gating the app behind the device credential |
| **Self-update** | `core/update`, `feature/settings` | Getting a newer build onto the phone |

`core/database`, `core/datastore`, `core/di`, `core/designsystem` and
`core/util` are plumbing shared by all of them and own no vocabulary.

System-wide decisions: [`docs/adr/`](docs/adr/).

## Words that mean something specific here

- **Board**: a `/x/` section. **Catalog**: its thread grid. **Thread**: an OP
  plus replies. **Post**: one message.
- **Entry** (`VaultEntry`): a saved file *plus* its provenance: which board,
  thread and post it came from. Not the same as a file on disk; a file with no
  meta is not an entry.
- **Sidecar**: the `meta.json` in each thread directory. It is the source of
  truth for the vault, not the database. See ADR-0001.
- **Unsorted**: `_unsorted/`, where migrated files with no known thread landed.
- **Rescan**: rebuilding the vault index from sidecars. The only way an index
  comes back after a reinstall.
- **Read mark**: the highest post number shown on screen, as opposed
  to the scroll position, which is where the user happened to stop.
- **Smoke launch**: installing the release APK and starting it, with no test
  code aboard; it passes if the process is still alive ten seconds later. The
  only check that sees the minified build, so the only one that can catch a
  release-only crash before a user does.

## The thing to know before touching storage

The vault is outside the app sandbox and **survives uninstalling the app**.
Bookmarks, history, hidden threads and settings live in Room and DataStore and
**do not**. Any claim about data surviving a reinstall has to name which side of
that line it is on.
