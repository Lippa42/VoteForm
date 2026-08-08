package it.marcolipparini.sfide.app

import android.content.res.AssetManager
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import java.io.IOException

/**
 * Serve i client web (viewer/spectator) dagli asset dell'app Android. È la
 * controparte Android del serving da filesystem usato sul desktop: stessa API,
 * sorgente diversa.
 */
fun Route.assetStatic(assets: AssetManager) {
    get("/{path...}") {
        val segments = call.parameters.getAll("path").orEmpty()
        val rel = segments.joinToString("/").ifEmpty { "viewer/index.html" }
        try {
            val bytes = assets.open("web/$rel").use { it.readBytes() }
            call.respondBytes(bytes, contentTypeFor(rel))
        } catch (e: IOException) {
            call.respond(HttpStatusCode.NotFound)
        }
    }
}

private fun contentTypeFor(path: String): ContentType =
    when (path.substringAfterLast('.', "")) {
        "html" -> ContentType.Text.Html
        "js" -> ContentType.Application.JavaScript
        "css" -> ContentType.Text.CSS
        "json" -> ContentType.Application.Json
        "svg" -> ContentType.Image.SVG
        else -> ContentType.Application.OctetStream
    }
