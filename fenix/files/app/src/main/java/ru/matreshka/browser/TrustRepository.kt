/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package ru.matreshka.browser

import android.content.Context
import android.util.Log
import java.io.File
import java.net.IDN
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import org.json.JSONArray
import org.json.JSONObject

/** A host in the effective trust list together with where it came from. */
data class TrustEntry(
    val host: String,
    val source: Source,
) {
    enum class Source {
        BASE,
        REMOTE,
        USER,
    }
}

/** The merged trust configuration used at runtime and shown in the UI. */
data class EffectiveTrust(
    val config: TrustConfig,
    val entries: List<TrustEntry>,
    val staleReason: String?,
)

/**
 * Builds the effective trust configuration from three layers:
 *
 * 1. the bundle shipped in the APK (hosts and roots),
 * 2. hosts from an optionally imported remote JSON file (hosts only; roots always come from the APK so that a remote
 *    document can never introduce a new CA),
 * 3. hosts added by the user on this device.
 *
 * The effective host list and the bundled roots are handed to GeckoView through [GeckoProvider.refreshTrust], so
 * changes apply without rebuilding or restarting the app.
 */
object TrustRepository {
    private const val TAG = "MatreshkaTrustRepo"
    private const val DIRECTORY = "matreshka"
    private const val USER_FILE = "user_trust.json"
    private const val REMOTE_FILE = "remote_trust.json"

    /** Warn when the list has not been updated for this many days. */
    private const val STALE_DAYS = 60L

    /** Warn when a bundled CA expires within this many days. */
    private const val CA_EXPIRY_WARNING_DAYS = 90L

    private const val MAX_REMOTE_HOSTS = 5_000
    private const val MAX_REMOTE_BYTES = 2 * 1024 * 1024

    @Volatile private var cached: EffectiveTrust? = null

    @Volatile private var cachedStamp: Long = -1

    @Volatile private var cachedDay: Long = -1

    fun base(context: Context): TrustConfig = TrustConfig.load(context)

    fun effective(context: Context): EffectiveTrust {
        val stamp = listOf(userFile(context), remoteFile(context)).maxOfOrNull { it.lastModified() } ?: 0L
        val day = LocalDate.now().toEpochDay()
        cached?.let { if (stamp == cachedStamp && day == cachedDay) return it }
        return buildEffective(context, stamp).also {
            cached = it
            cachedStamp = stamp
            cachedDay = day
        }
    }

    /** Adds [input] to the user layer. Returns the normalized host, or null if it is invalid. */
    fun addUserHost(context: Context, input: String): String? {
        val host = normalizeHost(input) ?: return null
        val user = readUserHosts(context)
        val additions = if (host.startsWith(".")) listOf(host) else listOf(host, ".$host")
        val merged = (user + additions).distinct()
        writeUserHosts(context, merged)
        invalidate()
        return host
    }

    /** Removes one user-added entry. Base and remote entries cannot be removed. */
    fun removeUserHost(context: Context, host: String) {
        val user = readUserHosts(context).toMutableList()
        if (user.remove(host)) {
            writeUserHosts(context, user)
            invalidate()
        }
    }

    fun clearUserHosts(context: Context) {
        userFile(context).delete()
        invalidate()
    }

    /** Imports a remote trust document. Only its hosts are used; its roots are ignored. */
    fun importRemote(context: Context, json: String): Int {
        val remote = RemoteTrust.parse(json)
        remoteFile(context).writeText(json)
        invalidate()
        return remote.hosts.size
    }

    fun clearRemote(context: Context) {
        remoteFile(context).delete()
        invalidate()
    }

    fun hasRemote(context: Context): Boolean = remoteFile(context).isFile

    fun invalidate() {
        cached = null
        cachedStamp = -1
        cachedDay = -1
    }

    private fun buildEffective(context: Context, stamp: Long): EffectiveTrust {
        val base =
            try {
                base(context)
            } catch (throwable: Throwable) {
                Log.w(TAG, "Unable to read ${TrustConfig.ASSET_PATH}", throwable)
                TrustConfig(version = 0, updatedAt = "", hosts = emptyList(), roots = emptyList())
            }

        val remote =
            try {
                if (remoteFile(context).isFile) RemoteTrust.parse(remoteFile(context).readText()) else null
            } catch (throwable: Throwable) {
                Log.w(TAG, "Unable to read remote trust file", throwable)
                null
            }

        val user =
            try {
                readUserHosts(context)
            } catch (throwable: Throwable) {
                Log.w(TAG, "Unable to read user trust file", throwable)
                emptyList()
            }

        val entries = buildList {
            base.hosts.forEach { add(TrustEntry(it, TrustEntry.Source.BASE)) }
            remote?.hosts?.forEach { add(TrustEntry(it, TrustEntry.Source.REMOTE)) }
            user.forEach { add(TrustEntry(it, TrustEntry.Source.USER)) }
        }
            .distinctBy { it.host }

        val updatedAt =
            listOfNotNull(base.updatedAt.takeIf { it.isNotBlank() }, remote?.updatedAt)
                .maxByOrNull { parseDate(it) ?: LocalDate.MIN }
                .orEmpty()

        val config =
            base.copy(
                version = remote?.version ?: base.version,
                updatedAt = updatedAt,
                hosts = entries.map { it.host },
                roots = base.roots,
            )

        cachedStamp = stamp
        return EffectiveTrust(
            config = config,
            entries = entries,
            staleReason = staleReason(updatedAt, base.roots),
        )
    }

