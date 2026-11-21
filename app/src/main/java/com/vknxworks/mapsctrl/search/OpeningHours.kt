package com.vknxworks.mapsctrl.search

import java.time.DayOfWeek
import java.time.LocalDateTime

/**
 * Reads the common forms of OpenStreetMap's `opening_hours` tag, offline, to tell whether a place is open at a
 * given moment. Pure logic, unit-tested.
 *
 * Understood: `24/7`; rules separated by `;` where a later rule replaces an earlier one for its days; extra rules
 * after `, ` that add to a day; weekdays and ranges (`Mo-Fr`, `Sa,Su`, `Fr-Mo`); times and lists of times
 * (`08:00-12:00,13:30-18:00`), times past midnight (`18:00-02:00`, `22:00-26:00`); `off` and `closed`; a weekday
 * rule without times (open all day). Public and school holidays (`PH`, `SH`) can't be known offline: those parts
 * are skipped, as if today were not a holiday.
 *
 * Anything else (months, dates, week numbers, "sunrise", open ends with `+`, comments, `||` fallbacks) gives
 * null: MapsCtrl can't tell, and says so instead of guessing.
 */
object OpeningHours {
    private val DAYS = listOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su")
    private val TIME = Regex("""^(\d{1,2}):(\d{2})-(\d{1,2}):(\d{2})$""")
    private val ADDITIONAL = Regex("""(?<=\d|off|closed),\s*(?=(?:Mo|Tu|We|Th|Fr|Sa|Su|PH|SH)\b)""")

    private class Rule(val days: Set<Int>?, val spans: List<IntRange>?, val additive: Boolean, val holidayOnly: Boolean)

    /** True when open at [at], false when closed, null when the hours can't be read. */
    fun isOpen(spec: String?, at: LocalDateTime): Boolean? {
        val text = spec?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (text == "24/7") return true
        if ("||" in text || '"' in text) return null
        val rules = ArrayList<Rule>()
        for (part in text.split(';')) {
            val trimmed = part.trim()
            if (trimmed.isEmpty()) continue
            ADDITIONAL.split(trimmed).forEachIndexed { i, r -> rules += parse(r.trim(), additive = i > 0) ?: return null }
        }
        if (rules.isEmpty()) return null
        val minute = at.hour * 60 + at.minute
        val today = at.dayOfWeek.ordinal()
        val yesterday = (today + 6) % 7
        if (spansFor(rules, today).any { minute in it }) return true
        // Yesterday's times that run past midnight.
        if (spansFor(rules, yesterday).any { it.last >= 1440 && minute + 1440 in it }) return true
        return false
    }

    private fun DayOfWeek.ordinal(): Int = value - 1

    /** The open times of [day] (0 = Monday): the last plain rule for that day, plus any extra rules after it. */
    private fun spansFor(rules: List<Rule>, day: Int): List<IntRange> {
        var out: List<IntRange> = emptyList()
        for (r in rules) {
            if (r.holidayOnly) continue
            if (r.days != null && day !in r.days) continue
            val spans = r.spans ?: listOf(0 until 1440)
            out = if (r.additive) out + spans else spans
        }
        return out
    }

    private fun parse(rule: String, additive: Boolean): Rule? {
        val tokens = rule.replace(Regex(",\\s+(?=\\d)"), ",").split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return null
        var i = 0
        var days: Set<Int>? = null
        var holidayOnly = false
        if (looksLikeDays(tokens[0])) {
            val parsed = days(tokens[0]) ?: return null
            days = parsed.first
            holidayOnly = parsed.first.isEmpty() && parsed.second
            i = 1
        }
        val rest = tokens.drop(i)
        val spans: List<IntRange>? = when {
            rest.isEmpty() -> if (days == null) return null else null
            rest.size == 1 && rest[0] in setOf("off", "closed") -> emptyList()
            rest.size == 1 && rest[0] == "open" -> null
            rest.size == 1 -> times(rest[0]) ?: return null
            else -> return null
        }
        return Rule(days, spans, additive, holidayOnly)
    }

    private fun looksLikeDays(t: String): Boolean = t.split(',', '-').all { it in DAYS || it == "PH" || it == "SH" }

    /** The weekdays in "Mo-Fr,Su,PH" (0 = Monday), and whether a holiday was named. */
    private fun days(t: String): Pair<Set<Int>, Boolean>? {
        val out = HashSet<Int>()
        var holiday = false
        for (item in t.split(',')) {
            if (item == "PH" || item == "SH") { holiday = true; continue }
            val ends = item.split('-')
            when (ends.size) {
                1 -> out += DAYS.indexOf(ends[0]).takeIf { it >= 0 } ?: return null
                2 -> {
                    val a = DAYS.indexOf(ends[0]).takeIf { it >= 0 } ?: return null
                    val b = DAYS.indexOf(ends[1]).takeIf { it >= 0 } ?: return null
                    var d = a
                    while (true) { out += d; if (d == b) break; d = (d + 1) % 7 }
                }
                else -> return null
            }
        }
        return out to holiday
    }

    /** "08:00-12:00,13:00-17:30" as minute ranges from the day's midnight; an end at or before the start runs past midnight. */
    private fun times(t: String): List<IntRange>? = t.split(',').map { span ->
        val m = TIME.matchEntire(span) ?: return null
        val (h1, m1, h2, m2) = m.destructured
        val start = h1.toInt() * 60 + m1.toInt()
        var end = h2.toInt() * 60 + m2.toInt()
        if (start >= 1440 || m1.toInt() > 59 || m2.toInt() > 59 || end > 48 * 60) return null
        if (end <= start) end += 1440
        start until end
    }
}
