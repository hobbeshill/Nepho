package com.example.updates

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

sealed interface UpdateCheckState {
    data object Idle : UpdateCheckState
    data object Checking : UpdateCheckState

    data class UpToDate(
        val currentVersionName: String,
        val latestTag: String,
        val releasePageUrl: String
    ) : UpdateCheckState

    data class UpdateAvailable(
        val currentVersionName: String,
        val latestTag: String,
        val releaseName: String,
        val releaseNotes: String,
        val downloadUrl: String,
        val releasePageUrl: String,
        val publishedAt: String
    ) : UpdateCheckState

    data class Error(val message: String) : UpdateCheckState
}

object GitHubReleaseUpdater {
    private val REPO_SLUG_REGEX = Regex("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$")
    private val GITHUB_URL_REGEX = Regex(
        "^(?:https?://)?(?:www\\.)?github\\.com/([A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+)(?:/.*)?$",
        RegexOption.IGNORE_CASE
    )

    /**
     * Normalizes either `owner/repo` or a full GitHub repository URL into `owner/repo`.
     * Returns null if the input is not a valid GitHub repository identifier.
     */
    fun normalizeRepoSlug(raw: String): String? {
        val trimmed = raw.trim().removeSuffix(".git").trimEnd('/')
        if (trimmed.isEmpty()) return null
        val extracted = GITHUB_URL_REGEX.matchEntire(trimmed)?.groupValues?.get(1) ?: trimmed
        return extracted.takeIf { REPO_SLUG_REGEX.matches(it) }
    }

    fun releasesPageUrl(repoSlug: String): String =
        "https://github.com/$repoSlug/releases"

    /**
     * Compares two dot-separated version strings (e.g. `v1.0.4` vs `1.0`).
     * Returns > 0 if [remote] is newer than [current], 0 if equal, < 0 if older.
     */
    fun compareVersions(remote: String, current: String): Int {
        val remoteParts = extractNumericSegments(remote)
        val currentParts = extractNumericSegments(current)
        if (remoteParts.isEmpty() || currentParts.isEmpty()) {
            return remote.trim().removePrefix("v").compareTo(current.trim().removePrefix("v"))
        }
        val maxLen = maxOf(remoteParts.size, currentParts.size)
        for (i in 0 until maxLen) {
            val r = remoteParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (r != c) return r.compareTo(c)
        }
        return 0
    }

    private fun extractNumericSegments(raw: String): List<Int> {
        val cleaned = raw.trim().removePrefix("v").removePrefix("V")
        return Regex("\\d+")
            .findAll(cleaned)
            .mapNotNull { it.value.toIntOrNull() }
            .toList()
    }

    fun parseLatestReleaseJson(
        jsonText: String,
        currentVersionName: String,
        repoSlug: String
    ): UpdateCheckState {
        return try {
            val obj = JSONObject(jsonText)
            val tagName = obj.optString("tag_name").trim()
            if (tagName.isEmpty()) {
                return UpdateCheckState.Error("Latest GitHub release is missing a version tag.")
            }
            val releaseName = obj.optString("name").trim().ifEmpty { "Nepho $tagName" }
            val releaseNotes = obj.optString("body").trim()
            val publishedAt = obj.optString("published_at").trim()
            val releasePageUrl = obj.optString("html_url").trim()
                .ifEmpty { releasesPageUrl(repoSlug) }

            var apkDownloadUrl: String? = null
            val assets = obj.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.optJSONObject(i) ?: continue
                    val name = asset.optString("name").lowercase()
                    val url = asset.optString("browser_download_url").trim()
                    if (name.endsWith(".apk") && url.startsWith("https://")) {
                        apkDownloadUrl = url
                        break
                    }
                }
            }

            if (compareVersions(tagName, currentVersionName) > 0) {
                UpdateCheckState.UpdateAvailable(
                    currentVersionName = currentVersionName,
                    latestTag = tagName,
                    releaseName = releaseName,
                    releaseNotes = releaseNotes,
                    downloadUrl = apkDownloadUrl ?: releasePageUrl,
                    releasePageUrl = releasePageUrl,
                    publishedAt = publishedAt
                )
            } else {
                UpdateCheckState.UpToDate(
                    currentVersionName = currentVersionName,
                    latestTag = tagName,
                    releasePageUrl = releasePageUrl
                )
            }
        } catch (e: Exception) {
            UpdateCheckState.Error("Couldn't read GitHub release response: ${e.message ?: "invalid JSON"}")
        }
    }

    suspend fun checkLatestRelease(
        repoSlug: String,
        currentVersionName: String
    ): UpdateCheckState = withContext(Dispatchers.IO) {
        val normalized = normalizeRepoSlug(repoSlug)
            ?: return@withContext UpdateCheckState.Error("Enter a valid GitHub repository (owner/repo).")
        val endpoint = URL("https://api.github.com/repos/$normalized/releases/latest")
        var connection: HttpURLConnection? = null
        try {
            connection = (endpoint.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "Nepho-Android/$currentVersionName")
            }
            val code = connection.responseCode
            when (code) {
                HttpURLConnection.HTTP_OK -> {
                    val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    parseLatestReleaseJson(body, currentVersionName, normalized)
                }
                HttpURLConnection.HTTP_NOT_FOUND -> UpdateCheckState.Error(
                    "No published releases found for $normalized yet. Push to main to build your first release."
                )
                HttpURLConnection.HTTP_FORBIDDEN -> UpdateCheckState.Error(
                    "GitHub API rate limit reached or repository is private. Open Releases in your browser instead."
                )
                else -> UpdateCheckState.Error(
                    "GitHub returned HTTP $code while checking $normalized."
                )
            }
        } catch (e: IOException) {
            UpdateCheckState.Error(
                "Couldn't reach GitHub. Check your internet connection and try again."
            )
        } catch (e: Exception) {
            UpdateCheckState.Error(e.message ?: "Unexpected error checking for updates.")
        } finally {
            connection?.disconnect()
        }
    }
}
