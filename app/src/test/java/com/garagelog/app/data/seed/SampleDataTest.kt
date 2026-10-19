package com.garagelog.app.data.seed

import com.garagelog.app.data.entity.LogCategory
import com.garagelog.app.util.DueStatus
import com.garagelog.app.util.computeDueInfo
import com.garagelog.app.util.fillUpMpg
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SampleDataTest {

    // computeDueInfo compares against the real today, so the samples are built from it too.
    private val sample = SampleData.build(LocalDate.now(), stamp = 1L)

    @Test
    fun `every sample record is marked as sample, so sync and backups skip it`() {
        assertTrue(sample.vehicles.all { SampleData.isSample(it.id) })
        assertTrue(sample.logs.all { SampleData.isSample(it.id) && SampleData.isSample(it.vehicleId) })
        assertTrue(sample.issues.all { SampleData.isSample(it.id) && SampleData.isSample(it.vehicleId) })
        assertTrue(sample.schedules.all { SampleData.isSample(it.id) && SampleData.isSample(it.vehicleId) })
    }

    @Test
    fun `ids are unique and linked schedules exist`() {
        val logIds = sample.logs.map { it.id }
        assertEquals(logIds.size, logIds.toSet().size)
        val scheduleIds = sample.schedules.map { it.id }.toSet()
        assertTrue(sample.logs.mapNotNull { it.fulfillsScheduleId }.all { it in scheduleIds })
    }

    @Test
    fun `the truck has one overdue and one due-soon item, the car has neither`() {
        fun statuses(vehicleId: String) = sample.schedules.filter { it.vehicleId == vehicleId }.associate { sched ->
            val miles = sample.vehicles.first { it.id == vehicleId }.miles
            sched.taskName to computeDueInfo(sched, miles).status
        }
        val truck = statuses(SampleData.TRUCK_ID)
        assertEquals(DueStatus.OVERDUE, truck["Oil change"])
        assertEquals(DueStatus.DUE_SOON, truck["Tire rotation"])
        val car = statuses(SampleData.CAR_ID)
        assertTrue(car.values.none { it == DueStatus.OVERDUE || it == DueStatus.DUE_SOON })
    }

    @Test
    fun `fill-ups give believable mpg and reach the current odometer`() {
        for (vehicle in sample.vehicles) {
            val fuel = sample.logs.filter { it.vehicleId == vehicle.id && it.category == LogCategory.Fuel.name }
            assertTrue("${vehicle.name}: ${fuel.size} fill-ups", fuel.size in 30..80)
            assertEquals(vehicle.miles, fuel.maxOf { it.mileage!! })
            val mpg = fillUpMpg(fuel).map { it.mpg }
            val (low, high) = if (vehicle.id == SampleData.TRUCK_ID) 13f to 20f else 22f to 33f
            assertTrue("${vehicle.name} mpg $mpg", mpg.all { it in low..high })
            assertTrue(fuel.all { (it.gallons ?: 0.0) > 3.0 })
        }
    }

    @Test
    fun `the same day builds the same history`() {
        val day = LocalDate.of(2026, 10, 18)
        assertEquals(SampleData.build(day, 1L), SampleData.build(day, 1L))
    }
}
