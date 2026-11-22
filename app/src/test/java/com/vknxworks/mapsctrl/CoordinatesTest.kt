package com.vknxworks.mapsctrl

import com.vknxworks.mapsctrl.geo.Coordinates
import com.vknxworks.mapsctrl.geo.LatLon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CoordinatesTest {

    private fun near(expected: LatLon, actual: LatLon?) {
        assertNotNull(actual)
        assertEquals(expected.lat, actual!!.lat, 1e-5)
        assertEquals(expected.lon, actual.lon, 1e-5)
    }

    @Test
    fun decimal() {
        near(LatLon(50.8466, 4.3528), Coordinates.parse("50.8466, 4.3528"))
        near(LatLon(50.8466, 4.3528), Coordinates.parse("50.8466 4.3528"))
        near(LatLon(-33.8688, 151.2093), Coordinates.parse("-33.8688,151.2093"))
        near(LatLon(50.8466, 4.3528), Coordinates.parse("50,8466; 4,3528"))
    }

    @Test
    fun hemispheres() {
        near(LatLon(50.8466, 4.3528), Coordinates.parse("N 50.8466 E 4.3528"))
        near(LatLon(50.8466, -4.3528), Coordinates.parse("50.8466N, 4.3528W"))
        // Longitude first, but the letters say which is which.
        near(LatLon(43.7384, 7.4246), Coordinates.parse("7.4246E 43.7384N"))
    }

    @Test
    fun degreesMinutesSeconds() {
        near(LatLon(50.0 + 50.0 / 60 + 47.8 / 3600, 4.0 + 21.0 / 60 + 10.1 / 3600), Coordinates.parse("50°50'47.8\"N 4°21'10.1\"E"))
        near(LatLon(50.0 + 50.797 / 60, 4.0 + 21.168 / 60), Coordinates.parse("N 50° 50.797' E 4° 21.168'"))
    }

    @Test
    fun geoUri() {
        near(LatLon(43.7384, 7.4246), Coordinates.parse("geo:43.7384,7.4246?z=16"))
        near(LatLon(43.7384, 7.4246), Coordinates.parse("geo:0,0?q=43.7384,7.4246(Casino)"))
        assertEquals("Grand Place", Coordinates.geoUriQuery("geo:0,0?q=Grand%20Place"))
    }

    @Test
    fun notCoordinates() {
        assertNull(Coordinates.parse("Monaco"))
        assertNull(Coordinates.parse("91, 10"))
        assertNull(Coordinates.parse("10, 200"))
        assertNull(Coordinates.parse("Avenue de la Madone 4"))
        assertNull(Coordinates.parse(""))
    }

    @Test
    fun malformedGeoUrisNeverThrow() {
        // A stray or incomplete "%" used to throw from URLDecoder and close the app.
        assertNull(Coordinates.parse("geo:0,0?q=100%"))
        assertNull(Coordinates.parse("geo:0,0?q=%zz"))
        assertEquals("100%", Coordinates.geoUriQuery("geo:0,0?q=100%"))
        assertEquals("50% korting", Coordinates.geoUriQuery("geo:0,0?q=50%25+korting"))
        assertEquals("%zz caf\u00e9", Coordinates.geoUriQuery("geo:0,0?q=%zz+caf%C3%A9"))
        assertEquals("50% korting", Coordinates.geoUriQuery("geo:0,0?q=50% korting"))
    }

    @Test
    fun percentDecode() {
        assertEquals("a b", Coordinates.percentDecode("a+b", plusAsSpace = true))
        assertEquals("a+b", Coordinates.percentDecode("a+b", plusAsSpace = false))
        assertEquals("Li\u00e8ge", Coordinates.percentDecode("Li%C3%A8ge", plusAsSpace = false))
        assertEquals("%", Coordinates.percentDecode("%", plusAsSpace = true))
        assertEquals("%4", Coordinates.percentDecode("%4", plusAsSpace = true))
        assertEquals("x%G1", Coordinates.percentDecode("x%G1", plusAsSpace = true))
    }

    @Test
    fun geoUriLabel() {
        assertEquals("Gent Sint-Pieters", Coordinates.geoUriLabel("geo:51.03,3.71?q=51.03,3.71(Gent%20Sint-Pieters)"))
        assertEquals("Station (perron 5)", Coordinates.geoUriLabel("geo:51.03,3.71?q=51.03,3.71(Station%20(perron%205))"))
        assertEquals("Station (perron 5)", Coordinates.geoUriLabel("geo:51.03,3.71?q=51.03,3.71(Station%20%28perron%205%29)"))
        assertEquals("100%", Coordinates.geoUriLabel("geo:51.03,3.71?q=51.03,3.71(100%)"))
        assertNull(Coordinates.geoUriLabel("geo:51.03,3.71"))
        assertNull(Coordinates.geoUriLabel("geo:51.03,3.71?q=51.03,3.71()"))
    }
}
