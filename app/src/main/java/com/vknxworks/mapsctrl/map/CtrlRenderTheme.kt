package com.vknxworks.mapsctrl.map

import java.io.File

/**
 * MapsCtrl's own map style, written for Mapsforge's render theme format: neutral greys, muted water and
 * green, white roads with grey edges, and no violet (violet is kept for your route, selection and position).
 * One rule set, two palettes (day and night). [write] puts the theme and its symbols in a folder for Mapsforge.
 */
object CtrlRenderTheme {

    data class Palette(
        val land: String, val outside: String, val water: String, val waterLine: String, val waterText: String,
        val green: String, val forest: String, val farm: String, val residential: String, val industrial: String,
        val commercial: String, val cemetery: String, val sand: String, val ice: String, val military: String,
        val building: String, val buildingLine: String,
        val road: String, val roadCasing: String, val major: String, val majorCasing: String,
        val motorway: String, val motorwayCasing: String, val path: String, val track: String, val rail: String,
        val boundary: String, val aeroway: String,
        val text: String, val textHalo: String, val textMuted: String, val placeText: String,
        val poi: String, val poiGlyph: String, val poiTransport: String, val poiHealth: String,
        /** How strong the terrain shading is, 0..255. */
        val hillMagnitude: Int = 90,
    )

    val Day = Palette(
        land = "#F1F1EE", outside = "#E2E2DD", water = "#C7D2DC", waterLine = "#B3C1CE", waterText = "#5E7184",
        green = "#DDE4D6", forest = "#D3DCCB", farm = "#ECECE4", residential = "#EAEAE6", industrial = "#E6E4E9",
        commercial = "#ECE8E6", cemetery = "#DCE2D8", sand = "#EEEAD9", ice = "#F6F8FA", military = "#EBE4E4",
        building = "#E1E1DC", buildingLine = "#D2D2CB",
        road = "#FFFFFF", roadCasing = "#D2D2CC", major = "#FFFFFF", majorCasing = "#BDBDB6",
        motorway = "#F9F9F7", motorwayCasing = "#9C9CA4", path = "#9B9BA2", track = "#B5AE9E", rail = "#A2A2A8",
        boundary = "#A9A3B4", aeroway = "#DADADF",
        text = "#3C3C44", textHalo = "#F1F1EE", textMuted = "#6C6C74", placeText = "#1E1E24",
        poi = "#6C6C76", poiGlyph = "#FFFFFF", poiTransport = "#5D6F84", poiHealth = "#B05A5A",
    )

    val Night = Palette(
        land = "#141417", outside = "#0B0B0D", water = "#1B232C", waterLine = "#25303B", waterText = "#7D8C9B",
        green = "#171D18", forest = "#151B16", farm = "#18181A", residential = "#18181B", industrial = "#1A191D",
        commercial = "#1B191A", cemetery = "#181C18", sand = "#1D1C18", ice = "#1E2124", military = "#1E1A1A",
        building = "#202024", buildingLine = "#2A2A30",
        road = "#2C2C33", roadCasing = "#0E0E11", major = "#3A3A43", majorCasing = "#0C0C0E",
        motorway = "#4A4A55", motorwayCasing = "#0A0A0C", path = "#5A5A63", track = "#5C574C", rail = "#4C4C54",
        boundary = "#5C566A", aeroway = "#26262B",
        text = "#C9C9CF", textHalo = "#141417", textMuted = "#8E8E96", placeText = "#E8E8EC",
        poi = "#55555F", poiGlyph = "#E8E8EC", poiTransport = "#4A5B6E", poiHealth = "#8E4A4A",
        hillMagnitude = 140,
    )

    /** Writes theme.xml and symbols/ into [dir] and returns the theme file. */
    fun write(dir: File, p: Palette): File {
        dir.mkdirs()
        val symbols = File(dir, "symbols")
        symbols.mkdirs()
        for ((name, glyph) in Symbols.glyphs) {
            val bg = when (name) {
                "bus", "train", "tram", "ferry", "airport", "parking", "fuel", "charging", "bicycle" -> p.poiTransport
                "hospital", "pharmacy", "doctor" -> p.poiHealth
                else -> p.poi
            }
            File(symbols, "$name.svg").writeText(Symbols.svg(glyph, bg, p.poiGlyph))
        }
        val f = File(dir, "theme.xml")
        f.writeText(xml(p))
        return f
    }

    private fun sym(name: String, zoom: Int, priority: Int = 0) =
        """<symbol id="s_$name" src="symbols/$name.svg" symbol-width="18" priority="$priority" display="ifspace"/>"""

