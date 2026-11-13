# MapsCtrl

**Private, offline maps and navigation: your maps on your phone, nothing else.**

[![Latest release](https://img.shields.io/github/v/release/VoidedKN0X/MapsCtrl?label=download)](https://github.com/VoidedKN0X/MapsCtrl/releases/latest) ![Android 15+](https://img.shields.io/badge/Android-15%2B-3DDC84?logo=android&logoColor=white) [![License: GPL-3.0](https://img.shields.io/badge/license-GPL--3.0-blue)](LICENSE) ![Telemetry-free](https://img.shields.io/badge/telemetry-none-brightgreen) ![Offline](https://img.shields.io/badge/works-offline-brightgreen) [![Sponsor](https://img.shields.io/badge/sponsor-GitHub-ea4aaa?logo=githubsponsors&logoColor=white)](https://github.com/sponsors/VoidedKN0X)

MapsCtrl shows OpenStreetMap maps you download once, then works without a connection:
- search places, addresses and coordinates on the phone
- routes by car, bike and on foot, worked out on the phone
- turn-by-turn navigation with spoken directions, speed limits and speed camera warnings

No account, no tracking, no map tiles streamed from a server. It only goes online when you start a download.

Part of the [Ctrl series](#the-ctrl-series) by VKNXWorks.

<a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/VoidedKN0X/MapsCtrl"><img src=".github/assets/badge_obtainium.png" alt="Get it on Obtainium" height="80"></a>

## Features

- **Offline vector maps** of any country or region from [download.mapsforge.org](https://download.mapsforge.org), drawn on the phone in MapsCtrl's own style: neutral greys, muted water and green, and violet only for your route, the selected place and your position. Day and night colours follow your phone's dark theme
- **Downloads:** browse continents, countries and regions, download with progress, check for updates, update and delete, see how much space maps and routing data take, or import a Mapsforge `.map` file you already have
- **Offline search** by name, address, category and coordinates (decimal, degrees-minutes-seconds or `geo:` links), with quick buttons for food, fuel, charging, parking, groceries, hotels, pharmacies, cash, toilets and transport
- **Place sheet** with the category, address, opening hours, phone, website and other OpenStreetMap details, and **Route to**, **Save** and **Share**
- **Routes by car, bike and on foot** with [BRouter](https://github.com/abrensch/brouter), from your position, a place or coordinates you type, or a point you tap on the map. Routing data is downloaded per area, only after MapsCtrl has shown you the size and you agreed
- **Turn-by-turn navigation:** next-turn card, arrival time, time and distance left, your speed, the **speed limit** of the road, **speed camera warnings**, **spoken directions** with your phone's text-to-speech (offline voices work) and **rerouting** when you leave the route. The map turns with your direction of travel
- **Saved places and tracks**, **track recording** (also with the screen off) and **GPX import and export**
- **Share your ETA with ntfy** (off by default): while navigating, your arrival time and the distance left go to an ntfy topic on a server you choose. Never your position
- **Opens `geo:` links** from other apps
- **Ctrl / Signal look:** dark and light, following your phone, one violet accent, Space Grotesk and JetBrains Mono

**Not yet:** Android Auto, hillshading and contour lines, public transport and live traffic. Turn instructions don't name streets yet.

## Requirements

- Android 15 or newer, on a 64-bit ARM phone (nearly every phone from the last several years)
- Works on any Android ROM as a normal app: stock Android, LineageOS, GrapheneOS, CalyxOS, /e/OS and the phone makers' own systems. It doesn't need root
- **Google-free and de-Googled:** no Google Play services, Firebase or Google location library. Your position comes from Android's own location service
- Spoken directions use your phone's text-to-speech engine. Download an offline voice in Android's text-to-speech settings to hear directions without a connection

## Install

1. Open the [latest release](https://github.com/VoidedKN0X/MapsCtrl/releases/latest) on your phone, scroll down to **Assets** and tap the file that ends in `.apk` (for example `MapsCtrl-v0.3.0.apk`). The "Source code" zip and tar.gz files are not the app; you don't need them.
2. Open it on your phone. Android may ask you to allow installing apps from your browser or file manager.
3. Open MapsCtrl, tap **Download maps** and pick your country or region.
4. Updates install over the old version. Your maps, saved places and settings are kept.

**Updating from 0.2.0:** MapsCtrl 0.3.0 is a new app with a new map format, so download your maps again (see the [changelog](CHANGELOG.md)).

**Automatic updates:** on your phone, tap the Obtainium button above. Install [Obtainium](https://github.com/ImranR98/Obtainium) first; the button adds MapsCtrl and installs every new release for you. If the button doesn't open the app, add `https://github.com/VoidedKN0X/MapsCtrl` in Obtainium by hand. If you use an F-Droid client with the CtrlF-Droid repository, MapsCtrl updates from there as well.

## Getting started

- **Maps:** tab **Downloads**, then **Add maps**. Turn **Search data** off if you only want to look at the map (it saves about a third of the space).
- **Search:** tap the search bar. Type a name, a street with a house number, a category such as "pharmacy", or coordinates.
- **A place:** tap a symbol or name on the map, or long-press anywhere for a pin.
- **Route:** **Route to** on a place, choose car, bike or foot. Tap **From** to start somewhere else than your position. The first route in an area asks to download routing data.
- **Navigate:** **Start**. Tap the speaker to mute, **End** to stop.
- **Settings** (profile picture, top right): units, map colours, voice, speed limit and camera warnings, ETA sharing.

## Is it safe to install?

- **Open source.** All of MapsCtrl's code is in this repo, under the GPL-3.0 licence. Anyone can read it.
- **Built by GitHub, not on a personal computer.** Every release on the Releases page is built by [the build workflow](.github/workflows/build.yml) on GitHub Actions, straight from the code in this repo. Each build also checks that the app has no Google libraries and no permissions it shouldn't have.
- **Always signed with the same key.** Android refuses to install an update signed with a different key over MapsCtrl. The SHA-256 fingerprint of MapsCtrl's signing certificate is:

  ```
  68:77:8C:2C:AF:00:30:9D:95:74:4D:2D:70:F0:7C:EF:2A:9F:D8:5F:F1:0D:B0:D5:B4:78:0E:AB:25:A1:B6:92
  ```

  Advanced users can check it with [AppVerifier](https://github.com/soupslurpr/AppVerifier).

**Check a download.** Every release is built and published by GitHub Actions, never on a personal computer:

- The release notes end with the APK's **SHA-256** checksum. Compare it with `sha256sum MapsCtrl-vX.Y.Z.apk` (or `Get-FileHash` on Windows).
- Each release APK has a signed **build provenance attestation**. With the [GitHub CLI](https://cli.github.com/), `gh attestation verify MapsCtrl-vX.Y.Z.apk -R VoidedKN0X/MapsCtrl` proves it was built by this repo's workflow from the tagged commit.
- The signing-certificate fingerprint above stays the same for every release.

**Permissions.**

| Permission | What MapsCtrl uses it for |
| --- | --- |
| Internet | Only for downloads you start (maps, search data, routing data) and, if you turn it on, ETA sharing to your ntfy server. Search, routing and navigation work offline. |
| Location (*you allow it*) | Your position on the map, navigation and track recording. It stays on the phone. MapsCtrl never asks for background location. |
| Foreground service (location) | Keeps navigation and track recording going with the screen off, with a notification while it runs. |
| App-internal broadcasts (`com.vknxworks.mapsctrl.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`) | Added by AndroidX so that only MapsCtrl itself can send to its own internal receivers. |

See [Privacy](#privacy) for what happens to your data.

## Privacy

**Telemetry-free.** MapsCtrl has no analytics, crash reporting, usage stats or tracking of any kind, and no account.

MapsCtrl connects to these servers, and only when you start it:

| Server | When | What is sent |
| --- | --- | --- |
| `download.mapsforge.org` | You open **Add maps**, download or update a map, or tap **Check for updates** | A normal HTTPS request for the list or the file. No location, no identifiers; the user agent is only `MapsCtrl/<version>` |
| `brouter.de` | A route needs routing data and you tap **Download** | The same: the list of tiles, then the tiles you agreed to |
| Your ntfy server | Only with **Share my ETA** on, while navigating, and for **Send a test message** | Your destination's name, arrival time and distance left, to the topic you set |

- Search, routes and navigation run on the phone. MapsCtrl never sends what you search for or where you go.
- No map tiles, geocoding or routing from online services.
- Your maps, saved places, tracks and settings are stored in MapsCtrl's private storage and can't be read by other apps. Android backups don't include them.
- The Support, Report a bug, Source code and Licence links, and websites on a place sheet, open in your browser only when you tap them.

## How MapsCtrl is made

MapsCtrl is a one-person project by VoidedKN0X (VKNXWorks). Much of the code is written with the help of AI (Claude, by Anthropic). I decide what the app does, review the changes and use the app myself. Every change is listed in the [changelog](CHANGELOG.md), and you can read every line of code here.

Since 0.3.0 MapsCtrl is written from scratch on its own engine; it is not based on another maps app.

## Build from source

You need [Android Studio](https://developer.android.com/studio), or JDK 17 or newer and the Android SDK.

1. Clone the repo:
   ```bash
   git clone https://github.com/VoidedKN0X/MapsCtrl.git
   cd MapsCtrl
   ```
2. Build the app:
   ```bash
   ./gradlew assembleDebug
   ```
   On Windows, use `gradlew.bat assembleDebug`.
3. The APK is in `app/build/outputs/apk/debug/`. Copy it to your phone and install it.

BRouter isn't published on Maven Central, so its routing core is built from source in the `brouter/` folder (see [brouter/README.md](brouter/README.md)). Unit tests: `./gradlew :app:testDebugUnitTest`.

**Dependency verification.** Every library and build plugin download is checked against the sha256 checksums in `gradle/verification-metadata.xml`, and the build fails on any mismatch. When you change a dependency, a plugin or the Gradle version, run the **Dependency verification metadata** workflow (Actions tab, *Run workflow*), check the diff of the file it prints and commit it in the same push.

## Support the project

MapsCtrl is free and open source. If it's useful to you, you can support development on [GitHub Sponsors](https://github.com/sponsors/VoidedKN0X). Please report bugs and ideas with the [bug report and feature request forms](https://github.com/VoidedKN0X/MapsCtrl/issues/new/choose). Starring the repo helps too.

Found a security problem? Please don't open a public issue; see [SECURITY.md](SECURITY.md).

## The Ctrl series

Native Android apps for self-hosted services and everyday tools, all with the same design.

| App | What it's for |
| --- | --- |
| [AdCtrl](https://github.com/VoidedKN0X/AdCtrl) | Control your AdGuard Home DNS server |
| [ArrCtrl](https://github.com/VoidedKN0X/ArrCtrl) | Radarr, Sonarr, Prowlarr, Bazarr, qBittorrent, autobrr and Cleanuparr in one app |
| [BeamCtrl](https://github.com/VoidedKN0X/BeamCtrl) | TV remote for phones with an IR blaster |
| [CallCtrl](https://github.com/VoidedKN0X/CallCtrl) | Phone app: dialer, call history and in-call screen |
| [CameraCtrl](https://github.com/VoidedKN0X/CameraCtrl) | Private camera with RAW (DNG) on every lens |
| [ChatCtrl](https://github.com/VoidedKN0X/ChatCtrl) | Matrix chat client for matrix.org and self-hosted Synapse |
| [ClockCtrl](https://github.com/VoidedKN0X/ClockCtrl) | Alarms, timers, stopwatch and world clock |
| [ContactCtrl](https://github.com/VoidedKN0X/ContactCtrl) | Contacts: browse, edit, import and export |
| [CraftyCtrl](https://github.com/VoidedKN0X/CraftyCtrl) | Manage Minecraft servers on Crafty Controller |
| [DateCtrl](https://github.com/VoidedKN0X/DateCtrl) | Calendar for the calendars on your phone and your accounts |
| [FilesCtrl](https://github.com/VoidedKN0X/FilesCtrl) | File manager with NFS, SMB, SFTP, FTP and WebDAV |
| [FrigateCtrl](https://github.com/VoidedKN0X/FrigateCtrl) | Watch and review your Frigate NVR cameras |
| [GalleryCtrl](https://github.com/VoidedKN0X/GalleryCtrl) | Private, offline gallery with an encrypted vault |
| [ImmichCtrl](https://github.com/VoidedKN0X/ImmichCtrl) | Photos and backup for your Immich server |
| [JellyCtrl](https://github.com/VoidedKN0X/JellyCtrl) | Movies, shows, music and Live TV from Jellyfin |
| [LijnCtrl](https://github.com/VoidedKN0X/LijnCtrl) | Live De Lijn bus and tram departures |
| [LumenCtrl](https://github.com/VoidedKN0X/LumenCtrl) | Private, offline photo and video editor |
| [MailCtrl](https://github.com/VoidedKN0X/MailCtrl) | Email for Gmail and Proton Mail |
| **MapsCtrl** (this app) | Private, offline maps and navigation |
| [MemoCtrl](https://github.com/VoidedKN0X/MemoCtrl) | Notes on your Memos server |
| [NaviCtrl](https://github.com/VoidedKN0X/NaviCtrl) | Music player for Navidrome servers |
| [PaperCtrl](https://github.com/VoidedKN0X/PaperCtrl) | Scan and browse documents on Paperless-ngx |
| [PrintCtrl](https://github.com/VoidedKN0X/PrintCtrl) | Bambu Lab printers through Bambuddy, and Spoolman spools |
| [ProxCtrl](https://github.com/VoidedKN0X/ProxCtrl) | Manage your Proxmox VE server |
| [RecorderCtrl](https://github.com/VoidedKN0X/RecorderCtrl) | Private, offline voice recorder |
| [RideCtrl](https://github.com/VoidedKN0X/RideCtrl) | Offline maintenance, fuel and mod log for your vehicles |
| [SeerrCtrl](https://github.com/VoidedKN0X/SeerrCtrl) | Request movies and shows on Seerr / Jellyseerr |
| [ShutterCtrl](https://github.com/VoidedKN0X/ShutterCtrl) | Remote control and photo transfer for Canon cameras |
| [SoundCtrl](https://github.com/VoidedKN0X/SoundCtrl) | JBL headphones and speakers, without the account |
| [SumCtrl](https://github.com/VoidedKN0X/SumCtrl) | Offline calculator, unit converter and work tools |
| [SynapseCtrl](https://github.com/VoidedKN0X/SynapseCtrl) | Administer your Matrix Synapse server |
| [TalkCtrl](https://github.com/VoidedKN0X/TalkCtrl) | Cardo intercom settings, radio and updates |
| [TapoCtrl](https://github.com/VoidedKN0X/TapoCtrl) | Tapo plugs and bulbs on your home network |
| [TextCtrl](https://github.com/VoidedKN0X/TextCtrl) | Private SMS and MMS messaging |
| [TrueCtrl](https://github.com/VoidedKN0X/TrueCtrl) | Monitor and manage your TrueNAS server |
| [WakeCtrl](https://github.com/VoidedKN0X/WakeCtrl) | Keep the screen awake, per app or while charging |
| [WrtCtrl](https://github.com/VoidedKN0X/WrtCtrl) | Manage your OpenWrt router |

## Licence and credits

MapsCtrl is licensed under the [GNU General Public License v3.0](LICENSE). © VKNXWorks

- **Map data:** © [OpenStreetMap](https://www.openstreetmap.org/copyright) contributors, available under the Open Database License (ODbL). Maps and search data are the Mapsforge files from download.mapsforge.org; routing data is BRouter's from brouter.de; both are made from OpenStreetMap.
- **[Mapsforge](https://github.com/mapsforge/mapsforge)** draws the maps: LGPL-3.0, used unchanged from Maven Central. MapsCtrl's map style (`CtrlRenderTheme`) and symbols are its own.
- **[BRouter](https://github.com/abrensch/brouter)** works out routes: MIT licence. Its routing core and the car, bike and foot profiles are included unchanged in `brouter/` and `app/src/main/assets/brouter/` (see [brouter/LICENSE](brouter/LICENSE)).
- **[AndroidSVG](https://github.com/BigBadaboom/androidsvg)** (Apache 2.0) draws the map symbols. **SQLite** (public domain), bundled through AndroidX's sqlite-bundled, reads the search data (Android's own SQLite lacks the R-tree and full-text modules it needs). AndroidX, Jetpack Compose, Material 3 and Kotlin libraries are Apache 2.0.
- **Fonts**, bundled in the app (nothing is downloaded): [Space Grotesk](https://github.com/floriankarsten/space-grotesk) and [JetBrains Mono](https://github.com/JetBrains/JetBrainsMono), both under the SIL Open Font License 1.1 ([FONT-LICENSE-SpaceGrotesk.txt](FONT-LICENSE-SpaceGrotesk.txt), [FONT-LICENSE-JetBrainsMono.txt](FONT-LICENSE-JetBrainsMono.txt)).
