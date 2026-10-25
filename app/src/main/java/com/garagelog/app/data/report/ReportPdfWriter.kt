package com.garagelog.app.data.report

import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import com.garagelog.app.data.entity.LogCategory
import com.garagelog.app.data.entity.LogEntryEntity
import com.garagelog.app.util.DueStatus
import com.garagelog.app.util.compactMoney
import com.garagelog.app.util.formatDate
import com.garagelog.app.util.formatMiles
import com.garagelog.app.util.formatMoney
import com.garagelog.app.util.formatTick
import com.garagelog.app.util.monthLabel
import com.garagelog.app.util.niceTicks
import java.io.OutputStream
import kotlin.math.roundToInt

/**
 * Lays a [VehicleReport] out as a US Letter PDF with Android's PdfDocument: real text and vector
 * charts (sharp at any zoom, small files), on a white page in print-friendly colors.
 *
 * Layout is a simple flow: everything is placed top to bottom, and anything that won't fit starts
 * a new page. Long text (a diagnosis) continues across pages line by line, and the service-history
 * table repeats its column headings on every page it runs onto.
 */
class ReportPdfWriter {

    fun write(report: VehicleReport, out: OutputStream) {
        val doc = PdfDocument()
        try {
            Flow(doc, report).render()
            doc.writeTo(out)
        } finally {
            doc.close()
        }
    }

    private class Flow(private val doc: PdfDocument, private val report: VehicleReport) {
        private var pageNumber = 0
        private lateinit var page: PdfDocument.Page
        private lateinit var canvas: Canvas
        private var y = 0f

        private val left = MARGIN
        private val width = PAGE_W - 2 * MARGIN
        private val bottom = PAGE_H - MARGIN - FOOTER_H

        fun render() {
            newPage()
            cover()
            maintenance()
            trends()
            issues()
            history()
            finishPage()
        }

        // ---- Sections ----------------------------------------------------------------------

        private fun cover() {
            text(report.title, titlePaint)
            if (report.subtitle.isNotBlank()) text(report.subtitle, mutedPaint(11f), after = 2f)
            text("Maintenance report · ${report.generatedOn}", mutedPaint(9f), after = 14f)
            summaryTiles()
            if (report.specs.isNotEmpty()) {
                heading("Vehicle")
                keyValueColumns(report.specs)
            }
        }

        private fun maintenance() {
            if (report.maintenance.isEmpty()) return
            heading("Maintenance schedule")
            val cols = floatArrayOf(0.34f, 0.22f, 0.22f, 0.22f)
            tableHeader(listOf("Service", "Interval", "Last done", "Status"), cols)
            report.maintenance.forEach { line ->
                tableRow(
                    listOf(line.task, line.interval, line.lastDone, line.statusLabel),
                    cols,
                    colors = mapOf(3 to statusColor(line.status)),
                ) { tableHeader(listOf("Service", "Interval", "Last done", "Status"), cols) }
            }
            y += 10f
        }

        private fun trends() {
            val hasAny = report.odometer.size >= 2 || report.mpg.size >= 2 ||
                report.serviceByMonth.isNotEmpty() || report.fuelByMonth.isNotEmpty()
            if (!hasAny) return
            heading("Trends")
            if (report.odometer.size >= 2) {
                chartTitle("Odometer")
                lineChart(report.odometer.map { it.second }, formatDate(report.odometer.first().first), formatDate(report.odometer.last().first), "mi")
            }
            if (report.mpg.size >= 2) {
                val avg = report.mpg.map { it.mpg }.average()
                chartTitle("Fuel economy — ${"%.1f".format(avg)} mpg average over ${report.mpg.size} full tanks")
                val shown = report.mpg.takeLast(24)
                lineChart(shown.map { it.mpg }, formatDate(shown.first().date), formatDate(shown.last().date), "mpg")
            }
            if (report.serviceByMonth.isNotEmpty()) {
                chartTitle("Service & repairs by month — ${formatMoney(report.serviceByMonth.sumOf { it.second })} over the months shown")
                barChart(report.serviceByMonth, ACCENT)
            }
            if (report.fuelByMonth.isNotEmpty()) {
                chartTitle("Fuel by month — ${formatMoney(report.fuelByMonth.sumOf { it.second })} over the months shown")
                barChart(report.fuelByMonth, FUEL)
            }
            if (report.byCategory.size > 1) {
                chartTitle("Where the money went")
                categoryBars(report.byCategory)
            }
        }

