package com.garagelog.app.data.seed

import com.garagelog.app.data.entity.IssueEntity
import com.garagelog.app.data.entity.IssuePriority
import com.garagelog.app.data.entity.IssueStatus
import com.garagelog.app.data.entity.LogCategory
import com.garagelog.app.data.entity.LogEntryEntity
import com.garagelog.app.data.entity.MaintenanceScheduleEntity
import com.garagelog.app.data.entity.VehicleEntity
import java.time.LocalDate
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Two made-up vehicles a fresh install starts with, so every screen has something to show before
 * the owner adds their own. Everything here has an id starting [PREFIX], and so does anything the
 * owner later adds to a sample vehicle (by its vehicleId): that's how sync, the shared garage and
 * backups leave sample data on the phone (see the DAOs' *ForSync queries), and how "Remove sample
 * data" finds it all.
 *
 * Dates count back from [today], so the overdue oil change is overdue whenever the app is installed.
 */
object SampleData {
    const val PREFIX = "sample-"
    const val TRUCK_ID = "sample-truck"
    const val CAR_ID = "sample-car"

    fun isSample(id: String) = id.startsWith(PREFIX)

    data class Records(
        val vehicles: List<VehicleEntity>,
        val logs: List<LogEntryEntity>,
        val issues: List<IssueEntity>,
        val schedules: List<MaintenanceScheduleEntity>,
    )

    private const val NOTE = "Sample vehicle with made-up records, here so you can look around. " +
        "Remove it from the banner on Home or in Settings → Sample data."

    fun build(today: LocalDate = LocalDate.now(), stamp: Long = System.currentTimeMillis()): Records {
        val truck = Plan(TRUCK_ID, milesNow = 68_420, milesPerDay = 33.0, mpg = 17.2, tankGallons = 23.0, pricePerGallon = 3.69)
        val car = Plan(CAR_ID, milesNow = 54_180, milesPerDay = 27.0, mpg = 28.5, tankGallons = 11.5, pricePerGallon = 3.49)

        val vehicles = listOf(
            VehicleEntity(
                id = TRUCK_ID, name = "Sample truck", year = 2018, make = "Ford", model = "F-150",
                engine = "5.0L V8", drivetrain = "4WD", vin = "", color = "Silver",
                miles = truck.milesAt(2), milesDate = today.minusDays(2).toString(),
                role = "Sample vehicle", notes = NOTE, sortOrder = 0,
                trim = "XLT", bodyStyle = "Pickup", cabStyle = "SuperCrew", bedLength = "5.5 ft",
                transmissionType = "Automatic", transmissionSpeeds = 10, cylinders = 8, displacementL = 5.0,
                fuelType = "Gasoline", aspiration = "Naturally aspirated", updatedAt = stamp,
            ),
            VehicleEntity(
                id = CAR_ID, name = "Sample car", year = 2017, make = "Honda", model = "CR-V",
                engine = "1.5L turbo I4", drivetrain = "AWD", vin = "", color = "Blue",
                miles = car.milesAt(4), milesDate = today.minusDays(4).toString(),
                role = "Sample vehicle", notes = NOTE, sortOrder = 1,
                trim = "EX", bodyStyle = "SUV",
                transmissionType = "CVT", cylinders = 4, displacementL = 1.5,
                fuelType = "Gasoline", aspiration = "Turbocharged", updatedAt = stamp,
            ),
        )

        val logs = mutableListOf<LogEntryEntity>()
        logs += fillUps(truck, today, Random(18), stamp, daysBack = 540, lastFillDaysAgo = 2)
        logs += fillUps(car, today, Random(17), stamp, daysBack = 540, lastFillDaysAgo = 4)

        fun service(
            id: String, plan: Plan, daysAgo: Long, category: LogCategory, task: String, cost: Double?,
            parts: String = "", notes: String = "", schedule: String? = null,
        ) = LogEntryEntity(
            id = id, vehicleId = plan.vehicleId, date = today.minusDays(daysAgo).toString(),
            mileage = plan.milesAt(daysAgo), category = category.name, task = task, cost = cost,
            parts = parts, notes = notes, fulfillsScheduleId = schedule, updatedAt = stamp,
        )

        // Truck: last oil change ~8,100 mi ago on a 7,500 mi interval (overdue), tires rotated
        // ~7,100 mi ago (due soon), plus a repair and an upgrade so every category shows up.
        logs += listOf(
            service("sample-truck-oil-1", truck, 480, LogCategory.Routine, "Oil change", 79.95, "5W-20 synthetic, 8.8 qt; Motorcraft FL-500S", schedule = "sample-truck-sched-oil"),
            service("sample-truck-oil-2", truck, 245, LogCategory.Routine, "Oil change", 84.50, "5W-20 synthetic, 8.8 qt; Motorcraft FL-500S", schedule = "sample-truck-sched-oil"),
            service("sample-truck-rotate", truck, 215, LogCategory.Routine, "Tire rotation", 25.00, schedule = "sample-truck-sched-rotate"),
            service("sample-truck-airfilter", truck, 215, LogCategory.Routine, "Engine air filter", 32.99, "Motorcraft FA-1883", "Did it myself while the tires were off.", schedule = "sample-truck-sched-air"),
            service("sample-truck-brakes", truck, 160, LogCategory.Repair, "Front brake pads and rotors", 412.36, "Ceramic pads, 2 rotors", "Grinding at low speed. Rotors were below minimum thickness."),
            service("sample-truck-tonneau", truck, 95, LogCategory.Upgrade, "Tri-fold tonneau cover", 389.00, notes = "Should help highway mpg a little."),
        )
        // Car: everything recent and on time, so its card reads "all good" next to the truck's.
        logs += listOf(
            service("sample-car-oil-1", car, 300, LogCategory.Routine, "Oil change", 64.99, "0W-20 synthetic, 3.7 qt", schedule = "sample-car-sched-oil"),
            service("sample-car-oil-2", car, 110, LogCategory.Routine, "Oil change", 69.99, "0W-20 synthetic, 3.7 qt", schedule = "sample-car-sched-oil"),
            service("sample-car-rotate", car, 110, LogCategory.Routine, "Tire rotation", 0.0, notes = "Free with the oil change.", schedule = "sample-car-sched-rotate"),
            service("sample-car-cabin", car, 60, LogCategory.Routine, "Cabin air filter", 18.49, schedule = "sample-car-sched-cabin"),
            service("sample-car-battery", car, 200, LogCategory.Repair, "Battery replaced", 189.95, "Group 51R, 500 CCA", "Slow cranking on cold mornings; tested weak."),
        )

        val issues = listOf(
            IssueEntity(
                id = "sample-truck-issue-squeak", vehicleId = TRUCK_ID, title = "Squeak from the front end over bumps",
                status = IssueStatus.Open.label, priority = IssuePriority.Normal.label,
                dateOpened = today.minusDays(20).toString(), dateResolved = "",
                description = "Only on rough roads, louder when it's cold. Passenger side, I think. Sway bar links?",
                updatedAt = stamp,
            ),
            IssueEntity(
                id = "sample-truck-issue-cel", vehicleId = TRUCK_ID, title = "Check engine light (P0457, EVAP leak)",
                status = IssueStatus.Resolved.label, priority = IssuePriority.Normal.label,
                dateOpened = today.minusDays(130).toString(), dateResolved = today.minusDays(122).toString(),
                description = "Gas cap wasn't clicking shut. New cap, light cleared after a few drives.",
                updatedAt = stamp,
            ),
            IssueEntity(
                id = "sample-car-issue-wiper", vehicleId = CAR_ID, title = "Rear wiper leaves streaks",
                status = IssueStatus.Open.label, priority = IssuePriority.Normal.label,
                dateOpened = today.minusDays(9).toString(), dateResolved = "",
                description = "Blade looks cracked. Pick one up next time I'm at the parts store.",
                updatedAt = stamp,
            ),
        )

        fun schedule(id: String, plan: Plan, task: String, miles: Int?, months: Int?, doneDaysAgo: Long?) =
            MaintenanceScheduleEntity(
                id = id, vehicleId = plan.vehicleId, taskName = task, intervalMiles = miles, intervalMonths = months,
                lastDoneMileage = doneDaysAgo?.let { plan.milesAt(it) },
                lastDoneDate = doneDaysAgo?.let { today.minusDays(it).toString() },
                updatedAt = stamp,
            )

        val schedules = listOf(
            schedule("sample-truck-sched-oil", truck, "Oil change", 7_500, 12, 245),
            schedule("sample-truck-sched-rotate", truck, "Tire rotation", 7_500, null, 215),
            schedule("sample-truck-sched-air", truck, "Replace engine air filter", 30_000, 36, 215),
            schedule("sample-truck-sched-cabin", truck, "Replace cabin air filter", 20_000, 24, null),
            schedule("sample-truck-sched-brakefluid", truck, "Change brake fluid", null, 36, null),
            schedule("sample-truck-sched-diff", truck, "Change rear differential oil", 150_000, null, null),
            schedule("sample-car-sched-oil", car, "Oil change", 7_500, 12, 110),
            schedule("sample-car-sched-rotate", car, "Tire rotation", 7_500, null, 110),
            schedule("sample-car-sched-cabin", car, "Replace cabin air filter", 15_000, 24, 60),
            schedule("sample-car-sched-air", car, "Replace engine air filter", 30_000, 36, 300),
            schedule("sample-car-sched-cvt", car, "Change CVT fluid", 25_000, null, 300),
        )

        return Records(vehicles, logs, issues, schedules)
    }

    /** How a sample vehicle is driven: steady miles per day back from its current odometer. */
    private class Plan(
        val vehicleId: String,
        val milesNow: Int,
        val milesPerDay: Double,
        val mpg: Double,
        val tankGallons: Double,
        val pricePerGallon: Double,
    ) {
        fun milesAt(daysAgo: Long): Int = (milesNow - milesPerDay * daysAgo).roundToInt()
    }

    /**
     * Full-tank fill-ups from [daysBack] days ago to [lastFillDaysAgo], each refilling what the
     * miles since the last one burned (about 70-90% of a tank), with a little noise in mpg and
     * price. A fixed [random] seed keeps the history the same on every install.
     */
    private fun fillUps(plan: Plan, today: LocalDate, random: Random, stamp: Long, daysBack: Long, lastFillDaysAgo: Long): List<LogEntryEntity> {
        val days = mutableListOf<Long>()
        var daysAgo = daysBack
        while (daysAgo > lastFillDaysAgo) {
            days += daysAgo
            val tankMiles = plan.tankGallons * (0.7 + random.nextDouble() * 0.2) * plan.mpg
            daysAgo -= (tankMiles / plan.milesPerDay).roundToInt().coerceAtLeast(3)
        }
        // A fill a day or two before the last one would log a silly half-gallon top-up.
        if (days.size > 1 && days.last() - lastFillDaysAgo < 4) days.removeAt(days.lastIndex)
        days += lastFillDaysAgo

        return days.mapIndexed { index, ago ->
            val miles = plan.milesAt(ago)
            val gallons = if (index == 0) {
                // The first fill only sets the starting point; nothing before it to measure against.
                plan.tankGallons * 0.8
            } else {
                // Winter months run a bit thirstier, like real fill-up history does.
                val season = if (today.minusDays(ago).monthValue in listOf(11, 12, 1, 2)) 0.9 else 1.0
                val mpg = plan.mpg * season * (0.94 + random.nextDouble() * 0.12)
                (miles - plan.milesAt(days[index - 1])) / mpg
            }.let { (it * 1000).roundToInt() / 1000.0 }
            val price = plan.pricePerGallon + (random.nextDouble() - 0.5) * 0.5
            LogEntryEntity(
                id = "${plan.vehicleId}-fuel-$index", vehicleId = plan.vehicleId,
                date = today.minusDays(ago).toString(), mileage = miles,
                category = LogCategory.Fuel.name, task = "Fuel", cost = (gallons * price * 100).roundToInt() / 100.0,
                parts = "", notes = "", gallons = gallons, fullTank = true, updatedAt = stamp,
            )
        }
    }
}
