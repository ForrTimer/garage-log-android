package com.garagelog.app.data.seed

import com.garagelog.app.data.entity.BuildPhaseEntity
import com.garagelog.app.data.entity.BuildStepEntity
import com.garagelog.app.data.entity.IssueEntity
import com.garagelog.app.data.entity.IssuePriority
import com.garagelog.app.data.entity.IssueStatus
import com.garagelog.app.data.entity.LogCategory
import com.garagelog.app.data.entity.LogEntryEntity
import com.garagelog.app.data.entity.MaintenanceScheduleEntity
import com.garagelog.app.data.entity.PhaseStatus
import com.garagelog.app.data.entity.StepPriority
import com.garagelog.app.data.entity.VehicleEntity

/**
 * Ethan's own real vehicle data, captured from a JSON backup of his phone (2026-09-02) so a
 * fresh install or a reset starts from his actual current setup instead of a stale snapshot.
 * Previously ported field-for-field from the PWA's seedData() in app.js as of 2026-08-05 —
 * see the "Vehicle Maintenance and Build" Claude Project docs for that original source.
 */
object SeedData {
    const val GUPPY_ID = "guppy"
    const val BLUE_TUNA_ID = "bluetuna"
    const val TACOMA_ID = "tacoma"

    fun vehicles(): List<VehicleEntity> = listOf(
        VehicleEntity(
            id = GUPPY_ID, name = "Guppy", year = 2010, make = "GMC", model = "Sierra 2500HD SLE",
            engine = "6.0L Vortec V8 (gas, flex-fuel)", drivetrain = "4x4",
            vin = "1GT4K0BG4AF106987", color = "Blue", miles = 204295, milesDate = "2026-09-02",
            role = "A-to-B / backup daily driver",
            notes = "Bought 4/3/2026 from Jacob Tramp, \$9,000, as-is. Runs Royal Purple oil.",
            sortOrder = 0,
        ),
        VehicleEntity(
            id = BLUE_TUNA_ID, name = "Blue Tuna", year = 1994, make = "Chevrolet", model = "Suburban K2500",
            engine = "6.5L Detroit Diesel (factory long block)", drivetrain = "4x4",
            vin = "", color = "", miles = 222376, milesDate = "2026-08-04",
            role = "Total rebuild project (off-road capable daily-driver goal)",
            notes = "Build window: Nov 1 – mid-May annually. Reliability-first, DIY-first. Machining goes to a Fairbanks shop.",
            sortOrder = 1,
        ),
        VehicleEntity(
            id = TACOMA_ID, name = "Tacoma", year = 2021, make = "Toyota", model = "Tacoma",
            engine = "", drivetrain = "", vin = "", color = "", miles = null, milesDate = "",
            role = "Wife's daily driver",
            notes = "Added to the tracker for completeness — no detailed history logged yet.",
            sortOrder = 2,
        ),
    )

    fun logEntries(): List<LogEntryEntity> = listOf(
        LogEntryEntity(
            id = "guppy-log-1", vehicleId = GUPPY_ID, date = "2026-04-10", mileage = 197000,
            category = LogCategory.Repair.name, task = "Oil change (2nd pass, cleanup)", cost = null,
            parts = "Royal Purple oil + filter",
            notes = "Drove ~1,000 mi on first fresh oil after sludgy factory-neglected oil, then changed again to help clean out residue.",
        ),
        LogEntryEntity(
            id = "guppy-log-2", vehicleId = GUPPY_ID, date = "2026-08-05", mileage = 203000,
            category = LogCategory.Repair.name, task = "Both catalytic converters replaced", cost = null, parts = "",
            notes = "This was the fix that finally cleared the last active check-engine code.",
        ),
        LogEntryEntity(
            id = "guppy-log-3", vehicleId = GUPPY_ID, date = "2026-08-05", mileage = 203000,
            category = LogCategory.Repair.name, task = "O2 sensors replaced", cost = null, parts = "", notes = "",
        ),
        LogEntryEntity(
            id = "guppy-log-4", vehicleId = GUPPY_ID, date = "2026-08-05", mileage = 203000,
            category = LogCategory.Repair.name, task = "Fuel vent solenoid replaced", cost = null, parts = "", notes = "",
        ),
        LogEntryEntity(
            id = "guppy-log-5", vehicleId = GUPPY_ID, date = "2026-08-05", mileage = 203000,
            category = LogCategory.Repair.name, task = "Driver seat cover replaced", cost = null, parts = "", notes = "",
        ),
        LogEntryEntity(
            id = "guppy-log-6", vehicleId = GUPPY_ID, date = "2026-08-05", mileage = 203000,
            category = LogCategory.Repair.name, task = "Brake light switch replaced", cost = null, parts = "", notes = "",
        ),
        LogEntryEntity(
            id = "guppy-log-7", vehicleId = GUPPY_ID, date = "2026-08-31", mileage = 203556,
            category = LogCategory.Routine.name, task = "Untitled entry", cost = null, parts = "", notes = "",
        ),
    )

