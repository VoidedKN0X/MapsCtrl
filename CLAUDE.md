# MapsCtrl

Offline maps and navigation in the VKNXWorks Ctrl series. Kotlin, Jetpack Compose, Material 3, manual DI (`AppContainer`), DataStore for settings. Maps are drawn with Mapsforge (in an `AndroidView`), routes come from BRouter, vendored as the `:brouter` module.

MapsCtrl is its own app. It is **not** a fork of Organic Maps or any other maps app: never copy code, assets, icons, styles, map files or server URLs from another maps app. Mapsforge, BRouter and OpenStreetMap data are the only outside parts, under their own licences.

## Layout

- `brouter/`: BRouter's routing core (util, codec, expressions, mapaccess, router) copied unchanged from upstream; see `brouter/README.md` for the commit. Profiles and `lookups.dat` are in `app/src/main/assets/brouter/` and copied to `filesDir/brouter/profiles2` once per version.
- `data/`: `Http` (the only network code: HTTPS GETs for downloads, the ntfy POST), `DirectoryIndex` (Apache and nginx index pages), `MapRepository` (download.mapsforge.org maps + POI files in `filesDir/maps` and `filesDir/poi`, `installed.json`, import, updates), `SegmentRepository` (brouter.de `.rd5` tiles in `filesDir/brouter/segments4`).
- `map/`: `CtrlRenderTheme` (our own Mapsforge theme, one rule set with day and night palettes, written to `filesDir/theme/<day|night>` with the symbols), `Symbols` (our own SVG glyphs), `MapController` (MapView, tile layer from all maps, route/track polylines, taps), `OverlayLayer` (position, pins, saved places).
- `search/`: `PoiDatabase` reads Mapsforge POI v5 SQLite files read-only through AndroidX `sqlite-bundled` (the platform SQLite has no R-tree or FTS5) (trigram FTS when present, R-tree, categories); `SearchRepository` merges all files and parses coordinates.
- `routing/`: `Router` (runs BRouter, `timode=1`, `processUnusedTags=1` so maxspeed and speed cameras are in the result), `RouteParser` (BRouter GeoJSON), `Maxspeed`, `Instructions` (turn text; words from `ResourceWords`).
- `nav/`: `RoutePlanner`, `NavigationTracker` (pure logic: snapping, next turn, off-route, announcements, cameras), `NavigationSession` (location, TTS, rerouting, ntfy, debug-only demo drive), `NavigationService` (location foreground service, only with the permission).
- `saved/`: places and tracks in `filesDir/saved.json`, `Gpx`, `TrackRecorder`.
- `ui/home`: `HomeScreen` (map + tabs Map, Saved, Downloads, More; route planning and navigation overlays), `MapOverlay`, `PlaceSheet`, `SearchScreen`, `RoutePanel`, `NavigationOverlay`. Settings open only from the profile picture (You page).

## Hard rules

- **Network only for what the user starts:** map, search-data and routing-data downloads, and ntfy ETA sharing when the user set it up and turned it on. Search, routing and navigation stay offline. No map tiles from servers, no geocoding services, no update checks in the background. CI fails on network-state or background-location permissions.
- **Google-free:** no Google Play services, Firebase, Google location or other Google libraries, including transitive ones (CI checks `releaseRuntimeClasspath`). Location comes from Android's `LocationManager` (fused provider when present, else GPS). No WebView. Fonts are bundled.
- **Telemetry-free:** no analytics or crash reporting.
- arm64-v8a only, Android 15+. Every user-facing string goes in `strings.xml` (shared Ctrl strings in `strings_ctrl.xml`).
- Violet is reserved for the route, the selection and your position; the map style itself stays neutral.
- Downloads always show their size first and only start when the user taps.
- Dependency verification is sha256 only (`gradle/verification-metadata.xml`); regenerate it with the workflow or `--write-verification-metadata sha256` after changing dependencies.

## Releasing

- A release is published only when `versionName` in `app/build.gradle.kts` is bumped. Bump `versionCode` by 1 at the same time.
- Tags are `vX.Y.Z` and releases are named "MapsCtrl X.Y.Z"; the APK is `MapsCtrl-vX.Y.Z.apk`. Push straight to `main`.
- Every new version needs a detailed, user-facing entry at the top of `CHANGELOG.md`; CI refuses to release without one.
- Signing secrets: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_PASSWORD`, `KEY_ALIAS` (secret or variable). Signer SHA-256 `68:77:8C:2C:AF:00:30:9D:95:74:4D:2D:70:F0:7C:EF:2A:9F:D8:5F:F1:0D:B0:D5:B4:78:0E:AB:25:A1:B6:92`.

## Testing

- `./gradlew :app:testDebugUnitTest`. Set `MAPSCTRL_TEST_SEGMENTS` to a folder with `E5_N40.rd5` (Monaco) to also run the real BRouter engine in `RouterIntegrationTest`; it's skipped otherwise.
- `-PreleaseTest` builds a minified release as `com.vknxworks.mapsctrl.releasetest` (debug key) to check R8 on a phone without touching the installed release.
- Debug builds (`.debug` suffix) have a **Demo** button in the route panel that drives a simulated position along the route, for testing navigation without the location permission.
- The manifest sets `intentMatchingFlags="enforceIntentFilter"`: start the app with `adb shell am start -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n com.vknxworks.mapsctrl.debug/com.vknxworks.mapsctrl.MainActivity`.