        private fun issues() {
            if (report.openIssues.isEmpty() && report.resolvedIssues.isEmpty()) return
            if (report.openIssues.isNotEmpty()) {
                heading("Open issues")
                report.openIssues.forEach { issue(it) }
            }
            if (report.resolvedIssues.isNotEmpty()) {
                heading("Resolved issues")
                report.resolvedIssues.forEach { issue(it) }
            }
        }

        private fun issue(issue: ReportIssue) {
            ensure(40f)
            text(issue.title, boldPaint(11f), after = 1f)
            val meta = buildList {
                if (issue.safetyCritical) add("Safety-critical")
                add(issue.status)
                add("opened ${issue.opened}")
                if (issue.resolved.isNotBlank()) add("resolved ${issue.resolved}")
            }.joinToString(" · ")
            text(meta, if (issue.safetyCritical) colorPaint(9f, ALARM) else mutedPaint(9f), after = 3f)
            if (issue.description.isNotBlank()) text(issue.description, bodyPaint, after = 4f)
            issue.diagnosis?.let { markdown ->
                text("Bob's diagnosis", boldPaint(9.5f).apply { color = ACCENT }, after = 2f)
                text(markdownToStyled(markdown), bodyPaint, indent = 8f, after = 4f)
            }
            rule()
        }

        private fun history() {
            if (report.logs.isEmpty()) return
            heading("Service history (${report.logs.size} entries)")
            val cols = floatArrayOf(0.15f, 0.14f, 0.13f, 0.44f, 0.14f)
            val headers = listOf("Date", "Miles", "Type", "Work", "Cost")
            tableHeader(headers, cols, rightAligned = setOf(1, 4))
            report.logs.forEach { entry ->
                tableRow(
                    listOf(
                        formatDate(entry.date),
                        entry.mileage?.let { formatMiles(it).removeSuffix(" mi") } ?: "—",
                        entry.category,
                        workText(entry),
                        entry.cost?.let { formatMoney(it) } ?: "",
                    ),
                    cols,
                    rightAligned = setOf(1, 4),
                ) { tableHeader(headers, cols, rightAligned = setOf(1, 4)) }
            }
        }

        private fun workText(entry: LogEntryEntity): CharSequence {
            val sb = SpannableStringBuilder(entry.task)
            if (entry.inProgress) sb.append(" (in progress)")
            if (entry.category == LogCategory.Fuel.name) {
                val extras = listOfNotNull(
                    entry.gallons?.let { "%.2f gal".format(it) },
                    if (!entry.fullTank) "partial" else null,
                    if (entry.missedFillUp) "after a skipped fill-up" else null,
                )
                if (extras.isNotEmpty()) sb.append(" · ").append(extras.joinToString(" · "))
            }
            if (entry.parts.isNotBlank()) sb.append("\nParts: ").append(entry.parts)
            if (entry.notes.isNotBlank()) sb.append("\n").append(entry.notes)
            return sb
        }

        // ---- Blocks ------------------------------------------------------------------------

        private fun summaryTiles() {
            val perRow = 3
            val gap = 8f
            val tileW = (width - gap * (perRow - 1)) / perRow
            val tileH = 44f
            report.summary.chunked(perRow).forEach { row ->
                ensure(tileH + gap)
                row.forEachIndexed { i, (value, label) ->
                    val x = left + i * (tileW + gap)
                    canvas.drawRoundRect(RectF(x, y, x + tileW, y + tileH), 6f, 6f, tilePaint)
                    canvas.drawText(value, x + 10f, y + 20f, boldPaint(13f))
                    canvas.drawText(TextUtils.ellipsize(label, mutedPaint(8.5f), tileW - 20f, TextUtils.TruncateAt.END).toString(), x + 10f, y + 35f, mutedPaint(8.5f))
                }
                y += tileH + gap
            }
            y += 6f
        }

        private fun keyValueColumns(pairs: List<Pair<String, String>>) {
            val colW = (width - 16f) / 2
            val labelW = 78f
            val rows = (pairs.size + 1) / 2
            val ordered = (0 until rows).map { r -> listOfNotNull(pairs.getOrNull(r), pairs.getOrNull(r + rows)) }
            ordered.forEach { row ->
                val heights = row.map { (_, value) -> layout(value, bodyPaint, colW - labelW).height.toFloat() }
                val h = (heights.maxOrNull() ?: 12f) + 3f
                ensure(h)
                row.forEachIndexed { i, (label, value) ->
                    val x = left + i * (colW + 16f)
                    drawLayout(layout(label, mutedPaint(9.5f), labelW), x, y)
                    drawLayout(layout(value, bodyPaint, colW - labelW), x + labelW, y)
                }
                y += h
            }
            y += 10f
        }