    fun issues(): List<IssueEntity> = listOf(
        IssueEntity(
            id = "guppy-issue-1", vehicleId = GUPPY_ID,
            title = "Takata passenger airbag recall (NHTSA #21V054 / GM #N212328800)",
            status = IssueStatus.Open.label, priority = IssuePriority.SafetyCritical.label,
            dateOpened = "2026-08-05", dateResolved = "",
            description = "Free dealer fix — remedy available but not yet performed by prior owner. Inflator can rupture on deployment. Schedule with a GM dealer ahead of the cosmetic backlog.",
        ),
        IssueEntity(
            id = "guppy-issue-2", vehicleId = GUPPY_ID, title = "Headlight pods need replacing",
            status = IssueStatus.Resolved.label, priority = IssuePriority.Normal.label,
            dateOpened = "2026-08-05", dateResolved = "2026-08-16", description = "About to be replaced.",
        ),
        IssueEntity(
            id = "guppy-issue-3", vehicleId = GUPPY_ID, title = "Turn signal switch needs replacing",
            status = IssueStatus.Open.label, priority = IssuePriority.Normal.label,
            dateOpened = "2026-08-05", dateResolved = "", description = "About to be replaced.",
        ),
        IssueEntity(
            id = "guppy-issue-4", vehicleId = GUPPY_ID, title = "Hard shifting / clunking in and out of park",
            status = IssueStatus.Open.label, priority = IssuePriority.Normal.label,
            dateOpened = "2026-08-05", dateResolved = "", description = "Diagnosed as present, root cause not yet identified.",
        ),
        IssueEntity(
            id = "guppy-issue-5", vehicleId = GUPPY_ID, title = "Clunk/click over big bumps (front left)",
            status = IssueStatus.Open.label, priority = IssuePriority.Normal.label,
            dateOpened = "2026-08-05", dateResolved = "",
            description = "Also shows up at slow speed with wheel at full lock. Suspect CV axle, sway bar end link, or similar — not yet diagnosed.",
        ),
        IssueEntity(
            id = "guppy-issue-6", vehicleId = GUPPY_ID, title = "Shaking around 54 mph",
            status = IssueStatus.Open.label, priority = IssuePriority.Normal.label,
            dateOpened = "2026-08-05", dateResolved = "",
            description = "Smooth below and above that speed, feels like rumble strips. Possible driveline/tire-balance or u-joint issue — not yet diagnosed.",
        ),
        IssueEntity(
            id = "guppy-issue-7", vehicleId = GUPPY_ID, title = "Running fuel rich",
            status = IssueStatus.Open.label, priority = IssuePriority.Normal.label,
            dateOpened = "2026-08-05", dateResolved = "",
            description = "Mixture needs to be leaned out. Commonly reported on this engine/platform.",
        ),
        IssueEntity(
            id = "tuna-issue-1", vehicleId = BLUE_TUNA_ID, title = "Busted motor mount",
            status = IssueStatus.Open.label, priority = IssuePriority.Normal.label,
            dateOpened = "2026-08-04", dateResolved = "",
            description = "Confirmed reason the engine needs to come out this year. GM CK-1 manual section 6A3-27 to 6A3-29 covers inspection/replacement.",
        ),
        IssueEntity(
            id = "tuna-issue-2", vehicleId = BLUE_TUNA_ID, title = "A/C blows ambient air (new leak)",
            status = IssueStatus.Open.label, priority = IssuePriority.Normal.label,
            dateOpened = "2026-08-04", dateResolved = "",
            description = "A/C was fully redone previously but appears to have a new leak. Heater works fine. Leak location TBD — decide DIY (needs EPA 609 cert + recovery/vacuum/charge equipment) vs. shop.",
        ),
        IssueEntity(
            id = "tuna-issue-3", vehicleId = BLUE_TUNA_ID, title = "Blower motor: only works on high/med",
            status = IssueStatus.Open.label, priority = IssuePriority.Normal.label,
            dateOpened = "2026-08-04", dateResolved = "",
            description = "Doesn't work on med-high/low — classic blower motor resistor symptom. Folded into Year 1.",
        ),
    )

