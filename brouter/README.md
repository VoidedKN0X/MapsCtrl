# brouter (vendored)

BRouter's offline routing core, built from source because BRouter is not published on Maven Central.

- Upstream: https://github.com/abrensch/brouter
- Commit: `fed8f185ecdba4c01f4d6913bff7c0fae4576a32` (2026-11-06), version 1.7.11-beta
- Modules copied unchanged into one Java library: `brouter-util`, `brouter-codec`, `brouter-expressions`,
  `brouter-mapaccess`, `brouter-core` (only `src/main/java`; tests, the map creator, server and Android app are left out).
- Licence: MIT (`LICENSE` in this folder). MapsCtrl as a whole is GPL-3.0, which the MIT licence allows.

The routing profiles MapsCtrl ships (`car-vario.brf`, `trekking.brf`, `hiking-mountain.brf` and `lookups.dat`)
come from `misc/profiles2` of the same commit and are in `app/src/main/assets/brouter/`.

To update: copy the same five `src/main/java` trees from a newer BRouter commit, update the commit above, and
copy the matching `lookups.dat` and profiles (a `lookups.dat` change means new segment files too).
