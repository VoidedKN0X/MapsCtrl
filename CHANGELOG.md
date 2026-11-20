# Changelog

What changed in each MapsCtrl release. Every release on the [Releases page](https://github.com/VoidedKN0X/MapsCtrl/releases) also lists the exact changes in that build.

To publish a new version: bump `versionCode` and `versionName` in `app/build.gradle.kts`, add a section here, and push to `main`. Pushes without a version bump only build; they don't make a release.

## Unreleased

Not released yet: these changes are on `main` and come with the next version.

### Changed

- **The map opens faster.** At start the map was set up up to four times in a row: once empty, again when the list of downloaded maps arrived, again for the night style on a dark phone, and again when hillshading is on. Each time every map file was opened and the drawn tiles were thrown away. MapsCtrl now waits a moment for those three things and sets the map up once, with all of them.
- **The map style is written to storage once, not at every start.** MapsCtrl's map style and all its symbols were written to the phone's storage again every time the map was set up and every time it switched between day and night, on the main thread. They're now written once per installed build (in the background, before the map opens) and reused.
- **Moving the map is smoother.** Panning, zooming or turning the map, each new position (once a second) and each compass reading (five times a second) made the whole home screen work out its layout again. Now only the parts that show them follow them: the map buttons and scale bar follow the map, and a small invisible part feeds your position and heading to the map. Nothing looks or behaves differently; navigation still gets your position once a second.
- **Long recordings stay light.** While a track is being recorded, every new point used to redraw the line by converting and adding the whole track again; now only the new point is added to the line on the map.
- **The map stops using your position when MapsCtrl isn't on screen.** Just looking at the map kept the phone's location running after you switched to another app or turned the screen off, until Android ended the app. It now stops when MapsCtrl leaves the screen and starts again when you come back. Navigation and track recording are not affected: they keep your position going in the background with their notification, as before.

### Security

- **Written down: HTTPS only, and only the phone's own certificate authorities.** MapsCtrl already refused plain HTTP in its own code, and Android already ignores user-installed certificates for it. A network security configuration now states both, like in the other Ctrl apps, so neither can change by accident.
- **No debug logging from libraries in release builds** (verbose, debug and info log calls are removed when the app is built, as in the other Ctrl apps; MapsCtrl's own two warnings about a damaged search file stay).
- **The build checks the Gradle download** against its published SHA-256 checksum, as the other Ctrl apps already did.

MapsCtrl has no home-screen widget and no Quick Settings tile, so there was nothing to remove.

## v0.4.0 (2026-11-13)

### New

- **Street names in directions.** The next-turn card and the voice now say where you turn: "Turn left onto Rue Grimaldi", "Take the 2nd exit onto Avenue de la Costa". BRouter's routing data has no street names, so MapsCtrl finds the road you turn onto in the maps on your phone, in your phone's language when the map has it. It works offline; where a road has no name, its number is used ("onto D6007"), and where nothing fits, the instruction stays as before.
- **Public transport with Transitous.** Route planning has a new **Transit** mode: leave now, or pick a day and a time to depart at or arrive by. You get journeys with their walks, lines in their own colours, the direction, platforms (and platform changes), the number of stops, changes, delays and cancellations, and the journey you pick is drawn on the map. Journeys come from [Transitous](https://transitous.org), a free, community-run service, so this needs a connection. Only your start and destination points and the time are sent, and only when you pick Transit. **Settings > Public transport** hides it completely.
- **Hillshading.** Hills and mountains can be shaded on the map: the new **Map layers** button on the map turns it on and off. It needs elevation data, which you download per map under **Downloads > Terrain data (hillshading)**: MapsCtrl shows the size first (Monaco is one tile, about 7.5 MB) and you can delete it again. The data is from the public Terrain Tiles set (Mapzen / Tilezen on AWS Open Data).

### Not in this version yet

- Contour lines, Android Auto, live traffic.
- Turn-by-turn navigation for public transport journeys (they're shown as a plan).

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