        private fun tableHeader(headers: List<String>, cols: FloatArray, rightAligned: Set<Int> = emptySet()) {
            ensure(30f)
            val paint = mutedPaint(7.5f).apply { isFakeBoldText = true; letterSpacing = 0.06f }
            val pad = 6f // same as the cells, so a right-aligned heading lines up with its numbers
            var x = left
            headers.forEachIndexed { i, h ->
                val label = h.uppercase()
                val colW = cols[i] * width
                val tx = if (i in rightAligned) x + colW - pad - paint.measureText(label) else x
                canvas.drawText(label, tx, y + 9f, paint)
                x += colW
            }
            y += 13f
            canvas.drawLine(left, y, left + width, y, rulePaint)
            y += 4f
        }

        private fun tableRow(
            cells: List<CharSequence>,
            cols: FloatArray,
            colors: Map<Int, Int> = emptyMap(),
            rightAligned: Set<Int> = emptySet(),
            onNewPage: () -> Unit,
        ) {
            val pad = 6f
            val layouts = cells.mapIndexed { i, cell ->
                val paint = colors[i]?.let { colorPaint(9f, it) } ?: smallBodyPaint
                layout(cell, paint, cols[i] * width - pad, if (i in rightAligned) Layout.Alignment.ALIGN_OPPOSITE else Layout.Alignment.ALIGN_NORMAL)
            }
            val h = layouts.maxOf { it.height }.toFloat() + 6f
            if (y + h > bottom) {
                newPage()
                onNewPage()
            }
            var x = left
            layouts.forEachIndexed { i, l ->
                drawLayout(l, x, y)
                x += cols[i] * width
            }
            y += h
            canvas.drawLine(left, y - 2f, left + width, y - 2f, hairlinePaint)
        }

        private fun lineChart(values: List<Float>, leading: String, trailing: String, unit: String) {
            val h = 120f
            ensure(h + 18f)
            val ticks = niceTicks(values.min(), values.max())
            val axisMin = ticks.first()
            val span = (ticks.last() - ticks.first()).takeIf { it > 0f } ?: 1f
            val step = if (ticks.size > 1) ticks[1] - ticks[0] else 1f
            val labelPaint = mutedPaint(7.5f)
            val labels = ticks.mapIndexed { i, t -> formatTick(t, step) + if (i == ticks.lastIndex) " $unit" else "" }
            val gutter = labels.maxOf { labelPaint.measureText(it) } + 6f
            val top = y + 5f
            val plotH = h - 10f
            fun yFor(v: Float) = top + plotH - (v - axisMin) / span * plotH
            ticks.forEachIndexed { i, t ->
                val ty = yFor(t)
                canvas.drawLine(left + gutter, ty, left + width, ty, gridPaint)
                canvas.drawText(labels[i], left + gutter - 6f - labelPaint.measureText(labels[i]), ty + 3f, labelPaint)
            }
            val radius = 2.2f
            val x0 = left + gutter + radius
            val plotW = width - gutter - 2 * radius
            val dx = if (values.size > 1) plotW / (values.size - 1) else 0f
            val path = Path()
            values.forEachIndexed { i, v -> if (i == 0) path.moveTo(x0, yFor(v)) else path.lineTo(x0 + i * dx, yFor(v)) }
            canvas.drawPath(path, linePaint)
            values.forEachIndexed { i, v -> canvas.drawCircle(x0 + i * dx, yFor(v), radius, dotPaint) }
            y += h
            canvas.drawText(leading, left + gutter, y + 9f, labelPaint)
            canvas.drawText(trailing, left + width - labelPaint.measureText(trailing), y + 9f, labelPaint)
            y += 18f
        }

