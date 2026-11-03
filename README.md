# Paradox Uploader

Android app that uploads files from your phone straight to the **Internet Archive** (archive.org)
using **your own S3-style API keys** (bring-your-own-key). There is no backend: everything runs on-device.
After archive.org has processed a file, the app notifies you that the direct download link is ready.

* Kotlin, Jetpack Compose (single activity), Material 3 with Material You, MVVM + Clean Architecture
* Hilt, Coroutines/Flow, Navigation Compose, Room, DataStore, OkHttp (streaming uploads), kotlinx.serialization
* WorkManager + foreground service (`dataSync`), EncryptedSharedPreferences, Coil, ZXing (QR codes)
* `minSdk 26`, `compileSdk/targetSdk 36`, Gradle Kotlin DSL with a version catalog

## Setup

1. Install a recent **Android Studio** (Ladybug or newer, with JDK 17+ and the Android 16 / API 36 SDK).
2. `File > Open` and choose the `ParadoxUploader` folder. Studio downloads Gradle 8.11.1 from
   `gradle/gradle-wrapper.properties` and syncs the project.
   (From a terminal you can also run `gradle wrapper --gradle-version 8.11.1` once to generate `gradlew`.)
3. Run the `app` configuration on a device or emulator (API 26+).
4. Unit tests: `./gradlew testDebugUnitTest`. Compose UI tests (device/emulator): `./gradlew connectedDebugAndroidTest`.
5. Release build: `./gradlew assembleRelease` (R8 and resource shrinking are enabled; add your own signing config).

The launcher icon is generated from your logo (`app/src/main/res/drawable-*/ic_launcher_foreground.png`,
adaptive icon with monochrome layer for themed icons). A 512 px Play Store icon is in `branding/ic_launcher_512.png`.

## Getting your archive.org keys

1. Create or sign in to an account at <https://archive.org>.
2. Open <https://archive.org/account/s3.php>.
3. Copy the **Access Key** and **Secret Key**.
4. In the app go to **Settings > API keys**, paste both keys and tap **Save**. The app uploads a tiny file to a
   temporary item (`paradox-test-<random>`), deletes it again (cascade delete) and only saves the keys if that
   test passes. You can choose **Save anyway** if the test fails (for example while offline).

The keys are stored with `EncryptedSharedPreferences` (Android Keystore), are excluded from backups
(`allowBackup="false"` plus data extraction rules) and are never logged; the `Authorization` header is redacted
in OkHttp debug logging.

## How it works

* **Upload**: `PUT https://s3.us.archive.org/<identifier>/<filename>` with `Authorization: LOW <access>:<secret>`,
  `x-archive-auto-make-bucket: 1`, collection / mediatype / title headers, streamed in 64 KiB chunks
  (the file is never loaded into memory). 503 SlowDown is retried with exponential backoff; network loss restarts the
  file automatically when connectivity returns (simple PUT uploads cannot resume).
* **Link Ready pipeline**: after the PUT succeeds the item is `PROCESSING`. A chain of short WorkManager jobs polls
  `https://archive.org/metadata/<identifier>` and confirms with a `HEAD` on the direct download URL
  (every 10 s for the first minute, every 30 s up to 10 min, every 2 min up to 1 h, then `DELAYED` with a manual
  **Check now**). Then it becomes `READY` and a "Link ready" notification is shown.
  State machine: `UPLOADING -> PROCESSING -> READY / DELAYED / FAILED`.
* **Delete**: `DELETE` on the S3 endpoint with `x-archive-cascade-delete: 1`; the row is removed from Room only on success.
* **Identifier strategy** (Settings): a new item per file (`paradox-<random8>`, default) or one fixed item you define.

## New in 1.3.0

* **Folder default details**: create or edit a folder (Upload screen, folder menu) and set title, description, tags and a
  Creative Commons license once. Every file uploaded into the folder uses them (values typed for one upload still win).
  Editing a folder that already has files also sends the new details to the existing archive.org item
  (`POST https://archive.org/metadata/<id>`). Empty fields never remove details that are already on archive.org.
* **Pause all / Resume all** (Upload screen, queue header) and **Only while charging** (Settings > Uploads). Pausing stops the
  running jobs and keeps files queued; an interrupted file restarts from the beginning because simple PUT uploads cannot resume.
* **Upload stats**: files and GB uploaded in total, this month and today (from the history, so imported history counts too).
* **Update checker**: once a day at app start (can be switched off) and a manual button in Settings > Updates. Set the source in
  `app/build.gradle.kts` (`UPDATE_CHECK_URL`): a GitHub "latest release" API URL, or your own JSON file
  `{"versionName": "1.4.0", "url": "https://...", "notes": "..."}`. **Change the default URL to your real repository.**
* **Copy / share all links** (My Files, share icon in the top bar): as text (one link per line) or as a CSV file. It exports the
  selected files, or everything the current filters show, so choose a folder first to export just that folder.
* **Folder filter** in My Files (chip next to the status filters) and the folder name shown on every file.
* **Duplicate check**: when you select a file with the same name and size as an earlier upload you get a warning, a badge in the
  list and a choice (Skip duplicates / Upload anyway) before uploading. Files are compared by name and size, not by content.

## Project layout

```
app/src/main/java/com/dparadox/paradoxuploader
  domain/        models, repository contracts, pure utilities (URLs, validation, polling, state machine)
  data/          Room, DataStore, encrypted key store, OkHttp ArchiveService, repository implementation
  work/          UploadWorker (foreground), PollWorker, scheduler, concurrency gate
  notifications/ channels, progress/link-ready/failure notifications, action receiver
  di/            Hilt modules
  ui/            theme, components, navigation, onboarding, upload, files, settings
app/src/test           unit tests (identifier validation, URL encoding, polling/backoff, state machine, repository)
app/src/androidTest    Compose UI tests
```

## Known limitations

* **Uploads are public.** Archive.org is a public archive; the app shows a one-time warning.
* **No resume.** Simple PUT uploads cannot resume; an interrupted file restarts from the beginning.
* **Collections.** Regular accounts can upload to the `opensource` collection (default). Other collections need
  special permissions on archive.org.
* **Processing time.** Archive.org may need seconds to many minutes (occasionally hours) before a file is downloadable.
* **Android 15+.** `dataSync` foreground services have a time limit per 24 hours; very long upload sessions may be
  paused by the system and are restarted by WorkManager.
* **Aggressive OEMs** (Samsung, Xiaomi, ...) can kill background work; use Settings > Background uploads to open the
  battery optimization settings.
* **Share-sheet files** without a persistable permission are copied to app-private storage until uploaded, which
  needs free space equal to the file size.
* **Swipe-to-delete** can only undo before the remote delete runs (5 seconds); once archive.org deleted a file it is gone.
* The project was assembled without running Gradle in the authoring environment: if a dependency version in
  `gradle/libs.versions.toml` is newer or older than what your Android Studio expects, adjust it there.
* Languages: English only.
