# Context map — Yotsuba

The package layout is layered (`core` / `domain` / `data` / `feature`), so the
contexts below cut across it rather than mapping one-to-one onto directories.
Each names where its vocabulary is defined.

| Context | Lives in | Owns |
|---|---|---|
| **Browsing** | `feature/{boards,catalog,thread}`, `core/text` | Boards, threads, posts, and how post HTML becomes something renderable |
| **Vault** | `core/vault`, `data/repository/MediaVault*`, `feature/vault` | Saved media on shared storage, its directory shape, and its index |
| **Library** | `feature/{bookmarks,history}`, hidden threads | What the user has kept, read, or chosen not to see |
| **Self-update** | `core/update`, `feature/settings` | Getting a newer build onto the phone |

System-wide decisions: [`docs/adr/`](docs/adr/).

## Words that mean something specific here

- **Board** — a `/x/` section. **Catalog** — its thread grid. **Thread** — an OP
  plus replies. **Post** — one message.
- **Entry** (`VaultEntry`) — a saved file *plus* its provenance: which board,
  thread and post it came from. Not the same as a file on disk; a file with no
  meta is not an entry.
- **Sidecar** — the `meta.json` in each thread directory. It is the source of
  truth for the vault, not the database. See ADR-0001.
- **Unsorted** — `_unsorted/`, where migrated files with no known thread landed.
- **Rescan** — rebuilding the vault index from sidecars. The only way an index
  comes back after a reinstall.
- **Read mark** — the highest post number actually shown on screen, as opposed
  to the scroll position, which is where the user happened to stop.

## The thing to know before touching storage

The vault is outside the app sandbox and **survives uninstalling the app**.
Bookmarks, history, hidden threads and settings live in Room and DataStore and
**do not**. Any claim about data surviving a reinstall has to name which side of
that line it is on.
