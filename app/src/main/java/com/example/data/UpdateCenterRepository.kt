package com.example.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.FileProvider
import com.example.BuildConfig
import com.example.model.UpdateDownloadState
import com.example.model.UpdateManifest
import com.example.model.VersionHistoryEntry
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

sealed interface UpdateCheckResult {
    data class UpdateAvailable(val manifest: UpdateManifest) : UpdateCheckResult
    data class UpToDate(val currentVersionName: String, val remoteVersionName: String) : UpdateCheckResult
    data class Error(val message: String) : UpdateCheckResult
}

@JsonClass(generateAdapter = true)
internal data class GitHubReleaseAssetDto(
    val name: String = "",
    @Json(name = "browser_download_url") val browserDownloadUrl: String = "",
)

@JsonClass(generateAdapter = true)
internal data class GitHubReleaseDto(
    @Json(name = "tag_name") val tagName: String = "",
    @Json(name = "published_at") val publishedAt: String = "",
    val body: String = "",
    val assets: List<GitHubReleaseAssetDto> = emptyList(),
)

/**
 * Pure JVM-testable manifest & GitHub Release JSON parser + HTTPS/SHA-256 validators.
 */
object UpdateManifestParser {
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val manifestAdapter = moshi.adapter(UpdateManifest::class.java)
    private val githubReleaseAdapter = moshi.adapter(GitHubReleaseDto::class.java)

    fun isSecureHttpsUrl(url: String): Boolean =
        url.trim().startsWith("https://", ignoreCase = true)

    fun isValidSha256Hex(sha256: String): Boolean {
        val trimmed = sha256.trim().lowercase()
        return trimmed.length == 64 && trimmed.all { it in '0'..'9' || it in 'a'..'f' }
    }

    fun parseManifestOrGitHubRelease(jsonString: String): UpdateManifest? {
        runCatching { manifestAdapter.fromJson(jsonString) }.getOrNull()?.let { direct ->
            if (direct.apkUrl.isNotBlank() && direct.versionName.isNotBlank()) return direct
        }

        return runCatching {
            val gh = githubReleaseAdapter.fromJson(jsonString) ?: return@runCatching null
            val tagName = gh.tagName.removePrefix("v").trim()
            if (tagName.isBlank()) return@runCatching null

            val apkDownloadUrl = gh.assets
                .firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
                ?.browserDownloadUrl
                .orEmpty()
            if (apkDownloadUrl.isBlank()) return@runCatching null

            val shaRegex = Regex("(?i)sha-?256\\s*[:=]\\s*([a-f0-9]{64})")
            val shaMatch = shaRegex.find(gh.body)?.groupValues?.getOrNull(1) ?: ""
            val notes = gh.body
                .lines()
                .map { it.trim().removePrefix("- ").removePrefix("* ") }
                .filter { it.isNotBlank() && !it.lowercase().contains("sha256") }
                .take(8)
                .ifEmpty { listOf("Release $tagName") }

            UpdateManifest(
                versionCode = parseSemverToCode(tagName),
                versionName = tagName,
                releaseDate = gh.publishedAt.take(10).ifBlank { "Latest" },
                apkUrl = apkDownloadUrl,
                sha256 = shaMatch,
                releaseNotes = notes,
            )
        }.getOrNull()
    }

    fun parseSemverToCode(version: String): Int {
        val parts = version.split(".").mapNotNull { it.filter(Char::isDigit).toIntOrNull() }
        val major = parts.getOrElse(0) { 1 }
        val minor = parts.getOrElse(1) { 0 }
        val patch = parts.getOrElse(2) { 0 }
        return major * 10_000 + minor * 100 + patch
    }
}

/**
 * Handles HTTPS-only update manifest fetching (supporting both custom JSON manifests and
 * GitHub Releases API responses), streaming APK download, SHA-256 integrity verification,
 * package archive validation, and user-confirmed installation via Android FileProvider.
 */