    fun buildPhases(): List<BuildPhaseEntity> = listOf(
        BuildPhaseEntity(
            id = "tuna-phase-1", vehicleId = BLUE_TUNA_ID,
            phase = "Year 1 — Engine (6.5L + Quadstar Super 54 turbo)",
            status = PhaseStatus.InProgress.label, order = 1,
            notes = "Reliability-first supporting mods: ARP fasteners, Fluidampr balancer, FASS lift pump, Flowkooler water pump, coolant filter kit, new injector lines, Harland Sharp rockers if heads come off. Super 54 currently out of stock at Quadstar (\$1,250) — need to contact for restock timing.",
        ),
        BuildPhaseEntity(
            id = "tuna-phase-2", vehicleId = BLUE_TUNA_ID, phase = "Year 1 — Exhaust/Cooling",
            status = PhaseStatus.NotStarted.label, order = 2,
            notes = "Custom Vibrant mandrel-bent exhaust per WMF reference build (stepped 4\"/oval-to-3\", not straight-pipe). Sizing/routing decision still needed before this phase starts — interacts with turbo spool/backpressure.",
        ),
        BuildPhaseEntity(
            id = "tuna-phase-3", vehicleId = BLUE_TUNA_ID, phase = "Year 1 — PMD / A/C / Electrical fixes",
            status = PhaseStatus.NotStarted.label, order = 3,
            notes = "PMD shield (location is thermally correct per GMT400 consensus) + dielectric grease. A/C leak diagnosis. Blower resistor.",
        ),
        BuildPhaseEntity(
            id = "tuna-phase-4", vehicleId = BLUE_TUNA_ID, phase = "Year 2 — Drivetrain / Off-road",
            status = PhaseStatus.NotStarted.label, order = 4,
            notes = "Re-gear for 35–37\" tires, locking diff decision, 3\" lift, tire/wheel selection, DIY front bumper + winch build, steering box/linkage upgrades.",
        ),
        BuildPhaseEntity(
            id = "tuna-phase-5", vehicleId = BLUE_TUNA_ID, phase = "Year 3 — Interior / Corrosion / Electrical",
            status = PhaseStatus.NotStarted.label, order = 5,
            notes = "Cargo liner, dash/door plastics, GMT900 seat swap research, amp + inverter install, connector weatherproofing (Deutsch DT/DTM leaning), CB/UHF/VHF radio, interior overhead rack.",
        ),
    )

    /** Steps under tuna-phase-1, imported from that phase's notes via the Build tab's "Import steps from notes" link. */
    fun buildSteps(): List<BuildStepEntity> = listOf(
        BuildStepEntity(
            id = "tuna-step-1", vehicleId = BLUE_TUNA_ID, phaseId = "tuna-phase-1",
            title = "ARP Fasteners", notes = "", priority = StepPriority.Medium.name,
            status = PhaseStatus.NotStarted.label, estimatedCost = null, actualCost = null,
            order = 1, manualPhaseOverride = true,
        ),
        BuildStepEntity(
            id = "tuna-step-2", vehicleId = BLUE_TUNA_ID, phaseId = "tuna-phase-1",
            title = "Fluidampr balancer", notes = "", priority = StepPriority.Medium.name,
            status = PhaseStatus.NotStarted.label, estimatedCost = null, actualCost = null,
            order = 2, manualPhaseOverride = true,
        ),
        BuildStepEntity(
            id = "tuna-step-3", vehicleId = BLUE_TUNA_ID, phaseId = "tuna-phase-1",
            title = "FASS lift pump", notes = "", priority = StepPriority.Medium.name,
            status = PhaseStatus.NotStarted.label, estimatedCost = null, actualCost = null,
            order = 3, manualPhaseOverride = true,
        ),
        BuildStepEntity(
            id = "tuna-step-4", vehicleId = BLUE_TUNA_ID, phaseId = "tuna-phase-1",
            title = "Flowkooler water pump", notes = "", priority = StepPriority.Medium.name,
            status = PhaseStatus.NotStarted.label, estimatedCost = null, actualCost = null,
            order = 4, manualPhaseOverride = true,
        ),
        BuildStepEntity(
            id = "tuna-step-5", vehicleId = BLUE_TUNA_ID, phaseId = "tuna-phase-1",
            title = "coolant filter kit", notes = "", priority = StepPriority.Medium.name,
            status = PhaseStatus.NotStarted.label, estimatedCost = null, actualCost = null,
            order = 5, manualPhaseOverride = true,
        ),
        BuildStepEntity(
            id = "tuna-step-6", vehicleId = BLUE_TUNA_ID, phaseId = "tuna-phase-1",
            title = "new injector lines", notes = "", priority = StepPriority.Medium.name,
            status = PhaseStatus.NotStarted.label, estimatedCost = null, actualCost = null,
            order = 6, manualPhaseOverride = true,
        ),
        BuildStepEntity(
            id = "tuna-step-7", vehicleId = BLUE_TUNA_ID, phaseId = "tuna-phase-1",
            title = "Harland Sharp rockers if heads come off. Super 54 currently out of stock at Quadstar (\$1,250) — need to contact for restock timing",
            notes = "", priority = StepPriority.Medium.name,
            status = PhaseStatus.NotStarted.label, estimatedCost = null, actualCost = null,
            order = 7, manualPhaseOverride = true,
        ),
    )

