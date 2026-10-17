package com.garagelog.app.data.report

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.garagelog.app.util.todayIso
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Builds one vehicle's report and writes it to cache/reports/, returning a content:// link the
 * share sheet can hand to any app. Only the latest report is kept; older ones are deleted, so the
 * cache never collects copies of the owner's history.
 */
class ReportExporter(context: Context, private val builder: VehicleReportBuilder) {
    private val appContext = context.applicationContext

    suspend fun export(vehicleId: String): Uri = withContext(Dispatchers.IO) {
        val today = todayIso()
        val report = builder.build(vehicleId, today)
        val dir = File(appContext.cacheDir, "reports").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "${fileSlug(report.title)}-report-$today.pdf")
        file.outputStream().use { ReportPdfWriter().write(report, it) }
        FileProvider.getUriForFile(appContext, "${appContext.packageName}.reports", file)
    }

    /** "2010-gmc-sierra" — a file name that survives email clients and every file system. */
    private fun fileSlug(title: String): String =
        title.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifBlank { "vehicle" }
}
