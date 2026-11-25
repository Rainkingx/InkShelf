# InkShelf 2.0 — Public Beta 2

This update focuses on release delivery and build reliability.

## Changes

- InkShelf 2.0 now checks GitHub for updates when opened, after first-run setup.
- A manual **Check for updates** option is available in Menu.
- New versions can be downloaded and installed through InkShelf, with Android's normal installation permissions and notifications.
- Development builds remain separate from the public update channel.
- Removed an unnecessary external Gradle Kotlin DSL bootstrap dependency to improve build reliability.

## Updating from Beta 1

Download **InkShelf.apk** from this release and install it over the previous public version. Beta 1 users may need to perform this update manually once to activate the new 2.0 update flow. Back up your library first. Do not uninstall the existing public app just to update it.

The public app retains its Android package identity and signing key. Development/debug builds use a different package and cannot be updated in place with the public APK.

This is a prerelease. Update checks require internet access; Android may require permission to display notifications or install APKs. Report bugs with the app version, device, Android version and reproduction steps.

InkShelf is an independent reader based on TachiyomiJ2K. Third-party sources and metadata services remain separate. Use lawful sources and respect rights holders. See the README and Apache-2.0 licence.
