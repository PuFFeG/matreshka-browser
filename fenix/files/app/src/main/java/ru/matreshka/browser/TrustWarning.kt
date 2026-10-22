/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package ru.matreshka.browser

import android.content.Context
import java.util.Locale
import mozilla.components.browser.state.state.SecurityInfo

/** Why the site info icon is highlighted. */
enum class TrustWarningKind {
    NONE,
    OUT_OF_LIST,
    STALE,
    INSECURE,
}

/** The warning shown next to the site info icon for the selected tab. */
data class TrustWarning(
    val kind: TrustWarningKind,
    val host: String = "",
    val issuer: String = "",
    val details: String = "",
)

/**
 * Evaluates the selected tab against the effective trust list. A yellow indicator is shown when:
 *
 * - the certificate issuer is the Russian Trusted CA and the host is not in the list,
 * - the certificate issuer is the Russian Trusted CA and the list or a bundled CA is stale,
 * - the connection is not trustworthy (possible MITM).
 */
object TrustWarningEvaluator {
    private const val WARNING_COLOR = 0xFFFFB300.toInt()
    private const val WARNING_BACKGROUND = 0x33FFB300

    fun color(): Int = WARNING_COLOR

    fun backgroundColor(): Int = WARNING_BACKGROUND

    private fun isRussianIssuer(issuer: String): Boolean =
        issuer.contains("Russian Trusted", ignoreCase = true) ||
            issuer.contains("Ministry of Digital", ignoreCase = true)

    fun evaluate(context: Context, url: String, securityInfo: SecurityInfo): TrustWarning {
        if (!url.startsWith("https://", ignoreCase = true)) {
            return TrustWarning(TrustWarningKind.NONE)
        }

        val host = TrustPartition.hostFromUrl(url) ?: securityInfo.host.lowercase(Locale.ROOT)
        val issuer =
            securityInfo.issuer.ifBlank {
                securityInfo.certificate?.issuerDN?.name.orEmpty()
            }
        val russianIssuer =
            isRussianIssuer(issuer) || isRussianIssuer(securityInfo.certificate?.issuerDN?.name.orEmpty())

        if (securityInfo is SecurityInfo.Insecure) {
            val details =
                "Соединение не защищено: сертификат отсутствует, просрочен или не прошёл проверку. " +
                    "Возможна атака «человек посередине» (MITM). Не вводите пароли, коды и данные карт."
            return TrustWarning(TrustWarningKind.INSECURE, host, issuer, details)
        }

        if (securityInfo !is SecurityInfo.Secure || !russianIssuer) {
            return TrustWarning(TrustWarningKind.NONE, host, issuer)
        }

        val effective =
            try {
                TrustRepository.effective(context)
            } catch (throwable: Throwable) {
                return TrustWarning(TrustWarningKind.NONE, host, issuer)
            }

        if (!effective.config.matches(host)) {
            val details = buildString {
                append("Домен отсутствует в списке доверенных сайтов Минцифры. ")
                append("Сертификат принят по правилу для зон .ru и .рф; это ожидаемо для российских сайтов, ")
                append("но проверьте адрес: при перехвате трафика возможен MITM.")
                effective.staleReason?.let { append("\n\n").append(it) }
            }
            return TrustWarning(TrustWarningKind.OUT_OF_LIST, host, issuer, details)
        }

        effective.staleReason?.let { reason ->
            return TrustWarning(
                TrustWarningKind.STALE,
                host,
                issuer,
                "Соединение защищено сертификатом Минцифры, но данные списка устарели: $reason",
            )
        }

        return TrustWarning(TrustWarningKind.NONE, host, issuer)
    }
}
