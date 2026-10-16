package com.garagelog.app.util

import com.garagelog.app.data.entity.VehicleEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HowToSearchTest {

    @Test
    fun `search uses year make model and engine size, not the nickname`() {
        val guppy = VehicleEntity(
            id = "g", name = "Guppy", year = 2010, make = "GMC", model = "Sierra 2500HD", engine = "", drivetrain = "",
            vin = "", color = "", miles = null, milesDate = "", role = "", notes = "", displacementL = 6.0,
        )
        assertEquals(
            "https://www.youtube.com/results?search_query=2010+GMC+Sierra+2500HD+6.0L+Transfer+case+fluid+how+to",
            howToSearchUrl(guppy, " Transfer case fluid "),
        )
    }

    @Test
    fun `accepts real YouTube video links`() {
        assertTrue(isYouTubeVideoUrl("https://www.youtube.com/watch?v=abc123XYZ"))
        assertTrue(isYouTubeVideoUrl("https://youtube.com/watch?t=30&v=abc123XYZ"))
        assertTrue(isYouTubeVideoUrl("https://m.youtube.com/watch?v=abc123XYZ"))
        assertTrue(isYouTubeVideoUrl("https://youtu.be/abc123XYZ"))
        assertTrue(isYouTubeVideoUrl("https://www.youtube.com/shorts/abc123XYZ"))
    }

    @Test
    fun `rejects anything that isn't a YouTube video`() {
        assertFalse(isYouTubeVideoUrl("http://www.youtube.com/watch?v=abc")) // not https
        assertFalse(isYouTubeVideoUrl("https://www.youtube.com/results?search_query=oil"))
        assertFalse(isYouTubeVideoUrl("https://www.youtube.com/watch"))
        assertFalse(isYouTubeVideoUrl("https://www.youtube.com/@somechannel"))
        assertFalse(isYouTubeVideoUrl("https://youtube.com.evil.example/watch?v=abc"))
        assertFalse(isYouTubeVideoUrl("https://example.com/watch?v=abc"))
        assertFalse(isYouTubeVideoUrl("not a url"))
    }
}
