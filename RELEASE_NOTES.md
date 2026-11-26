# InkShelf 2.0 — Public Beta 17

## Stability improvements

- Source search now isolates individual source timeouts so other sources can still return results.
- Automatic fallback can find a matching, explicitly dated edition when the original source cannot load its issue list. Disabled sources are excluded and existing library entries are not migrated.
- Coordinated issue loading and library writes reduce racing requests and duplicate saves.
- Library duplicate detection recognises equivalent titles with missing publication years when a substantial synopsis matches. Distinct editions, such as Noir editions, and conflicting publication years remain separate.
- Existing duplicate favourites are grouped for display without deleting their source records, downloads or reading history. The active copy with the most progress is preferred.
- Device testing on Dev Build 641 confirmed duplicate cards were gone and downloads, reading, resume and startup worked for the tester.

## Updating

Use the in-app update notification. The public app identity and release signing remain unchanged. Back up your library before updating. Automatic fallback covers issue-list failures, not individual comic page failures; unverified editions must be selected manually.

This is a prerelease. InkShelf is an independent reader based on TachiyomiJ2K. Third-party sources and services remain separate. Use lawful sources and respect rights holders.
