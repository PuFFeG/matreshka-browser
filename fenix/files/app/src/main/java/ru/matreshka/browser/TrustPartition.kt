/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package ru.matreshka.browser

import android.content.Context
import android.net.Uri
import mozilla.components.browser.state.action.BrowserAction
import mozilla.components.browser.state.action.EngineAction
import mozilla.components.browser.state.action.TabListAction
import mozilla.components.browser.state.state.BrowserState
import mozilla.components.browser.state.state.TabSessionState
import mozilla.components.browser.state.state.createTab
import mozilla.components.lib.state.Middleware
import mozilla.components.lib.state.Store
import org.mozilla.fenix.gecko.GeckoProvider

/**
 * Storage partition for sites that may be verified by the bundled Russian Trusted CA.
 *
 * GeckoView's session context ID partitions cookies, localStorage, IndexedDB, service workers, cache and permissions.
 * It can only be set when the GeckoSession is created, so the partition is chosen from the tab's initial URL. A tab
 * that starts on a Russian host stays in the isolated partition even if it later navigates elsewhere; a tab that starts
 * elsewhere stays in the normal partition. That is a deliberate trade-off: changing the partition would require
 * recreating the session and losing navigation state.
 */
object TrustPartition {
    /** Raw context ID. GeckoView hashes it into its own `gvctx...` namespace internally. */
    const val RUSSIAN_CONTEXT_ID = "matreshka"

    /** Returns the URL's normalized host, or null for non-web URLs. */
    fun hostFromUrl(url: String?): String? {
        if (url.isNullOrBlank()) {
            return null
        }
        val host =
            try {
                Uri.parse(url).host
            } catch (throwable: Throwable) {
                null
            } ?: return null
        return TrustRepository.normalizeHost(host)
    }

    /** True for the .ru TLD and the punycode form of .рф. */
    fun isRussianHost(host: String): Boolean =
        host == "ru" || host.endsWith(".ru") || host == "xn--p1ai" || host.endsWith(".xn--p1ai")

    /** True for hosts explicitly present in the bundled/user/remote list. */
    fun isListed(context: Context, host: String): Boolean =
        try {
            TrustRepository.effective(context).config.matches(host)
        } catch (throwable: Throwable) {
            false
        }

    /** Context ID for a tab opened at [url], or null when it belongs in the normal partition. */
    fun contextIdForUrl(context: Context, url: String?): String? {
        val host = hostFromUrl(url) ?: return null
        return if (isRussianHost(host) || isListed(context, host)) RUSSIAN_CONTEXT_ID else null
    }

    /** Deletes all storage belonging to the isolated partition. */
    fun clear(context: Context) {
        GeckoProvider.getRuntime()?.storageController?.clearDataForSessionContext(RUSSIAN_CONTEXT_ID)
    }
}

/**
 * Assigns [TrustPartition.RUSSIAN_CONTEXT_ID] to newly created tabs whose initial URL belongs to the Russian trust
 * layer. It must run before `EngineMiddleware` so that the context ID is present when the `GeckoSession` is created.
 */
class MatreshkaStorageMiddleware(private val context: Context) : Middleware<BrowserState, BrowserAction> {
    override fun invoke(
        store: Store<BrowserState, BrowserAction>,
        next: (BrowserAction) -> Unit,
        action: BrowserAction,
    ) {
        when (action) {
            is TabListAction.AddTabAction -> next(action.copy(tab = action.tab.withTrustContext()))
            is TabListAction.AddMultipleTabsAction ->
                next(action.copy(tabs = action.tabs.map { it.withTrustContext() }))
            is EngineAction.LoadUrlAction ->
                if (!redirectBlankTab(store, action)) {
                    next(action)
                }
            else -> next(action)
        }
    }

    /**
     * A new tab is created as about:blank, so its partition is already fixed when the user types a Russian URL. If the
     * tab is still blank, replace it with an isolated tab instead of loading the URL into the normal partition.
     */
    private fun redirectBlankTab(
        store: Store<BrowserState, BrowserAction>,
        action: EngineAction.LoadUrlAction,
    ): Boolean {
        val tab = store.state.tabs.find { it.id == action.tabId } ?: return false
        if (tab.content.private || tab.contextId != null) {
            return false
        }
        val contextId = TrustPartition.contextIdForUrl(context, action.url) ?: return false
        if (!tab.content.url.isBlankUrl()) {
            return false
        }

        val replacement =
            createTab(
                url = action.url,
                contextId = contextId,
                source = tab.source,
                initialLoadFlags = action.flags,
                initialAdditionalHeaders = action.additionalHeaders,
            )
        store.dispatch(TabListAction.AddTabAction(replacement, select = true))
        store.dispatch(TabListAction.RemoveTabAction(action.tabId, selectParentIfExists = false))
        return true
    }

    private fun String.isBlankUrl(): Boolean =
        isEmpty() || equals("about:blank", ignoreCase = true) || equals("about:home", ignoreCase = true)

    private fun TabSessionState.withTrustContext(): TabSessionState {
        if (contextId != null || content.private || engineState.engineSession != null) {
            return this
        }
        val contextId = TrustPartition.contextIdForUrl(context, content.url) ?: return this
        return copy(contextId = contextId)
    }
}
