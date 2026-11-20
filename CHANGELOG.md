# Changelog

What changed in each MapsCtrl release. Every release on the [Releases page](https://github.com/VoidedKN0X/MapsCtrl/releases) also lists the exact changes in that build.

To publish a new version: bump `versionCode` and `versionName` in `app/build.gradle.kts`, add a section here, and push to `main`. Pushes without a version bump only build; they don't make a release.

## Unreleased

Built and checked by CI only: nothing here has been run on a phone yet.

### Changed

- **Recording a long track stays fast.** For every new point of a recording MapsCtrl made a fresh copy of the whole track so far, so a long ride cost more and more work per point (a 100 km ride is about 20,000 points). The recording now grows in place and the map and the Saved tab are handed a view of it instead of a copy. The length shown under "Recording now" on the Saved tab is added up as the points come in, instead of being measured over the whole track again each time. The saved track, its points and its times are the same as before.
- **The compass is switched off while you ride.** While navigating (and in compass mode), the compass sensor ran the whole time, although above walking pace the map already takes your direction from the GPS and ignored the compass. The sensor is now switched off above about 22 km/h and switched on again below about 14 km/h, well before the compass is used again (below about 5 km/h). If you brake so hard that the compass is needed before it has given its first reading, the map keeps your last GPS direction until it has, so it doesn't swing north for a moment. On foot and when cycling slowly nothing changes. A phone without a compass behaves exactly as before.
- **Downloaded maps are opened in the background at start.** Opening each downloaded map file (reading its header) happened on the main thread while the map screen was being set up; with several country maps that could hold up the first frame. It's now done on a background thread just before the map is built. The map that appears is the same.
- **Speed cameras along a route are matched in the background.** When navigation starts (and after a reroute), every speed camera from your search data near the route was matched against the whole route on the main thread, which could make the screen stutter for a moment on a long route. That now happens on a background thread; the warnings are the same.
- **The voice engine is let go when a trip ends.** After your first navigation, MapsCtrl stayed connected to the phone's text-to-speech engine until Android closed the app. It now disconnects when you end navigation and connects again when the next trip starts (or when you tap "Test the voice"), exactly like the first trip after opening the app. Ending navigation also hands back the audio focus straight away, so music that was turned down for a sentence that got cut off goes back to normal volume.

## v0.5.0 (2026-11-20)

**This release encrypts some of what the app stores on the phone. An older version cannot read it afterwards, so export or back up anything you need before you update (details below).** Built and checked by CI only: nothing in this release had been run on a phone when it was published.

### Security

- **Your saved places and tracks are now encrypted on the phone.** The file that holds them (`saved.json` in MapsCtrl's private storage: every saved place with its name, note and position, and every recorded or imported track with all its points and times) is now stored encrypted with AES-256-GCM. The key is 32 random bytes made on your phone, kept in the file `data.key` next to it, and itself encrypted by a key in the phone's secure hardware (the Android Keystore, in the StrongBox chip when the phone has one) that can't be copied off the phone. It needs no PIN or fingerprint. What this protects: a copy of MapsCtrl's files taken off the phone (from a backup image, a repair shop's dump, a rooted file manager) no longer shows where you saved places or where you recorded tracks. What it doesn't: someone who can use your unlocked phone, or software that runs inside MapsCtrl itself, sees them as before.
- **Moving to the encrypted file can't lose anything.** The first start after the update reads your saved file as before, writes an encrypted copy next to it, reads that copy back from storage, decrypts it and compares it byte for byte with the original, and only then puts it in the original's place in one step. If anything goes wrong on the way (no space, the phone's secure hardware not answering, the app being closed), the original file stays exactly as it was, MapsCtrl keeps using it, and it tries again at the next start. On a phone where the secure hardware can't make a key at all, MapsCtrl keeps reading and writing the ordinary file as before.
- **If the key is ever lost, nothing is deleted.** The key lives in the phone's secure hardware, so it doesn't survive copying MapsCtrl's files to another phone. If the encrypted file can't be opened, MapsCtrl shows "Can't open your saved places" instead of the map, with **Try again**. It doesn't write anything while that's showing, so nothing is overwritten. **Start empty** (only after you confirm) moves the unreadable file aside as `saved.json.unreadable` (and the key file as `data.key.unreadable`) instead of deleting it, and lets you go on with an empty list; your maps and settings aren't affected.
- **GPX files stay normal GPX.** Export still writes an ordinary GPX file to the place you pick, and import still reads ordinary GPX. A track that's being recorded is kept in memory as before and is encrypted when you stop the recording and it's saved.
- **An older MapsCtrl can't read the encrypted file.** Don't install an older version over this one: it would show no saved places or tracks (the encrypted file stays on the phone and opens again in this version or a newer one, as long as the older version didn't save anything in the meantime: saving a place or a track there would write a new file over the encrypted one). If you want to go back, export everything to GPX first (**Saved > Export**).
- **Also safer than before:** a saved file that turns out to be damaged is now set aside as `saved.json.unreadable` instead of being overwritten by the next save, and a place can no longer be saved in the split second before the saved file has been read at start (which could have replaced the file with only that place).

**Not encrypted, on purpose:**

- **Map files, search data, routing data and terrain data** (`maps/*.map`, `poi/*.poi`, `brouter/segments4/*.rd5`, `dem/*.zip`): they're public downloads, hundreds of megabytes to gigabytes each, and the map, search and routing engines read them directly from storage piece by piece. Encrypting them would mean decrypting whole files into memory. Which regions you downloaded can therefore still be seen from MapsCtrl's files, and so can the list of installed maps (`maps/installed.json`), which says nothing the map files next to it don't.
- **The map style and routing profiles** (`theme/`, `brouter/profiles2/`): copies of files that are in the app itself.
- **Settings**, including where the map was last looking (so it opens there again), the units, the travel mode and your ntfy server and topic. The last map position is needed before the map is first drawn, and settings are read by parts of the app that must work without waiting for a key. They stay as they were.
- MapsCtrl keeps no search history, no list of recent destinations and no navigation history, so there was nothing of that kind to encrypt.

## v0.4.1 (2026-11-20)

Built and checked by CI only: nothing in this release had been run on a phone when it was published.

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
