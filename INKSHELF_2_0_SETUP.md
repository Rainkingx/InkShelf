# InkShelf 2.0 — actual shell rebuild

**Approved design:** The user's four-screen purple/black mock-up (Home, Discover, Library, Comic Details) is the final visual specification.
**Work branch:** `inkshelf-2.0`. Do not change stable `main`.

## Non-negotiable architecture

InkShelf 2.0 is a **new application UI**, not an old J2K theme with extra overlays.

- **Retain:** J2K sources/extensions, manga/chapter database, library, reading history and progress, downloads/updates, backups, tracking, existing reader including Panel Flow, and Metron integrations.
- **Replace:** visual root, app header, bottom navigation, Home, Discover, Library, Comic Details and Account navigation.
- **Do not:** hide legacy headers, add spacer hacks to compensate for the old layout, render two app bars, or re-skin old Conductor controllers as the final solution.
- The old UI must not be mixed with the new shell in a production destination. When migration is complete the old activity chrome must cease to own these screens.
- Keep the existing reader until its separate overhaul; it remains a distinct full-screen reading activity.

## Findings from the existing implementation

- `MainActivity.kt` uses the old XML `main_activity.xml`, `setSupportActionBar(binding.toolbar)`, `binding.bottomNav`, Conductor routing, and a custom `inkshelfHeaderContainer`.
- `MainActivity.kt` explicitly gives Home a short header while Library and Discover keep the older large comic masthead (`inkShelfHeaderHeightFor`, `applyInkShelfMainTitleTreatment`).
- `HomeController.kt` manually sets top scroll padding to match `inkshelf_home_header_height` and calculates bottom padding from the old navigation view.
- Therefore the present mismatch and clipping have an architectural source. **Do not repair this layout. Build a replacement alongside it and only switch ownership after it is functional.**

## New visual shell

Source: `app/src/main/java/eu/kanade/tachiyomi/ui/inkshelf/shell/InkShelf2Shell.kt`

- Kotlin/Jetpack Compose root; dedicated black/purple Material 3 theme.
- Consistent wordmark header with search and notifications actions.
- Four-tab bottom navigation: Home, Discover, Library, Account.
- Explicit Android status/navigation-bar inset handling.
- Screen-specific content receives Scaffold padding; no magic numbers for fixed header/nav offsets.
- Immersive Comic Details and reader screens may opt out of the shared chrome.
- The shell uses callbacks; data access and reader navigation live in adapters, not in decorative UI components.

## Build sequence — do not skip the foundation

1. Build independent new Compose visual shell and theme. **Started**.
2. Add a new app entry/routing host that owns the Compose shell, outside old `MainActivity` layout. Preserve external intents and existing engine destinations as services/adapters.
3. Implement new Home as a Compose screen within that host: real seven-day hero art, swipe controls, real recommendations, reading history and correct scrolling.
4. Implement Comic Details, drawing on existing working metadata and issue-cover logic.
5. Implement new Library grid/list and live filtering against existing library DB.
6. Implement Discover search, categories, curated collections and recommendations using existing source/cache/Metron logic.
7. Implement Account/settings and all less-common routes, ensuring source management, backup/restore and extensions remain accessible.
8. Switch main app launcher to the new root **only after** required destinations are usable. Remove legacy UI ownership rather than concealing it.
9. Check against the mock-up on the actual phone, test reader launch/return, existing library, downloads, updates, deep links, large/small screens and insets.

## Design acceptance

The four-screen reference specifies a premium near-black UI, purple gradients/glow, bright white text, genuine comic covers, an edge-to-edge weekday hero, a compact consistent shared header, illuminated four-tab bottom navigation, horizontal reading/recommendation shelves and an immersive Comic Details page.

Mock-up fictional titles, example percentages, dates and ratings are visual references only. Production data must be real or explicitly unavailable.

## Safety and progress

- Do not wipe or migrate the database just to change the presentation.
- Keep a working signed APK build in CI after each development change.
- Do not mark a visual screen complete before comparing a real-device screenshot against the mock-up.
- Maintain a separate work list, differentiating **built**, **connected to engine**, **visually reviewed**, and **tested**.
