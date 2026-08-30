# ADR-0002: Repository interfaces in `domain`, implementations in `data`

**Status:** accepted

## Context

ViewModels that talk to Room DAOs and Retrofit services directly can only be
tested on a device. An emulator per test run is slow enough that the tests stop
being written.

## Decision

`domain/repository` holds interfaces expressed purely in domain models such as
`Bookmark`, `HistoryEntry` and `VaultEntry`, with no Room, Retrofit or Android
types in their signatures. `data/repository` implements them.

Sentinels were removed in the same spirit: `VaultLocation` is a sum type rather
than a magic `"_unsorted"` string, and deleted media is a nullable `PostMedia`
rather than a `fullUrl = ""` row.

## Consequences

- ViewModel tests run on the JVM against hand-written fakes, which is why there
  are 38 test files rather than 5.
- A repository method returning a Room entity is a boundary violation even when
  it compiles, and it will quietly drag Android back into the test path.
- Some things stay untestable on the JVM regardless, meaning anything needing `Context`
  or ExoPlayer. `todo.md` records that honestly rather than reporting a coverage
  number that implies otherwise.
