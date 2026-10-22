/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package ru.matreshka.browser

import android.content.Context
import org.json.JSONObject

/**
 * The curated list of hosts that may use certificates issued by the Russian Ministry of Digital Development (Минцифры),
 * together with the base64-encoded DER roots that issued them.
 */
data class TrustConfig(
    val version: Int,
    val updatedAt: String,
    val hosts: List<String>,
    val roots: List<String>,
) {
    /**
     * Returns true if [host] is covered by this configuration. Entries are either exact host names or suffixes
     * beginning with a dot (for example `.tbank.ru`).
     */
    fun matches(host: String): Boolean = hosts.any { entry ->
        if (entry.startsWith(".")) {
            host == entry.substring(1) || host.endsWith(entry)
        } else {
            host == entry
        }
    }

    /**
     * Serializes the configuration into the compact text format understood by the patched GeckoView: the first line is
     * a comma-separated host list, every following line is one base64-encoded DER CA certificate.
     */
    fun toGeckoViewFormat(): String = buildString {
        append(hosts.joinToString(","))
        append('\n')
        roots.forEach { root ->
            append(root)
            append('\n')
        }
    }

    companion object {
        /** Path of the configuration inside the APK's assets. */
        const val ASSET_PATH = "matreshka/trust.json"

        /** Loads the configuration from the application's assets. */
        fun load(context: Context): TrustConfig =
            context.assets.open(ASSET_PATH).bufferedReader().use { it.readText() }.let(::fromJson)

        /** Parses the JSON contract documented in `app/src/main/assets/matreshka/trust.json`. */
        fun fromJson(json: String): TrustConfig {
            val objectJson = JSONObject(json)
            val hostsJson = objectJson.getJSONArray("hosts")
            val rootsJson = objectJson.getJSONArray("roots")

            return TrustConfig(
                version = objectJson.getInt("version"),
                updatedAt = objectJson.getString("updatedAt"),
                hosts = List(hostsJson.length()) { index -> hostsJson.getString(index) },
                roots = List(rootsJson.length()) { index -> rootsJson.getString(index) },
            )
        }
    }
}
