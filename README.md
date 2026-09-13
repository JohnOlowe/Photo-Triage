# Photo Triage

A card-swipe (Tinder-style) Android app for quickly sorting a folder of photos into
named destination folders. Each photo is shown as a card; tap a category button (or
swipe) to file the current photo into that folder.

## Screens

- **Main** — the swipe deck, plus controls to change the source folder, add/remove
  category folders, skip a photo and reload the deck.
- **Settings** — everything that used to be hard-coded is now configurable:
  - Root (destination base) folder
  - Inbox folder name (the folder photos are triaged out of)
  - Supported image extensions
  - Sort order (name / date, ascending / descending)
  - File operation (move or copy)
  - Photo label (auto iPhone number / full filename / position)
- **Tools** — the two maintenance routines that were previously one-off, hard-coded
  methods are now fully configurable tools:
  - **Reorganize photos (migrate):** maps folders whose names match a filter regex
    into clean destination names and pulls the matching high-res photos out of the
    inbox. Folder name stripping, separators, explicit renames, move-vs-copy and
    delete-existing are all configurable.
  - **Scan folder into gallery:** re-indexes any folder (optionally recursive) with
    the media scanner.

## Storage permission

The app operates on shared storage using absolute paths, so it needs All Files Access
on Android 11+ (or storage permissions on older versions). On first launch it opens
the relevant permission screen.

## Signing (stable signature across builds)

By default, Gradle signs debug builds with an auto-generated *debug* keystore that
lives outside the project (`~/.android/debug.keystore`). That key changes whenever it
is regenerated — e.g. on a new machine, after reinstalling your IDE, or when an
on-device build tool creates a fresh key — which is why the APK signature was changing
between builds.

This project pins every build to a single keystore so the signature never changes:

- `keystore/photo-triage.keystore` — a PKCS12 keystore committed to the repo.
- `gradle.properties` — holds the keystore path, alias and passwords
  (`PHOTO_TRIAGE_*`), which `app/build.gradle.kts` reads into a `signingConfigs`
  entry named `stable`.
- The `debug` build type uses the `stable` signing config. To sign `release` builds
  with the same key, uncomment the `release` block in `app/build.gradle.kts`.

> ⚠️ This keystore is for keeping *debug/development* builds stable. It is committed
> to the repository and its password is in `gradle.properties`, so anyone with repo
> access could sign APKs with it. If you ever publish this app, generate a separate,
> private release keystore (e.g. `keytool -genkeypair -keystore release.jks -alias
> release`) and keep it out of git. The first APK signed with this stable key cannot
> update an install signed with your old debug key — uninstall the old app once, then
> future updates will install over each other.

## Building

Standard Android Gradle project (Java). Import in Android Studio and run, or:

```bash
./gradlew assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/`.
