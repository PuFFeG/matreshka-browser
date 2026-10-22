/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package ru.matreshka.browser

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.mozilla.fenix.R
import org.mozilla.fenix.gecko.GeckoProvider

private const val CONNECT_TIMEOUT_MS = 15_000
private const val READ_TIMEOUT_MS = 15_000
private const val MAX_DOWNLOAD_BYTES = 2 * 1024 * 1024
private const val MAX_REDIRECTS = 5

/** Shows and edits the effective Минцифры trust list: bundled hosts, a remote update and user additions. */
class MatreshkaSitesActivity : AppCompatActivity() {
    private val trustState = mutableStateOf<EffectiveTrust?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setTitle(R.string.matreshka_trust_sites_title)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        reloadTrust()

        val scope = lifecycleScope
        setContentView(
            ComposeView(this).apply {
                setContent {
                    MaterialTheme(colorScheme = darkColorScheme()) {
                        MatreshkaSitesScreen(
                            trust = trustState.value,
                            scope = scope,
                            onRefresh = { reloadTrust() },
                        )
                    }
                }
            }
        )
    }

    override fun onResume() {
        super.onResume()
        reloadTrust()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun reloadTrust() {
        trustState.value =
            try {
                TrustRepository.effective(this)
            } catch (throwable: Throwable) {
                Log.w(TAG, "Unable to load effective trust list", throwable)
                null
            }
    }

    companion object {
        private const val TAG = "MatreshkaSites"
    }
}

