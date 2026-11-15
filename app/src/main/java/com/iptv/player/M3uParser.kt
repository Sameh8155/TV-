package com.iptv.player

import java.io.BufferedReader

data class Channel(
    val name: String,
    val url: String,
    val group: String,
    val country: String,
    val userAgent: String?,
    val referer: String?
)

object M3uParser {
    private val attrRegex = Regex("([\\w-]+)=\"([^\"]*)\"")

    fun parse(reader: BufferedReader): List<Channel> {
        val out = ArrayList<Channel>()
        var name = ""
        var group = "Undefined"
        var country = ""
        var ua: String? = null
        var ref: String? = null
        var pending = false

        reader.forEachLine { raw ->
            val line = raw.trim()
            if (line.isEmpty()) return@forEachLine
            if (line.startsWith("#EXTINF")) {
                pending = true
                ua = null
                ref = null
                country = ""
                var g = "Undefined"
                val q = line.lastIndexOf('"')
                val c = line.indexOf(',', if (q >= 0) q else 0)
                name = if (c >= 0) line.substring(c + 1).trim() else ""
                for (m in attrRegex.findAll(line)) {
                    val key = m.groupValues[1]
                    val value = m.groupValues[2]
                    if (key == "group-title") g = value
                    else if (key == "http-user-agent") ua = value
                    else if (key == "http-referrer" || key == "http-referer") ref = value
                    else if (key == "tvg-id") {
                        val base = value.substringBefore('@')
                        country = if (base.contains('.')) base.substringAfterLast('.').lowercase() else ""
                    }
                }
                group = if (g.isBlank()) "Undefined" else g
            } else if (line.startsWith("#EXTVLCOPT:")) {
                val kv = line.removePrefix("#EXTVLCOPT:")
                val i = kv.indexOf('=')
                if (i > 0) {
                    val k = kv.substring(0, i).lowercase()
                    val v = kv.substring(i + 1)
                    if (k == "http-user-agent") ua = v
                    else if (k == "http-referrer" || k == "http-referer") ref = v
                }
            } else if (line.startsWith("#")) {
                // other directive, ignore
            } else if (pending) {
                out.add(Channel(if (name.isBlank()) line else name, line, group, country, ua, ref))
                pending = false
            }
        }
        return out
    }
}
