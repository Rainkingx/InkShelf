# On-demand event metadata import

Blood Hunt is the pilot hero in Reading Guides. Opening a guide uses bundled metadata first, then validated saved metadata, then the importer. Civil War's 132-entry bundled order and existing progress keys are unchanged.

## User flow

1. Tap Blood Hunt to open an ordinary visible Android WebView with its default user agent. Import only if permitted. Complete any website verification normally.
2. Once the event list is visible, tap **Extract visible page — I have permission**. Each pagination page requires its own explicit open and extract action. The initial page opens from the hero tap; later pages and extraction require explicit action.
3. Compare the Complete and Essential counts with the source. Saving requires all discovered pagination pages, unique contiguous reading positions, the declared total (when present), and every short-path entry to match.
4. Save opens the existing native guide UI. Later visits use compact metadata without opening the website.

Access errors, denied access, TLS errors, unsupported markup and incomplete lists do not produce a guide. The screen offers user-selected HTML/MHT files or the existing authorized Metron integration. Saved files are parsed as data, never executed. MHT supports a single HTML part with base64, quoted-printable or unencoded content; unsupported archives fail explicitly. HTML/MHT input is limited to 2 MB per page, 30 pages and 10,000 entries.

The importer does not call the comic reader, download comics, automate verification, export cookies/security tokens, or replay browser requests. It has no JavaScript bridge, file access, mixed-content access or download handler. Browser navigation is restricted to the selected event's HTTPS list pages. Verification flows requiring other destinations fall back rather than bypassing the restriction.

Raw HTML is transient memory only; the session retains parsed rows and a sanitized short-path list. It is discarded on close/save. No temporary HTML files are created and user-selected originals are not deleted. The old background HTML cache is cleared when the guide catalogue opens. Ordinary WebView-managed website storage is not exported, nor is shared browser state cleared in a way that could affect the existing reader.

## Validation

`InkShelf2EventImportSessionTest` covers page completeness, overlapping and duplicate positions, wrong events, count mismatches, challenge pages, size limits, navigation policy, cleanup, HTML and MHT decoding. The existing parser, reading-selection, edition-matching and bundled Civil War regression tests remain included. GitHub Actions builds the dev debug APK and runs these tests.

Device acceptance still required: successful permitted Blood Hunt navigation/extraction, normal manual verification when offered, denied-access fallback, picker cancellation, rotation/back during import, saved metadata after restart, and native Essential/Complete reading with existing progress. Offline fixtures are not evidence of live website permission or compatibility. No live Blood Hunt count is claimed until a permitted import validates it.
