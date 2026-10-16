package com.garagelog.app.util

import com.garagelog.app.data.entity.VehicleEntity
import java.net.URI
import java.net.URLEncoder

/**
 * A YouTube search for doing [task] on [vehicle]: year, make, model and engine size, then the job.
 * Free, instant, and works for everyone — no Bob, no key. The nickname is left out (YouTube
 * doesn't know "Guppy"), and so is everything else about the owner.
 */
fun howToSearchUrl(vehicle: VehicleEntity, task: String): String {
    val query = listOfNotNull(
        vehicle.year?.toString(),
        vehicle.make.takeIf { it.isNotBlank() },
        vehicle.model.takeIf { it.isNotBlank() },
        vehicle.displacementL?.let { "${formatLiters(it)}L" },
        task.trim(),
        "how to",
    ).joinToString(" ")
    return "https://www.youtube.com/results?search_query=" + URLEncoder.encode(query, "UTF-8") // the Charset overload is API 33+
}

/** Only links to an actual YouTube video are opened from Bob's answer; anything else is dropped. */
fun isYouTubeVideoUrl(url: String): Boolean {
    val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return false
    if (uri.scheme != "https") return false
    val segments = uri.path.orEmpty().split('/').filter { it.isNotEmpty() }
    return when (uri.host?.lowercase()) {
        "youtu.be" -> segments.size == 1
        "www.youtube.com", "youtube.com", "m.youtube.com" ->
            (uri.path == "/watch" && uri.query.orEmpty().split('&').any { it.startsWith("v=") && it.length > 2 }) ||
                (segments.size == 2 && segments[0] == "shorts")
        else -> false
    }
}
