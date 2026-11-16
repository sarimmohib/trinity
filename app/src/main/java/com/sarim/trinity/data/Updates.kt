package com.sarim.trinity.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.sarim.trinity.BuildConfig
import com.sarim.trinity.MainActivity
import com.sarim.trinity.R
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Where Trinity lives on GitHub, and the one way the app opens a link. */
object Links {
    const val REPO = "sarimmohib/trinity"
    const val REPO_URL = "https://github.com/$REPO"

    /** GitHub has no "star" deep link; the repo page is one tap from the Star button. */
    const val STAR_URL = REPO_URL
    const val ISSUES_URL = "$REPO_URL/issues/new/choose"
    const val FORK_URL = "$REPO_URL/fork"
    const val RELEASES_URL = "$REPO_URL/releases"

    fun open(context: Context, url: String) {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}

/** One published GitHub release, trimmed to what the overlay shows. */
data class Release(
    val version: String,
    val name: String,
    /** The release notes: what the author wrote as the changelog and fixes. */
    val notes: String,
    val pageUrl: String,
    val apkUrl: String?,
) {
    fun toJson(): String = JSONObject()
        .put("version", version)
        .put("name", name)
        .put("notes", notes)
        .put("page", pageUrl)
        .put("apk", apkUrl ?: JSONObject.NULL)
        .toString()

    companion object {
        fun fromJson(raw: String): Release? = runCatching {
            val o = JSONObject(raw)
            Release(
                version = o.getString("version"),
                name = o.optString("name"),
                notes = o.optString("notes"),
                pageUrl = o.optString("page", Links.RELEASES_URL),
                apkUrl = if (o.isNull("apk")) null else o.optString("apk").takeIf { it.isNotBlank() },
            )
        }.getOrNull()
    }
}

/**
 * Asks GitHub for the latest release of the repo and compares it with the version
 * this build was made as. Network work is blocking: call it off the main thread.
 */
class UpdateChecker(context: Context) {

    sealed interface Outcome {
        data object UpToDate : Outcome
        data class Available(val release: Release) : Outcome
        data class Failed(val reason: String) : Outcome
    }

    private val prefs = context.applicationContext
        .getSharedPreferences("trinity.updates", Context.MODE_PRIVATE)

    /** The newest release seen so far, if it is still newer than this build. */
    fun cached(): Release? =
        prefs.getString("release", null)?.let(Release::fromJson)?.takeIf { isNewer(it.version, current) }

    /** True when it has been long enough since the last successful look. */
    fun due(now: Long = System.currentTimeMillis()): Boolean =
        now - prefs.getLong("checkedAt", 0L) > CHECK_EVERY_MS

    /** Opt-in only: no automatic request is made until the user turns this on. */
    fun autoCheck(): Boolean = prefs.getBoolean("auto_check", false)

    fun setAutoCheck(enabled: Boolean) {
        prefs.edit().putBoolean("auto_check", enabled).apply()
    }

    fun isSkipped(version: String): Boolean = prefs.getString("skipped", null) == version

    fun skip(version: String) {
        prefs.edit().putString("skipped", version).apply()
    }

    fun wasNotified(version: String): Boolean = prefs.getString("notified", null) == version

    fun markNotified(version: String) {
        prefs.edit().putString("notified", version).apply()
    }

    fun check(): Outcome {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL("https://api.github.com/repos/${Links.REPO}/releases/latest")
                .openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "Trinity/${BuildConfig.VERSION_NAME}")
            }
            when (val code = connection.responseCode) {
                200 -> {
                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    val release = parse(JSONObject(body))
                        ?: return Outcome.Failed("Unreadable release")
                    prefs.edit()
                        .putLong("checkedAt", System.currentTimeMillis())
                        .putString("release", release.toJson())
                        .apply()
                    if (isNewer(release.version, current)) Outcome.Available(release) else Outcome.UpToDate
                }
                // The repo has not published a release yet: nothing to update to.
                404 -> {
                    prefs.edit().putLong("checkedAt", System.currentTimeMillis()).remove("release").apply()
                    Outcome.UpToDate
                }
                else -> Outcome.Failed("GitHub answered $code")
            }
        } catch (e: Exception) {
            Outcome.Failed(e.message ?: "No connection")
        } finally {
            connection?.disconnect()
        }
    }

    private fun parse(json: JSONObject): Release? {
        val tag = json.optString("tag_name").trim()
        if (tag.isBlank()) return null
        var apk: String? = null
        val assets = json.optJSONArray("assets")
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                if (asset.optString("name").endsWith(".apk", ignoreCase = true)) {
                    apk = asset.optString("browser_download_url").takeIf { it.isNotBlank() }
                    break
                }
            }
        }
        return Release(
            version = tag.removePrefix("v").removePrefix("V"),
            name = json.optString("name").ifBlank { tag },
            notes = json.optString("body").trim(),
            pageUrl = json.optString("html_url").ifBlank { Links.RELEASES_URL },
            apkUrl = apk,
        )
    }

    companion object {
        private const val CHECK_EVERY_MS = 12L * 60 * 60 * 1000

        /** The version this build was made as. */
        val current: String get() = BuildConfig.VERSION_NAME

        /** Compares dotted numbers (0.10.0 beats 0.9.0); suffixes like -beta are ignored. */
        fun isNewer(remote: String, local: String): Boolean {
            fun parts(v: String) = v.trim().removePrefix("v").removePrefix("V")
                .substringBefore('-').substringBefore('+')
                .split('.')
                .map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
            val a = parts(remote)
            val b = parts(local)
            for (i in 0 until maxOf(a.size, b.size)) {
                val x = a.getOrElse(i) { 0 }
                val y = b.getOrElse(i) { 0 }
                if (x != y) return x > y
            }
            return false
        }
    }
}

/** The quiet notification that tells the user a newer version exists. */
object UpdateNotifier {
    private const val CHANNEL = "updates"
    private const val ID = 7200

    fun show(context: Context, release: Release) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "App updates", NotificationManager.IMPORTANCE_LOW)
                    .apply { description = "A newer version of Trinity is on GitHub" },
            )
        }
        val open = PendingIntent.getActivity(
            context,
            ID,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val first = release.notes.lineSequence().map { it.trim() }.firstOrNull { it.isNotBlank() }
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Trinity ${release.version} is available")
            .setContentText(first ?: "Tap to see what's new")
            .setStyle(NotificationCompat.BigTextStyle().bigText(release.notes.ifBlank { "Tap to see what's new" }))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()
        runCatching { manager.notify(ID, notification) }
    }

    fun cancel(context: Context) {
        runCatching { context.getSystemService(NotificationManager::class.java)?.cancel(ID) }
    }
}