        private fun barChart(totals: List<Pair<String, Double>>, color: Int) {
            val h = 110f
            ensure(h + 16f)
            val max = totals.maxOf { it.second }.coerceAtLeast(0.01)
            val slot = width / totals.size
            val barW = minOf(slot * 0.6f, 34f)
            val labelPaint = mutedPaint(7.5f)
            val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
            val plotTop = y + 12f
            val plotH = h - 12f
            totals.forEachIndexed { i, (month, total) ->
                val cx = left + slot * i + slot / 2
                val barH = (total / max * plotH).toFloat().coerceAtLeast(1.5f)
                canvas.drawRoundRect(RectF(cx - barW / 2, plotTop + plotH - barH, cx + barW / 2, plotTop + plotH), 2f, 2f, fill)
                val value = compactMoney(total.toFloat())
                canvas.drawText(value, cx - labelPaint.measureText(value) / 2, plotTop + plotH - barH - 3f, labelPaint)
                val label = monthLabel(month)
                canvas.drawText(label, cx - labelPaint.measureText(label) / 2, plotTop + plotH + 10f, labelPaint)
            }
            y += h + 16f
        }

        private fun categoryBars(totals: List<Pair<String, Double>>) {
            val grand = totals.sumOf { it.second }
            val labelW = 80f
            val pctW = 36f
            val barMax = width - labelW - pctW
            val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ACCENT }
            val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = TILE }
            totals.forEach { (category, total) ->
                ensure(16f)
                val share = (total / grand).toFloat()
                canvas.drawText(category, left, y + 9f, smallBodyPaint)
                canvas.drawRoundRect(RectF(left + labelW, y + 2f, left + labelW + barMax, y + 10f), 3f, 3f, track)
                canvas.drawRoundRect(RectF(left + labelW, y + 2f, left + labelW + barMax * share.coerceIn(0.02f, 1f), y + 10f), 3f, 3f, fill)
                val pct = "${(share * 100).roundToInt()}% · ${compactMoney(total.toFloat())}"
                canvas.drawText(pct, left + width - mutedPaint(8f).measureText(pct), y + 9f, mutedPaint(8f))
                y += 16f
            }
            y += 10f
        }

        // ---- Text --------------------------------------------------------------------------

        private fun heading(title: String) {
            ensure(60f) // a heading never sits alone at the bottom of a page
            y += 8f
            text(title, boldPaint(14f).apply { color = INK }, after = 2f)
            canvas.drawLine(left, y, left + width, y, accentRulePaint)
            y += 8f
        }

        private fun chartTitle(title: String) {
            ensure(150f) // keep the title with its chart
            text(title, boldPaint(9.5f), after = 4f)
        }

        private fun rule() {
            y += 4f
            canvas.drawLine(left, y, left + width, y, hairlinePaint)
            y += 8f
        }

        /** Draws [content] at the current position, continuing onto new pages a line at a time. */
        private fun text(content: CharSequence, paint: TextPaint, indent: Float = 0f, after: Float = 6f) {
            val l = layout(content, paint, width - indent)
            var line = 0
            while (line < l.lineCount) {
                if (y + (l.getLineBottom(line) - l.getLineTop(line)) > bottom) newPage()
                // Every line from [line] that fits on this page.
                var last = line
                while (last + 1 < l.lineCount && y + (l.getLineBottom(last + 1) - l.getLineTop(line)) <= bottom) last++
                val top = l.getLineTop(line).toFloat()
                val h = l.getLineBottom(last) - top
                canvas.save()
                canvas.clipRect(left + indent, y, left + width, y + h)
                canvas.translate(left + indent, y - top)
                l.draw(canvas)
                canvas.restore()
                y += h
                line = last + 1
            }
            y += after
        }

        private fun layout(
            content: CharSequence,
            paint: TextPaint,
            w: Float,
            align: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL,
        ): StaticLayout = StaticLayout.Builder.obtain(content, 0, content.length, paint, w.toInt().coerceAtLeast(1))
            .setAlignment(align)
            .setLineSpacing(1.5f, 1f)
            .build()

        private fun drawLayout(l: StaticLayout, x: Float, top: Float) {
            canvas.save()
            canvas.translate(x, top)
            l.draw(canvas)
            canvas.restore()
        }

        // ---- Pages -------------------------------------------------------------------------

        private fun ensure(h: Float) {
            if (y + h > bottom) newPage()
        }

        private fun newPage() {
            if (pageNumber > 0) finishPage()
            pageNumber++
            page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W.toInt(), PAGE_H.toInt(), pageNumber).create())
            canvas = page.canvas
            y = MARGIN
            if (pageNumber > 1) {
                canvas.drawText("${report.title} — maintenance report", left, y, mutedPaint(8f))
                y += 18f
            }
        }

        private fun finishPage() {
            val footer = "Garage Log · ${report.generatedOn} · Page $pageNumber"
            val paint = mutedPaint(8f)
            canvas.drawText(footer, left + width - paint.measureText(footer), PAGE_H - MARGIN + 6f, paint)
            doc.finishPage(page)
        }

        private fun statusColor(status: DueStatus): Int = when (status) {
            DueStatus.OVERDUE -> ALARM
            DueStatus.DUE_SOON -> FUEL
            DueStatus.OK -> OK
            DueStatus.UNKNOWN -> MUTED
        }
    }

    private companion object {
        // US Letter in PDF points (1/72 in).
        const val PAGE_W = 612f
        const val PAGE_H = 792f
        const val MARGIN = 48f
        const val FOOTER_H = 16f

        // Print colors: the app's light palette, on white paper.
        const val INK = 0xFF15181B.toInt()
        const val MUTED = 0xFF575E65.toInt()
        const val ACCENT = 0xFF1B4F9C.toInt()
        const val FUEL = 0xFF8A5A08.toInt()
        const val ALARM = 0xFFB42318.toInt()
        const val OK = 0xFF2E6B37.toInt()
        const val RULE = 0xFFD3D7DA.toInt()
        const val TILE = 0xFFEDF0F5.toInt()

        fun basePaint(size: Float, color: Int = INK) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            typeface = Typeface.DEFAULT
        }

        fun boldPaint(size: Float) = basePaint(size).apply { typeface = Typeface.DEFAULT_BOLD }
        fun mutedPaint(size: Float) = basePaint(size, MUTED)
        fun colorPaint(size: Float, color: Int) = basePaint(size, color)

        val titlePaint = basePaint(22f).apply { typeface = Typeface.DEFAULT_BOLD }
        val bodyPaint = basePaint(10f)
        val smallBodyPaint = basePaint(9f)
        val tilePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = TILE }
        val rulePaint = Paint().apply { color = RULE; strokeWidth = 1f }
        val hairlinePaint = Paint().apply { color = RULE; strokeWidth = 0.5f }
        val accentRulePaint = Paint().apply { color = ACCENT; strokeWidth = 1.5f }
        val gridPaint = Paint().apply { color = RULE; strokeWidth = 0.6f; pathEffect = DashPathEffect(floatArrayOf(1.5f, 3f), 0f) }
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ACCENT
            style = Paint.Style.STROKE
            strokeWidth = 1.6f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ACCENT }
    }
}

