package com.example

import com.example.data.UpdateManifestParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCenterRepositoryTest {

    @Test
    fun `isSecureHttpsUrl accepts https and rejects http`() {
        assertTrue(UpdateManifestParser.isSecureHttpsUrl("https://api.github.com/repos/a/b/releases/latest"))
        assertFalse(UpdateManifestParser.isSecureHttpsUrl("http://insecure.example.com/release.json"))
        assertFalse(UpdateManifestParser.isSecureHttpsUrl("file:///sdcard/app.apk"))
    }

    @Test
    fun `isValidSha256Hex validates 64 hex chars`() {
        assertTrue(UpdateManifestParser.isValidSha256Hex("a".repeat(64)))
        assertFalse(UpdateManifestParser.isValidSha256Hex("a".repeat(63)))
        assertFalse(UpdateManifestParser.isValidSha256Hex("z".repeat(64)))
    }

    @Test
    fun `parseManifestOrGitHubRelease parses GitHub Releases payload and sha256`() {
        val sha = "a".repeat(64)
        val githubJson = """
            {
              "tag_name": "v1.2.0",
              "published_at": "2026-12-01T10:00:00Z",
              "body": "- Improved Fast Pair parsing\n- SHA256: $sha",
              "assets": [
                {
                  "name": "NordBudsPremium-1.2.0-release.apk",
                  "browser_download_url": "https://github.com/example/releases/download/v1.2.0/app.apk"
                }
              ]
            }
        """.trimIndent()

        val parsed = UpdateManifestParser.parseManifestOrGitHubRelease(githubJson)
        assertNotNull(parsed)
        assertEquals("1.2.0", parsed?.versionName)
        assertEquals(10200, parsed?.versionCode)
        assertEquals(sha, parsed?.sha256)
        assertEquals("https://github.com/example/releases/download/v1.2.0/app.apk", parsed?.apkUrl)
    }

    @Test
    fun `parseManifestOrGitHubRelease parses direct manifest JSON`() {
        val sha = "b".repeat(64)
        val directJson = """
            {
              "versionCode": 2,
              "versionName": "1.1.0",
              "releaseDate": "2026-12-01",
              "apkUrl": "https://cdn.example.com/NordBudsPremium-1.1.0.apk",
              "sha256": "$sha",
              "minAndroidSdk": 24,
              "releaseNotes": ["New EQ preset"],
              "isCritical": false
            }
        """.trimIndent()

        val parsed = UpdateManifestParser.parseManifestOrGitHubRelease(directJson)
        assertNotNull(parsed)
        assertEquals(2, parsed?.versionCode)
        assertEquals("1.1.0", parsed?.versionName)
        assertEquals(sha, parsed?.sha256)
    }
}