    fun maintenanceSchedules(): List<MaintenanceScheduleEntity> = listOf(
        MaintenanceScheduleEntity(
            id = "guppy-sched-1", vehicleId = GUPPY_ID, taskName = "Oil change",
            intervalMiles = 3000, intervalMonths = 3, lastDoneMileage = 198999, lastDoneDate = "2026-09-02",
        ),
        MaintenanceScheduleEntity(
            id = "guppy-sched-2", vehicleId = GUPPY_ID, taskName = "Tire rotation",
            intervalMiles = 6000, intervalMonths = 6, lastDoneMileage = null, lastDoneDate = null,
        ),
        MaintenanceScheduleEntity(
            id = "guppy-sched-3", vehicleId = GUPPY_ID, taskName = "Change Automatic Transmission Fluid and Filter",
            intervalMiles = 30000, intervalMonths = 30, lastDoneMileage = null, lastDoneDate = null,
        ),
        MaintenanceScheduleEntity(
            id = "guppy-sched-4", vehicleId = GUPPY_ID, taskName = "Change Brake Fluid",
            intervalMiles = 30000, intervalMonths = 30, lastDoneMileage = null, lastDoneDate = null,
        ),
        MaintenanceScheduleEntity(
            id = "guppy-sched-5", vehicleId = GUPPY_ID, taskName = "Change Differential Oil",
            intervalMiles = 60000, intervalMonths = 48, lastDoneMileage = null, lastDoneDate = null,
        ),
        MaintenanceScheduleEntity(
            id = "guppy-sched-6", vehicleId = GUPPY_ID, taskName = "Change Transfer Case Oil",
            intervalMiles = 30000, intervalMonths = 30, lastDoneMileage = null, lastDoneDate = null,
        ),
        MaintenanceScheduleEntity(
            id = "guppy-sched-7", vehicleId = GUPPY_ID, taskName = "Flush Cooling System",
            intervalMiles = 100000, intervalMonths = 60, lastDoneMileage = null, lastDoneDate = null,
        ),
        MaintenanceScheduleEntity(
            id = "guppy-sched-8", vehicleId = GUPPY_ID, taskName = "Lube the Chassis",
            intervalMiles = 15000, intervalMonths = 12, lastDoneMileage = null, lastDoneDate = null,
        ),
        MaintenanceScheduleEntity(
            id = "guppy-sched-9", vehicleId = GUPPY_ID, taskName = "Replace Air Filter",
            intervalMiles = 30000, intervalMonths = 30, lastDoneMileage = null, lastDoneDate = null,
        ),
        MaintenanceScheduleEntity(
            id = "guppy-sched-10", vehicleId = GUPPY_ID, taskName = "Replace Spark Plug Wires",
            intervalMiles = 60000, intervalMonths = 48, lastDoneMileage = null, lastDoneDate = null,
        ),
        MaintenanceScheduleEntity(
            id = "guppy-sched-11", vehicleId = GUPPY_ID, taskName = "Replace Spark Plugs",
            intervalMiles = 60000, intervalMonths = 48, lastDoneMileage = null, lastDoneDate = null,
        ),
        MaintenanceScheduleEntity(
            id = "tuna-sched-1", vehicleId = BLUE_TUNA_ID, taskName = "Oil & filter (6.5L Detroit Diesel)",
            intervalMiles = 3000, intervalMonths = 6, lastDoneMileage = 222376, lastDoneDate = "2026-08-04",
        ),
        MaintenanceScheduleEntity(
            id = "tacoma-sched-1", vehicleId = TACOMA_ID, taskName = "Oil change",
            intervalMiles = 5000, intervalMonths = 6, lastDoneMileage = null, lastDoneDate = null,
        ),
    )
}
