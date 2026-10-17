package com.garagelog.app.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri

/** Opens the share sheet for a vehicle report PDF (email, Drive, Files, print…). */
fun shareReport(context: Context, uri: Uri, vehicleName: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, "$vehicleName — maintenance report")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "Share report"))
}
