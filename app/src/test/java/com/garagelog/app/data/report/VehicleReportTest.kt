package com.garagelog.app.data.report

import com.garagelog.app.data.entity.LogCategory
import com.garagelog.app.data.entity.LogEntryEntity
import com.garagelog.app.data.entity.MaintenanceScheduleEntity
import com.garagelog.app.data.entity.VehicleEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleReportTest {
    private val vehicle = VehicleEntity(
        id = "v1", name = "Guppy", year = 2010, make = "GMC", model = "Sierra 2500HD", engine = "6.0L V8",
        drivetrain = "4WD", vin = "1GTHK53K0AF000000", color = "White", miles = 204571, milesDate = "2026-10-01",
        role = "Daily driver", notes = "Bought from a neighbour for \$9,000",
    )

    private fun log(id: String, date: String, mileage: Int, category: LogCategory, cost: Double) = LogEntryEntity(
        id = id, vehicleId = "v1", date = date, mileage = mileage, category = category.name,
        task = "Task $id", cost = cost, parts = "", notes = "",
    )

    private fun build(
        schedules: List<MaintenanceScheduleEntity> = emptyList(),
        logs: List<LogEntryEntity> = emptyList(),
    ) = assembleReport(vehicle, logs, emptyList(), schedules, emptyMap(), today = "2026-10-16")

    @Test
    fun specsKeepVinButDropNotesAndRole() {
        val labels = build().specs.map { it.first }
        assertTrue("VIN" in labels)
        assertFalse("Notes" in labels)
        assertFalse("Role" in labels)
    }

    @Test
    fun neverDoneTaskReadsAsAStatementNotAnInstruction() {
        val tracked = MaintenanceScheduleEntity("s1", "v1", "Oil change", 5000, 6, null, null)
        val noInterval = MaintenanceScheduleEntity("s2", "v1", "Wipers", null, null, null, null)
        val labels = build(schedules = listOf(tracked, noInterval)).maintenance.associate { it.task to it.statusLabel }
        assertEquals("Not recorded yet", labels["Oil change"])
        assertEquals("No interval set", labels["Wipers"])
    }

    @Test
    fun logsNewestFirstAndSpendSplitByFuel() {
        val report = build(
            logs = listOf(
                log("a", "2026-01-05", 200000, LogCategory.Routine, 80.0),
                log("b", "2026-09-10", 204000, LogCategory.Fuel, 60.0),
                log("c", "2026-05-02", 202000, LogCategory.Repair, 420.0),
            ),
        )
        assertEquals(listOf("b", "c", "a"), report.logs.map { it.id })
        val summary = report.summary.associate { it.second to it.first }
        assertEquals("\$500.00", summary["service & repairs logged"])
        assertEquals("\$60.00", summary["fuel logged"])
    }

    @Test
    fun diagnosisSummaryPrefersTldr() {
        val md = "**TL;DR:** Likely the DRL relay. Check fuse 12 first.\n\n## Most likely cause\nLong reasoning here."
        assertEquals("Likely the DRL relay. Check fuse 12 first.", diagnosisSummary(md))
    }

    @Test
    fun diagnosisSummaryFallsBackToMostLikelyCause() {
        val md = "## Most likely cause\n\nWorn PCV valve,\nfeeding oil to the intake.\n\n## Other possibilities\n- Rings"
        assertEquals("Worn PCV valve, feeding oil to the intake.", diagnosisSummary(md))
    }
}
