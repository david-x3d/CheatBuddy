# Cheat Buddy

A phone editor and an offline Wear OS quick-reference viewer. Create sheets with text, tables, gallery photos or camera captures on the phone. Drag blocks using their handles, then switch to the round watch preview. Changes save automatically and queue background sync.

## Project

- `shared`: serializable models, payload validation, revision and sync planning.
- `mobile`: Compose Material 3 Expressive editor, Room storage, image import and Data Layer publishing.
- `wear`: Wear Compose Material 3 viewer, local Room database and image files, background sync and a last-viewed-sheet tile.
- [Implementation plan](docs/PLAN.md).

The phone is the source of truth. The watch UI reads its own database and files, so previously received content remains available with the phone switched off. The watch manifest declares `com.google.android.wearable.standalone=true`. Sync status and retry are available in watch Settings. Images not yet transferred have a placeholder; allow the initial image transfer to finish before going offline.

## Build

Use JDK 21 and an Android Studio version supporting Android Gradle Plugin 9.4.1. Install Android SDK Platform 37 and SDK platform tools. The wrapper downloads Gradle 9.8.1; dependencies require internet access on the first build.

Set `ANDROID_HOME` to your SDK directory, or create an untracked `local.properties` containing `sdk.dir=/absolute/path/to/Android/Sdk`.

```sh
export JAVA_HOME=/path/to/jdk-21
export ANDROID_HOME=/path/to/Android/Sdk
./gradlew :shared:test :mobile:testDebugUnitTest :wear:testDebugUnitTest :mobile:assembleDebug :wear:assembleDebug
```

Optimized release APKs (run the release build command below):

- `mobile/build/outputs/apk/release/mobile-release.apk`
- `wear/build/outputs/apk/release/wear-release.apk`

Phone minimum API: 26. Watch minimum API: 30 (Wear OS 3+). Both apps use `com.cheatbuddy.app` and must be signed with the same certificate for Data Layer communication.

For release builds, set `CHEATBUDDY_KEYSTORE`, `CHEATBUDDY_KEYSTORE_PASSWORD`, `CHEATBUDDY_KEY_ALIAS` and `CHEATBUDDY_KEY_PASSWORD`, then run `./gradlew :mobile:assembleRelease :wear:assembleRelease`. Without those variables, release APKs use the debug certificate for local testing. Configure your own signing key before distribution.

## Import tables from JSON

In the phone editor, add a table (or use an existing one), then tap **Import JSON**. Paste JSON and tap **Import**, or choose a JSON file. Import replaces that table, preserves its position, and autosaves/syncs the result. Invalid input leaves the existing table unchanged and shows an error.

Supported formats:

```json
{
  "hasHeader": true,
  "rows": [
    ["Term", "Meaning"],
    ["CPU", "Central processing unit"],
    ["RAM", "Random access memory"]
  ]
}
```

A plain array such as `[["Term","Meaning"],["CPU","Central processing unit"]]` also works and uses its first row as the header. Cells may be strings, numbers, booleans or null (empty). Short rows are padded with empty cells. Imports are limited to 200 rows, 8 columns and 90 KB; the resulting sheet must also fit the sync payload limit. A ready-to-use file is in [docs/example-table.json](docs/example-table.json).

## Install and pair

Pair a physical watch using its manufacturer's phone companion app. Enable developer options and USB debugging on the phone, and wireless debugging on the watch. Authorize the development computer. For wireless debugging, use the pairing address and code displayed by the watch, then connect to its separately displayed debugging address:

```sh
adb pair WATCH_IP:PAIRING_PORT
adb connect WATCH_IP:DEBUG_PORT
adb devices
adb -s PHONE_SERIAL install -r mobile/build/outputs/apk/release/mobile-release.apk
adb -s WATCH_SERIAL install -r wear/build/outputs/apk/release/wear-release.apk
adb -s PHONE_SERIAL shell am start -n com.cheatbuddy.app/com.cheatbuddy.mobile.MainActivity
adb -s WATCH_SERIAL shell am start -n com.cheatbuddy.app/com.cheatbuddy.wear.MainActivity
```

Use the serials shown by `adb devices`. Install both APKs from the same build/signing setup. Open both apps once, create a sheet on the phone and wait for it to appear on the watch. Add Cheat Buddy from the watch's tile picker to open the most recently viewed sheet.

## Emulator testing

Create a phone AVD with Google Play and Android 11 or newer, plus a Wear OS 3+ AVD. In Android Studio Device Manager, use the Wear OS emulator pairing assistant and complete its setup. The pairing assistant requires a phone with Google Play and API 30+, even though the phone app itself supports API 26. See the official [pairing guide](https://developer.android.com/training/wearables/get-started/connect-phone) and [Wear emulator guide](https://developer.android.com/training/wearables/get-started/emulator).

Install the phone APK on the phone AVD and the watch APK on the Wear AVD with the commands above. A generic Android phone emulator cannot substitute for a Wear OS system image when testing the watch UI or Data Layer.

Manual acceptance checks:

1. Create a sheet containing formatted text, a table with several rows/columns, and an image. Reorder blocks and check the preview and watch ordering.
2. Wait until sync is complete, then switch off the phone. Relaunch the watch app and open the sheet: text, table and image should remain available.
3. Rotate the crown to scroll the sheet. Pinch images/tables with two fingers to zoom, drag to pan, and double-tap to reset. Check the table's pinned header and horizontal pan.
4. Reconnect the phone, edit content and delete another sheet. Confirm the watch updates automatically.
5. Check watch Settings for sync errors/retry. Test round and square Wear AVDs, ambient mode, phone light/dark themes and the tile.

## Sync and storage

Text, tables and metadata travel as DataClient items; watch images travel over ChannelClient and are compressed to a maximum edge of 400px. WorkManager jobs run after changes, on connection events, and periodically on the watch. The watch acknowledges applied revisions. Missing images retry on reconnection; existing local content remains readable after a sync failure. Oversized sheets receive a clear error and should be split into smaller sheets.

Unit tests cover serialization, sync planning, phone publication, watch updates/deletes, missing images and storage-full handling. `wear/.../OfflineLoadTest.kt` uses a real in-memory Room database with a gateway that fails every Data Layer call, then verifies content reads require no phone calls.

No server account or cloud backend is required. Clearing app data or uninstalling removes local content; keep the phone's source sheets to resync the watch.

## Validation status

Both optimized version 1.1 release APKs build successfully. All 31 JVM/Robolectric tests pass, including JSON import validation, serialization, local watch reads with a disconnected phone, and recovery from stalled image transfers. Release lint passes for both modules with non-blocking warnings. The user confirmed the initial version works on the paired phone and Pixel Watch 5; responsiveness and gestures in the updated build require device testing.
