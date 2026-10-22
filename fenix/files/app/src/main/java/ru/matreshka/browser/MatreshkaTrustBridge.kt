/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package ru.matreshka.browser

import android.content.Context
import android.util.Log
import org.mozilla.geckoview.GeckoRuntimeSettings

/**
 * Passes the matreshka trust configuration to GeckoView.
 *
 * Only a patched GeckoView exposes `GeckoRuntimeSettings.Builder.matreshkaTrust(String)` and
 * `GeckoRuntimeSettings.setMatreshkaTrust(String)`. Stock GeckoView does not, so every call is made through reflection
 * and failures are logged instead of propagated. This keeps the fork buildable and runnable against either GeckoView.
 */
object MatreshkaTrustBridge {
    private const val TAG = "MatreshkaTrust"

    /**
     * Reads the trust JSON from assets and converts it to the compact text format expected by the patched GeckoView, or
     * null if it cannot be read or parsed.
     */
    fun readTrustConfig(context: Context): String? =
        try {
            TrustConfig.load(context).toGeckoViewFormat()
        } catch (throwable: Throwable) {
            Log.w(TAG, "Unable to read ${TrustConfig.ASSET_PATH}", throwable)
            null
        }

    /**
     * Calls `GeckoRuntimeSettings.Builder.matreshkaTrust(String)` if the patched GeckoView provides it. Returns true
     * when the configuration was accepted by the builder.
     */
    fun applyToBuilder(
        trustText: String?,
        builder: GeckoRuntimeSettings.Builder,
    ): Boolean = invoke("matreshkaTrust", builder, trustText)

    /**
     * Calls `GeckoRuntimeSettings.setMatreshkaTrust(String)` if the patched GeckoView provides it. Used only when the
     * builder variant is unavailable.
     */
    fun applyToSettings(
        trustText: String?,
        settings: GeckoRuntimeSettings,
    ): Boolean = invoke("setMatreshkaTrust", settings, trustText)

    private fun invoke(
        methodName: String,
        target: Any,
        trustText: String?,
    ): Boolean {
        if (trustText == null) {
            return false
        }

        return try {
            val method = target.javaClass.getMethod(methodName, String::class.java)
            method.invoke(target, trustText)
            Log.i(TAG, "Passed matreshka trust configuration via $methodName")
            true
        } catch (throwable: NoSuchMethodException) {
            Log.i(TAG, "GeckoView does not implement $methodName; using stock trust store")
            false
        } catch (throwable: Throwable) {
            Log.w(TAG, "Failed to pass matreshka trust configuration via $methodName", throwable)
            false
        }
    }
}
