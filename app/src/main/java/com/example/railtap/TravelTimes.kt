package com.example.railtap

import kotlin.math.max
import kotlin.math.min

/** A neighbouring station and the minutes needed to travel the hop to/from the clicked station. */
data class Hop(val station: MapStation, val minutes: Int)

/**
 * Average travel time between consecutive stations. Each number is the minutes from the PREVIOUS
 * station in the list (the first station of a line is 0). Stations are looked up by name, ignoring
 * case and the kind of dash, and the order comes from [LinesMapData] (which the Python script
 * generates in line order).
 */
object TravelTimes {

    private val lrt1 = listOf(
        "Fernando Poe Jr." to 0, "Balintawak" to 6, "Monumento" to 3, "5th Avenue" to 2,
        "R. Papa" to 2, "Abad Santos" to 2, "Blumentritt" to 2, "Tayuman" to 2, "Bambang" to 2,
        "Doroteo Jose" to 2, "Carriedo" to 2, "Central Terminal" to 2, "United Nations" to 2,
        "Pedro Gil" to 2, "Quirino" to 2, "Vito Cruz" to 2, "Gil Puyat" to 2, "Libertad" to 2,
        "EDSA" to 2, "Baclaran" to 2, "Redemptorist-Aseana" to 3, "MIA Road" to 3, "PITX" to 3,
        "Ninoy Aquino Avenue" to 3, "Dr. Santos" to 3,
    )

    private val lrt2 = listOf(
        "Recto" to 0, "Legarda" to 3, "Pureza" to 2, "V. Mapa" to 2, "J. Ruiz" to 2, "Gilmore" to 2,
        "Betty Go-Belmonte" to 2, "Araneta Center-Cubao" to 2, "Anonas" to 2, "Katipunan" to 2,
        "Santolan" to 3, "Marikina-Pasig" to 3, "Antipolo" to 3,
    )

    private fun key(name: String) = name.replace('–', '-').trim().lowercase()

    private val minutesFromPrev: Map<MapLine, Map<String, Int>> = mapOf(
        MapLine.LRT1 to lrt1.associate { key(it.first) to it.second },
        MapLine.LRT2 to lrt2.associate { key(it.first) to it.second },
    )

    private fun stationsOf(line: MapLine) =
        LinesMapData.stations.filter { it.line == line && it.isOpen }

    private fun minutesTo(s: MapStation) = minutesFromPrev[s.line]?.get(key(s.name)) ?: 0

    /** (station before, station after). Null at the ends of the line. */
    fun neighbors(s: MapStation): Pair<Hop?, Hop?> {
        val list = stationsOf(s.line)
        val i = list.indexOfFirst { it.name == s.name }
        if (i < 0) return null to null
        val before = list.getOrNull(i - 1)?.let { Hop(it, minutesTo(s)) }       // before -> s
        val after = list.getOrNull(i + 1)?.let { Hop(it, minutesTo(it)) }       // s -> after
        return before to after
    }

    /** Total minutes between two stations on the same line, or null if they are on different lines. */
    fun between(a: MapStation, b: MapStation): Int? {
        if (a.line != b.line) return null
        val list = stationsOf(a.line)
        val i = list.indexOfFirst { it.name == a.name }
        val j = list.indexOfFirst { it.name == b.name }
        if (i < 0 || j < 0) return null
        return (min(i, j) + 1..max(i, j)).sumOf { minutesTo(list[it]) }
    }
}
