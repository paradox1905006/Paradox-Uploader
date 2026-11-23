# Paradox Uploader 1.7.1 — fixes

- Build: Gradle wrapper pointed at Gradle 9.6, which the Android Gradle Plugin 8.9 used here cannot run on. Pinned to 8.11.1 (matches build_release.sh) and removed the stray duplicate gradle/gradle-wrapper.properties.
- Telegram: cancelling an upload did not stop it — TDLib kept uploading and the file was still posted. Cancel now deletes the pending message, which aborts the upload.
- Telegram: when Telegram rejected a file, the real reason (e.g. flood wait) was thrown away. It is now shown.
- Telegram: cancelling while a file was being copied left a partial copy in the cache. The staging folder is now always cleaned up.
- Telegram: picking a file the app could not read crashed the upload screen; opening a message link with no app to handle it crashed. Both now fail gracefully.
- Telegram: upload notification percentage could go above 100%.
- archive.org: duplicate detection skipped any file that shared a name and size with an earlier upload, even a different file going to a different item. It now skips only the same source already queued, or the same name + size into the same item.
- archive.org: files marked "Delayed" were not re-checked after a restart. They are now.
- In-app update: an interrupted download could be handed to the installer, and a bad link in update.json crashed the app. Downloads now go to a temp file, are size-checked, and errors are reported instead of crashing.
- Tests: 2 tests still expected old polling behaviour (HEAD check, give-up after 1 hour) and 2 failed because of the duplicate bug. All 85 unit tests pass now, including new ones for the fixed rules.
- Version 1.7.1 (versionCode 7); update.json points at the V1.7.1 GitHub release. Removed old 1.3.2 build output from app/release.
