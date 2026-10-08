# Cheat Buddy – Plan

## Goals
Phone = editor and source of truth. Watch = offline-first viewer. The watch reads **only** from its local Room DB
and image files; the Data Layer is used only by background sync.

## Modules
```
CheatBuddy/
├── shared/   Pure Kotlin/JVM: models, JSON codec, Data Layer protocol, sync planning (diffs, revisions)
├── mobile/   Phone app: Room, editor UI (M3 Expressive), image import, push sync, image server
└── wear/     Watch app: Room + files, viewer UI (Wear Compose M3), pull sync, tile
```

## Data model (shared)
`Sheet(id, title, updatedAt, blocks)`; `Block` is sealed: `ImageBlock(imageId, width, height)`,
`TextBlock(text, bold, size)`, `TableBlock(rows, hasHeader)`. Serialized with kotlinx.serialization (`type`
discriminator, unknown keys ignored, `schemaVersion` in the envelope for forward compatibility).

## Sync protocol (Wearable Data Layer)
| What | Transport | Path |
|---|---|---|
| Sheet JSON (text + tables + image refs) | `DataClient` DataItem | `/cheatbuddy/sheet/<id>` |
| Manifest (ordered ids + versions + revision) | `DataClient` DataItem | `/cheatbuddy/manifest` |
| Image bytes (≤400px JPEG) | `ChannelClient` (watch pulls) | `/cheatbuddy/image/<imageId>` |
| Ack (watch applied revision) | `MessageClient` | `/cheatbuddy/ack` |

* **Phone:** every edit is saved to Room, then a unique `PushWorker` (WorkManager, debounced) writes the manifest and
  changed sheet items and removes deleted ones. The Data Layer queues items while the watch is away, so changes
  arrive automatically on reconnection. `PhoneListenerService` serves image channels and stores acks.
* **Watch:** `WatchListenerService` (data changed / phone capability appeared) and a periodic job enqueue
  `PullWorker`. It reads the locally cached DataItems, computes a `SyncPlan` (upserts, deletes, missing and
  orphaned images) with shared pure logic, applies it to Room in one transaction, pulls missing images over
  `ChannelClient`, deletes orphans and sends an ack. Status (idle / syncing / phone unreachable / failed /
  storage full) goes to DataStore and is shown only in Settings.

## Watch UI
`AppScaffold` + `TimeText`, `SwipeDismissableNavHost` (Home → Sheet, Settings), `ScalingLazyColumn` with
edge scaling and scroll indicator. The crown scrolls the sheet using Wear foundation rotary scrolling. Pinch zooms images and tables, with
haptic feedback at the min and max zoom;
a double tap resets the zoom, and drag pans the content. Tables have a sticky header, alternating row tint and
horizontal pan. The watch also supports ambient mode, has a tile that opens the last viewed sheet, and shows
empty states.

## Phone UI
M3 Expressive theme (dynamic color, light/dark). Sheet list → editor with drag-and-drop block list
(Image via gallery or camera, Text, Table), autosave, and an "Edit | Watch preview" toggle with a round watch
mockup. A sync status chip shows synced, waiting for watch, or failed (with retry).

## Testing
`shared`: codec round-trip, sync planning. `mobile`: push engine with a fake gateway. `wear`: pull engine
(phone unreachable, storage full, deletes) and a Robolectric/Room test that loads sheets with no phone
connected.

## Steps
1. Gradle skeleton + version catalog, wrapper. 2. `shared` + tests. 3. `wear` data/sync + tests.
4. `wear` UI, tile. 5. `mobile` data/sync + tests. 6. `mobile` UI. 7. Build both, README.
