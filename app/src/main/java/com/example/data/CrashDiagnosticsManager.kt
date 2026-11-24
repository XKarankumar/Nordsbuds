package com.example.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.example.BuildConfig
import com.example.model.CrashDiagnosticEntry
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

/**
 * Consent-gated crash diagnostics recorder.
 * - Captures uncaught exceptions locally ONLY when the user has explicitly opted in.
 * - Scrubs Bluetooth MAC addresses from stack traces before encrypted persistence.
 * - Never transmits diagnostics silently in the background.
 */
class CrashDiagnosticsManager(
    private val context: Context,
    private val storage: SecureStorageRepository,
) {
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val adapter = moshi.adapter<List<CrashDiagnosticEntry>>(
        Types.newParameterizedType(List::class.java, CrashDiagnosticEntry::class.java),
    )

    private val _entries = MutableStateFlow<List<CrashDiagnosticEntry>>(emptyList())
    val entries: StateFlow<List<CrashDiagnosticEntry>> = _entries.asStateFlow()

    @Volatile
    private var isConsentEnabled = false

    fun initialize() {
        _entries.value = storage.loadEncryptedDiagnostics()
        isConsentEnabled = runBlocking { storage.isDiagnosticsConsentEnabledSync() }
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            if (isConsentEnabled) {
                runCatching { recordCrash(thread.name, throwable) }
            }
            previousHandler?.uncaughtException(thread, throwable)
        }
    }

    fun updateConsentInMemory(enabled: Boolean) {
        isConsentEnabled = enabled
    }

    fun refreshFromStorage() {
        _entries.value = storage.loadEncryptedDiagnostics()
    }

    fun clearAll() {
        storage.clearDiagnostics()
        _entries.value = emptyList()
    }

    fun recordCrash(threadName: String, throwable: Throwable) {
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        val scrubbedTrace = scrubSensitiveTokens(sw.toString()).take(2200)
        val entry = CrashDiagnosticEntry(
            id = UUID.randomUUID().toString().take(8),
            timestampIso = currentUtcIso(),
            appVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
            androidRelease = Build.VERSION.RELEASE ?: "Unknown",
            sdkInt = Build.VERSION.SDK_INT,
            threadName = threadName,
            exceptionSummary = "${throwable.javaClass.simpleName}: ${scrubSensitiveTokens(throwable.message ?: "No message")}",
            stackTracePreview = scrubbedTrace,
        )
        val updated = (listOf(entry) + storage.loadEncryptedDiagnostics()).take(20)
        storage.saveEncryptedDiagnostics(updated)
        _entries.value = updated
    }

    fun exportDiagnosticsShareIntent(): Intent? {
        val current = _entries.value
        val exportDir = File(context.cacheDir, "diagnostics").apply { mkdirs() }
        val exportFile = File(exportDir, "nordbuds_diagnostics.json")
        val content = if (current.isEmpty()) {
            """{"appVersion":"${BuildConfig.VERSION_NAME}","device":"${Build.MANUFACTURER} ${Build.MODEL}","sdk":${Build.VERSION.SDK_INT},"entries":[]}"""
        } else {
            adapter.toJson(current)
        }
        exportFile.writeText(content)
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            exportFile,
        )
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_SUBJECT, "NordBuds Premium Diagnostics (${BuildConfig.VERSION_NAME})")
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun buildBugReportIntent(summary: String, reproSteps: String): Intent {
        val latestCrash = _entries.value.firstOrNull()
        val body = buildString {
            appendLine("## Environment")
            appendLine("- App: NordBuds Premium v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("- Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("- Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine()
            appendLine("## Issue Summary")
            appendLine(summary.ifBlank { "Describe the issue here..." })
            appendLine()
            appendLine("## Reproduction Steps")
            appendLine(reproSteps.ifBlank { "1. \n2. " })
            if (latestCrash != null && isConsentEnabled) {
                appendLine()
                appendLine("## Consented Crash Diagnostic (${latestCrash.timestampIso})")
                appendLine("```")
                appendLine(latestCrash.exceptionSummary)
                appendLine(latestCrash.stackTracePreview.take(900))
                appendLine("```")
            }
        }
        val uri = Uri.Builder()
            .scheme("https")
            .authority("github.com")
            .path("nordbuds-companion/nordbuds-premium/issues/new")
            .appendQueryParameter("title", summary.ifBlank { "Bug Report: NordBuds Premium" })
            .appendQueryParameter("body", body)
            .build()
        return Intent(Intent.ACTION_VIEW, uri)
    }

    private fun scrubSensitiveTokens(raw: String): String {
        val macRegex = Regex("([0-9A-Fa-f]{2}[:-]){5}[0-9A-Fa-f]{2}")
        return raw.replace(macRegex, "[REDACTED_BT_MAC]")
    }

    private fun currentUtcIso(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }
}