    private fun xml(c: Palette): String = """<?xml version="1.0" encoding="UTF-8"?>
<rendertheme xmlns="http://mapsforge.org/renderTheme" version="5" map-background="${c.land}" map-background-outside="${c.outside}">

  <!-- Sea and land -->
  <rule e="way" k="natural" v="issea|sea"><area fill="${c.water}"/></rule>
  <rule e="way" k="natural" v="nosea"><area fill="${c.land}" stroke="${c.land}" stroke-width="1.0"/></rule>

  <!-- Land use -->
  <rule e="way" k="*" v="*" closed="yes">
    <rule e="way" k="landuse" v="forest|wood"><area fill="${c.forest}"/></rule>
    <rule e="way" k="natural" v="wood|scrub|heath|fell"><area fill="${c.forest}"/></rule>
    <rule e="way" k="landuse" v="grass|meadow|village_green|recreation_ground|greenfield|orchard|vineyard|allotments"><area fill="${c.green}"/></rule>
    <rule e="way" k="natural" v="grassland|wetland|marsh"><area fill="${c.green}"/></rule>
    <rule e="way" k="leisure" v="park|garden|common|golf_course|nature_reserve|dog_park|playground"><area fill="${c.green}"/></rule>
    <rule e="way" k="leisure" v="pitch|sports_centre|stadium|track"><area fill="${c.green}" stroke="${c.forest}" stroke-width="0.2"/></rule>
    <rule e="way" k="landuse" v="farm|farmland|farmyard"><area fill="${c.farm}"/></rule>
    <rule e="way" k="landuse" v="residential"><area fill="${c.residential}"/></rule>
    <rule e="way" k="landuse" v="industrial|railway|quarry|landfill|brownfield|construction"><area fill="${c.industrial}"/></rule>
    <rule e="way" k="landuse" v="commercial|retail"><area fill="${c.commercial}"/></rule>
    <rule e="way" k="landuse" v="cemetery"><area fill="${c.cemetery}"/></rule>
    <rule e="way" k="amenity" v="grave_yard"><area fill="${c.cemetery}"/></rule>
    <rule e="way" k="landuse" v="military"><area fill="${c.military}"/></rule>
    <rule e="way" k="natural" v="beach|scree"><area fill="${c.sand}"/></rule>
    <rule e="way" k="natural" v="glacier"><area fill="${c.ice}"/></rule>
    <rule e="way" k="aeroway" v="aerodrome|airport" zoom-min="10"><area fill="${c.aeroway}"/></rule>
    <rule e="way" k="aeroway" v="apron|terminal" zoom-min="13"><area fill="${c.aeroway}" stroke="${c.buildingLine}" stroke-width="0.2"/></rule>
  </rule>

  <!-- Terrain shading: drawn only when hillshading is on and elevation data is on the phone. -->
  <hillshading zoom-min="6" zoom-max="18" magnitude="${c.hillMagnitude}"/>

  <!-- Water -->
  <rule e="way" k="*" v="*" closed="yes">
    <rule e="way" k="natural" v="water"><area fill="${c.water}"/></rule>
    <rule e="way" k="landuse" v="reservoir|basin"><area fill="${c.water}"/></rule>
    <rule e="way" k="waterway" v="riverbank|dock"><area fill="${c.water}"/></rule>
    <rule e="way" k="leisure" v="swimming_pool|water_park" zoom-min="15"><area fill="${c.water}" stroke="${c.waterLine}" stroke-width="0.2"/></rule>
  </rule>
  <rule e="way" k="waterway" v="*" closed="no">
    <rule e="way" k="waterway" v="river" zoom-min="8"><line stroke="${c.water}" stroke-width="1.4"/></rule>
    <rule e="way" k="waterway" v="canal" zoom-min="10"><line stroke="${c.water}" stroke-width="1.0"/></rule>
    <rule e="way" k="waterway" v="stream|drain" zoom-min="13"><line stroke="${c.water}" stroke-width="0.4"/></rule>
    <rule e="way" k="waterway" v="river|canal" zoom-min="13">
      <pathText k="name" font-style="italic" font-size="11" fill="${c.waterText}" stroke="${c.textHalo}" stroke-width="2.5"/>
    </rule>
    <rule e="way" k="waterway" v="dam" zoom-min="14"><line stroke="${c.buildingLine}" stroke-width="0.6"/></rule>
  </rule>

  <!-- Boundaries -->
  <rule e="way" k="boundary" v="administrative">
    <rule e="way" k="admin_level" v="2"><line stroke="${c.boundary}" stroke-width="0.6" stroke-dasharray="8,4" stroke-linecap="butt"/></rule>
    <rule e="way" k="admin_level" v="4" zoom-min="7"><line stroke="${c.boundary}" stroke-width="0.3" stroke-dasharray="6,4" stroke-linecap="butt"/></rule>
  </rule>
  <rule e="way" k="boundary" v="national_park" zoom-min="9"><line stroke="${c.forest}" stroke-width="0.8"/></rule>

  <!-- Buildings -->
  <rule e="way" k="building" v="*" zoom-min="15">
    <area fill="${c.building}" stroke="${c.buildingLine}" stroke-width="0.15"/>
  </rule>
  <rule e="way" k="man_made" v="pier" zoom-min="14"><line stroke="${c.road}" stroke-width="0.6" stroke-linecap="butt"/></rule>

  <!-- Aeroways -->
  <rule e="way" k="aeroway" v="runway" zoom-min="11"><line stroke="${c.major}" stroke-width="3.0" stroke-linecap="butt"/></rule>
  <rule e="way" k="aeroway" v="taxiway" zoom-min="13"><line stroke="${c.major}" stroke-width="0.8"/></rule>

  <!-- Railways -->
  <rule e="way" k="railway" v="rail|light_rail|narrow_gauge|preserved|funicular|monorail" zoom-min="9">
    <line stroke="${c.rail}" stroke-width="0.5" stroke-linecap="butt"/>
    <rule e="way" k="*" v="*" zoom-min="13"><line stroke="${c.land}" stroke-width="0.25" stroke-dasharray="6,6" stroke-linecap="butt"/></rule>
  </rule>
  <rule e="way" k="railway" v="subway|tram" zoom-min="13"><line stroke="${c.rail}" stroke-width="0.3" stroke-linecap="butt"/></rule>
  <rule e="way" k="route" v="ferry" zoom-min="10"><line stroke="${c.waterText}" stroke-width="0.3" stroke-dasharray="8,6"/></rule>

  <!-- Roads: casings first, then fills, from small to big -->
  <rule e="way" k="highway" v="*">
    <rule e="way" k="area" v="yes|true" closed="yes">
      <rule e="way" k="highway" v="pedestrian|footway|service|living_street|platform" zoom-min="15"><area fill="${c.road}" stroke="${c.roadCasing}" stroke-width="0.2"/></rule>
    </rule>
    <rule e="way" k="area" v="~|no|false">
      <!-- paths and tracks -->
      <rule e="way" k="highway" v="footway|path|pedestrian|steps|cycleway|bridleway" zoom-min="15">
        <line stroke="${c.path}" stroke-width="0.3" stroke-dasharray="3,3" stroke-linecap="butt"/>
      </rule>
      <rule e="way" k="highway" v="track" zoom-min="14">
        <line stroke="${c.track}" stroke-width="0.4" stroke-dasharray="6,3" stroke-linecap="butt"/>
      </rule>
      <!-- casings -->
      <rule e="way" k="highway" v="service|living_street" zoom-min="14"><line stroke="${c.roadCasing}" stroke-width="0.9"/></rule>
      <rule e="way" k="highway" v="residential|unclassified|road" zoom-min="13"><line stroke="${c.roadCasing}" stroke-width="1.35"/></rule>
      <rule e="way" k="highway" v="tertiary|tertiary_link" zoom-min="11"><line stroke="${c.majorCasing}" stroke-width="1.6"/></rule>
      <rule e="way" k="highway" v="secondary|secondary_link" zoom-min="9"><line stroke="${c.majorCasing}" stroke-width="1.8"/></rule>
      <rule e="way" k="highway" v="primary|primary_link" zoom-min="8"><line stroke="${c.majorCasing}" stroke-width="2.0"/></rule>
      <rule e="way" k="highway" v="trunk|trunk_link" zoom-min="7"><line stroke="${c.motorwayCasing}" stroke-width="2.1"/></rule>
      <rule e="way" k="highway" v="motorway|motorway_link" zoom-min="5"><line stroke="${c.motorwayCasing}" stroke-width="2.3"/></rule>
      <!-- fills -->
      <rule e="way" k="highway" v="service|living_street" zoom-min="14"><line stroke="${c.road}" stroke-width="0.65"/></rule>
      <rule e="way" k="highway" v="residential|unclassified|road" zoom-min="13"><line stroke="${c.road}" stroke-width="1.05"/></rule>
      <rule e="way" k="highway" v="residential|unclassified|road" zoom-min="11" zoom-max="12"><line stroke="${c.roadCasing}" stroke-width="0.5"/></rule>
      <rule e="way" k="highway" v="tertiary|tertiary_link" zoom-min="11"><line stroke="${c.major}" stroke-width="1.3"/></rule>
      <rule e="way" k="highway" v="secondary|secondary_link" zoom-min="9"><line stroke="${c.major}" stroke-width="1.5"/></rule>
      <rule e="way" k="highway" v="primary|primary_link" zoom-min="8"><line stroke="${c.major}" stroke-width="1.7"/></rule>
      <rule e="way" k="highway" v="trunk|trunk_link" zoom-min="7"><line stroke="${c.motorway}" stroke-width="1.8"/></rule>
      <rule e="way" k="highway" v="motorway|motorway_link" zoom-min="5"><line stroke="${c.motorway}" stroke-width="1.9"/></rule>
      <!-- one-way arrows are left out on purpose: the route line shows the way to go -->
      <!-- names -->
      <rule e="way" k="highway" v="residential|unclassified|road|living_street|pedestrian|service|tertiary|secondary|primary|trunk" zoom-min="15">
        <pathText k="name" font-size="11" fill="${c.text}" stroke="${c.textHalo}" stroke-width="2.5"/>
      </rule>
      <rule e="way" k="highway" v="motorway|trunk|primary" zoom-min="10" zoom-max="14">
        <pathText k="ref" font-style="bold" font-size="10" fill="${c.textMuted}" stroke="${c.textHalo}" stroke-width="2.5" repeat-gap="250"/>
      </rule>
    </rule>
  </rule>

  <!-- Barriers -->
  <rule e="way" k="barrier" v="wall|city_wall|retaining_wall" zoom-min="16"><line stroke="${c.buildingLine}" stroke-width="0.25"/></rule>

  <!-- Points of interest -->
  <rule e="node" k="*" v="*" zoom-min="16">
    <rule e="node" k="amenity" v="restaurant|fast_food|food_court|bbq">${sym("food", 16)}</rule>
    <rule e="node" k="amenity" v="cafe|ice_cream">${sym("cafe", 16)}</rule>
    <rule e="node" k="amenity" v="pub|bar|biergarten|nightclub">${sym("bar", 16)}</rule>
    <rule e="node" k="amenity" v="hospital|clinic">${sym("hospital", 16, 10)}</rule>
    <rule e="node" k="amenity" v="pharmacy">${sym("pharmacy", 16, 5)}</rule>
    <rule e="node" k="amenity" v="doctors|dentist">${sym("doctor", 16)}</rule>
    <rule e="node" k="amenity" v="bank|atm|bureau_de_change">${sym("money", 16)}</rule>
    <rule e="node" k="amenity" v="toilets">${sym("toilets", 17)}</rule>
    <rule e="node" k="amenity" v="school|kindergarten|college|university|library">${sym("school", 16)}</rule>
    <rule e="node" k="amenity" v="place_of_worship">${sym("worship", 16)}</rule>
    <rule e="node" k="amenity" v="police|fire_station|townhall|post_office|embassy">${sym("civic", 16)}</rule>
    <rule e="node" k="amenity" v="cinema|theatre|arts_centre">${sym("culture", 16)}</rule>
    <rule e="node" k="amenity" v="bicycle_rental|bicycle_parking" zoom-min="17">${sym("bicycle", 17)}</rule>
    <rule e="node" k="amenity" v="drinking_water|fountain" zoom-min="17">${sym("water", 17)}</rule>
    <rule e="node" k="shop" v="supermarket|convenience|bakery|mall|department_store">${sym("shop", 16)}</rule>
    <rule e="node" k="shop" v="*" zoom-min="17">${sym("shop", 17, -5)}</rule>
    <rule e="node" k="tourism" v="hotel|hostel|guest_house|motel|alpine_hut|camp_site">${sym("hotel", 16)}</rule>
    <rule e="node" k="tourism" v="museum|attraction|gallery|zoo|theme_park">${sym("museum", 16)}</rule>
    <rule e="node" k="tourism" v="viewpoint">${sym("viewpoint", 16)}</rule>
    <rule e="node" k="tourism" v="information" zoom-min="17">${sym("info", 17)}</rule>
    <rule e="node" k="historic" v="castle|monument|memorial|ruins">${sym("museum", 16)}</rule>
    <rule e="node" k="highway" v="bus_stop" zoom-min="17">${sym("bus", 17)}</rule>
    <rule e="node" k="railway" v="tram_stop" zoom-min="16">${sym("tram", 16)}</rule>
    <rule e="node" k="leisure" v="playground|sports_centre|swimming_pool|fitness_centre" zoom-min="17">${sym("leisure", 17)}</rule>
    <rule e="node" k="*" v="*">
      <caption k="name" position="below" font-size="10" fill="${c.textMuted}" stroke="${c.textHalo}" stroke-width="2.2" text-wrap-width="120"/>
    </rule>
  </rule>
  <rule e="node" k="*" v="*" zoom-min="14">
    <rule e="node" k="amenity" v="fuel">${sym("fuel", 14, 20)}<caption k="name" position="below" font-size="10" fill="${c.textMuted}" stroke="${c.textHalo}" stroke-width="2.2"/></rule>
    <rule e="node" k="amenity" v="charging_station">${sym("charging", 15, 15)}</rule>
    <rule e="node" k="amenity" v="parking">${sym("parking", 15, 10)}</rule>
    <rule e="node" k="railway" v="station|halt">${sym("train", 13, 30)}<caption k="name" position="below" font-size="11" fill="${c.text}" stroke="${c.textHalo}" stroke-width="2.4"/></rule>
    <rule e="node" k="aeroway" v="aerodrome|airport">${sym("airport", 11, 30)}<caption k="name" position="below" font-size="11" fill="${c.text}" stroke="${c.textHalo}" stroke-width="2.4"/></rule>
    <rule e="node" k="amenity" v="ferry_terminal">${sym("ferry", 14)}</rule>
  </rule>
  <rule e="node" k="natural" v="peak|volcano" zoom-min="12">
    ${sym("peak", 12, 5)}
    <caption k="name" position="below" font-size="10" fill="${c.textMuted}" stroke="${c.textHalo}" stroke-width="2.2"/>
  </rule>
  <rule e="node" k="addr:housenumber" v="*" zoom-min="18">
    <caption k="addr:housenumber" font-size="9" fill="${c.textMuted}" stroke="${c.textHalo}" stroke-width="2.0"/>
  </rule>

  <!-- Area names -->
  <rule e="way" k="*" v="*" closed="yes" zoom-min="16">
    <rule e="way" k="leisure" v="park|garden|nature_reserve"><caption k="name" font-size="11" fill="${c.textMuted}" stroke="${c.textHalo}" stroke-width="2.2"/></rule>
    <rule e="way" k="natural" v="water"><caption k="name" font-style="italic" font-size="11" fill="${c.waterText}" stroke="${c.textHalo}" stroke-width="2.2"/></rule>
  </rule>
  <rule e="way" k="building" v="*" zoom-min="18">
    <caption k="addr:housenumber" font-size="9" fill="${c.textMuted}" stroke="${c.textHalo}" stroke-width="2.0"/>
  </rule>

  <!-- Places -->
  <rule e="node" k="place" v="*">
    <rule e="node" k="place" v="country" zoom-max="6"><caption k="name" priority="100" font-style="bold" font-size="13" fill="${c.textMuted}" stroke="${c.textHalo}" stroke-width="3" text-transform="uppercase"/></rule>
    <rule e="node" k="place" v="city" zoom-min="5" zoom-max="15"><caption k="name" priority="90" font-style="bold" font-size="15" fill="${c.placeText}" stroke="${c.textHalo}" stroke-width="3"/></rule>
    <rule e="node" k="place" v="town" zoom-min="8" zoom-max="16"><caption k="name" priority="80" font-style="bold" font-size="13" fill="${c.placeText}" stroke="${c.textHalo}" stroke-width="3"/></rule>
    <rule e="node" k="place" v="village" zoom-min="11"><caption k="name" priority="70" font-size="12" fill="${c.text}" stroke="${c.textHalo}" stroke-width="2.6"/></rule>
    <rule e="node" k="place" v="suburb" zoom-min="12" zoom-max="16"><caption k="name" priority="60" font-size="12" fill="${c.textMuted}" stroke="${c.textHalo}" stroke-width="2.6" text-transform="uppercase"/></rule>
    <rule e="node" k="place" v="hamlet|locality" zoom-min="14"><caption k="name" priority="50" font-size="11" fill="${c.textMuted}" stroke="${c.textHalo}" stroke-width="2.4"/></rule>
    <rule e="node" k="place" v="island" zoom-min="10"><caption k="name" priority="40" font-size="11" fill="${c.text}" stroke="${c.textHalo}" stroke-width="2.4"/></rule>
  </rule>
</rendertheme>
"""
}