/**
 * Bob writes GitHub-flavored Markdown; a PDF wants styled text. Headings and **bold** become bold,
 * list markers become bullets, and links keep their text (the URLs would just be noise on paper).
 */
internal fun markdownToStyled(markdown: String): CharSequence {
    val out = SpannableStringBuilder()
    var afterHeading = false
    var first = true
    markdown.trim().lines().forEach { raw ->
        val line = raw.trimEnd()
        // The heading's own spacing is enough; a blank line under it reads as a gap.
        if (afterHeading && line.isBlank()) return@forEach
        if (!first) out.append('\n')
        first = false
        afterHeading = line.startsWith("#")
        val bullet = line.trimStart().let { it.startsWith("- ") || it.startsWith("* ") }
        when {
            afterHeading -> {
                val start = out.length
                appendInline(out, line.trimStart('#').trim())
                out.setSpan(StyleSpan(Typeface.BOLD), start, out.length, 0)
                out.setSpan(RelativeSizeSpan(1.08f), start, out.length, 0)
            }
            bullet -> {
                out.append("   • ")
                appendInline(out, line.trimStart().drop(2))
            }
            else -> appendInline(out, line)
        }
    }
    return out
}

/** **bold**, then *italic*; a lone asterisk (like "5 * 2") has no partner on the line and stays. */
private val inlineEmphasis = Regex("""\*\*(.+?)\*\*|\*(\S(?:.*?\S)?)\*""")

/** Appends [text], turning **bold** and *italic* into spans and [label](url) into just the label. */
private fun appendInline(out: SpannableStringBuilder, text: String) {
    val plain = text.replace(Regex("""\[([^\]]+)]\([^)]*\)"""), "$1")
    var last = 0
    inlineEmphasis.findAll(plain).forEach { match ->
        out.append(plain, last, match.range.first)
        val bold = match.groups[1]
        val start = out.length
        out.append(bold?.value ?: match.groupValues[2])
        out.setSpan(StyleSpan(if (bold != null) Typeface.BOLD else Typeface.ITALIC), start, out.length, 0)
        last = match.range.last + 1
    }
    out.append(plain, last, plain.length)
}
