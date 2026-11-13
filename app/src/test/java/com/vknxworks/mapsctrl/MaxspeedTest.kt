package com.vknxworks.mapsctrl

import com.vknxworks.mapsctrl.routing.Maxspeed
import com.vknxworks.mapsctrl.routing.SpeedLimit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MaxspeedTest {

    @Test
    fun numbers() {
        assertEquals(SpeedLimit(50), Maxspeed.parse("50"))
        assertEquals(SpeedLimit(70), Maxspeed.parse("70 km/h"))
        assertEquals(SpeedLimit(48, signMph = true), Maxspeed.parse("30 mph"))
        assertEquals(SpeedLimit(32, signMph = true), Maxspeed.parse("20mph"))
        assertEquals(20, Maxspeed.parse("20_mph")?.signValue)
        assertEquals(30, Maxspeed.parse("30 mph")?.signValue)
    }

    @Test
    fun words() {
        assertTrue(Maxspeed.parse("none")!!.none)
        assertEquals(SpeedLimit(6), Maxspeed.parse("walk"))
        assertNull(Maxspeed.parse("signals"))
        assertNull(Maxspeed.parse(""))
        assertNull(Maxspeed.parse(null))
        assertNull(Maxspeed.parse("fast"))
    }

    @Test
    fun zones() {
        assertEquals(SpeedLimit(50), Maxspeed.parse("DE:urban"))
        assertEquals(SpeedLimit(100), Maxspeed.parse("DE:rural"))
        assertTrue(Maxspeed.parse("DE:motorway")!!.none)
        assertEquals(SpeedLimit(30), Maxspeed.parse("BE-BRU:urban"))
        assertEquals(SpeedLimit(30), Maxspeed.parse("DE:zone:30"))
        assertEquals(SpeedLimit(97, signMph = true), Maxspeed.parse("GB:nsl_single"))
        assertNull(Maxspeed.parse("XX:urban"))
    }

    @Test
    fun lowestOfSeveral() {
        assertEquals(SpeedLimit(50), Maxspeed.parse("70;50"))
    }

    @Test
    fun direction() {
        val tags = mapOf("highway" to "primary", "maxspeed" to "70", "maxspeed:forward" to "50", "maxspeed:backward" to "90")
        assertEquals(SpeedLimit(50), Maxspeed.forWay(tags))
        assertEquals(SpeedLimit(90), Maxspeed.forWay(tags + ("reversedirection" to "yes")))
        assertEquals(SpeedLimit(70), Maxspeed.forWay(mapOf("maxspeed" to "70")))
        assertEquals(SpeedLimit(30), Maxspeed.forWay(mapOf("zone:maxspeed" to "30")))
        assertNull(Maxspeed.forWay(mapOf("highway" to "residential")))
    }
}