class UpdateCenterRepository(
    private val context: Context,
) {
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    val builtInVersionHistory: List<VersionHistoryEntry> = listOf(
        VersionHistoryEntry(
            versionName = BuildConfig.VERSION_NAME,
            versionCode = BuildConfig.VERSION_CODE,
            releaseDate = "2026-11-24",
            highlights = listOf(
                "Initial release tailored for Vivo X300 and OnePlus Nord Buds 3 Pro.",
                "Live Bluetooth A2DP/HFP connection state, codec inspection, and Fast Pair (0xFE2C) / BLE Battery Service (0x180F) parsing.",
                "Real Android 5+ band Equalizer & Bass Boost AudioFX engine with custom sound profiles.",
                "Transparent hardware protocol boundary indicators (never simulates OEM-locked ANC or touch remapping).",
                "Consent-gated AES-GCM encrypted crash diagnostics and SHA-256 verified APK Update Center.",
            ),
        ),
    )

    suspend fun checkForUpdates(manifestUrl: String): UpdateCheckResult = withContext(Dispatchers.IO) {
        val trimmed = manifestUrl.trim()
        if (!UpdateManifestParser.isSecureHttpsUrl(trimmed)) {
            return@withContext UpdateCheckResult.Error("Insecure URL rejected. Only HTTPS update endpoints are allowed.")
        }

        val request = Request.Builder()
            .url(trimmed)
            .header("Accept", "application/vnd.github+json, application/json")
            .header("User-Agent", "NordBudsPremium/${BuildConfig.VERSION_NAME}")
            .build()

        runCatching {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext UpdateCheckResult.Error(
                        "Update server returned HTTP ${response.code}. Configure a live GitHub Releases or HTTPS manifest URL.",
                    )
                }
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) {
                    return@withContext UpdateCheckResult.Error("Empty response from update endpoint.")
                }
                val manifest = UpdateManifestParser.parseManifestOrGitHubRelease(body)
                    ?: return@withContext UpdateCheckResult.Error("Unable to parse release manifest JSON.")

                if (!UpdateManifestParser.isSecureHttpsUrl(manifest.apkUrl)) {
                    return@withContext UpdateCheckResult.Error("Manifest rejected: APK download URL must use HTTPS.")
                }

                if (manifest.versionCode > BuildConfig.VERSION_CODE) {
                    UpdateCheckResult.UpdateAvailable(manifest)
                } else {
                    UpdateCheckResult.UpToDate(
                        currentVersionName = BuildConfig.VERSION_NAME,
                        remoteVersionName = manifest.versionName,
                    )
                }
            }
        }.getOrElse { err ->
            UpdateCheckResult.Error("Network check failed: ${err.localizedMessage ?: err.javaClass.simpleName}")
        }
    }

    fun downloadAndVerifyApk(manifest: UpdateManifest): Flow<UpdateDownloadState> = flow {
        if (!UpdateManifestParser.isSecureHttpsUrl(manifest.apkUrl)) {
            emit(UpdateDownloadState.Failed("Rejected non-HTTPS APK URL."))
            return@flow
        }
        val expectedSha256 = manifest.sha256.trim().lowercase()
        if (!UpdateManifestParser.isValidSha256Hex(expectedSha256)) {
            emit(UpdateDownloadState.Failed("Update aborted: manifest missing valid 64-char SHA-256 checksum."))
            return@flow
        }

        val updatesDir = File(context.filesDir, "updates").apply { mkdirs() }
        updatesDir.listFiles()?.forEach { it.delete() }
        val targetApk = File(updatesDir, "NordBudsPremium-${manifest.versionName}.apk")

        try {
            val request = Request.Builder()
                .url(manifest.apkUrl)
                .header("User-Agent", "NordBudsPremium/${BuildConfig.VERSION_NAME}")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    emit(UpdateDownloadState.Failed("Download failed (HTTP ${response.code})"))
                    return@flow
                }
                val body = response.body ?: run {
                    emit(UpdateDownloadState.Failed("Empty APK response body"))
                    return@flow
                }

                val totalBytes = body.contentLength()
                var downloaded = 0L
                val digest = MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(16 * 1024)

                body.byteStream().use { input ->
                    FileOutputStream(targetApk).use { output ->
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            digest.update(buffer, 0, read)
                            downloaded += read
                            val pct = if (totalBytes > 0) ((downloaded * 100) / totalBytes).toInt().coerceIn(0, 100) else 0
                            emit(UpdateDownloadState.Downloading(pct, downloaded, totalBytes))
                        }
                    }
                }

                emit(UpdateDownloadState.VerifyingIntegrity)
                val actualSha256 = digest.digest().joinToString("") { "%02x".format(it) }
                if (!actualSha256.equals(expectedSha256, ignoreCase = true)) {
                    targetApk.delete()
                    emit(UpdateDownloadState.Failed("SHA-256 integrity mismatch. File discarded."))
                    return@flow
                }

                val archiveInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.packageManager.getPackageArchiveInfo(
                        targetApk.absolutePath,
                        PackageManager.PackageInfoFlags.of(0),
                    )
                } else {
                    @Suppress("DEPRECATION")
                    context.packageManager.getPackageArchiveInfo(targetApk.absolutePath, 0)
                }

                if (archiveInfo == null || archiveInfo.packageName != context.packageName) {
                    targetApk.delete()
                    emit(
                        UpdateDownloadState.Failed(
                            "Package verification failed: APK package (${archiveInfo?.packageName ?: "invalid"}) does not match ${context.packageName}.",
                        ),
                    )
                    return@flow
                }

                emit(
                    UpdateDownloadState.VerifiedReady(
                        apkFilePath = targetApk.absolutePath,
                        sha256Verified = actualSha256,
                        versionName = manifest.versionName,
                    ),
                )
            }
        } catch (err: Exception) {
            if (targetApk.exists()) targetApk.delete()
            emit(UpdateDownloadState.Failed(err.localizedMessage ?: "Download failed"))
        }
    }.flowOn(Dispatchers.IO)

    fun buildInstallIntent(apkFilePath: String): Intent {
        val file = File(apkFilePath)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