@Composable
private fun MatreshkaSitesScreen(
    trust: EffectiveTrust?,
    scope: CoroutineScope,
    onRefresh: () -> Unit,
) {
    val context = LocalContext.current
    var showAddDialog by remember { mutableStateOf(false) }
    var showLinkDialog by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    var showClearDataDialog by remember { mutableStateOf(false) }

    val hasRemote = TrustRepository.hasRemote(context)

    if (showAddDialog) {
        AddHostDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { input ->
                val host = TrustRepository.addUserHost(context, input)
                if (host != null) {
                    GeckoProvider.refreshTrust(context)
                    onRefresh()
                }
                host != null
            },
        )
    }

    if (showLinkDialog) {
        UpdateByLinkDialog(
            scope = scope,
            hasRemote = hasRemote,
            onDismiss = { showLinkDialog = false },
            onImported = onRefresh,
            onClearRemote = {
                TrustRepository.clearRemote(context)
                GeckoProvider.refreshTrust(context)
                onRefresh()
            },
        )
    }

    if (showClearDataDialog) {
        ConfirmDialog(
            title = "Очистить данные российских сайтов?",
            message =
                "Будут удалены cookie, localStorage, IndexedDB, кэш и разрешения, сохранённые " +
                    "в изолированном хранилище сайтов, открытых по сертификату Минцифры. Обычные сайты " +
                    "не затрагиваются. Открытые вкладки могут снова сохранить данные.",
            confirmText = "Очистить",
            onDismiss = { showClearDataDialog = false },
            onConfirm = {
                TrustPartition.clear(context)
                showClearDataDialog = false
            },
        )
    }

    if (showClearDialog) {
        ConfirmDialog(
            title = "Очистить добавленные домены?",
            message = "Все домены, добавленные вручную на этом устройстве, будут удалены.",
            confirmText = "Очистить",
            onDismiss = { showClearDialog = false },
            onConfirm = {
                TrustRepository.clearUserHosts(context)
                GeckoProvider.refreshTrust(context)
                onRefresh()
                showClearDialog = false
            },
        )
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        LazyColumn(
            contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    text = "Список доверенных сайтов Минцифры",
                    style = MaterialTheme.typography.headlineSmall,
                )
            }

            item {
                Text(
                    text =
                        "Сайты из списка могут использовать сертификаты, выпущенные российским " +
                            "доверенным корневым сертификатом Минцифры.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            if (trust == null) {
                item {
                    Text(
                        text = "Не удалось загрузить список доверенных сайтов.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            } else {
                item {
                    Text(
                        text = "Обновлено: ${trust.config.updatedAt.ifBlank { "нет данных" }}",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }

                item {
                    Text(
                        text = "Корневых сертификатов: ${trust.config.roots.size}",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }

                item {
                    Text(
                        text = "Записей в списке: ${trust.entries.size}",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }

                trust.staleReason?.let { reason ->
                    item {
                        StaleWarning(reason)
                    }
                }

                item {
                    RuleCard()
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { showAddDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Добавить домен")
                        }
                        Button(
                            onClick = { showLinkDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Обновить по ссылке")
                        }
                        Button(
                            onClick = { showClearDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Очистить добавленные")
                        }
                        Button(
                            onClick = { showClearDataDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Очистить данные российских сайтов")
                        }
                    }
                }

                item {
                    Box(
                        modifier =
                            Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant)
                    )
                }

                items(trust.entries, key = { entry -> entry.host }) { entry ->
                    HostRow(
                        entry = entry,
                        onRemove = {
                            TrustRepository.removeUserHost(context, entry.host)
                            GeckoProvider.refreshTrust(context)
                            onRefresh()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun StaleWarning(reason: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = Color(0xFFFFE082),
                contentColor = Color(0xFF4E342E),
            ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "Список может быть устаревшим",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = reason,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun RuleCard() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "Как это работает",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text =
                    "Для сайтов в зонах .ru и .рф, которых нет в списке, встроенный российский " +
                        "доверенный корневой сертификат Минцифры всё равно принимается. Список в основном " +
                        "управляет жёлтым предупреждением: если сертификат сайта выпущен Минцифры, а самого " +
                        "сайта нет в списке, браузер показывает предупреждение.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HostRow(
    entry: TrustEntry,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = entry.host,
                style = MaterialTheme.typography.titleMedium,
            )
            SourceBadge(entry.source)
        }
        if (entry.source == TrustEntry.Source.USER) {
            TextButton(onClick = onRemove) {
                Text("Удалить")
            }
        }
    }
}

@Composable
private fun SourceBadge(source: TrustEntry.Source) {
    val colors = MaterialTheme.colorScheme
    val (label, container, content) =
        when (source) {
            TrustEntry.Source.BASE -> Triple("встроенный", colors.secondaryContainer, colors.onSecondaryContainer)
            TrustEntry.Source.REMOTE -> Triple("обновление", colors.tertiaryContainer, colors.onTertiaryContainer)
            TrustEntry.Source.USER -> Triple("добавлен", colors.primaryContainer, colors.onPrimaryContainer)
        }
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = container,
        contentColor = content,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun AddHostDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Boolean,
) {
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Добавить домен") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { value ->
                        input = value
                        error = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Домен") },
                    placeholder = { Text("example.ru") },
                    singleLine = true,
                    isError = error != null,
                )
                Text(
                    text = "Будут добавлены домен и все его поддомены.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                error?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (onConfirm(input)) {
                        onDismiss()
                    } else {
                        error = "Некорректный домен"
                    }
                }
            ) {
                Text("Добавить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        },
    )
}

@Composable
private fun UpdateByLinkDialog(
    scope: CoroutineScope,
    hasRemote: Boolean,
    onDismiss: () -> Unit,
    onImported: () -> Unit,
    onClearRemote: () -> Unit,
) {
    val context = LocalContext.current
    var input by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        title = { Text("Обновить по ссылке") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { value ->
                        input = value
                        message = null
                        isError = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Ссылка") },
                    placeholder = { Text("https://example.ru/trust.json") },
                    singleLine = true,
                    isError = isError,
                    enabled = !isLoading,
                )
                Text(
                    text = "Ссылка должна начинаться с https://. Из документа берутся только домены.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                message?.let { text ->
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodySmall,
                        color =
                            if (isError) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isLoading,
                onClick = {
                    val url = input.trim()
                    if (!url.startsWith("https://")) {
                        isError = true
                        message = "Ссылка должна начинаться с https://"
                        return@TextButton
                    }
                    isLoading = true
                    isError = false
                    message = null
                    scope.launch {
                        try {
                            val json =
                                withContext(Dispatchers.IO) {
                                    fetchTrustDocument(url)
                                }
                            val count =
                                withContext(Dispatchers.IO) {
                                    TrustRepository.importRemote(context, json)
                                }
                            GeckoProvider.refreshTrust(context)
                            onImported()
                            isError = false
                            message = "Загружено доменов: $count"
                        } catch (cancellation: CancellationException) {
                            throw cancellation
                        } catch (throwable: Throwable) {
                            isError = true
                            message = throwable.message ?: "Не удалось загрузить список"
                        } finally {
                            isLoading = false
                        }
                    }
                },
            ) {
                Text(if (isLoading) "Загрузка…" else "Загрузить")
            }
        },
        dismissButton = {
            Row {
                if (hasRemote) {
                    TextButton(
                        onClick = {
                            onClearRemote()
                            onDismiss()
                        }
                    ) {
                        Text("Удалить обновление")
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Отмена")
                }
            }
        },
    )
}

@Composable
private fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        },
    )
}

private fun fetchTrustDocument(rawUrl: String): String {
    var current = URL(rawUrl)
    var redirects = 0
    while (true) {
        require(current.protocol.equals("https", ignoreCase = true)) {
            "Ссылка должна использовать https://"
        }
        val connection =
            (current.openConnection() as HttpsURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = false
                requestMethod = "GET"
            }
        try {
            val code = connection.responseCode
            if (code in 300..399) {
                val location =
                    connection.getHeaderField("Location") ?: throw IOException("Сервер не указал адрес перенаправления")
                redirects += 1
                if (redirects > MAX_REDIRECTS) {
                    throw IOException("Слишком много перенаправлений")
                }
                current = URL(current, location)
                continue
            }
            if (code !in 200..299) {
                throw IOException("Сервер вернул код $code")
            }
            return connection.inputStream.use { stream -> readLimited(stream) }
        } finally {
            connection.disconnect()
        }
    }
}

private fun readLimited(input: InputStream): String {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(16 * 1024)
    var total = 0
    while (true) {
        val read = input.read(buffer)
        if (read < 0) {
            break
        }
        total += read
        if (total > MAX_DOWNLOAD_BYTES) {
            throw IOException("Файл больше 2 МБ")
        }
        output.write(buffer, 0, read)
    }
    return output.toString("UTF-8")
}
