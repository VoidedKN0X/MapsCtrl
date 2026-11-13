# Changelog

What changed in each MapsCtrl release. Every release on the [Releases page](https://github.com/VoidedKN0X/MapsCtrl/releases) also lists the exact changes in that build.

To publish a new version: bump `versionCode` and `versionName` in `app/build.gradle.kts`, add a section here, and push to `main`. Pushes without a version bump only build; they don't make a release.

## v0.3.0 (2026-11-13)

**MapsCtrl is rewritten from scratch.** Earlier versions were a reskinned copy of another maps app. From 0.3.0 MapsCtrl is its own app, with its own code, its own map style and symbols, and its own engine: maps are drawn with [Mapsforge](https://github.com/mapsforge/mapsforge) and routes come from [BRouter](https://github.com/abrensch/brouter), both running on your phone. It installs as an update over 0.2.0, with the same signing key.

### Before you update

- **Maps you downloaded in 0.2.0 must be downloaded again.** The new engine uses a different map format, so the old map files can't be used. MapsCtrl 0.3.0 starts with no maps: open **Downloads** and add your countries or regions again. If the old map files are still on your phone, Downloads offers to delete them and shows how much space that frees.
- Bookmarks from 0.2.0 aren't carried over. Export them from 0.2.0 as GPX before you update, then import that file under **Saved > Import GPX**.

### New

- **Offline maps in the Ctrl style:** neutral greys, muted water and green, white roads; violet only for your route, the selected place and your position. Day and night colours follow your phone's dark theme, or choose Always day / Always night.
- **Downloads:** browse every country and region on download.mapsforge.org, download with progress, check for updates, update and delete, and see how much space maps and routing data use. You can also import a Mapsforge `.map` file you already have.
- **Offline search** by name, address, category ("pharmacy", "fuel") and coordinates, with quick category buttons. It uses the search data that comes with each map (on by default; you can turn it off to save space).
- **Place sheet** with the category, address, opening hours, phone, website and the other details from OpenStreetMap, plus **Route to**, **Save** and **Share**.
- **Offline routes** by car, bike and on foot, from your position, a place or coordinates you type, or a point you tap on the map. The first time a route needs routing data, MapsCtrl tells you which tiles and how big they are, and downloads them only when you agree.
- **Turn-by-turn navigation** with a next-turn card, arrival time, time and distance left, your speed, the **speed limit** of the road you're on, **speed camera warnings**, **spoken directions** with your phone's own text-to-speech (offline voices work), and **rerouting** when you leave the route.
- **Saved places and tracks:** save places, record tracks (also with the screen off), and import and export **GPX**.
- **Share your ETA with ntfy** (off by default): while navigating, send your arrival time and distance left to an ntfy topic on a server you choose.
- **Open "geo:" links** from other apps.

### Not in this version yet

- Android Auto, hillshading and contour lines, public transport, live traffic.
- Turn instructions don't name streets (the routing data has no names).
- Downloads keep running while MapsCtrl is open; leave it open until a big download finishes.
