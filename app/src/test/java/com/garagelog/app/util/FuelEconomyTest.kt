package com.garagelog.app.util

import com.garagelog.app.data.entity.LogCategory
import com.garagelog.app.data.entity.LogEntryEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class FuelEconomyTest {

    private fun fill(miles: Int, gallons: Double, fullTank: Boolean = true, missed: Boolean = false) = LogEntryEntity(
        id = "f$miles",
        vehicleId = "v",
        date = "2026-01-01",
        mileage = miles,
        category = LogCategory.Fuel.name,
        task = "Fuel",
        cost = null,
        parts = "",
        notes = "",
        gallons = gallons,
        fullTank = fullTank,
        missedFillUp = missed,
    )

    @Test
    fun `MPG between consecutive full tanks`() {
        val mpg = fillUpMpg(listOf(fill(1000, 10.0), fill(1300, 15.0)))
        assertEquals(listOf(20f), mpg.map { it.mpg })
    }

    @Test
    fun `partial fills carry their gallons to the next full tank`() {
        // 1000 -> 1400 burned 5 (partial) + 15 (full) = 20 gallons over 400 miles.
        val mpg = fillUpMpg(listOf(fill(1000, 10.0), fill(1200, 5.0, fullTank = false), fill(1400, 15.0)))
        assertEquals(listOf(20f), mpg.map { it.mpg })
    }

    @Test
    fun `a skipped fill-up gives no MPG for that stretch and restarts from it`() {
        // The fill-up between 1300 and 1600 was never logged, so 1300 -> 1600 can't be measured;
        // 1600 is a full tank, so 1600 -> 1900 can.
        val mpg = fillUpMpg(
            listOf(fill(1000, 10.0), fill(1300, 15.0), fill(1600, 12.0, missed = true), fill(1900, 10.0)),
        )
        assertEquals(listOf(20f, 30f), mpg.map { it.mpg })
    }

    @Test
    fun `a skipped partial fill-up waits for the next full tank to restart`() {
        val mpg = fillUpMpg(
            listOf(fill(1000, 10.0), fill(1200, 4.0, fullTank = false, missed = true), fill(1500, 15.0), fill(1800, 10.0)),
        )
        // 1000 -> 1500 is broken by the skip; counting restarts at 1500, so only 1500 -> 1800.
        assertEquals(listOf(30f), mpg.map { it.mpg })
    }
}