    private fun staleReason(updatedAt: String, roots: List<String>): String? {
        val updated = parseDate(updatedAt)
        if (updated != null) {
            val days = ChronoUnit.DAYS.between(updated, LocalDate.now())
            if (days > STALE_DAYS) {
                return "Список не обновлялся $days дн. (последнее обновление: $updatedAt)"
            }
        }

        val expiry = earliestExpiry(roots)
        if (expiry != null) {
            val left = ChronoUnit.DAYS.between(LocalDate.now(), expiry)
            if (left < CA_EXPIRY_WARNING_DAYS) {
                return "Сертификат Минцифры истекает $expiry (осталось $left дн.)"
            }
        }
        return null
    }

    private fun earliestExpiry(roots: List<String>): LocalDate? =
        roots
            .mapNotNull { encoded ->
                try {
                    val bytes = android.util.Base64.decode(encoded, android.util.Base64.DEFAULT)
                    val certificate =
                        CertificateFactory.getInstance("X.509").generateCertificate(bytes.inputStream())
                            as X509Certificate
                    certificate.notAfter.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
                } catch (throwable: Throwable) {
                    Log.w(TAG, "Unable to parse bundled CA", throwable)
                    null
                }
            }
            .minOrNull()

    private fun parseDate(value: String): LocalDate? =
        try {
            LocalDate.parse(value)
        } catch (throwable: Throwable) {
            null
        }

    private fun userFile(context: Context) = file(context, USER_FILE)

    private fun remoteFile(context: Context) = file(context, REMOTE_FILE)

    private fun file(context: Context, name: String): File {
        val directory = File(context.filesDir, DIRECTORY)
        if (!directory.exists()) {
            directory.mkdirs()
        }
        return File(directory, name)
    }

    private fun readUserHosts(context: Context): List<String> {
        val file = userFile(context)
        if (!file.isFile) {
            return emptyList()
        }
        val hosts = JSONObject(file.readText()).optJSONArray("hosts") ?: return emptyList()
        return List(hosts.length()) { index -> hosts.getString(index) }.mapNotNull(::normalizeHost).distinct()
    }

    private fun writeUserHosts(context: Context, hosts: List<String>) {
        val file = userFile(context)
        val json = JSONObject().put("hosts", JSONArray(hosts)).toString(2)
        val temporary = File(file.parentFile, "${file.name}.tmp")
        temporary.writeText(json)
        if (!temporary.renameTo(file)) {
            file.writeText(json)
            temporary.delete()
        }
    }

    /** Normalizes user input into a lowercase ASCII host, optionally prefixed with a dot. */
    fun normalizeHost(raw: String): String? {
        var value = raw.trim().lowercase()
        if (value.isEmpty()) {
            return null
        }
        if (value.contains("://")) {
            value = value.substringAfter("://")
        }
        value = value.substringBefore('/').substringBefore('?').substringBefore('#')
        if (value.contains('@')) {
            return null
        }
        value = value.substringBefore(':')
        value = value.removePrefix("*.")
        val suffix = value.startsWith(".")
        value = value.removePrefix(".")
        if (value.isEmpty() || value.length > 253) {
            return null
        }
        val ascii =
            try {
                IDN.toASCII(value, IDN.ALLOW_UNASSIGNED).lowercase()
            } catch (throwable: Throwable) {
                return null
            }
        if (!ascii.matches(Regex("^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$"))) {
            return null
        }
        return if (suffix) ".$ascii" else ascii
    }

    /** Hosts file format accepted by [importRemote]. Roots are intentionally ignored. */
    private data class RemoteTrust(
        val version: Int?,
        val updatedAt: String?,
        val hosts: List<String>,
    ) {
        companion object {
            fun parse(json: String): RemoteTrust {
                require(json.length <= MAX_REMOTE_BYTES) { "Файл слишком большой" }
                val root = JSONObject(json)
                val hostsJson = root.optJSONArray("hosts") ?: throw IllegalArgumentException("Нет поля hosts")
                require(hostsJson.length() <= MAX_REMOTE_HOSTS) { "Слишком много доменов" }
                val hosts =
                    List(hostsJson.length()) { index -> hostsJson.getString(index) }
                        .mapNotNull(::normalizeHost)
                        .distinct()
                require(hosts.isNotEmpty()) { "Список доменов пуст" }
                return RemoteTrust(
                    version = if (root.has("version")) root.getInt("version") else null,
                    updatedAt = root.optString("updatedAt").takeIf { it.isNotBlank() },
                    hosts = hosts,
                )
            }
        }
    }
}
