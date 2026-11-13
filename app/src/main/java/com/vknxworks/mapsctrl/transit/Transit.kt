package com.vknxworks.mapsctrl.transit

import com.vknxworks.mapsctrl.geo.LatLon
import java.time.Instant

/** What a leg of a journey is done by. */
enum class LegKind { WALK, BIKE, CAR, BUS, COACH, TRAM, SUBWAY, RAIL, FERRY, AIRPLANE, CABLE, OTHER;

    val isTransit: Boolean get() = this != WALK && this != BIKE && this != CAR

    companion object {
        /** MOTIS mode names (Mode in the OpenAPI) to our kinds. Unknown modes count as "other" transit. */
        fun fromMotis(mode: String?): LegKind = when (mode) {
            "WALK" -> WALK
            "BIKE", "RENTAL" -> BIKE
            "CAR", "CAR_PARKING", "CAR_DROPOFF", "ODM", "RIDE_SHARING" -> CAR
            "BUS", "FLEX" -> BUS
            "COACH" -> COACH
            "TRAM" -> TRAM
            "SUBWAY", "METRO" -> SUBWAY
            "RAIL", "HIGHSPEED_RAIL", "LONG_DISTANCE", "NIGHT_RAIL", "REGIONAL_FAST_RAIL", "REGIONAL_RAIL", "SUBURBAN" -> RAIL
            "FERRY" -> FERRY
            "AIRPLANE" -> AIRPLANE
            "FUNICULAR", "AERIAL_LIFT", "AREAL_LIFT", "CABLE_CAR" -> CABLE
            else -> OTHER
        }
    }
}

/** A stop (or the start or end) of a leg. [track] is the platform or track, live when known. */
data class TransitStop(
    val name: String,
    val point: LatLon,
    val track: String?,
    val scheduledTrack: String?,
    val cancelled: Boolean = false,
)

data class TransitLeg(
    val kind: LegKind,
    val from: TransitStop,
    val to: TransitStop,
    val start: Instant,
    val end: Instant,
    val scheduledStart: Instant?,
    val scheduledEnd: Instant?,
    val realTime: Boolean,
    /** The line as it's signed: "TER 881134", "6", "M1". Null for walking. */
    val line: String?,
    val lineLongName: String?,
    val headsign: String?,
    val agency: String?,
    /** ARGB colours from the timetable, when it has them. */
    val color: Int?,
    val textColor: Int?,
    val distanceMeters: Double?,
    /** Stops between [from] and [to]. */
    val intermediateStops: Int,
    val cancelled: Boolean,
    /** Stay in the vehicle: it continues as this leg's line (interlining). */
    val stayOnBoard: Boolean,
    val geometry: List<LatLon>,
) {
    val durationSeconds: Long get() = (end.epochSecond - start.epochSecond).coerceAtLeast(0)
    val delayedMinutes: Long? get() = scheduledStart?.let { (start.epochSecond - it.epochSecond) / 60 }?.takeIf { realTime && it != 0L }
}

data class Itinerary(
    val start: Instant,
    val end: Instant,
    val transfers: Int,
    val legs: List<TransitLeg>,
) {
    val durationSeconds: Long get() = (end.epochSecond - start.epochSecond).coerceAtLeast(0)
    val walkMeters: Double get() = legs.filter { it.kind == LegKind.WALK }.sumOf { it.distanceMeters ?: 0.0 }
    val transitLegs: List<TransitLeg> get() = legs.filter { it.kind.isTransit }
}

/** The answer to one search: journeys with transit, and the walk-only option when there is one. */
data class TransitPlan(val itineraries: List<Itinerary>, val direct: List<Itinerary>)

/** When to travel: now, leave at a time, or arrive by a time. */
sealed interface TransitTime {
    data object Now : TransitTime
    data class DepartAt(val time: Instant) : TransitTime
    data class ArriveBy(val time: Instant) : TransitTime
}
